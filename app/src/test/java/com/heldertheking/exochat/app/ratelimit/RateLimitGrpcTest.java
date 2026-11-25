package com.heldertheking.exochat.app.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.heldertheking.exochat.app.GrpcIntegrationTestSupport;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.ChangePasswordRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;
import com.heldertheking.exochat.identity.v1alpha1.ListSessionsRequest;
import com.heldertheking.exochat.identity.v1alpha1.SessionServiceGrpc;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/// End to end: the limiter in the interceptor chain of the real gRPC server, with tight limits and the mutable
/// clock. The store lives as long as the cached application context, so every test runs on a day of its own and never
/// sees the counters of another.
@SpringBootTest(
        properties = {
            "exochat.ratelimit.enabled=true",
            "exochat.ratelimit.policies.register.limit=100",
            "exochat.ratelimit.policies.login.limit=10",
            "exochat.ratelimit.policies.login.request-key-limit=3",
            "exochat.ratelimit.policies.change-password.limit=2",
            "exochat.ratelimit.policies.change-password.window=1m",
        })
class RateLimitGrpcTest extends GrpcIntegrationTestSupport {
    private static final Metadata.Key<String> RETRY_AFTER =
            Metadata.Key.of("retry-after", Metadata.ASCII_STRING_MARSHALLER);

    // the base class resets the clock to START before every test, so each test moves to a day of its own
    private static final AtomicInteger NEXT_DAY = new AtomicInteger();

    private SessionServiceGrpc.SessionServiceBlockingStub sessions;

    @BeforeEach
    void freshCounters() {
        clock.advance(Duration.ofDays(NEXT_DAY.incrementAndGet()));
        sessions = SessionServiceGrpc.newBlockingStub(channel);
    }

    private static Status.Code outcome(Runnable call) {
        try {
            call.run();
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    private Status.Code loginAttempt(String email) {
        return outcome(() -> auth.authenticateUser(AuthenticateUserRequest.newBuilder()
                .setEmail(email)
                .setPassword("wrong password")
                .setDeviceLabel("phone")
                .build()));
    }

    private Status.Code changePassword(RegisterUserResponse user) {
        return outcome(() -> bearer(auth, user.getTokens().getAccessToken())
                .changePassword(ChangePasswordRequest.newBuilder()
                        .setName(user.getUser().getName())
                        .setOldPassword("not my password")
                        .setNewPassword("another correct horse")
                        .build()));
    }

    @Test
    void aPeerPolicyBlocksTheCallAfterTheLimit() {
        for (int i = 0; i < 10; i++) {
            assertThat(loginAttempt("nobody" + i + "@example.com")).isEqualTo(Status.Code.UNAUTHENTICATED);
        }

        assertThat(loginAttempt("nobody10@example.com")).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
    }

    @Test
    void theRejectionIsGenericAndTellsWhenToRetry() {
        for (int i = 0; i < 10; i++) {
            loginAttempt("nobody" + i + "@example.com");
        }
        clock.advance(Duration.ofSeconds(100));

        var e = assertThrows(StatusRuntimeException.class, () -> login("x@example.com", "p"));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(e.getStatus().getDescription()).isEqualTo("too many requests");
        // login window is 5 minutes, 100 seconds have passed
        assertThat(Status.trailersFromThrowable(e).get(RETRY_AFTER)).isEqualTo("200");
    }

    @Test
    void theCounterResetsAfterTheWindow() {
        for (int i = 0; i < 10; i++) {
            loginAttempt("nobody" + i + "@example.com");
        }
        assertThat(loginAttempt("again@example.com")).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);

        clock.advance(Duration.ofMinutes(5));

        assertThat(loginAttempt("again@example.com")).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void theEmailAddressOfTheRequestIsASecondKey() {
        for (int i = 0; i < 3; i++) {
            assertThat(loginAttempt("Victim@Example.com")).isEqualTo(Status.Code.UNAUTHENTICATED);
        }

        // another spelling of the same address shares the counter, another address does not
        assertThat(loginAttempt(" victim@example.com ")).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(loginAttempt("other@example.com")).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void aUserPolicyIsPerUser() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");

        assertThat(changePassword(alice)).isNotEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(changePassword(alice)).isNotEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(changePassword(alice)).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);

        assertThat(changePassword(bob)).isNotEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(changePassword(bob)).isNotEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(changePassword(bob)).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);

        clock.advance(Duration.ofSeconds(61));

        assertThat(changePassword(alice)).isNotEqualTo(Status.Code.RESOURCE_EXHAUSTED);
    }

    @Test
    void theLimiterRunsAfterAuthenticationSoCallsWithoutATokenNeverUseUpTheirTurn() {
        var alice = register("alice@example.com", "alice");

        for (int i = 0; i < 5; i++) {
            var e = assertThrows(
                    StatusRuntimeException.class,
                    () -> auth.changePassword(ChangePasswordRequest.newBuilder()
                            .setName(alice.getUser().getName())
                            .setOldPassword(PASSWORD)
                            .setNewPassword("another correct horse")
                            .build()));
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        }

        assertThat(changePassword(alice)).isNotEqualTo(Status.Code.RESOURCE_EXHAUSTED);
    }

    @Test
    void methodsWithoutTheAnnotationAreNotLimited() {
        var alice = register("alice@example.com", "alice");
        var request = ListSessionsRequest.newBuilder()
                .setParent(alice.getUser().getName())
                .build();

        for (int i = 0; i < 50; i++) {
            assertThat(outcome(() ->
                            bearer(sessions, alice.getTokens().getAccessToken()).listSessions(request)))
                    .isEqualTo(Status.Code.OK);
        }
    }
}
