package com.heldertheking.exochat.app.identity;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/// Validates and normalizes what clients send for users and accounts. Problems are INVALID_ARGUMENT, a taken value
/// is ALREADY_EXISTS.
public final class IdentityInput {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.]{3,32}$");
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MAX_PHONE_LENGTH = 32;
    private static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private static final int MAX_AVATAR_URL_LENGTH = 2048;

    private IdentityInput() {}

    /// The trimmed, lowercase email, null if it is not a valid address.
    public static @Nullable String normalizeEmail(String email) {
        var normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= MAX_EMAIL_LENGTH
                        && EMAIL.matcher(normalized).matches()
                ? normalized
                : null;
    }

    /// @throws StatusRuntimeException INVALID_ARGUMENT if the email is not valid
    public static String requireEmail(String email) {
        var normalized = normalizeEmail(email);
        if (normalized == null) {
            throw invalid("email is not valid");
        }
        return normalized;
    }

    /// The trimmed, lowercase username.
    ///
    /// @throws StatusRuntimeException INVALID_ARGUMENT unless it has 3 to 32 characters of a-z, 0-9, _ and .
    public static String requireUsername(String username) {
        var normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(normalized).matches()) {
            throw invalid("username must be 3 to 32 characters of a-z, 0-9, _ and .");
        }
        return normalized;
    }

    /// The trimmed phone number, null if blank.
    public static @Nullable String optionalPhoneNumber(@Nullable String phoneNumber) {
        var phone = blankToNull(phoneNumber);
        if (phone != null && phone.length() > MAX_PHONE_LENGTH) {
            throw invalid("phone_number is too long");
        }
        return phone;
    }

    /// The trimmed display name, null if blank.
    public static @Nullable String optionalDisplayName(@Nullable String displayName) {
        var display = blankToNull(displayName);
        if (display != null && display.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw invalid("display_name is too long");
        }
        return display;
    }

    /// The trimmed avatar location, null if blank.
    public static @Nullable String optionalAvatarUrl(@Nullable String avatarUrl) {
        var url = blankToNull(avatarUrl);
        if (url != null && url.length() > MAX_AVATAR_URL_LENGTH) {
            throw invalid("avatar_url is too long");
        }
        return url;
    }

    /// Translates a violated unique constraint of the identity tables into ALREADY_EXISTS. Any other integrity
    /// violation is rethrown as it is.
    public static RuntimeException duplicate(DataIntegrityViolationException e) {
        var cause = String.valueOf(e.getMostSpecificCause().getMessage());
        if (cause.contains("uq_user_email")) {
            return Status.ALREADY_EXISTS
                    .withDescription("email is already registered")
                    .asRuntimeException();
        } else if (cause.contains("uq_user_phone_number")) {
            return Status.ALREADY_EXISTS
                    .withDescription("phone_number is already registered")
                    .asRuntimeException();
        } else if (cause.contains("uq_account_username")) {
            return Status.ALREADY_EXISTS.withDescription("username is taken").asRuntimeException();
        }
        return e;
    }

    public static StatusRuntimeException invalid(String description) {
        return Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
