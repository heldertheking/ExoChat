package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.RELATIONSHIP;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.heldertheking.exochat.app.GrpcIntegrationTestSupport;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;
import com.heldertheking.exochat.identity.v1alpha1.AcceptRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.Account;
import com.heldertheking.exochat.identity.v1alpha1.AccountServiceGrpc;
import com.heldertheking.exochat.identity.v1alpha1.BlockAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.CreateAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.CreateRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.DeleteRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListRelationshipsRequest;
import com.heldertheking.exochat.identity.v1alpha1.RejectRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.Relationship;
import com.heldertheking.exochat.identity.v1alpha1.Relationship.RelationshipType;
import com.heldertheking.exochat.identity.v1alpha1.RelationshipServiceGrpc;

import io.grpc.Status;

class RelationshipGrpcServiceTest extends GrpcIntegrationTestSupport {

    private RelationshipServiceGrpc.RelationshipServiceBlockingStub as(RegisterUserResponse user) {
        return bearer(
                RelationshipServiceGrpc.newBlockingStub(channel),
                user.getTokens().getAccessToken());
    }

    private static String account(RegisterUserResponse user) {
        return user.getAccount().getName();
    }

    private Relationship send(RegisterUserResponse from, RegisterUserResponse to) {
        return as(from).createRelationship(CreateRelationshipRequest.newBuilder()
                .setParent(account(from))
                .setRelationship(Relationship.newBuilder().setTargetAccount(account(to)))
                .build());
    }

    private List<Relationship> list(RegisterUserResponse user) {
        return as(user).listRelationships(ListRelationshipsRequest.newBuilder()
                        .setParent(account(user))
                        .build())
                .getRelationshipsList();
    }

    private Relationship only(RegisterUserResponse user) {
        var all = list(user);
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    private void block(RegisterUserResponse who, RegisterUserResponse whom) {
        as(who).blockAccount(BlockAccountRequest.newBuilder()
                .setParent(account(who))
                .setTargetAccount(account(whom))
                .build());
    }

    @Test
    void sendRequest_should_createAPendingLinkOnBothSides() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");

        var sent = send(alice, bob);

        assertThat(sent.getName()).startsWith(account(alice) + "/relationships/");
        assertThat(sent.getTargetAccount()).isEqualTo(account(bob));
        assertThat(sent.getType()).isEqualTo(RelationshipType.RELATIONSHIP_TYPE_PENDING_OUTGOING);
        var received = only(bob);
        assertThat(received.getTargetAccount()).isEqualTo(account(alice));
        assertThat(received.getType()).isEqualTo(RelationshipType.RELATIONSHIP_TYPE_PENDING_INCOMING);
        assertThat(only(alice).getName()).isEqualTo(sent.getName());
    }

    @Test
    void accept_should_makeBothSidesFriends() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var sent = send(alice, bob);
        var incoming = only(bob);

        assertThat(codeOf(() -> as(alice)
                        .acceptRelationship(AcceptRelationshipRequest.newBuilder()
                                .setName(sent.getName())
                                .build())))
                .as("the sender can not accept its own request")
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
        var accepted = as(bob).acceptRelationship(AcceptRelationshipRequest.newBuilder()
                .setName(incoming.getName())
                .build());

        assertThat(accepted.getType()).isEqualTo(RelationshipType.RELATIONSHIP_TYPE_FRIEND);
        assertThat(only(bob).getType()).isEqualTo(RelationshipType.RELATIONSHIP_TYPE_FRIEND);
        assertThat(only(alice).getType()).isEqualTo(RelationshipType.RELATIONSHIP_TYPE_FRIEND);
        assertThat(codeOf(() -> as(bob).acceptRelationship(AcceptRelationshipRequest.newBuilder()
                        .setName(incoming.getName())
                        .build())))
                .as("only a pending request can be accepted")
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void sendRequest_should_befriendAtOnce_when_theTargetAskedFirst() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        send(alice, bob);

        var answer = send(bob, alice);

        assertThat(answer.getType()).isEqualTo(RelationshipType.RELATIONSHIP_TYPE_FRIEND);
        assertThat(only(alice).getType()).isEqualTo(RelationshipType.RELATIONSHIP_TYPE_FRIEND);
        assertThat(dsl.fetchCount(RELATIONSHIP)).isEqualTo(2);
    }

    @Test
    void sendRequest_should_rejectDuplicatesSelfTargetsAndUnknownAccounts() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        send(alice, bob);
        var second = AccountServiceGrpc.newBlockingStub(channel);
        var ownSecond = bearer(second, alice.getTokens().getAccessToken())
                .createAccount(CreateAccountRequest.newBuilder()
                        .setParent(alice.getUser().getName())
                        .setAccount(Account.newBuilder().setUsername("alice_two"))
                        .build());

        assertThat(codeOf(() -> send(alice, bob))).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(codeOf(() -> as(alice)
                        .createRelationship(CreateRelationshipRequest.newBuilder()
                                .setParent(account(alice))
                                .setRelationship(Relationship.newBuilder().setTargetAccount(ownSecond.getName()))
                                .build())))
                .as("an account of the same user")
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> as(alice)
                        .createRelationship(CreateRelationshipRequest.newBuilder()
                                .setParent(account(alice))
                                .setRelationship(Relationship.newBuilder()
                                        .setTargetAccount(bob.getUser().getName()
                                                + "/accounts/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01"))
                                .build())))
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> as(alice)
                        .createRelationship(CreateRelationshipRequest.newBuilder()
                                .setParent(account(alice))
                                .setRelationship(Relationship.newBuilder().setTargetAccount("nope"))
                                .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(dsl.fetchCount(RELATIONSHIP)).isEqualTo(2);
    }

    @Test
    void reject_should_removeTheRequestOnBothSides() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var sent = send(alice, bob);

        assertThat(codeOf(() -> as(alice)
                        .rejectRelationship(RejectRelationshipRequest.newBuilder()
                                .setName(sent.getName())
                                .build())))
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
        as(bob).rejectRelationship(RejectRelationshipRequest.newBuilder()
                .setName(only(bob).getName())
                .build());

        assertThat(list(alice)).isEmpty();
        assertThat(list(bob)).isEmpty();
    }

    @Test
    void delete_should_unfriendWithdrawAndUnblock() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var carol = register("carol@example.com", "carol");

        send(alice, bob);
        as(alice)
                .deleteRelationship(DeleteRelationshipRequest.newBuilder()
                        .setName(only(alice).getName())
                        .build());
        assertThat(dsl.fetchCount(RELATIONSHIP))
                .as("a withdrawn request is gone on both sides")
                .isZero();

        send(alice, bob);
        as(bob).acceptRelationship(AcceptRelationshipRequest.newBuilder()
                .setName(only(bob).getName())
                .build());
        as(bob).deleteRelationship(DeleteRelationshipRequest.newBuilder()
                .setName(only(bob).getName())
                .build());
        assertThat(dsl.fetchCount(RELATIONSHIP))
                .as("unfriending removes both rows")
                .isZero();

        block(alice, carol);
        as(alice)
                .deleteRelationship(DeleteRelationshipRequest.newBuilder()
                        .setName(only(alice).getName())
                        .build());
        assertThat(dsl.fetchCount(RELATIONSHIP)).isZero();
        assertThat(codeOf(() -> as(alice)
                        .deleteRelationship(DeleteRelationshipRequest.newBuilder()
                                .setName(account(alice) + "/relationships/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01")
                                .build())))
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void block_should_workOnStrangersAndEndFriendships() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var carol = register("carol@example.com", "carol");
        send(alice, bob);
        as(bob).acceptRelationship(AcceptRelationshipRequest.newBuilder()
                .setName(only(bob).getName())
                .build());

        block(alice, carol);
        block(alice, bob);

        var blocked = list(alice);
        assertThat(blocked).hasSize(2);
        assertThat(blocked).extracting(Relationship::getType).containsOnly(RelationshipType.RELATIONSHIP_TYPE_BLOCKED);
        assertThat(list(bob))
                .as("the friendship disappeared on the side of the blocked account")
                .isEmpty();
        assertThat(list(carol)).as("the blocked account is told nothing").isEmpty();
        assertThat(codeOf(() -> send(alice, bob))).as("unblock first").isEqualTo(Status.Code.FAILED_PRECONDITION);
        block(alice, bob);
        assertThat(list(alice)).as("blocking twice changes nothing").hasSize(2);
    }

    @Test
    void sendRequest_should_notReachAnAccountThatBlockedTheSender() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        block(bob, alice);

        var sent = send(alice, bob);

        assertThat(sent.getType())
                .as("the sender can not tell")
                .isEqualTo(RelationshipType.RELATIONSHIP_TYPE_PENDING_OUTGOING);
        assertThat(list(bob))
                .extracting(Relationship::getType)
                .containsExactly(RelationshipType.RELATIONSHIP_TYPE_BLOCKED);
        assertThat(codeOf(() -> as(bob).acceptRelationship(AcceptRelationshipRequest.newBuilder()
                        .setName(only(bob).getName())
                        .build())))
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void deletingARelationshipShouldNeverRemoveABlockOfTheOtherSide() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        block(bob, alice);
        var sent = send(alice, bob);

        as(alice)
                .deleteRelationship(DeleteRelationshipRequest.newBuilder()
                        .setName(sent.getName())
                        .build());

        assertThat(list(bob))
                .extracting(Relationship::getType)
                .containsExactly(RelationshipType.RELATIONSHIP_TYPE_BLOCKED);
        assertThat(list(alice)).isEmpty();
    }

    @Test
    void getRelationship_should_returnOnlyOwnRelationships() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var carol = register("carol@example.com", "carol");
        var sent = send(alice, bob);

        assertThat(as(alice)
                        .getRelationship(GetRelationshipRequest.newBuilder()
                                .setName(sent.getName())
                                .build())
                        .getTargetAccount())
                .isEqualTo(account(bob));
        assertThat(codeOf(() -> as(carol)
                        .getRelationship(GetRelationshipRequest.newBuilder()
                                .setName(sent.getName())
                                .build())))
                .as("a relationship of another user")
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(codeOf(() -> as(carol)
                        .listRelationships(ListRelationshipsRequest.newBuilder()
                                .setParent(account(alice))
                                .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        var aliceSegments = account(alice).split("/");
        var wrongOwner = "users/" + carol.getUser().getName().substring("users/".length()) + "/accounts/"
                + aliceSegments[aliceSegments.length - 1];
        assertThat(codeOf(() -> as(carol)
                        .listRelationships(ListRelationshipsRequest.newBuilder()
                                .setParent(wrongOwner)
                                .build())))
                .as("my user name with the account of someone else points nowhere")
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> RelationshipServiceGrpc.newBlockingStub(channel)
                        .listRelationships(ListRelationshipsRequest.newBuilder()
                                .setParent(account(alice))
                                .build())))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void sendRequest_should_followTheSendFriendRequestsRule() {
        var restricted = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        dsl.update(ACCOUNT)
                .set(ACCOUNT.STANDING, "RESTRICTED")
                .where(ACCOUNT.USERNAME.eq("alice"))
                .execute();

        assertThat(codeOf(() -> send(restricted, bob))).isEqualTo(Status.Code.PERMISSION_DENIED);
        block(restricted, bob);
        assertThat(list(restricted)).as("blocking is never denied").hasSize(1);
    }

    @Test
    void listRelationships_should_filterOrderAndPage() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var carol = register("carol@example.com", "carol");
        var dave = register("dave@example.com", "dave");
        send(alice, bob);
        send(carol, alice);
        send(dave, alice);

        var incoming = as(alice)
                .listRelationships(ListRelationshipsRequest.newBuilder()
                        .setParent(account(alice))
                        .setFilter("type = 'PENDING_INCOMING'")
                        .build());
        assertThat(incoming.getRelationshipsList())
                .extracting(Relationship::getTargetAccount)
                .containsExactlyInAnyOrder(account(carol), account(dave));

        var types = new ArrayList<String>();
        var token = "";
        int pages = 0;
        do {
            var page = as(alice)
                    .listRelationships(ListRelationshipsRequest.newBuilder()
                            .setParent(account(alice))
                            .setPageSize(2)
                            .setOrderBy("type desc")
                            .setPageToken(token)
                            .build());
            page.getRelationshipsList().forEach(r -> types.add(r.getType().name()));
            token = page.getNextPageToken();
            pages++;
        } while (!token.isEmpty() && pages < 5);
        assertThat(types).hasSize(3);
        assertThat(codeOf(() -> as(alice)
                        .listRelationships(ListRelationshipsRequest.newBuilder()
                                .setParent(account(alice))
                                .setFilter("password = 'x'")
                                .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void deletingAnAccountShouldRemoveItsRelationships() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        send(alice, bob);

        dsl.deleteFrom(ACCOUNT).where(ACCOUNT.USERNAME.eq("bob")).execute();

        assertThat(dsl.fetchCount(RELATIONSHIP)).isZero();
    }
}
