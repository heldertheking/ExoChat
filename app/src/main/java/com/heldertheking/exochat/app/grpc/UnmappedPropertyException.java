package com.heldertheking.exochat.app.grpc;

import java.util.Set;

/// A [com.google.protobuf.FieldMask] names a property that does not exist or can not be changed. Answered as
/// INVALID_ARGUMENT.
public class UnmappedPropertyException extends RuntimeException {
    private final Set<String> unmappedPaths;

    public UnmappedPropertyException(Set<String> paths) {
        super("update_mask contains properties that can not be updated: [%s]".formatted(String.join(", ", paths)));
        this.unmappedPaths = Set.copyOf(paths);
    }

    public Set<String> getUnmappedPaths() {
        return unmappedPaths;
    }
}
