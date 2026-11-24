package com.heldertheking.quela.pagination;

import java.util.List;

/// Interface for serializing and deserializing pagination tokens to and from strings. Pagination state is exchanged
/// as opaque strings between the client and the server. The server uses the token to determine the next page to return.
/// [Token]s are used to represent the state of a paginated query. It consists of a checksum and a list of values
/// of ordered fields for the last value returned in a page. The checksum is used to ensure a token is only used with
/// requests that have the same filter, order_by and additional pagination params.
///
/// (!) A serializer must properly serialize and deserialize null values in [Token#values()]
public interface PaginationTokenSerializer {

    /// Deserializes a token from a string into a checksum and a list of String values.
    ///
    /// @param token the token string
    /// @return the deserialized token
    /// @throws Exception if the token can not be deserialized for any reason
    Token deserialize(String token) throws Exception;

    /// Serializes a token into a string.
    ///
    /// @param token the token to serialize
    /// @return the serialized token
    String serialize(Token token);

    /// A Token representing a page of a paginated query.
    ///
    /// @param checksum
    /// @param values   list of values of ordered fields for the last value returned in a page.
    ///                 Note that some values may be null and [#serialize(Token)] and [#deserialize(String)]
    ///                 must properly serialize/deserialize null values.
    record Token(long checksum, List<String> values) {

    }
}
