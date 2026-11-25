package com.heldertheking.exochat.app.auth;

import static com.heldertheking.exochat.db.schema.Tables.SESSION;
import static com.heldertheking.exochat.db.schema.Tables.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.auth.v1alpha1.AuthServiceGrpc;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.RefreshSessionRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/// End to end: the gRPC server, the services and the MariaDB test database (docker compose up -d).
@SpringBootTest(properties = "spring.grpc.server.port=0", webEnvironment = WebEnvironment.NONE)
@Import(AuthGrpcServiceTest.ClockConfig.class)
class AuthGrpcServiceTest {
    private static final Instant START = Instant.parse("2026-11-25T10:00:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    /// A clock the tests move forward.
    static class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        void advance(Duration duration) {
            this.now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired
    private GrpcServerLifecycle lifecycle;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private AccessTokens accessTokens;

    @Autowired
    private MutableClock clock;

    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub auth;

    @BeforeEach
    void setUp() {
        clock.set(START);
        dsl.deleteFrom(USER).execute();
        channel = ManagedChannelBuilder.forAddress("localhost", lifecycle.getPort())
                .usePlaintext()
                .build();
        auth = AuthServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
    }

    private RegisterUserResponse register(String email, String username) {
        return auth.registerUser(RegisterUserRequest.newBuilder()
                .setEmail(email)
                .setPassword("correct horse battery")
                .setUsername(username)
                .setDeviceLabel("test device")
                .build());
    }

    private static Status.Code codeOf(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
        throw new AssertionError("expected the call to fail");
    }

    @Test
    void registerUser_should_createUserAccountSessionAndTokens() {
        var response = register("  Alice@Example.COM ", "Alice_1");

        assertThat(response.getUser().getName()).startsWith("users/");
        assertThat(response.getUser().getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getAccount().getName())
                .startsWith(response.getUser().getName() + "/accounts/");
        assertThat(response.getAccount().getUsername()).isEqualTo("alice_1");
        assertThat(response.getAccount().getDisplayName()).isEqualTo("alice_1");
        assertThat(response.getSession().getName())
                .startsWith(response.getUser().getName() + "/sessions/");
        assertThat(response.getSession().getAccount())
                .isEqualTo(response.getAccount().getName());
        assertThat(response.getSession().getDeviceLabel()).isEqualTo("test device");
        assertThat(response.getSession().getCurrent()).isTrue();

        var claims = accessTokens.verify(response.getTokens().getAccessToken());
        assertThat("users/" + claims.userId()).isEqualTo(response.getUser().getName());
        assertThat(response.getSession().getName()).endsWith("/sessions/" + claims.sessionId());
        assertThat(response.getAccount().getName()).endsWith("/accounts/" + claims.accountId());
        assertThat(response.getTokens().getRefreshToken()).isNotBlank();
        assertThat(response.getTokens().getRefreshTokenExpireTime().getSeconds())
                .isEqualTo(START.plus(Duration.ofDays(30)).getEpochSecond());
        assertThat(dsl.select(USER.PASSWORD_HASH).from(USER).fetchOne(USER.PASSWORD_HASH))
                .startsWith("$argon2id$");
    }

    @Test
    void registerUser_should_rejectBadInputAndDuplicates() {
        register("alice@example.com", "alice");

        assertThat(codeOf(() -> register("alice@example.com", "other"))).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(codeOf(() -> register("ALICE@example.com", "other"))).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(codeOf(() -> register("other@example.com", "Alice"))).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(codeOf(() -> register("not-an-email", "someone"))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> register("b@example.com", "no spaces"))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> register("c@example.com", "ab"))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> auth.registerUser(RegisterUserRequest.newBuilder()
                        .setEmail("d@example.com")
                        .setUsername("daniel")
                        .setPassword("short")
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(dsl.fetchCount(USER))
                .as("failed registrations leave nothing behind")
                .isEqualTo(1);
    }

    @Test
    void authenticateUser_should_logTheDeviceIn() {
        var registered = register("alice@example.com", "alice");

        var response = auth.authenticateUser(AuthenticateUserRequest.newBuilder()
                .setEmail("Alice@example.com")
                .setPassword("correct horse battery")
                .setDeviceLabel("second device")
                .build());

        assertThat(response.getUser().getName()).isEqualTo(registered.getUser().getName());
        assertThat(response.getSession().getName())
                .isNotEqualTo(registered.getSession().getName());
        assertThat(response.getSession().getAccount())
                .isEqualTo(registered.getAccount().getName());
        assertThat(accessTokens.verify(response.getTokens().getAccessToken()).accountId())
                .isNotNull();
        assertThat(dsl.fetchCount(SESSION)).isEqualTo(2);
    }

    @Test
    void authenticateUser_should_notTellUnknownEmailFromWrongPassword() {
        register("alice@example.com", "alice");

        var wrongPassword = statusOf(() -> auth.authenticateUser(AuthenticateUserRequest.newBuilder()
                .setEmail("alice@example.com")
                .setPassword("wrong password!")
                .build()));
        var unknownEmail = statusOf(() -> auth.authenticateUser(AuthenticateUserRequest.newBuilder()
                .setEmail("nobody@example.com")
                .setPassword("wrong password!")
                .build()));

        assertThat(wrongPassword.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(unknownEmail.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(unknownEmail.getDescription()).isEqualTo(wrongPassword.getDescription());
    }

    private static Status statusOf(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            return e.getStatus();
        }
        throw new AssertionError("expected the call to fail");
    }

    @Test
    void refreshSession_should_rotateTheRefreshToken() {
        var registered = register("alice@example.com", "alice");
        var firstRefresh = registered.getTokens().getRefreshToken();
        var sessionId =
                accessTokens.verify(registered.getTokens().getAccessToken()).sessionId();
        clock.advance(Duration.ofMinutes(20));

        var refreshed = auth.refreshSession(
                RefreshSessionRequest.newBuilder().setRefreshToken(firstRefresh).build());

        assertThat(refreshed.getSession().getName())
                .isEqualTo(registered.getSession().getName());
        assertThat(refreshed.getTokens().getRefreshToken()).isNotEqualTo(firstRefresh);
        assertThat(refreshed.getTokens().getAccessToken())
                .isNotEqualTo(registered.getTokens().getAccessToken());
        assertThat(accessTokens.verify(refreshed.getTokens().getAccessToken()).sessionId())
                .isEqualTo(sessionId);
        assertThat(refreshed.getSession().getLastUsedTime().getSeconds())
                .isEqualTo(START.plus(Duration.ofMinutes(20)).getEpochSecond());
        // the new token keeps working
        auth.refreshSession(RefreshSessionRequest.newBuilder()
                .setRefreshToken(refreshed.getTokens().getRefreshToken())
                .build());
    }

    @Test
    void refreshSession_should_revokeTheSession_when_aSpentTokenIsUsedAgain() {
        var registered = register("alice@example.com", "alice");
        var spent = registered.getTokens().getRefreshToken();
        var refreshed = auth.refreshSession(
                RefreshSessionRequest.newBuilder().setRefreshToken(spent).build());

        assertThat(codeOf(() -> auth.refreshSession(RefreshSessionRequest.newBuilder()
                        .setRefreshToken(spent)
                        .build())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);

        assertThat(dsl.fetchCount(SESSION)).as("the session is gone").isZero();
        assertThat(codeOf(() -> auth.refreshSession(RefreshSessionRequest.newBuilder()
                        .setRefreshToken(refreshed.getTokens().getRefreshToken())
                        .build())))
                .as("the thief and the owner are both logged out")
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void refreshSession_should_rejectUnknownTokens() {
        assertThat(codeOf(() -> auth.refreshSession(RefreshSessionRequest.newBuilder()
                        .setRefreshToken(UuidV7.next().toString())
                        .build())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(codeOf(() -> auth.refreshSession(RefreshSessionRequest.getDefaultInstance())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void refreshSession_should_slideTheExpiryButNeverPassTheAbsoluteCap() {
        var token = register("alice@example.com", "alice").getTokens().getRefreshToken();

        // refreshed every 20 days: idle expiry (30 days) never hits, the absolute cap of 90 days does
        for (int day = 20; day <= 80; day += 20) {
            clock.set(START.plus(Duration.ofDays(day)));
            token = auth.refreshSession(RefreshSessionRequest.newBuilder()
                            .setRefreshToken(token)
                            .build())
                    .getTokens()
                    .getRefreshToken();
        }
        var last = token;
        clock.set(START.plus(Duration.ofDays(91)));

        assertThat(codeOf(() -> auth.refreshSession(
                        RefreshSessionRequest.newBuilder().setRefreshToken(last).build())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(dsl.fetchCount(SESSION)).as("an expired session is removed").isZero();
    }

    @Test
    void refreshSession_should_rejectAnIdleSession() {
        var token = register("alice@example.com", "alice").getTokens().getRefreshToken();
        clock.advance(Duration.ofDays(31));

        assertThat(codeOf(() -> auth.refreshSession(RefreshSessionRequest.newBuilder()
                        .setRefreshToken(token)
                        .build())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void unimplementedMethods_should_answerUnimplemented() {
        assertThatThrownBy(() -> auth.changePassword(
                        com.heldertheking.exochat.auth.v1alpha1.ChangePasswordRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED));
    }
}
