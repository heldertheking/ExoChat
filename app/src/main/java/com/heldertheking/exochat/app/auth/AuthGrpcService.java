package com.heldertheking.exochat.app.auth;

import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.identity.IdentityMapper;
import com.heldertheking.exochat.app.security.PermissionEvaluator;
import com.heldertheking.exochat.auth.v1alpha1.AuthServiceGrpc;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserResponse;
import com.heldertheking.exochat.auth.v1alpha1.ChangePasswordRequest;
import com.heldertheking.exochat.auth.v1alpha1.ChangePasswordResponse;
import com.heldertheking.exochat.auth.v1alpha1.RefreshSessionRequest;
import com.heldertheking.exochat.auth.v1alpha1.RefreshSessionResponse;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;
import com.heldertheking.exochat.auth.v1alpha1.SwitchAccountRequest;
import com.heldertheking.exochat.auth.v1alpha1.SwitchAccountResponse;

import io.grpc.stub.StreamObserver;

/// gRPC adapter of [AuthRepository].
@Service
class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {
    private final AuthRepository authRepository;
    private final AuthPermissionChecker permissionChecker;
    private final PermissionEvaluator permissionEvaluator;

    AuthGrpcService(
            AuthRepository authRepository,
            AuthPermissionChecker permissionChecker,
            PermissionEvaluator permissionEvaluator) {
        this.authRepository = authRepository;
        this.permissionChecker = permissionChecker;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Override
    public void registerUser(RegisterUserRequest request, StreamObserver<RegisterUserResponse> responseObserver) {
        var result = authRepository.register(
                request.getEmail(),
                request.getPhoneNumber(),
                request.getPassword(),
                request.getUsername(),
                request.getDisplayName(),
                request.getDeviceLabel());
        var response = RegisterUserResponse.newBuilder()
                .setUser(IdentityMapper.user(result.user()))
                .setSession(AuthMapper.session(result.session(), true))
                .setTokens(AuthMapper.tokens(result));
        if (result.account() != null) {
            response.setAccount(IdentityMapper.account(result.account(), true));
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void authenticateUser(
            AuthenticateUserRequest request, StreamObserver<AuthenticateUserResponse> responseObserver) {
        var result = authRepository.authenticate(request.getEmail(), request.getPassword(), request.getDeviceLabel());
        responseObserver.onNext(AuthenticateUserResponse.newBuilder()
                .setUser(IdentityMapper.user(result.user()))
                .setSession(AuthMapper.session(result.session(), true))
                .setTokens(AuthMapper.tokens(result))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void switchAccount(SwitchAccountRequest request, StreamObserver<SwitchAccountResponse> responseObserver) {
        var sessionName = SessionName.fromName(request.getName());
        var accountName = AccountName.fromName(request.getAccount());
        permissionChecker.verifySwitchAccountPermission(sessionName, accountName);
        var result = authRepository.switchAccount(
                sessionName.getParent().getPersistenceId(),
                sessionName.getPersistenceId(),
                accountName.getPersistenceId());
        responseObserver.onNext(SwitchAccountResponse.newBuilder()
                .setSession(AuthMapper.session(result.session(), true))
                .setAccessToken(result.accessToken().token())
                .setAccessTokenExpireTime(
                        AuthMapper.timestamp(result.accessToken().expireTime()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void changePassword(ChangePasswordRequest request, StreamObserver<ChangePasswordResponse> responseObserver) {
        var user = UserName.fromName(request.getName());
        permissionChecker.verifyChangePasswordPermission(user);
        authRepository.changePassword(
                user.getPersistenceId(),
                permissionEvaluator.currentSessionId(),
                request.getOldPassword(),
                request.getNewPassword());
        responseObserver.onNext(ChangePasswordResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void refreshSession(RefreshSessionRequest request, StreamObserver<RefreshSessionResponse> responseObserver) {
        var result = authRepository.refresh(request.getRefreshToken());
        responseObserver.onNext(RefreshSessionResponse.newBuilder()
                .setSession(AuthMapper.session(result.session(), true))
                .setTokens(AuthMapper.tokens(result))
                .build());
        responseObserver.onCompleted();
    }
}
