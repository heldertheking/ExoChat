package com.heldertheking.exochat.app.identity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.google.protobuf.Empty;
import com.heldertheking.exochat.app.grpc.NameParsing;
import com.heldertheking.exochat.app.grpc.UpdateMasks;
import com.heldertheking.exochat.identity.v1alpha1.Account;
import com.heldertheking.exochat.identity.v1alpha1.AccountServiceGrpc;
import com.heldertheking.exochat.identity.v1alpha1.CreateAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.DeleteAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListAccountsRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListAccountsResponse;
import com.heldertheking.exochat.identity.v1alpha1.UpdateAccountRequest;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/// Lists, reads, creates, changes and deletes accounts, the public personas of a user. Who may do what is decided by
/// the [AccountPermissionChecker].
@Service
class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {
    /// A user can own this many accounts.
    static final int MAX_ACCOUNTS_PER_USER = 5;

    private static final Set<String> UPDATABLE = Set.of("display_name", "avatar_url");

    private final AccountRepository accounts;
    private final AccountPermissionChecker permissionChecker;
    private final Clock clock;

    AccountGrpcService(AccountRepository accounts, AccountPermissionChecker permissionChecker, Clock clock) {
        this.accounts = accounts;
        this.permissionChecker = permissionChecker;
        this.clock = clock;
    }

    @Override
    public void listAccounts(ListAccountsRequest request, StreamObserver<ListAccountsResponse> responseObserver) {
        var parent = NameParsing.user("parent", request.getParent());
        permissionChecker.verifyListPermission(parent);
        var page = accounts.list(
                parent.getPersistenceId(),
                request.getFilter(),
                request.getOrderBy(),
                request.getPageSize(),
                request.getPageToken());
        var response = ListAccountsResponse.newBuilder().setNextPageToken(page.getNextPageToken());
        page.forEach(row -> response.addAccounts(IdentityMapper.account(row, true)));
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getAccount(GetAccountRequest request, StreamObserver<Account> responseObserver) {
        var name = NameParsing.account("name", request.getName());
        permissionChecker.verifyReadPermission(name);
        var account = accounts.find(name.getParent().getPersistenceId(), name.getPersistenceId())
                .orElseThrow(AccountGrpcService::notFound);
        responseObserver.onNext(IdentityMapper.account(account, permissionChecker.isOwner(name)));
        responseObserver.onCompleted();
    }

    @Override
    public void createAccount(CreateAccountRequest request, StreamObserver<Account> responseObserver) {
        var parent = NameParsing.user("parent", request.getParent());
        permissionChecker.verifyCreatePermission(parent);
        var requested = request.getAccount();
        var username = IdentityInput.requireUsername(requested.getUsername());
        var display = IdentityInput.optionalDisplayName(requested.getDisplayName());
        var avatar = IdentityInput.optionalAvatarUrl(requested.getAvatarUrl());
        try {
            var created = accounts.create(
                            parent.getPersistenceId(),
                            username,
                            display == null ? username : display,
                            avatar,
                            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                            MAX_ACCOUNTS_PER_USER)
                    .orElseThrow(() -> Status.FAILED_PRECONDITION
                            .withDescription("a user can have at most %d accounts".formatted(MAX_ACCOUNTS_PER_USER))
                            .asRuntimeException());
            responseObserver.onNext(IdentityMapper.account(created, true));
            responseObserver.onCompleted();
        } catch (DataIntegrityViolationException e) {
            throw IdentityInput.duplicate(e);
        }
    }

    /// With an `update_mask` exactly its fields are updated (an empty `avatar_url` removes the avatar). Without one,
    /// the fields that are set in the request are. The username never changes.
    @Override
    public void updateAccount(UpdateAccountRequest request, StreamObserver<Account> responseObserver) {
        var changes = request.getAccount();
        var name = NameParsing.account("account.name", changes.getName());
        permissionChecker.verifyUpdatePermission(name);
        var paths = UpdateMasks.paths(request.getUpdateMask(), UPDATABLE, populated(changes));
        var account = accounts.find(name.getParent().getPersistenceId(), name.getPersistenceId())
                .orElseThrow(AccountGrpcService::notFound);
        if (paths.contains("display_name")) {
            var display = IdentityInput.optionalDisplayName(changes.getDisplayName());
            if (display == null) {
                throw IdentityInput.invalid("display_name must not be empty");
            }
            account.setDisplayName(display);
        }
        if (paths.contains("avatar_url")) {
            account.setAvatarUrl(IdentityInput.optionalAvatarUrl(changes.getAvatarUrl()));
        }
        if (account.changed()) {
            accounts.update(account);
        }
        responseObserver.onNext(IdentityMapper.account(account, true));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteAccount(DeleteAccountRequest request, StreamObserver<Empty> responseObserver) {
        var name = NameParsing.account("name", request.getName());
        permissionChecker.verifyDeletePermission(name);
        switch (accounts.delete(name.getParent().getPersistenceId(), name.getPersistenceId())) {
            case NOT_FOUND -> throw notFound();
            case LAST_ACCOUNT ->
                throw Status.FAILED_PRECONDITION
                        .withDescription("the last account of a user can not be deleted")
                        .asRuntimeException();
            case DELETED -> {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        }
    }

    private static Set<String> populated(Account account) {
        var paths = new LinkedHashSet<String>();
        if (!account.getDisplayName().isEmpty()) {
            paths.add("display_name");
        }
        if (!account.getAvatarUrl().isEmpty()) {
            paths.add("avatar_url");
        }
        return paths;
    }

    private static RuntimeException notFound() {
        return Status.NOT_FOUND.withDescription("account not found").asRuntimeException();
    }
}
