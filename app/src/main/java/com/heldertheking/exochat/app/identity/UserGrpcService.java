package com.heldertheking.exochat.app.identity;

import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.google.protobuf.Empty;
import com.heldertheking.exochat.app.grpc.NameParsing;
import com.heldertheking.exochat.app.grpc.UpdateMasks;
import com.heldertheking.exochat.identity.v1alpha1.DeleteUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.UpdateUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.User;
import com.heldertheking.exochat.identity.v1alpha1.UserServiceGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/// Reads, changes and deletes the user of the caller. Users are created by `AuthService.RegisterUser`.
@Service
class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    private static final Set<String> UPDATABLE = Set.of("email", "phone_number");

    private final UserRepository users;
    private final UserPermissionChecker permissionChecker;

    UserGrpcService(UserRepository users, UserPermissionChecker permissionChecker) {
        this.users = users;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<User> responseObserver) {
        var name = NameParsing.user("name", request.getName());
        permissionChecker.verifyReadPermission(name);
        var user = users.find(name.getPersistenceId()).orElseThrow(UserGrpcService::notFound);
        responseObserver.onNext(IdentityMapper.user(user));
        responseObserver.onCompleted();
    }

    /// With an `update_mask` exactly its fields are updated (an empty `phone_number` clears the number). Without
    /// one, the fields that are set in the request are.
    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<User> responseObserver) {
        var changes = request.getUser();
        var name = NameParsing.user("user.name", changes.getName());
        permissionChecker.verifyUpdatePermission(name);
        var paths = UpdateMasks.paths(request.getUpdateMask(), UPDATABLE, populated(changes));
        var user = users.find(name.getPersistenceId()).orElseThrow(UserGrpcService::notFound);
        if (paths.contains("email")) {
            user.setEmail(IdentityInput.requireEmail(changes.getEmail()));
        }
        if (paths.contains("phone_number")) {
            user.setPhoneNumber(IdentityInput.optionalPhoneNumber(changes.getPhoneNumber()));
        }
        try {
            if (user.changed()) {
                users.update(user);
            }
        } catch (DataIntegrityViolationException e) {
            throw IdentityInput.duplicate(e);
        }
        responseObserver.onNext(IdentityMapper.user(user));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<Empty> responseObserver) {
        var name = NameParsing.user("name", request.getName());
        permissionChecker.verifyDeletePermission(name);
        if (!users.delete(name.getPersistenceId())) {
            throw notFound();
        }
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private static Set<String> populated(User user) {
        var paths = new java.util.LinkedHashSet<String>();
        if (!user.getEmail().isEmpty()) {
            paths.add("email");
        }
        if (!user.getPhoneNumber().isEmpty()) {
            paths.add("phone_number");
        }
        return paths;
    }

    private static RuntimeException notFound() {
        return Status.NOT_FOUND.withDescription("user not found").asRuntimeException();
    }
}
