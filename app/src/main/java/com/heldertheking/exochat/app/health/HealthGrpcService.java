package com.heldertheking.exochat.app.health;

import org.springframework.stereotype.Service;

import com.heldertheking.exochat.health.v1alpha1.CheckRequest;
import com.heldertheking.exochat.health.v1alpha1.CheckResponse;
import com.heldertheking.exochat.health.v1alpha1.HealthServiceGrpc;

import io.grpc.stub.StreamObserver;

@Service
class HealthGrpcService extends HealthServiceGrpc.HealthServiceImplBase {

    @Override
    public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
        responseObserver.onNext(CheckResponse.newBuilder().setStatus("ok").build());
        responseObserver.onCompleted();
    }
}
