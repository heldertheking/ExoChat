package com.heldertheking.exochat.app.identity;

import org.springframework.stereotype.Service;

import com.google.protobuf.Empty;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.ratelimit.RateLimited;
import com.heldertheking.exochat.identity.v1alpha1.DeleteUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.UpdateUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.User;
import com.heldertheking.exochat.identity.v1alpha1.UserServiceGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/// The controller of the users of the caller: parses the request, asks the [UserPermissionChecker] and hands over
/// to the [UserRepository]. Users are created by `AuthService.RegisterUser`.
@Service
class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    private final UserRepository users;
    private final UserPermissionChecker permissionChecker;

    UserGrpcService(UserRepository users, UserPermissionChecker permissionChecker) {
        this.users = users;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<User> responseObserver) {
        var name = UserName.fromName(request.getName());
        permissionChecker.verifyReadPermission(name);
        var user = users.find(name.getPersistenceId()).orElseThrow(UserGrpcService::notFound);
        responseObserver.onNext(IdentityMapper.user(user));
        responseObserver.onCompleted();
    }

    @RateLimited("user-update")
    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<User> responseObserver) {
        var changes = request.getUser();
        var name = UserName.fromName(changes.getName());
        permissionChecker.verifyUpdatePermission(name);
        var user = users.update(
                name.getPersistenceId(), changes, request.hasUpdateMask() ? request.getUpdateMask() : null);
        responseObserver.onNext(IdentityMapper.user(user));
        responseObserver.onCompleted();
    }

    @RateLimited("user-delete")
    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<Empty> responseObserver) {
        var name = UserName.fromName(request.getName());
        permissionChecker.verifyDeletePermission(name);
        if (!users.delete(name.getPersistenceId())) {
            throw notFound();
        }
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private static RuntimeException notFound() {
        return Status.NOT_FOUND.withDescription("user not found").asRuntimeException();
    }
}
