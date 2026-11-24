package com.heldertheking.resourcename;

/// Use this for child resources which do not have its own template variables, i.e. for singletons
/// @param <P> propertyParser of the parent
public non-sealed interface ChildResourceName<P> extends Name {
    P getParent();
}
