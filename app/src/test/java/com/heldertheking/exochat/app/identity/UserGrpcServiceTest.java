package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.SESSION;
import static com.heldertheking.exochat.db.schema.Tables.USER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.protobuf.FieldMask;
import com.heldertheking.exochat.app.GrpcIntegrationTestSupport;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.DeleteUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.UpdateUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.User;
import com.heldertheking.exochat.identity.v1alpha1.UserServiceGrpc;

import io.grpc.Status;

class UserGrpcServiceTest extends GrpcIntegrationTestSupport {

    private UserServiceGrpc.UserServiceBlockingStub users(String accessToken) {
        return bearer(UserServiceGrpc.newBlockingStub(channel), accessToken);
    }

    private static FieldMask mask(String... paths) {
        return FieldMask.newBuilder().addAllPaths(java.util.List.of(paths)).build();
    }

    @Test
    void getUser_should_returnTheOwnUserOnly() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");

        var user = users(alice.getTokens().getAccessToken())
                .getUser(GetUserRequest.newBuilder()
                        .setName(alice.getUser().getName())
                        .build());

        assertThat(user.getName()).isEqualTo(alice.getUser().getName());
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(codeOf(() -> users(alice.getTokens().getAccessToken())
                        .getUser(GetUserRequest.newBuilder()
                                .setName(bob.getUser().getName())
                                .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(codeOf(() -> users(alice.getTokens().getAccessToken())
                        .getUser(GetUserRequest.newBuilder().setName("users/-").build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> UserServiceGrpc.newBlockingStub(channel)
                        .getUser(GetUserRequest.newBuilder()
                                .setName(alice.getUser().getName())
                                .build())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void updateUser_should_changeEmailAndPhoneNumberByMask() {
        var alice = register("alice@example.com", "alice");
        var stub = users(alice.getTokens().getAccessToken());
        var name = alice.getUser().getName();

        var updated = stub.updateUser(UpdateUserRequest.newBuilder()
                .setUser(User.newBuilder()
                        .setName(name)
                        .setEmail("  New@Example.COM ")
                        .setPhoneNumber("+351000000001"))
                .setUpdateMask(mask("email", "phone_number"))
                .build());

        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getPhoneNumber()).isEqualTo("+351000000001");
        login("new@example.com", "again");
        assertThat(codeOf(() -> login("alice@example.com", "old")))
                .as("the old email no longer logs in")
                .isEqualTo(Status.Code.UNAUTHENTICATED);

        var cleared = stub.updateUser(UpdateUserRequest.newBuilder()
                .setUser(User.newBuilder().setName(name))
                .setUpdateMask(mask("phone_number"))
                .build());
        assertThat(cleared.getPhoneNumber()).isEmpty();
        assertThat(cleared.getEmail()).as("untouched").isEqualTo("new@example.com");
    }

    @Test
    void updateUser_should_updateThePopulatedFieldsWithoutAMask() {
        var alice = register("alice@example.com", "alice");

        var updated = users(alice.getTokens().getAccessToken())
                .updateUser(UpdateUserRequest.newBuilder()
                        .setUser(User.newBuilder()
                                .setName(alice.getUser().getName())
                                .setPhoneNumber("+351000000002"))
                        .build());

        assertThat(updated.getPhoneNumber()).isEqualTo("+351000000002");
        assertThat(updated.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void updateUser_should_rejectTakenInvalidAndImmutableValues() {
        var alice = register("alice@example.com", "alice");
        register("bob@example.com", "bob");
        var stub = users(alice.getTokens().getAccessToken());
        var name = alice.getUser().getName();

        assertThat(codeOf(() -> stub.updateUser(UpdateUserRequest.newBuilder()
                        .setUser(User.newBuilder().setName(name).setEmail("BOB@example.com"))
                        .setUpdateMask(mask("email"))
                        .build())))
                .isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(codeOf(() -> stub.updateUser(UpdateUserRequest.newBuilder()
                        .setUser(User.newBuilder().setName(name).setEmail("nope"))
                        .setUpdateMask(mask("email"))
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.updateUser(UpdateUserRequest.newBuilder()
                        .setUser(User.newBuilder().setName(name).setEmail(""))
                        .setUpdateMask(mask("email"))
                        .build())))
                .as("the email can not be removed")
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.updateUser(UpdateUserRequest.newBuilder()
                        .setUser(User.newBuilder().setName(name))
                        .setUpdateMask(mask("create_time"))
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void updateUser_should_notChangeAnotherUser() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");

        assertThat(codeOf(() -> users(alice.getTokens().getAccessToken())
                        .updateUser(UpdateUserRequest.newBuilder()
                                .setUser(User.newBuilder()
                                        .setName(bob.getUser().getName())
                                        .setEmail("evil@example.com"))
                                .setUpdateMask(mask("email"))
                                .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(dsl.select(USER.EMAIL).from(USER).fetch(USER.EMAIL))
                .containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
    }

    @Test
    void deleteUser_should_removeTheUserWithItsAccountsAndSessions() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        login("alice@example.com", "laptop");
        var stub = users(alice.getTokens().getAccessToken());

        assertThat(codeOf(() -> users(bob.getTokens().getAccessToken())
                        .deleteUser(DeleteUserRequest.newBuilder()
                                .setName(alice.getUser().getName())
                                .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        stub.deleteUser(DeleteUserRequest.newBuilder()
                .setName(alice.getUser().getName())
                .build());

        assertThat(dsl.fetchCount(USER)).isEqualTo(1);
        assertThat(dsl.fetchCount(ACCOUNT))
                .as("only the account of bob is left")
                .isEqualTo(1);
        assertThat(dsl.fetchCount(SESSION))
                .as("only the session of bob is left")
                .isEqualTo(1);
        assertThat(codeOf(() -> stub.getUser(GetUserRequest.newBuilder()
                        .setName(alice.getUser().getName())
                        .build())))
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> auth.authenticateUser(AuthenticateUserRequest.newBuilder()
                        .setEmail("alice@example.com")
                        .setPassword(PASSWORD)
                        .build())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }
}
