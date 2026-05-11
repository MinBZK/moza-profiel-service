package nl.rijksoverheid.moz.entity;

import java.util.List;

public interface Scoped {
    List<Scope> getScopes();
    void addScope(Scope scope);
    void clearScopes();
}
