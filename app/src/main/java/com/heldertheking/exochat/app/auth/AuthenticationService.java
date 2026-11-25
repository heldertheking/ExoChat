package com.heldertheking.exochat.app.auth;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.SESSION;
import static com.heldertheking.exochat.db.schema.Tables.USER;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.app.identity.IdentityInput;
import com.heldertheking.exochat.app.permission.Standing;
import com.heldertheking.exochat.db.schema.tables.records.AccountRecord;
import com.heldertheking.exochat.db.schema.tables.records.SessionRecord;
import com.heldertheking.exochat.db.schema.tables.records.UserRecord;

import io.grpc.Status;

/// Registers users and creates, renews and checks their sessions (ADR-012). Problems are reported as
/// [io.grpc.StatusRuntimeException] with the gRPC status the caller should see.
@Service
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_DEVICE_LABEL_LENGTH = 100;

    private final DSLContext dsl;
    private final PasswordHasher passwordHasher;
    private final AccessTokens accessTokens;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthenticationService(
            DSLContext dsl,
            PasswordHasher passwordHasher,
            AccessTokens accessTokens,
            AuthProperties properties,
            Clock clock) {
        this.dsl = dsl;
        this.passwordHasher = passwordHasher;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.clock = clock;
    }

    /// Creates a user with its first account and a session.
    ///
    /// @throws io.grpc.StatusRuntimeException INVALID_ARGUMENT for bad input, ALREADY_EXISTS if the email, phone
    ///         number or username is taken
    public Authenticated register(
            String email,
            @Nullable String phoneNumber,
            String password,
            String username,
            @Nullable String displayName,
            @Nullable String deviceLabel) {
        var normalizedEmail = IdentityInput.requireEmail(email);
        var normalizedUsername = IdentityInput.requireUsername(username);
        var phone = IdentityInput.optionalPhoneNumber(phoneNumber);
        var display = IdentityInput.optionalDisplayName(displayName);
        if (!PasswordHasher.isAcceptable(password)) {
            throw invalid("password must have between %d and %d characters"
                    .formatted(PasswordHasher.MIN_LENGTH, PasswordHasher.MAX_LENGTH));
        }
        var passwordHash = passwordHasher.hash(password);
        var now = now();
        try {
            return dsl.transactionResult(configuration -> {
                var tx = configuration.dsl();
                var user = tx.newRecord(USER);
                user.setId(UuidV7.next());
                user.setEmail(normalizedEmail);
                user.setPhoneNumber(phone);
                user.setPasswordHash(passwordHash);
                user.setCreateTime(now);
                user.insert();
                var account = tx.newRecord(ACCOUNT);
                account.setId(UuidV7.next());
                account.setUserId(user.getId());
                account.setUsername(normalizedUsername);
                account.setDisplayName(display == null ? normalizedUsername : display);
                account.setStanding(Standing.MEMBER.name());
                account.setCreateTime(now);
                account.insert();
                return createSession(tx, user, account, deviceLabel, now);
            });
        } catch (DataIntegrityViolationException e) {
            throw IdentityInput.duplicate(e);
        }
    }

    /// Logs a device in with email and password.
    ///
    /// @throws io.grpc.StatusRuntimeException UNAUTHENTICATED for an unknown email or a wrong password alike
    public Authenticated authenticate(String email, String password, @Nullable String deviceLabel) {
        var normalizedEmail = IdentityInput.normalizeEmail(email);
        var user = normalizedEmail == null
                ? null
                : dsl.selectFrom(USER).where(USER.EMAIL.eq(normalizedEmail)).fetchOne();
        if (password.length() > PasswordHasher.MAX_LENGTH * 4) {
            // far beyond the policy, nobody can have such a password; do not feed it to the hash function
            throw badCredentials();
        }
        if (user == null) {
            passwordHasher.spendVerificationTime(password);
            throw badCredentials();
        }
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw badCredentials();
        }
        var now = now();
        return dsl.transactionResult(configuration -> {
            var tx = configuration.dsl();
            return createSession(tx, user, firstAccount(tx, user.getId()), deviceLabel, now);
        });
    }

    /// Spends a refresh token: returns a new access token and a new refresh token for the same session. A token that
    /// was already spent revokes the session, see ADR-012.
    ///
    /// @throws io.grpc.StatusRuntimeException UNAUTHENTICATED if the token is unknown, expired or was already spent
    public Authenticated refresh(String refreshToken) {
        var hash = RefreshTokens.hash(refreshToken);
        var now = now();
        // The rejections are returned, not thrown: throwing would roll back the deletion of a revoked session.
        var outcome = dsl.transactionResult(configuration -> {
            var tx = configuration.dsl();
            var session = tx.selectFrom(SESSION)
                    .where(SESSION.REFRESH_TOKEN_HASH.eq(hash))
                    .forUpdate()
                    .fetchOne();
            if (session == null) {
                var replaced = tx.selectFrom(SESSION)
                        .where(SESSION.PREVIOUS_REFRESH_TOKEN_HASH.eq(hash))
                        .forUpdate()
                        .fetchOne();
                if (replaced == null) {
                    return new Refresh.Rejected("unknown refresh token");
                }
                replaced.delete();
                return new Refresh.Rejected(
                        "spent refresh token presented again, session " + replaced.getId() + " revoked");
            }
            if (!session.getExpireTime().isAfter(now)
                    || !session.getAbsoluteExpireTime().isAfter(now)) {
                session.delete();
                return new Refresh.Rejected("session expired");
            }
            var user =
                    tx.selectFrom(USER).where(USER.ID.eq(session.getUserId())).fetchSingle();
            var account = session.getAccountId() == null
                    ? firstAccount(tx, user.getId())
                    : tx.selectFrom(ACCOUNT)
                            .where(ACCOUNT.ID.eq(session.getAccountId()))
                            .fetchOne();
            var next = RefreshTokens.generate();
            session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
            session.setRefreshTokenHash(next.hash());
            session.setAccountId(account == null ? null : account.getId());
            session.setLastUsedTime(now);
            session.setExpireTime(earlier(now.plus(properties.refreshTokenIdleTtl()), session.getAbsoluteExpireTime()));
            session.update();
            return new Refresh.Done(authenticated(user, account, session, next.token()));
        });
        return switch (outcome) {
            case Refresh.Done done -> done.authenticated();
            case Refresh.Rejected rejected -> {
                LOG.warn("refresh token rejected: {}", rejected.reason());
                throw Status.UNAUTHENTICATED
                        .withDescription("invalid refresh token")
                        .asRuntimeException();
            }
        };
    }

    /// Makes the account the active one of the session of the caller and issues an access token for it.
    ///
    /// @throws io.grpc.StatusRuntimeException NOT_FOUND if the user has no such account, UNAUTHENTICATED if the
    ///         session was revoked in the meantime
    public SwitchedAccount switchAccount(UUID userId, UUID sessionId, UUID accountId) {
        return dsl.transactionResult(configuration -> {
            var tx = configuration.dsl();
            var account = tx.selectFrom(ACCOUNT)
                    .where(ACCOUNT.ID.eq(accountId).and(ACCOUNT.USER_ID.eq(userId)))
                    .fetchOne();
            if (account == null) {
                throw Status.NOT_FOUND.withDescription("account not found").asRuntimeException();
            }
            var session = tx.selectFrom(SESSION)
                    .where(SESSION.ID.eq(sessionId).and(SESSION.USER_ID.eq(userId)))
                    .forUpdate()
                    .fetchOne();
            if (session == null) {
                throw Status.UNAUTHENTICATED
                        .withDescription("session was revoked")
                        .asRuntimeException();
            }
            session.setAccountId(account.getId());
            session.update();
            return new SwitchedAccount(session, accessTokens.issue(userId, session.getId(), account.getId()));
        });
    }

    /// Changes the password of the user of the caller and revokes every other session of it.
    ///
    /// @throws io.grpc.StatusRuntimeException PERMISSION_DENIED if the old password is wrong, INVALID_ARGUMENT if
    ///         the new one is not acceptable or equals the old one
    public void changePassword(UUID userId, UUID sessionId, String oldPassword, String newPassword) {
        var user = dsl.selectFrom(USER).where(USER.ID.eq(userId)).fetchOne();
        if (user == null) {
            throw Status.UNAUTHENTICATED.withDescription("user does not exist").asRuntimeException();
        }
        if (oldPassword.length() > PasswordHasher.MAX_LENGTH * 4
                || !passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("old password is wrong")
                    .asRuntimeException();
        }
        if (!PasswordHasher.isAcceptable(newPassword)) {
            throw invalid("new_password must have between %d and %d characters"
                    .formatted(PasswordHasher.MIN_LENGTH, PasswordHasher.MAX_LENGTH));
        }
        if (newPassword.equals(oldPassword)) {
            throw invalid("new_password must differ from the old password");
        }
        var newHash = passwordHasher.hash(newPassword);
        dsl.transaction(configuration -> {
            var tx = configuration.dsl();
            tx.update(USER)
                    .set(USER.PASSWORD_HASH, newHash)
                    .where(USER.ID.eq(userId))
                    .execute();
            tx.deleteFrom(SESSION)
                    .where(SESSION.USER_ID.eq(userId).and(SESSION.ID.ne(sessionId)))
                    .execute();
        });
    }

    private Authenticated createSession(
            DSLContext tx,
            UserRecord user,
            @Nullable AccountRecord account,
            @Nullable String deviceLabel,
            LocalDateTime now) {
        var refreshToken = RefreshTokens.generate();
        var session = tx.newRecord(SESSION);
        session.setId(UuidV7.next());
        session.setUserId(user.getId());
        session.setAccountId(account == null ? null : account.getId());
        var label = blankToNull(deviceLabel);
        session.setDeviceLabel(label == null ? null : truncate(label, MAX_DEVICE_LABEL_LENGTH));
        session.setRefreshTokenHash(refreshToken.hash());
        session.setCreateTime(now);
        session.setLastUsedTime(now);
        session.setAbsoluteExpireTime(now.plus(properties.refreshTokenMaxTtl()));
        session.setExpireTime(earlier(now.plus(properties.refreshTokenIdleTtl()), session.getAbsoluteExpireTime()));
        session.insert();
        return authenticated(user, account, session, refreshToken.token());
    }

    private Authenticated authenticated(
            UserRecord user, @Nullable AccountRecord account, SessionRecord session, String refreshToken) {
        var accessToken = accessTokens.issue(user.getId(), session.getId(), account == null ? null : account.getId());
        return new Authenticated(
                user,
                account,
                session,
                accessToken.token(),
                accessToken.expireTime(),
                refreshToken,
                session.getExpireTime().toInstant(ZoneOffset.UTC));
    }

    private static @Nullable AccountRecord firstAccount(DSLContext tx, UUID userId) {
        return tx.selectFrom(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .orderBy(ACCOUNT.ID)
                .limit(1)
                .fetchOne();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static io.grpc.StatusRuntimeException invalid(String description) {
        return IdentityInput.invalid(description);
    }

    private static io.grpc.StatusRuntimeException badCredentials() {
        return Status.UNAUTHENTICATED
                .withDescription("invalid email or password")
                .asRuntimeException();
    }

    /// A logged in session: the user, its active account (null if the user has none), the session row and the tokens.
    public record Authenticated(
            UserRecord user,
            @Nullable AccountRecord account,
            SessionRecord session,
            String accessToken,
            Instant accessTokenExpireTime,
            String refreshToken,
            Instant refreshTokenExpireTime) {}

    /// The session with its new active account and the access token for it.
    public record SwitchedAccount(SessionRecord session, AccessTokens.IssuedAccessToken accessToken) {}

    private sealed interface Refresh {
        record Done(Authenticated authenticated) implements Refresh {}

        record Rejected(String reason) implements Refresh {}
    }
}
