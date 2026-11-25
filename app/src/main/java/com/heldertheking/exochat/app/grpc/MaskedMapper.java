package com.heldertheking.exochat.app.grpc;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.google.protobuf.FieldMask;

/// Maps the properties of a gRPC message to the persistence model for an update with a [FieldMask] (AIP-134):
/// a property is set if the mask names it (or is the wildcard `*`) or if there is no mask at all. Ported from
/// app-gateway.
///
/// ```
/// MaskedMapper.forMask(mask)
///     .setIfMaskedAndPresent("comment", message.hasComment(), message::getComment, record::setComment)
///     .throwOnUnmappedProperty();
/// ```
///
/// Call [#throwOnUnmappedProperty()] last: a path in the mask that no property claimed is an error for the caller.
public final class MaskedMapper {
    private static final String WILDCARD = "*";

    private final Set<String> allowedPaths;
    private final boolean allPropertiesAllowed;
    private final Set<String> stillToBeMappedPaths;

    private MaskedMapper(Set<String> allowedPaths, boolean allPropertiesAllowed) {
        this.allowedPaths = allowedPaths;
        this.allPropertiesAllowed = allPropertiesAllowed;
        this.stillToBeMappedPaths = new HashSet<>(allowedPaths);
        this.stillToBeMappedPaths.remove(WILDCARD);
    }

    /// Every property may be set.
    public static MaskedMapper forNoExplicitMask() {
        return new MaskedMapper(Set.of(), true);
    }

    /// Only the properties the mask names may be set. Without a mask (null) every property may.
    public static MaskedMapper forMask(@Nullable FieldMask mask) {
        if (mask == null) {
            return forNoExplicitMask();
        }
        var paths = Set.copyOf(mask.getPathsList());
        return new MaskedMapper(paths, paths.contains(WILDCARD));
    }

    private boolean isSettable(String path) {
        return allPropertiesAllowed || allowedPaths.contains(path);
    }

    /// Sets the property if the mask allows it.
    public <V> MaskedMapper setIfMasked(String property, V value, Consumer<V> setter) {
        if (isSettable(property)) {
            setter.accept(value);
        }
        stillToBeMappedPaths.remove(property);
        return this;
    }

    /// Sets the property if the mask allows it and the message has a value for it. The value is only read then,
    /// so the supplier may validate it.
    public <V> MaskedMapper setIfMaskedAndPresent(
            String property, boolean isPresent, Supplier<V> valueSupplier, Consumer<V> setter) {
        if (isPresent && isSettable(property)) {
            setter.accept(valueSupplier.get());
        }
        stillToBeMappedPaths.remove(property);
        return this;
    }

    /// @throws UnmappedPropertyException if the mask names a property that no call claimed
    public void throwOnUnmappedProperty() {
        if (!stillToBeMappedPaths.isEmpty()) {
            throw new UnmappedPropertyException(stillToBeMappedPaths);
        }
    }
}
