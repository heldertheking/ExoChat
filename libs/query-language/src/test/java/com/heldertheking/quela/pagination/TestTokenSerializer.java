package com.heldertheking.quela.pagination;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/// TestTokenSerializer is a test implementation of [PaginationTokenSerializer] that uses a map to store tokens.
/// It does not actually serialize or deserialize tokens, but instead uses a random number to generate a new token.
public class TestTokenSerializer implements PaginationTokenSerializer {
    private final Map<String, Token> tokens = new HashMap<>();
    private Random random;

    @Override
    public Token deserialize(String tokenStr) {
        if (tokenStr.isEmpty()) {
            return new Token(1234L, null);
        }
        var token = tokens.get(tokenStr);
        if (token == null) {
            throw new InvalidPageTokenException("Token not found");
        }
        return token;
    }

    @Override
    public String serialize(Token token) {
        random = new Random();
        var str = "test-token-new-" + random.nextLong();
        tokens.put(str, token);
        return str;
    }
}
