// SPDX-License-Identifier: GPL-3.0-only
package com.oldcucumber.oppocastunlock;

import android.content.SharedPreferences;

/** A successful snapshot in the hooked app, available even if ColorOS blocks the provider. */
final class SelectionCache {
    private final SharedPreferences prefs;
    SelectionCache(SharedPreferences prefs) { this.prefs = prefs; }
    SelectionPolicy read() {
        String value = prefs.getString("policy", null);
        if (value != null) try { return SelectionStore.decode(value); }
        catch (IllegalArgumentException ignored) { /* Fall back when a snapshot is damaged. */ }
        return SelectionPolicy.defaults();
    }
    void save(String value) {
        // Validate first; failed reads must never replace the last working snapshot.
        SelectionStore.decode(value);
        if (!value.equals(prefs.getString("policy", null))) prefs.edit().putString("policy", value).apply();
    }
}
