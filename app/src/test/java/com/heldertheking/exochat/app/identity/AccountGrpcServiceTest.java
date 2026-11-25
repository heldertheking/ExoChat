package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT_RULE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.protobuf.FieldMask;
import com.heldertheking.exochat.app.GrpcIntegrationTestSupport;
import com.heldertheking.exochat.auth.v1alpha1.RefreshSessionRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;
import com.heldertheking.exochat.identity.v1alpha1.Account;
import com.heldertheking.exochat.identity.v1alpha1.AccountServiceGrpc;
import com.heldertheking.exochat.identity.v1alpha1.CreateAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.DeleteAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListAccountsRequest;
import com.heldertheking.exochat.identity.v1alpha1.UpdateAccountRequest;

import io.grpc.Status;

class AccountGrpcServiceTest extends GrpcIntegrationTestSupport {

    private AccountServiceGrpc.AccountServiceBlockingStub accounts(RegisterUserResponse user) {
        return bearer(
                AccountServiceGrpc.newBlockingStub(channel), user.getTokens().getAccessToken());
    }

    private static FieldMask mask(String... paths) {
        return FieldMask.newBuilder().addAllPaths(List.of(paths)).build();
    }

    private static CreateAccountRequest create(RegisterUserResponse user, String username) {
        return CreateAccountRequest.newBuilder()
                .setParent(user.getUser().getName())
                .setAccount(Account.newBuilder().setUsername(username))
                .build();
    }

    @Test
    void createAccount_should_addAnAccountToTheUser() {
        var alice = register("alice@example.com", "alice");

        var created = accounts(alice)
                .createAccount(CreateAccountRequest.newBuilder()
                        .setParent(alice.getUser().getName())
                        .setAccount(Account.newBuilder()
                                .setUsername("  Alice_Work ")
                                .setDisplayName("Alice at work")
                                .setAvatarUrl("https://example.com/a.png"))
                        .build());

        assertThat(created.getName()).startsWith(alice.getUser().getName() + "/accounts/");
        assertThat(created.getUsername()).isEqualTo("alice_work");
        assertThat(created.getDisplayName()).isEqualTo("Alice at work");
        assertThat(created.getAvatarUrl()).isEqualTo("https://example.com/a.png");
        assertThat(created.getStanding()).isEqualTo(Account.AccountStanding.ACCOUNT_STANDING_MEMBER);
        assertThat(dsl.fetchCount(ACCOUNT)).isEqualTo(2);

        var plain = accounts(alice).createAccount(create(alice, "alice_plain"));
        assertThat(plain.getDisplayName()).as("defaults to the username").isEqualTo("alice_plain");
    }

    @Test
    void createAccount_should_rejectBadInputTakenHandlesAndOtherUsers() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var stub = accounts(alice);

        assertThat(codeOf(() -> stub.createAccount(create(alice, "bob"))))
                .as("handles are unique across users")
                .isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(codeOf(() -> stub.createAccount(create(alice, "ab")))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.createAccount(create(alice, "has space"))))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.createAccount(create(bob, "sneaky"))))
                .as("not for another user")
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(codeOf(() -> AccountServiceGrpc.newBlockingStub(channel).createAccount(create(alice, "anon"))))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(dsl.fetchCount(ACCOUNT)).isEqualTo(2);
    }

    @Test
    void createAccount_should_stopAtTheLimit() {
        var alice = register("alice@example.com", "alice");
        var stub = accounts(alice);
        for (int i = 1; i < AccountGrpcService.MAX_ACCOUNTS_PER_USER; i++) {
            stub.createAccount(create(alice, "alice_" + i));
        }

        assertThat(codeOf(() -> stub.createAccount(create(alice, "one_too_many"))))
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(dsl.fetchCount(ACCOUNT)).isEqualTo(AccountGrpcService.MAX_ACCOUNTS_PER_USER);
    }

    @Test
    void createAccount_should_followTheCreateAccountRule() {
        var restricted = register("alice@example.com", "alice");
        var suspended = register("bob@example.com", "bob");
        var lifted = register("carol@example.com", "carol");
        dsl.update(ACCOUNT)
                .set(ACCOUNT.STANDING, "RESTRICTED")
                .where(ACCOUNT.USERNAME.in("alice", "carol"))
                .execute();
        dsl.update(ACCOUNT)
                .set(ACCOUNT.STANDING, "SUSPENDED")
                .where(ACCOUNT.USERNAME.eq("bob"))
                .execute();
        dsl.insertInto(ACCOUNT_RULE)
                .select(dsl.select(
                                ACCOUNT.ID,
                                org.jooq.impl.DSL.inline("CREATE_ACCOUNT"),
                                org.jooq.impl.DSL.inline("ALLOW"))
                        .from(ACCOUNT)
                        .where(ACCOUNT.USERNAME.eq("carol")))
                .execute();

        assertThat(codeOf(() -> accounts(restricted).createAccount(create(restricted, "alice_2"))))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(codeOf(() -> accounts(suspended).createAccount(create(suspended, "bob_2"))))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        accounts(lifted).createAccount(create(lifted, "carol_2"));
    }

    @Test
    void getAccount_should_beReadableByEveryoneButTheStandingIsPrivate() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        dsl.update(ACCOUNT)
                .set(ACCOUNT.STANDING, "RESTRICTED")
                .where(ACCOUNT.USERNAME.eq("alice"))
                .execute();
        var request = GetAccountRequest.newBuilder()
                .setName(alice.getAccount().getName())
                .build();

        var own = accounts(alice).getAccount(request);
        var seenByBob = accounts(bob).getAccount(request);

        assertThat(own.getStanding()).isEqualTo(Account.AccountStanding.ACCOUNT_STANDING_RESTRICTED);
        assertThat(seenByBob.getUsername()).isEqualTo("alice");
        assertThat(seenByBob.getStanding())
                .as("only the owner learns the standing")
                .isEqualTo(Account.AccountStanding.ACCOUNT_STANDING_UNSPECIFIED);
    }

    @Test
    void getAccount_should_failForMissingMismatchedAndWildcardNames() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var stub = accounts(alice);
        var aliceAccountId = alice.getAccount()
                .getName()
                .substring(alice.getAccount().getName().lastIndexOf('/') + 1);

        assertThat(codeOf(() -> stub.getAccount(GetAccountRequest.newBuilder()
                        .setName(alice.getUser().getName() + "/accounts/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01")
                        .build())))
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> stub.getAccount(GetAccountRequest.newBuilder()
                        .setName(bob.getUser().getName() + "/accounts/" + aliceAccountId)
                        .build())))
                .as("the account does not belong to the user of the name")
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> stub.getAccount(GetAccountRequest.newBuilder()
                        .setName("users/-/accounts/-")
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void listAccounts_should_listPageOrderAndFilterTheOwnAccounts() {
        var alice = register("alice@example.com", "alice");
        var stub = accounts(alice);
        stub.createAccount(create(alice, "alice_b"));
        stub.createAccount(create(alice, "alice_c"));
        var parent = alice.getUser().getName();

        var all = stub.listAccounts(
                ListAccountsRequest.newBuilder().setParent(parent).build());
        assertThat(all.getAccountsList())
                .extracting(Account::getUsername)
                .containsExactly("alice", "alice_b", "alice_c");

        var names = new ArrayList<String>();
        var token = "";
        int pages = 0;
        do {
            var page = stub.listAccounts(ListAccountsRequest.newBuilder()
                    .setParent(parent)
                    .setPageSize(2)
                    .setOrderBy("username desc")
                    .setPageToken(token)
                    .build());
            page.getAccountsList().forEach(a -> names.add(a.getUsername()));
            token = page.getNextPageToken();
            pages++;
        } while (!token.isEmpty() && pages < 5);
        assertThat(names).containsExactly("alice_c", "alice_b", "alice");

        var filtered = stub.listAccounts(ListAccountsRequest.newBuilder()
                .setParent(parent)
                .setFilter("username = 'alice_*'")
                .build());
        assertThat(filtered.getAccountsList()).extracting(Account::getUsername).containsExactly("alice_b", "alice_c");
    }

    @Test
    void listAccounts_should_rejectBadQueriesAndOtherUsers() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var stub = accounts(alice);

        assertThat(codeOf(() -> stub.listAccounts(ListAccountsRequest.newBuilder()
                        .setParent(alice.getUser().getName())
                        .setFilter("password_hash = 'x'")
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.listAccounts(ListAccountsRequest.newBuilder()
                        .setParent(bob.getUser().getName())
                        .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void updateAccount_should_changeDisplayNameAndAvatarButNeverTheUsername() {
        var alice = register("alice@example.com", "alice");
        var stub = accounts(alice);
        var name = alice.getAccount().getName();

        var updated = stub.updateAccount(UpdateAccountRequest.newBuilder()
                .setAccount(Account.newBuilder()
                        .setName(name)
                        .setDisplayName("Alice A.")
                        .setAvatarUrl("https://example.com/new.png"))
                .setUpdateMask(mask("display_name", "avatar_url"))
                .build());
        assertThat(updated.getDisplayName()).isEqualTo("Alice A.");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://example.com/new.png");
        assertThat(updated.getUsername()).isEqualTo("alice");

        var cleared = stub.updateAccount(UpdateAccountRequest.newBuilder()
                .setAccount(Account.newBuilder().setName(name))
                .setUpdateMask(mask("avatar_url"))
                .build());
        assertThat(cleared.getAvatarUrl()).isEmpty();
        assertThat(cleared.getDisplayName()).isEqualTo("Alice A.");

        var withoutMask = stub.updateAccount(UpdateAccountRequest.newBuilder()
                .setAccount(Account.newBuilder().setName(name).setDisplayName("Populated"))
                .build());
        assertThat(withoutMask.getDisplayName()).isEqualTo("Populated");

        assertThat(codeOf(() -> stub.updateAccount(UpdateAccountRequest.newBuilder()
                        .setAccount(Account.newBuilder().setName(name).setUsername("hijack"))
                        .setUpdateMask(mask("username"))
                        .build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.updateAccount(UpdateAccountRequest.newBuilder()
                        .setAccount(Account.newBuilder().setName(name))
                        .setUpdateMask(mask("display_name"))
                        .build())))
                .as("the display name can not be emptied")
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void updateAccount_should_notChangeAnotherUsersAccount() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");

        assertThat(codeOf(() -> accounts(bob)
                        .updateAccount(UpdateAccountRequest.newBuilder()
                                .setAccount(Account.newBuilder()
                                        .setName(alice.getAccount().getName())
                                        .setDisplayName("Pwned"))
                                .setUpdateMask(mask("display_name"))
                                .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(dsl.select(ACCOUNT.DISPLAY_NAME)
                        .from(ACCOUNT)
                        .where(ACCOUNT.USERNAME.eq("alice"))
                        .fetchOne(ACCOUNT.DISPLAY_NAME))
                .isEqualTo("alice");
    }

    @Test
    void deleteAccount_should_keepTheLastAccountAndFallBackTheSessions() {
        var alice = register("alice@example.com", "alice");
        var bob = register("bob@example.com", "bob");
        var stub = accounts(alice);
        var second = stub.createAccount(create(alice, "alice_two"));

        assertThat(codeOf(() -> accounts(bob)
                        .deleteAccount(DeleteAccountRequest.newBuilder()
                                .setName(alice.getAccount().getName())
                                .build())))
                .isEqualTo(Status.Code.PERMISSION_DENIED);

        stub.deleteAccount(DeleteAccountRequest.newBuilder()
                .setName(alice.getAccount().getName())
                .build());
        assertThat(dsl.fetchCount(ACCOUNT, ACCOUNT.USERNAME.in("alice", "alice_two")))
                .as("the active account of the session is gone")
                .isEqualTo(1);

        var refreshed = auth.refreshSession(RefreshSessionRequest.newBuilder()
                .setRefreshToken(alice.getTokens().getRefreshToken())
                .build());
        assertThat(refreshed.getSession().getAccount())
                .as("the session falls back to the remaining account")
                .isEqualTo(second.getName());

        assertThat(codeOf(() -> stub.deleteAccount(DeleteAccountRequest.newBuilder()
                        .setName(second.getName())
                        .build())))
                .as("the last account stays")
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(codeOf(() -> stub.deleteAccount(DeleteAccountRequest.newBuilder()
                        .setName(alice.getAccount().getName())
                        .build())))
                .isEqualTo(Status.Code.NOT_FOUND);
    }
}
