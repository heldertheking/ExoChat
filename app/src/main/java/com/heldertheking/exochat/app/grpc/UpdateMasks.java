package com.heldertheking.exochat.app.grpc;

import java.util.LinkedHashSet;
import java.util.Set;

import com.google.protobuf.FieldMask;

import io.grpc.Status;

/// Reads the `update_mask` of an update request (AIP-134).
public final class UpdateMasks {
    private UpdateMasks() {}

    /// The paths of the mask. A mask without paths means every given field is updated, the caller then passes the
    /// fields it finds set in the request.
    ///
    /// @param mask       the mask of the request
    /// @param updatable  the field paths that may be updated
    /// @param whenAbsent the paths to use if the mask is empty
    /// @throws io.grpc.StatusRuntimeException INVALID_ARGUMENT if the mask names a field that cannot be updated
    public static Set<String> paths(FieldMask mask, Set<String> updatable, Set<String> whenAbsent) {
        if (mask.getPathsCount() == 0) {
            return whenAbsent;
        }
        var paths = new LinkedHashSet<String>();
        for (var path : mask.getPathsList()) {
            if (!updatable.contains(path)) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("update_mask path '%s' is unknown or can not be updated".formatted(path))
                        .asRuntimeException();
            }
            paths.add(path);
        }
        return paths;
    }
}
