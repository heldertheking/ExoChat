package com.heldertheking.quela.pagination;

/// Exception thrown when a page token is invalid.
///
/// Page tokens can be invalid for several reasons, e.g. when the token can not be decoded or when the token was created
/// for other pagination params i.e. different search, order_by ord parent params.
public class InvalidPageTokenException extends RuntimeException {
    public InvalidPageTokenException(String message) {
        super(message);
    }

    public InvalidPageTokenException(String message, Exception e) {
        super(message, e);
    }
}
