package com.heldertheking.exochat.app.identity;

import org.springframework.stereotype.Service;

import com.google.protobuf.Empty;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.ratelimit.RateLimited;
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

/// The controller of the accounts, the public personas of a user: parses the request, asks the
/// [AccountPermissionChecker] and hands over to the [AccountRepository].
@Service
class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {
    private final AccountRepository accounts;
    private final AccountPermissionChecker permissionChecker;

    AccountGrpcService(AccountRepository accounts, AccountPermissionChecker permissionChecker) {
        this.accounts = accounts;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public void listAccounts(ListAccountsRequest request, StreamObserver<ListAccountsResponse> responseObserver) {
        var parent = UserName.fromName(request.getParent());
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
        var name = AccountName.fromName(request.getName());
        permissionChecker.verifyReadPermission(name);
        var account = accounts.find(name.getParent().getPersistenceId(), name.getPersistenceId())
                .orElseThrow(() ->
                        Status.NOT_FOUND.withDescription("account not found").asRuntimeException());
        responseObserver.onNext(IdentityMapper.account(account, permissionChecker.isOwner(name)));
        responseObserver.onCompleted();
    }

    @RateLimited("account-create")
    @Override
    public void createAccount(CreateAccountRequest request, StreamObserver<Account> responseObserver) {
        var parent = UserName.fromName(request.getParent());
        permissionChecker.verifyCreatePermission(parent);
        var created = accounts.create(parent.getPersistenceId(), request.getAccount());
        responseObserver.onNext(IdentityMapper.account(created, true));
        responseObserver.onCompleted();
    }

    @Override
    public void updateAccount(UpdateAccountRequest request, StreamObserver<Account> responseObserver) {
        var changes = request.getAccount();
        var name = AccountName.fromName(changes.getName());
        permissionChecker.verifyUpdatePermission(name);
        var account = accounts.update(
                name.getParent().getPersistenceId(),
                name.getPersistenceId(),
                changes,
                request.hasUpdateMask() ? request.getUpdateMask() : null);
        responseObserver.onNext(IdentityMapper.account(account, true));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteAccount(DeleteAccountRequest request, StreamObserver<Empty> responseObserver) {
        var name = AccountName.fromName(request.getName());
        permissionChecker.verifyDeletePermission(name);
        accounts.delete(name.getParent().getPersistenceId(), name.getPersistenceId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
