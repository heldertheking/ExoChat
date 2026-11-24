package com.heldertheking.resourcename;

/// ResourceNotFoundException indicates that some element was not found because it does not exist in the data storage.
/// This should be theo
public class ResourceNotFoundException extends RuntimeException {
    private final Name resourceName;

    public ResourceNotFoundException(Name notFoundName) {
        super("object %s has not been found".formatted(notFoundName.toString()));
        resourceName = notFoundName;
    }

    public ResourceNotFoundException(String message, ResourceName<?> resourceName) {
        super("%s (resource: %s)".formatted(message, resourceName.toString()));
        this.resourceName = resourceName;
    }

    public Name getResourceName() {
        return resourceName;
    }
}
