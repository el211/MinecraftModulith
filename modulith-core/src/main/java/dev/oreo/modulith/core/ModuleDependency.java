package dev.oreo.modulith.core;

import java.util.Objects;

/** Parsed dependency selector such as {@code economy} or {@code economy::payments}. */
public record ModuleDependency(String moduleId, String apiName) {
    public ModuleDependency {
        Objects.requireNonNull(moduleId, "moduleId");
        if (moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId cannot be blank");
        }
        if (apiName != null && apiName.isBlank()) {
            throw new IllegalArgumentException("apiName cannot be blank");
        }
    }

    public static ModuleDependency parse(String selector) {
        Objects.requireNonNull(selector, "selector");
        String value = selector.trim();
        int delimiter = value.indexOf("::");
        if (delimiter < 0) {
            return new ModuleDependency(value, null);
        }
        if (value.indexOf("::", delimiter + 2) >= 0) {
            throw new IllegalArgumentException("Invalid module dependency selector: " + selector);
        }
        return new ModuleDependency(value.substring(0, delimiter).trim(), value.substring(delimiter + 2).trim());
    }

    public boolean allows(String ownerModule, String publishedApi) {
        if (!moduleId.equals(ownerModule)) {
            return false;
        }
        return apiName == null || apiName.equals(publishedApi);
    }

    @Override
    public String toString() {
        return apiName == null ? moduleId : moduleId + "::" + apiName;
    }
}
