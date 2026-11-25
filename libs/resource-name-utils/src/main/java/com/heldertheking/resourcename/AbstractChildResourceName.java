package com.heldertheking.resourcename;

import java.util.Objects;

import com.google.api.pathtemplate.PathTemplate;

public abstract class AbstractChildResourceName<P extends ResourceName<?>> implements ChildResourceName<P> {
    private final P parent;

    protected AbstractChildResourceName(P parent) {
        this.parent = parent;
    }

    protected abstract PathTemplate pathTemplate();

    @Override
    public String toString() {
        return pathTemplate().instantiate(parent.getTemplateVariables());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (this.getClass() != o.getClass()) {
            return false;
        }
        var that = (AbstractChildResourceName<?>) o;
        return Objects.equals(parent, that.getParent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent);
    }

    @Override
    public P getParent() {
        return parent;
    }
}
