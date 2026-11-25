package com.heldertheking.quela.pagination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;

import org.jspecify.annotations.Nullable;

/// A stateless [PaginationTokenSerializer]: the token is the checksum and the values in a small binary layout,
/// base64url encoded. It is opaque to clients but not signed or encrypted. That is fine because the checksum binds a
/// token to the request it belongs to and every value is parsed and typed again when it is used; a client that edits
/// its own token can only change where its own list continues.
public class Base64PaginationTokenSerializer implements PaginationTokenSerializer {
    private static final int MAX_VALUES = 64;

    @Override
    public Token deserialize(String token) {
        try (var in = new DataInputStream(
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(token)))) {
            var checksum = in.readLong();
            var count = in.readUnsignedByte();
            if (count > MAX_VALUES) {
                throw new InvalidPageTokenException("too many values");
            }
            var values = new ArrayList<@Nullable String>(count);
            for (int i = 0; i < count; i++) {
                values.add(in.readBoolean() ? in.readUTF() : null);
            }
            if (in.read() != -1) {
                throw new InvalidPageTokenException("trailing data");
            }
            return new Token(checksum, values);
        } catch (IOException | IllegalArgumentException e) {
            throw new InvalidPageTokenException("malformed page token", e);
        }
    }

    @Override
    public String serialize(Token token) {
        var values = token.values();
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeLong(token.checksum());
            out.writeByte(values.size());
            for (var value : values) {
                out.writeBoolean(value != null);
                if (value != null) {
                    out.writeUTF(value);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }
}
