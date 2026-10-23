// SPDX-License-Identifier: GPL-3.0-only
package com.oldcucumber.oppocastunlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Shared selection rules; independent of Android so UI and hooks agree. */
public final class SelectionPolicy {
    public final Set<String> included;
    public final Set<String> excluded;

    public SelectionPolicy(Collection<String> included, Collection<String> excluded) {
        this.excluded = Collections.unmodifiableSet(new LinkedHashSet<>(excluded));
        LinkedHashSet<String> allow = new LinkedHashSet<>(included);
        allow.removeAll(this.excluded);
        this.included = Collections.unmodifiableSet(allow);
    }
    public static SelectionPolicy defaults() { return new SelectionPolicy(Collections.emptySet(), Collections.emptySet()); }
    public boolean isVisible(String pkg, boolean userApp, Collection<String> original) {
        if (excluded.contains(pkg)) return false;
        return included.contains(pkg) || userApp || original.contains(pkg);
    }
    public SelectionPolicy withVisible(String pkg, boolean visible) {
        LinkedHashSet<String> allow = new LinkedHashSet<>(included);
        LinkedHashSet<String> deny = new LinkedHashSet<>(excluded);
        if (visible) { deny.remove(pkg); allow.add(pkg); }
        else { allow.remove(pkg); deny.add(pkg); }
        return new SelectionPolicy(allow, deny);
    }
    public ArrayList<String> apply(Collection<String> original, Collection<String> userApps) {
        LinkedHashSet<String> result = new LinkedHashSet<>(original);
        result.addAll(userApps);
        result.addAll(included);
        result.removeAll(excluded);
        return new ArrayList<>(result);
    }
}
