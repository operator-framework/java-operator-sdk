package io.javaoperatorsdk.operator.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class OperatorConfiguration {
    
    private Set<String> namespaces = new HashSet<>();
    public static String ALL_NAMESPACES = "all_namespaces";
    
    public Set<String> getNamespaces() {
        return namespaces;
    }
    
    public OperatorConfiguration setNamespaces(Set<String> namespaces) {
        this.namespaces = namespaces;
        return this;
    }
    
    public OperatorConfiguration addWatchedNamespaces(String... namespaces) {
        this.namespaces.addAll(Arrays.asList(namespaces));
        return this;
    }
    
    public OperatorConfiguration watchAllNamespaces() {
        this.namespaces.add(ALL_NAMESPACES);
        return this;
    }
    
    public boolean isWatchingAllNamespaces() {
        return namespaces.contains(ALL_NAMESPACES);
    }
    
    public boolean isWatchingCurrentNamespace() {
        return namespaces.isEmpty();
    }
    
    public Optional<String> getWatchedNamespaceIfUnique() {
        return namespaces.stream().findFirst();
    }
}
