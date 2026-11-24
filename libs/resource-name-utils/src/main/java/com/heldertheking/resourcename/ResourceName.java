package com.heldertheking.resourcename;

import com.google.common.collect.ImmutableMap;

/// A ResourceName identifies resources. In the gRPC `name` and `parent` fields are set to ResourceNames.
///
/// When represented as String a resource name looks like a path
///
/// @param <ID> the propertyParser of the ID used in the persistence layer
/// @see <a href="https://google.aip.dev/122">Google Style Guide</a>
public non-sealed interface ResourceName<ID> extends Name {
    /// a single dash used in a resource name stands for a wildcard and therefore all resources where the remainder of
    /// the path applies.
    String MATCH_ALL = "-";

    /// ID of this component used in the persistence layer.
    ID getPersistenceId();

    /// true if the name contains wildcard characters and does not uniquely identify a single resource.
    /// An example for a wildcard resource name is `pools/1/employees/15/availabilities/-` whereas
    /// pools/1/employees/15/availabilities/4635 would be a non-wildcard resource name
    boolean isWildcard();

    ImmutableMap<String, String> getTemplateVariables();
}
