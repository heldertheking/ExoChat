package com.heldertheking.exochat.app.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;

class ClientAddressesTest {
    private static final Metadata.Key<String> FORWARDED =
            Metadata.Key.of("x-forwarded-for", Metadata.ASCII_STRING_MARSHALLER);

    private static Attributes from(String host) throws Exception {
        return Attributes.newBuilder()
                .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, new InetSocketAddress(InetAddress.getByName(host), 5555))
                .build();
    }

    private static Metadata forwarded(String value) {
        var headers = new Metadata();
        headers.put(FORWARDED, value);
        return headers;
    }

    @Test
    void usesTheSocketAddressAndIgnoresProxyHeadersByDefault() throws Exception {
        var addresses = new ClientAddresses(null);

        assertThat(addresses.resolve(from("203.0.113.7"), forwarded("198.51.100.1")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void readsTheLastEntryOfTheConfiguredHeaderBecauseTheClientControlsTheFirst() throws Exception {
        var addresses = new ClientAddresses("X-Forwarded-For");

        assertThat(addresses.resolve(from("10.0.0.1"), forwarded("1.1.1.1, 198.51.100.1")))
                .isEqualTo("198.51.100.1");
    }

    @Test
    void fallsBackToTheSocketWhenTheHeaderIsMissingOrNotAnAddress() throws Exception {
        var addresses = new ClientAddresses("x-forwarded-for");

        assertThat(addresses.resolve(from("10.0.0.1"), new Metadata())).isEqualTo("10.0.0.1");
        assertThat(addresses.resolve(from("10.0.0.1"), forwarded("evil.example.com")))
                .isEqualTo("10.0.0.1");
    }

    @Test
    void anIpv6SubscriberIsOneKeyForItsWholePrefix() throws Exception {
        var addresses = new ClientAddresses(null);

        assertThat(addresses.resolve(from("2001:db8:1:2:aaaa::1"), new Metadata()))
                .isEqualTo(addresses.resolve(from("2001:db8:1:2:bbbb::2"), new Metadata()));
        assertThat(addresses.resolve(from("2001:db8:1:3::1"), new Metadata()))
                .isNotEqualTo(addresses.resolve(from("2001:db8:1:2::1"), new Metadata()));
    }
}
