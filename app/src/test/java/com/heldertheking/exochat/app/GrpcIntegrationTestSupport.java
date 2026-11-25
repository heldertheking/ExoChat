package com.heldertheking.exochat.app;

import static com.heldertheking.exochat.db.schema.Tables.USER;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.heldertheking.exochat.auth.v1alpha1.AuthServiceGrpc;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserResponse;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

/// Base of the end to end tests: the whole application with its gRPC server on a random port, the MariaDB test
/// database (`docker compose up -d`), a clock the tests move, and helpers to register users and call with a token.
@SpringBootTest(properties = "spring.grpc.server.port=0", webEnvironment = WebEnvironment.NONE)
@Import(GrpcIntegrationTestSupport.ClockConfig.class)
public abstract class GrpcIntegrationTestSupport {
    protected static final Instant START = Instant.parse("2026-11-25T10:00:00Z");
    protected static final String PASSWORD = "correct horse battery";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    /// A clock the tests move forward.
    public static class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        public void set(Instant instant) {
            this.now = instant;
        }

        public void advance(Duration duration) {
            this.now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired
    protected GrpcServerLifecycle lifecycle;

    @Autowired
    protected DSLContext dsl;

    @Autowired
    protected JwtDecoder jwtDecoder;

    @Autowired
    protected MutableClock clock;

    protected ManagedChannel channel;
    protected AuthServiceGrpc.AuthServiceBlockingStub auth;

    @BeforeEach
    void setUpChannel() {
        clock.set(START);
        dsl.deleteFrom(USER).execute();
        channel = ManagedChannelBuilder.forAddress("localhost", lifecycle.getPort())
                .usePlaintext()
                .build();
        auth = AuthServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDownChannel() {
        channel.shutdownNow();
    }

    /// The stub with the access token in its `authorization` header.
    protected static <S extends AbstractStub<S>> S bearer(S stub, String accessToken) {
        var headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + accessToken);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    /// The status code a call fails with.
    protected static Status.Code codeOf(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
        throw new AssertionError("expected the call to fail");
    }

    protected RegisterUserResponse register(String email, String username) {
        return auth.registerUser(RegisterUserRequest.newBuilder()
                .setEmail(email)
                .setPassword(PASSWORD)
                .setUsername(username)
                .setDeviceLabel("phone")
                .build());
    }

    protected AuthenticateUserResponse login(String email, String device) {
        return auth.authenticateUser(AuthenticateUserRequest.newBuilder()
                .setEmail(email)
                .setPassword(PASSWORD)
                .setDeviceLabel(device)
                .build());
    }
}
