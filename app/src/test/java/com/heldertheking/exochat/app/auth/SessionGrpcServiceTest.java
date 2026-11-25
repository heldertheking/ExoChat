package com.heldertheking.exochat.app.auth;

import static com.heldertheking.exochat.db.schema.Tables.SESSION;
import static com.heldertheking.exochat.db.schema.Tables.USER;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

import com.heldertheking.exochat.auth.v1alpha1.AuthServiceGrpc;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.ChangePasswordRequest;
import com.heldertheking.exochat.auth.v1alpha1.RefreshSessionRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;
import com.heldertheking.exochat.health.v1alpha1.CheckRequest;
import com.heldertheking.exochat.health.v1alpha1.HealthServiceGrpc;
import com.heldertheking.exochat.identity.v1alpha1.DeleteSessionRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetSessionRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListSessionsRequest;
import com.heldertheking.exochat.identity.v1alpha1.Session;
import com.heldertheking.exochat.identity.v1alpha1.SessionServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

/// The auth interceptor and the session service end to end, against the MariaDB test database.
@SpringBootTest(properties = "spring.grpc.server.port=0", webEnvironment = WebEnvironment.NONE)
@Import(AuthGrpcServiceTest.ClockConfig.class)
class SessionGrpcServiceTest {
    private static final Instant START = Instant.parse("2026-11-25T10:00:00Z");
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private GrpcServerLifecycle lifecycle;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private AuthGrpcServiceTest.MutableClock clock;

    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub auth;
    private SessionServiceGrpc.SessionServiceBlockingStub sessions;

    @BeforeEach
    void setUp() {
        clock.set(START);
        dsl.deleteFrom(USER).execute();
        channel = ManagedChannelBuilder.forAddress("localhost", lifecycle.getPort())
                .usePlaintext()
                .build();
        auth = AuthServiceGrpc.newBlockingStub(channel);
        sessions = SessionServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
    }

    private static <S extends AbstractStub<S>> S bearer(S stub, String accessToken) {
        var headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + accessToken);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static Status.Code codeOf(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
        throw new AssertionError("expected the call to fail");
    }

    private RegisterUserResponse register(String email, String username) {
        return auth.registerUser(RegisterUserRequest.newBuilder()
                .setEmail(email)
                .setPassword(PASSWORD)
                .setUsername(username)
                .setDeviceLabel("phone")
                .build());
    }

    private com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserResponse login(String email, String device) {
        return auth.authenticateUser(AuthenticateUserRequest.newBuilder()
                .setEmail(email)
                .setPassword(PASSWORD)
                .setDeviceLabel(device)
                .build());
    }

    @Test
    void protectedMethods_should_requireAValidAccessToken() {
        var registered = register("alice@example.com", "alice");
        var request = ListSessionsRequest.newBuilder()
                .setParent(registered.getUser().getName())
                .build();

        assertThat(codeOf(() -> sessions.listSessions(request))).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(codeOf(() -> bearer(sessions, "garbage").listSessions(request)))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(bearer(sessions, registered.getTokens().getAccessToken())
                        .listSessions(request)
                        .getSessionsCount())
                .isEqualTo(1);

        clock.advance(Duration.ofMinutes(16));
        assertThat(codeOf(() -> bearer(sessions, registered.getTokens().getAccessToken())
                        .listSessions(request)))
                .as("access tokens expire after 15 minutes")
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void publicMethods_should_workWithoutAToken() {
        assertThat(HealthServiceGrpc.newBlockingStub(channel)
                        .check(CheckRequest.getDefaultInstance())
                        .getStatus())
                .isEqualTo("ok");
        register("alice@example.com", "alice");
        login("alice@example.com", "laptop");
    }

    @Test
    void listSessions_should_listPageAndMarkTheCurrentSession() {
        var registered = register("alice@example.com", "alice");
        clock.advance(Duration.ofHours(1));
        login("alice@example.com", "laptop");
        clock.advance(Duration.ofHours(1));
        var latest = login("alice@example.com", "tablet");
        var user = registered.getUser().getName();
        var stub = bearer(sessions, latest.getTokens().getAccessToken());

        var all = stub.listSessions(
                ListSessionsRequest.newBuilder().setParent(user).build());

        assertThat(all.getSessionsList())
                .extracting(Session::getDeviceLabel)
                .containsExactly("phone", "laptop", "tablet");
        assertThat(all.getSessionsList()).extracting(Session::getCurrent).containsExactly(false, false, true);
        assertThat(all.getNextPageToken()).isEmpty();

        var labels = new ArrayList<String>();
        var token = "";
        int pages = 0;
        do {
            var page = stub.listSessions(ListSessionsRequest.newBuilder()
                    .setParent(user)
                    .setPageSize(2)
                    .setOrderBy("create_time desc")
                    .setPageToken(token)
                    .build());
            page.getSessionsList().forEach(s -> labels.add(s.getDeviceLabel()));
            token = page.getNextPageToken();
            pages++;
        } while (!token.isEmpty() && pages < 5);
        assertThat(labels).containsExactly("tablet", "laptop", "phone");

        var filtered = stub.listSessions(ListSessionsRequest.newBuilder()
                .setParent(user)
                .setFilter("device_label = 'laptop'")
                .build());
        assertThat(filtered.getSessionsList())
                .extracting(Session::getDeviceLabel)
                .containsExactly("laptop");
    }

    @Test
    void listSessions_should_rejectBadQueriesAndOtherUsers() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var stub = bearer(sessions, alice.getTokens().getAccessToken());
        var parent = alice.getUser().getName();

        assertThat(codeOf(() -> stub.listSessions(ListSessionsRequest.newBuilder()
                        .setParent(parent)
                        .setFilter("nonsense ===")
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.listSessions(ListSessionsRequest.newBuilder()
                        .setParent(parent)
                        .setOrderBy("password_hash")
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.listSessions(ListSessionsRequest.newBuilder()
                        .setParent(parent)
                        .setPageToken("bogus")
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.listSessions(ListSessionsRequest.newBuilder()
                        .setParent("users/not-a-uuid")
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.listSessions(ListSessionsRequest.newBuilder()
                        .setParent(bob.getUser().getName())
                        .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void getSession_should_returnOwnSessionsOnly() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var aliceStub = bearer(sessions, alice.getTokens().getAccessToken());
        var bobStub = bearer(sessions, bob.getTokens().getAccessToken());

        var session = aliceStub.getSession(GetSessionRequest.newBuilder()
                .setName(alice.getSession().getName())
                .build());
        assertThat(session.getName()).isEqualTo(alice.getSession().getName());
        assertThat(session.getCurrent()).isTrue();
        assertThat(session.getAccount()).isEqualTo(alice.getAccount().getName());

        assertThat(codeOf(() -> bobStub.getSession(GetSessionRequest.newBuilder()
                        .setName(alice.getSession().getName())
                        .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        var missing = alice.getUser().getName() + "/sessions/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01";
        assertThat(codeOf(() -> aliceStub.getSession(
                        GetSessionRequest.newBuilder().setName(missing).build())))
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> aliceStub.getSession(
                        GetSessionRequest.newBuilder().setName("nope").build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void deleteSession_should_revokeTheRefreshToken() {
        var phone = register("alice@example.com", "alice");
        var laptop = login("alice@example.com", "laptop");
        var stub = bearer(sessions, laptop.getTokens().getAccessToken());

        stub.deleteSession(DeleteSessionRequest.newBuilder()
                .setName(phone.getSession().getName())
                .build());

        assertThat(dsl.fetchCount(SESSION)).isEqualTo(1);
        assertThat(codeOf(() -> auth.refreshSession(RefreshSessionRequest.newBuilder()
                        .setRefreshToken(phone.getTokens().getRefreshToken())
                        .build())))
                .as("the revoked session can not be refreshed")
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(codeOf(() -> stub.deleteSession(DeleteSessionRequest.newBuilder()
                        .setName(phone.getSession().getName())
                        .build())))
                .isEqualTo(Status.Code.NOT_FOUND);

        // logging out the current device
        stub.deleteSession(DeleteSessionRequest.newBuilder()
                .setName(laptop.getSession().getName())
                .build());
        assertThat(dsl.fetchCount(SESSION)).isZero();
    }

    @Test
    void deleteSession_should_notRevokeSessionsOfOtherUsers() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");

        assertThat(codeOf(() -> bearer(sessions, bob.getTokens().getAccessToken())
                        .deleteSession(DeleteSessionRequest.newBuilder()
                                .setName(alice.getSession().getName())
                                .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(dsl.fetchCount(SESSION)).isEqualTo(2);
    }

    @Test
    void changePassword_should_beUnimplementedForAuthenticatedCallers() {
        var alice = register("alice@example.com", "alice");

        assertThat(codeOf(() -> auth.changePassword(ChangePasswordRequest.getDefaultInstance())))
                .as("not public")
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(codeOf(() -> bearer(auth, alice.getTokens().getAccessToken())
                        .changePassword(ChangePasswordRequest.getDefaultInstance())))
                .isEqualTo(Status.Code.UNIMPLEMENTED);
    }
}
