package com.heldertheking.exochat.app.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.heldertheking.exochat.health.v1alpha1.CheckRequest;
import com.heldertheking.exochat.health.v1alpha1.HealthServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@SpringBootTest(properties = "spring.grpc.server.port=0", webEnvironment = WebEnvironment.NONE)
class HealthGrpcServiceTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.grpc.server.lifecycle.GrpcServerLifecycle lifecycle;

    @Test
    void checkReturnsOk() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", lifecycle.getPort())
                .usePlaintext()
                .build();
        try {
            var response = HealthServiceGrpc.newBlockingStub(channel).check(CheckRequest.getDefaultInstance());
            assertThat(response.getStatus()).isEqualTo("ok");
        } finally {
            channel.shutdownNow();
        }
    }
}
