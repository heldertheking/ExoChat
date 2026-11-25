package com.heldertheking.exochat.app.auth;

import org.springframework.stereotype.Service;

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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/// gRPC adapter of [AuthenticationService].
@Service
class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {
    private final AuthenticationService authentication;

    AuthGrpcService(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @Override
    public void registerUser(RegisterUserRequest request, StreamObserver<RegisterUserResponse> responseObserver) {
        var result = authentication.register(
                request.getEmail(),
                request.getPhoneNumber(),
                request.getPassword(),
                request.getUsername(),
                request.getDisplayName(),
                request.getDeviceLabel());
        var response = RegisterUserResponse.newBuilder()
                .setUser(AuthMapper.user(result.user()))
                .setSession(AuthMapper.session(result.session(), true))
                .setTokens(AuthMapper.tokens(result));
        if (result.account() != null) {
            response.setAccount(AuthMapper.account(result.account()));
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void authenticateUser(
            AuthenticateUserRequest request, StreamObserver<AuthenticateUserResponse> responseObserver) {
        var result = authentication.authenticate(request.getEmail(), request.getPassword(), request.getDeviceLabel());
        responseObserver.onNext(AuthenticateUserResponse.newBuilder()
                .setUser(AuthMapper.user(result.user()))
                .setSession(AuthMapper.session(result.session(), true))
                .setTokens(AuthMapper.tokens(result))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void switchAccount(SwitchAccountRequest request, StreamObserver<SwitchAccountResponse> responseObserver) {
        var caller = Caller.current();
        var sessionName = NameParsing.session("name", request.getName());
        var accountName = NameParsing.account("account", request.getAccount());
        caller.requireUser(sessionName.getParent());
        caller.requireUser(accountName.getParent());
        if (!sessionName.getPersistenceId().equals(caller.sessionId())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("only the session of the caller can be changed")
                    .asRuntimeException();
        }
        var result = authentication.switchAccount(caller, accountName.getPersistenceId());
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
        var caller = Caller.current();
        caller.requireUser(NameParsing.user("name", request.getName()));
        authentication.changePassword(caller, request.getOldPassword(), request.getNewPassword());
        responseObserver.onNext(ChangePasswordResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void refreshSession(RefreshSessionRequest request, StreamObserver<RefreshSessionResponse> responseObserver) {
        var result = authentication.refresh(request.getRefreshToken());
        responseObserver.onNext(RefreshSessionResponse.newBuilder()
                .setSession(AuthMapper.session(result.session(), true))
                .setTokens(AuthMapper.tokens(result))
                .build());
        responseObserver.onCompleted();
    }
}
