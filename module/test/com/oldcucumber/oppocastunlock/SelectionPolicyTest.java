package com.oldcucumber.oppocastunlock;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static org.junit.Assert.*;

public class SelectionPolicyTest {
    @Test public void defaultsUseOnlyOriginalListWithoutDuplicates() {
        assertEquals(List.of("system.gallery", "user.chat"),
            SelectionPolicy.defaults().apply(List.of("system.gallery", "user.chat", "user.chat")));
        assertFalse(SelectionPolicy.defaults().isVisible("other.user", List.of("user.chat")));
    }
    @Test public void hidingRemovesOriginalAndUserEntries() {
        SelectionPolicy policy = SelectionPolicy.defaults().withVisible("user.reader", true)
            .withVisible("system.gallery", false).withVisible("user.chat", false);
        assertEquals(List.of("user.reader"), policy.apply(List.of("system.gallery", "user.chat")));
        assertFalse(policy.isVisible("system.gallery", List.of("system.gallery")));
        assertFalse(policy.isVisible("user.chat", List.of()));
    }
    @Test public void reselectRestoresPreviouslyHiddenApp() {
        SelectionPolicy policy = SelectionPolicy.defaults().withVisible("system.gallery", false).withVisible("system.gallery", true);
        assertEquals(List.of("system.gallery"), policy.apply(List.of("system.gallery")));
        assertTrue(policy.excluded.isEmpty());
    }
    @Test public void explicitlyIncludedSystemAppIsAdded() {
        SelectionPolicy policy = SelectionPolicy.defaults().withVisible("system.settings", true);
        assertTrue(policy.isVisible("system.settings", List.of()));
        assertEquals(List.of("system.settings"), policy.apply(List.of()));
    }
    @Test public void denyWinsConflictingInput() {
        SelectionPolicy policy = new SelectionPolicy(List.of("user.chat"), List.of("user.chat"));
        assertFalse(policy.isVisible("user.chat", List.of("user.chat")));
        assertTrue(policy.apply(List.of("user.chat")).isEmpty());
    }
    @Test public void selectionDoesNotMutateSourceOrPreviousPolicy() {
        ArrayList<String> original = new ArrayList<>(List.of("system.gallery"));
        SelectionPolicy before = SelectionPolicy.defaults();
        before.withVisible("user.chat", true).apply(original);
        assertEquals(List.of("system.gallery"), original);
        assertTrue(before.excluded.isEmpty());
    }
    @Test public void resetFollowsUpdatedBuiltInListWithoutAddingNewApps() {
        SelectionPolicy reset = SelectionPolicy.defaults();
        assertEquals(List.of("built.old"), reset.apply(List.of("built.old")));
        assertEquals(List.of("built.new"), reset.apply(List.of("built.new")));
        assertFalse(reset.isVisible("new.user", List.of("built.new")));
    }
    @Test public void hidingEverythingClearsStaleCache() {
        HashMap<String, Object> cache = new HashMap<>();
        cache.put("built.chat", new Object()); cache.put("extra.user", new Object());
        SelectionPolicy.defaults().withVisible("built.chat", false).pruneCache(cache, List.of("built.chat"));
        assertTrue(cache.isEmpty());
    }
    @Test public void resettingRemovesAddedAppsFromCache() {
        HashMap<String, Object> cache = new HashMap<>();
        cache.put("built.chat", new Object()); cache.put("extra.user", new Object());
        SelectionPolicy.defaults().pruneCache(cache, List.of("built.chat"));
        assertEquals(List.of("built.chat"), new ArrayList<>(cache.keySet()));
    }
}
