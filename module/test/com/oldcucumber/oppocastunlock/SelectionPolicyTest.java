package com.oldcucumber.oppocastunlock;

import org.junit.Test;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SelectionPolicyTest {
    @Test public void defaultsPreserveOriginalAndAddUserAppsWithoutDuplicates() {
        assertEquals(Arrays.asList("system.gallery", "user.chat", "user.reader"),
            SelectionPolicy.defaults().apply(Arrays.asList("system.gallery", "user.chat"), Arrays.asList("user.chat", "user.reader")));
    }
    @Test public void hidingRemovesOriginalAndUserEntries() {
        SelectionPolicy policy = SelectionPolicy.defaults().withVisible("system.gallery", false).withVisible("user.chat", false);
        assertEquals(List.of("user.reader"), policy.apply(List.of("system.gallery"), List.of("user.chat", "user.reader")));
        assertFalse(policy.isVisible("system.gallery", false, List.of("system.gallery")));
        assertFalse(policy.isVisible("user.chat", true, List.of()));
    }
    @Test public void reselectRestoresPreviouslyHiddenApp() {
        SelectionPolicy policy = SelectionPolicy.defaults().withVisible("system.gallery", false).withVisible("system.gallery", true);
        assertEquals(List.of("system.gallery"), policy.apply(List.of("system.gallery"), List.of()));
        assertTrue(policy.excluded.isEmpty());
    }
    @Test public void explicitlyIncludedSystemAppIsAdded() {
        SelectionPolicy policy = SelectionPolicy.defaults().withVisible("system.settings", true);
        assertTrue(policy.isVisible("system.settings", false, List.of()));
        assertEquals(List.of("system.settings"), policy.apply(List.of(), List.of()));
    }
    @Test public void denyWinsConflictingInput() {
        SelectionPolicy policy = new SelectionPolicy(List.of("user.chat"), List.of("user.chat"));
        assertFalse(policy.isVisible("user.chat", true, List.of("user.chat")));
        assertTrue(policy.apply(List.of("user.chat"), List.of("user.chat")).isEmpty());
    }
    @Test public void selectionDoesNotMutateSourceOrPreviousPolicy() {
        ArrayList<String> original = new ArrayList<>(List.of("system.gallery"));
        SelectionPolicy before = SelectionPolicy.defaults();
        before.withVisible("user.chat", false).apply(original, List.of("user.chat"));
        assertEquals(List.of("system.gallery"), original);
        assertTrue(before.excluded.isEmpty());
    }
    @Test public void resetReturnsToOriginalAndNewUserApps() {
        assertTrue(SelectionPolicy.defaults().isVisible("new.app", true, List.of()));
        assertFalse(SelectionPolicy.defaults().isVisible("unknown.system", false, List.of()));
    }
}
