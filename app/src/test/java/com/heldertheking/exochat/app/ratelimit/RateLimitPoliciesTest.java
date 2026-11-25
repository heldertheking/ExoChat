package com.heldertheking.exochat.app.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heldertheking.exochat.identity.v1alpha1.GetUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.UpdateUserRequest;
import com.heldertheking.exochat.identity.v1alpha1.User;
import com.heldertheking.exochat.identity.v1alpha1.UserServiceGrpc;

import io.grpc.stub.StreamObserver;

class RateLimitPoliciesTest {

    private static RateLimitProperties properties(Map<String, RateLimitProperties.Policy> policies) {
        return new RateLimitProperties(true, true, null, 1000, policies);
    }

    @Test
    void builtInPoliciesApplyWithoutConfiguration() {
        var policies = RateLimitPolicies.resolve(properties(Map.of()));

        assertThat(policies).containsKeys("register", "login", "refresh", "change-password", "account-create");
        assertThat(policies.get("login").key()).isEqualTo(KeyStrategy.PEER);
        assertThat(policies.get("change-password").key()).isEqualTo(KeyStrategy.USER);
    }

    @Test
    void configurationOverridesOnlyTheFieldsItSets() {
        var policies = RateLimitPolicies.resolve(
                properties(Map.of("login", new RateLimitProperties.Policy(5, null, null, null))));

        var login = policies.get("login");
        assertThat(login.limit()).isEqualTo(5);
        assertThat(login.window())
                .isEqualTo(RateLimitPolicies.BUILT_IN.get("login").window());
        assertThat(login.requestKeyLimit()).isEqualTo(10);
    }

    @Test
    void aNewPolicyNeedsAllItsFields() {
        var incomplete = properties(Map.of("custom", new RateLimitProperties.Policy(5, null, null, null)));
        assertThatThrownBy(() -> RateLimitPolicies.resolve(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("custom");

        var complete = properties(
                Map.of("custom", new RateLimitProperties.Policy(5, Duration.ofMinutes(1), KeyStrategy.PEER, null)));
        assertThat(RateLimitPolicies.resolve(complete)).containsKey("custom");
    }

    @Test
    void nonsenseValuesAreRejected() {
        var bad = properties(Map.of("login", new RateLimitProperties.Policy(0, null, null, null)));

        assertThatThrownBy(() -> RateLimitPolicies.resolve(bad)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapsTheAnnotatedMethodToItsFullGrpcMethodName() {
        var policies = RateLimitPolicies.resolve(properties(Map.of()));

        var bound = RateLimitPolicies.bind(List.of(new Annotated()), policies);

        assertThat(bound).containsOnlyKeys(UserServiceGrpc.getUpdateUserMethod().getFullMethodName());
        assertThat(bound.get(UserServiceGrpc.getUpdateUserMethod().getFullMethodName())
                        .name())
                .isEqualTo("user-update");
    }

    @Test
    void startupFailsForAnUnknownPolicy() {
        var policies = RateLimitPolicies.resolve(properties(Map.of()));

        assertThatThrownBy(() -> RateLimitPolicies.bind(List.of(new UnknownPolicy()), policies))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void startupFailsForAnAnnotationOnAMethodThatIsNoRpc() {
        var policies = RateLimitPolicies.resolve(properties(Map.of()));

        assertThatThrownBy(() -> RateLimitPolicies.bind(List.of(new NoRpc()), policies))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an RPC");
    }

    public static class Annotated extends UserServiceGrpc.UserServiceImplBase {
        @Override
        @RateLimited("user-update")
        public void updateUser(UpdateUserRequest request, StreamObserver<User> responseObserver) {}

        @Override
        public void getUser(GetUserRequest request, StreamObserver<User> responseObserver) {}
    }

    public static class UnknownPolicy extends UserServiceGrpc.UserServiceImplBase {
        @Override
        @RateLimited("nope")
        public void getUser(GetUserRequest request, StreamObserver<User> responseObserver) {}
    }

    public static class NoRpc extends UserServiceGrpc.UserServiceImplBase {
        @RateLimited("login")
        public void helper() {}
    }
}
