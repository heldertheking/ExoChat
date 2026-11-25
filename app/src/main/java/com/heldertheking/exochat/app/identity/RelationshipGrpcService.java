package com.heldertheking.exochat.app.identity;

import org.springframework.stereotype.Service;

import com.google.protobuf.Empty;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.RelationshipName;
import com.heldertheking.exochat.app.identity.RelationshipRepository.RelationshipView;
import com.heldertheking.exochat.app.ratelimit.RateLimited;
import com.heldertheking.exochat.identity.v1alpha1.AcceptRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.BlockAccountRequest;
import com.heldertheking.exochat.identity.v1alpha1.BlockAccountResponse;
import com.heldertheking.exochat.identity.v1alpha1.CreateRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.DeleteRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListRelationshipsRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListRelationshipsResponse;
import com.heldertheking.exochat.identity.v1alpha1.RejectRelationshipRequest;
import com.heldertheking.exochat.identity.v1alpha1.RejectRelationshipResponse;
import com.heldertheking.exochat.identity.v1alpha1.Relationship;
import com.heldertheking.exochat.identity.v1alpha1.RelationshipServiceGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/// The controller of the friends, requests and blocks of an account: parses the request, asks the
/// [RelationshipPermissionChecker] and hands over to the [RelationshipRepository], which holds the rules of the
/// links.
@Service
class RelationshipGrpcService extends RelationshipServiceGrpc.RelationshipServiceImplBase {
    private final RelationshipRepository relationships;
    private final AccountRepository accounts;
    private final RelationshipPermissionChecker permissionChecker;

    RelationshipGrpcService(
            RelationshipRepository relationships,
            AccountRepository accounts,
            RelationshipPermissionChecker permissionChecker) {
        this.relationships = relationships;
        this.accounts = accounts;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public void listRelationships(
            ListRelationshipsRequest request, StreamObserver<ListRelationshipsResponse> responseObserver) {
        var owner = AccountName.fromName(request.getParent());
        permissionChecker.verifyListPermission(owner);
        requireAccount(owner);
        var page = relationships.list(
                owner.getPersistenceId(),
                request.getFilter(),
                request.getOrderBy(),
                request.getPageSize(),
                request.getPageToken());
        var response = ListRelationshipsResponse.newBuilder().setNextPageToken(page.getNextPageToken());
        page.forEach(view -> response.addRelationships(RelationshipMapper.relationship(view, owner)));
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRelationship(GetRelationshipRequest request, StreamObserver<Relationship> responseObserver) {
        var name = RelationshipName.fromName(request.getName());
        permissionChecker.verifyReadPermission(name);
        requireAccount(name.getParent());
        var view = relationships
                .find(name.getParent().getPersistenceId(), name.getPersistenceId())
                .orElseThrow(RelationshipGrpcService::notFound);
        respond(responseObserver, view, name.getParent());
    }

    @RateLimited("relationship-request")
    @Override
    public void createRelationship(CreateRelationshipRequest request, StreamObserver<Relationship> responseObserver) {
        var owner = AccountName.fromName(request.getParent());
        var target = AccountName.fromName(request.getRelationship().getTargetAccount());
        permissionChecker.verifyCreatePermission(owner, target);
        requireAccount(owner);
        requireAccount(target);
        respond(
                responseObserver,
                relationships.sendRequest(owner.getPersistenceId(), target.getPersistenceId()),
                owner);
    }

    @Override
    public void acceptRelationship(AcceptRelationshipRequest request, StreamObserver<Relationship> responseObserver) {
        var name = RelationshipName.fromName(request.getName());
        permissionChecker.verifyAnswerPermission(name);
        requireAccount(name.getParent());
        respond(
                responseObserver,
                relationships.accept(name.getParent().getPersistenceId(), name.getPersistenceId()),
                name.getParent());
    }

    @Override
    public void rejectRelationship(
            RejectRelationshipRequest request, StreamObserver<RejectRelationshipResponse> responseObserver) {
        var name = RelationshipName.fromName(request.getName());
        permissionChecker.verifyAnswerPermission(name);
        requireAccount(name.getParent());
        relationships.reject(name.getParent().getPersistenceId(), name.getPersistenceId());
        responseObserver.onNext(RejectRelationshipResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteRelationship(DeleteRelationshipRequest request, StreamObserver<Empty> responseObserver) {
        var name = RelationshipName.fromName(request.getName());
        permissionChecker.verifyDeletePermission(name);
        requireAccount(name.getParent());
        relationships.delete(name.getParent().getPersistenceId(), name.getPersistenceId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @RateLimited("relationship-block")
    @Override
    public void blockAccount(BlockAccountRequest request, StreamObserver<BlockAccountResponse> responseObserver) {
        var owner = AccountName.fromName(request.getParent());
        var target = AccountName.fromName(request.getTargetAccount());
        permissionChecker.verifyBlockPermission(owner, target);
        requireAccount(owner);
        requireAccount(target);
        var view = relationships.block(owner.getPersistenceId(), target.getPersistenceId());
        responseObserver.onNext(BlockAccountResponse.newBuilder()
                .setRelationship(RelationshipMapper.relationship(view, owner))
                .build());
        responseObserver.onCompleted();
    }

    private static void respond(
            StreamObserver<Relationship> responseObserver, RelationshipView view, AccountName owner) {
        responseObserver.onNext(RelationshipMapper.relationship(view, owner));
        responseObserver.onCompleted();
    }

    /// The account of the name must exist and belong to the user of the name, a mismatch is a name that points
    /// nowhere.
    private void requireAccount(AccountName name) {
        if (accounts.find(name.getParent().getPersistenceId(), name.getPersistenceId())
                .isEmpty()) {
            throw Status.NOT_FOUND.withDescription("account not found").asRuntimeException();
        }
    }

    private static RuntimeException notFound() {
        return Status.NOT_FOUND.withDescription("relationship not found").asRuntimeException();
    }
}
