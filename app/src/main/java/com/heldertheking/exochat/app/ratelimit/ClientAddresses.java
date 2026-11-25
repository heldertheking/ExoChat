package com.heldertheking.exochat.app.ratelimit;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;

/// Works out the address a call is counted under. By default that is the address of the socket. A header is only
/// read when the operator names it, because a client can send any header and would pick its own counter.
final class ClientAddresses {
    private static final Pattern LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final Metadata.@Nullable Key<String> trustedHeader;

    ClientAddresses(@Nullable String trustedProxyHeader) {
        this.trustedHeader = trustedProxyHeader == null || trustedProxyHeader.isBlank()
                ? null
                : Metadata.Key.of(
                        trustedProxyHeader.strip().toLowerCase(Locale.ROOT), Metadata.ASCII_STRING_MARSHALLER);
    }

    String resolve(Attributes attributes, Metadata headers) {
        if (trustedHeader != null) {
            var fromProxy = lastEntry(headers.get(trustedHeader));
            if (fromProxy != null) {
                return fromProxy;
            }
        }
        SocketAddress remote = attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        if (remote instanceof InetSocketAddress inet && inet.getAddress() != null) {
            var address = inet.getAddress();
            if (address instanceof Inet6Address v6) {
                // a whole /64 is one subscriber, otherwise a client rotates through its addresses
                var bytes = v6.getAddress();
                var prefix = new StringBuilder("v6:");
                for (int i = 0; i < 8; i++) {
                    prefix.append("%02x".formatted(bytes[i]));
                }
                return prefix.toString();
            }
            return address.getHostAddress();
        }
        return "unknown";
    }

    /// The entry the trusted proxy appended, the earlier ones come from the client.
    private static @Nullable String lastEntry(@Nullable String header) {
        if (header == null) {
            return null;
        }
        var entry = header.substring(header.lastIndexOf(',') + 1).strip();
        return LITERAL.matcher(entry).matches() ? entry.toLowerCase(Locale.ROOT) : null;
    }
}
