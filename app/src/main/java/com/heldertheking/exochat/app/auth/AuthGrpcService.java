package com.heldertheking.exochat.app.auth;

import org.springframework.stereotype.Service;

import com.heldertheking.exochat.auth.v1alpha1.AuthServiceGrpc;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserResponse;
import com.heldertheking.exochat.auth.v1alpha1.RefreshSessionRequest;
import com.heldertheking.exochat.auth.v1alpha1.RefreshSessionResponse;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;

import io.grpc.stub.StreamObserver;

/// gRPC adapter of [AuthenticationService]. SwitchAccount and ChangePassword are not implemented yet and answer
/// UNIMPLEMENTED.
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
                .setSession(AuthMapper.session(result.session()))
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
                .setSession(AuthMapper.session(result.session()))
                .setTokens(AuthMapper.tokens(result))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void refreshSession(RefreshSessionRequest request, StreamObserver<RefreshSessionResponse> responseObserver) {
        var result = authentication.refresh(request.getRefreshToken());
        responseObserver.onNext(RefreshSessionResponse.newBuilder()
                .setSession(AuthMapper.session(result.session()))
                .setTokens(AuthMapper.tokens(result))
                .build());
        responseObserver.onCompleted();
    }
}
