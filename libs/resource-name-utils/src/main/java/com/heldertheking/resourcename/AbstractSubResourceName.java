package com.heldertheking.resourcename;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.google.api.pathtemplate.TemplatedResourceName;
import com.google.api.pathtemplate.ValidationException;
import com.google.common.collect.ImmutableMap;

/// AbstractSubResourceName can be used to more easily implement the [SubResourceName] interface on sub resources.
///
/// @param <P>   parent propertyParser
/// @param <PID> parent id propertyParser
/// @param <ID>  this id propertyParser
public abstract class AbstractSubResourceName<P extends ResourceName<PID>, PID, ID>
        implements SubResourceName<P, PID, ID> {
    private final Optional<ID> id;
    private final P parent;

    protected AbstractSubResourceName(P parent, @Nullable ID id) {
        if (parent.isWildcard() && id != null) {
            throw new IllegalArgumentException("parent can only be wildcard if child is also wildcard");
        }

        this.id = Optional.ofNullable(id);
        this.parent = parent;
    }

    protected static @Nullable Long extractLong(TemplatedResourceName name, String variableName) {
        var v = name.get(variableName);
        if (v == null) {
            throw new ValidationException(
                    "variable %s not found in %s", variableName, name.template().toRawString());
        }
        if (MATCH_ALL.equals(v)) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException _) {
            throw new ValidationException(
                    "could not extract '%s' from template '%s' as integer number",
                    variableName, name.template().toRawString());
        }
    }

    protected static @Nullable String extractString(TemplatedResourceName name, String variableName) {
        var v = name.get(variableName);
        if (v == null) {
            throw new ValidationException(
                    "variable %s not found in %s", variableName, name.template().toRawString());
        }
        if (MATCH_ALL.equals(v)) {
            return null;
        }
        return v;
    }

    protected abstract PathTemplate pathTemplate();

    protected abstract String variableName();

    @Override
    public P getParent() {
        return parent;
    }

    @Override
    public boolean isWildcard() {
        return id.isEmpty() || parent.isWildcard();
    }

    @Override
    public ID getPersistenceId() {
        if (id.isEmpty()) {
            throw new IllegalStateException("can not get persistence id on wildcard name %s".formatted(toString()));
        }
        return id.get();
    }

    @Override
    public String toString() {
        return pathTemplate().instantiate(getTemplateVariables());
    }

    @Override
    public ImmutableMap<String, String> getTemplateVariables() {
        return ImmutableMap.<String, String>builder()
                .putAll(parent.getTemplateVariables())
                .put(variableName(), id.map(Object::toString).orElse(MATCH_ALL))
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (this.getClass() != o.getClass()) {
            return false;
        }
        var that = (AbstractSubResourceName<?, ?, ?>) o;
        return Objects.equals(id, that.id) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, parent);
    }
}
