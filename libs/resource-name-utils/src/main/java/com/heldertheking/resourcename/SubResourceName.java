package com.heldertheking.resourcename;


/// A SubResourceName is a resource name belonging to a parent [ResourceName].
///
/// @param <P>   Type of the parent resource name
/// @param <ID>  Type of the persistence ID for this sub resource
/// @param <PID> persistent ID propertyParser of parent resource name
/// @see ResourceName
public interface SubResourceName<P extends ResourceName<PID>, PID, ID> extends ResourceName<ID>, ChildResourceName<P> {
}
