package com.oldcucumber.oppocastunlock

import android.icu.text.AlphabeticIndex
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SelectionStoreTest {
    @Test fun openingPickerSignalsRecoveryWithoutChangingSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SelectionStore(context)
        val before = store.read()
        val notified = CountDownLatch(1)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { notified.countDown() }
        }
        context.contentResolver.registerContentObserver(SelectionStore.URI, false, observer)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue("Opening the picker should wake an existing hook observer", notified.await(5, TimeUnit.SECONDS))
                assertEquals(before.included, store.read().included)
                assertEquals(before.excluded, store.read().excluded)
            }
        } finally { context.contentResolver.unregisterContentObserver(observer) }
    }
    @Test fun cachedSelectionSurvivesRestartAndRejectsInvalidUpdate() = withPreferences { _, prefs ->
        val value = """{"included":["extra.chat"],"excluded":["built.hidden"]}"""
        SelectionCache(prefs).save(value)
        try {
            SelectionCache(prefs).save("invalid")
            fail("Invalid configuration must not replace the working snapshot")
        } catch (_: IllegalArgumentException) { }
        val restored = SelectionCache(prefs).read()
        assertTrue(restored.isVisible("extra.chat", emptySet()))
        assertFalse(restored.isVisible("built.hidden", setOf("built.hidden")))
    }
    @Test fun missingOrDamagedSnapshotUsesBuiltInSelection() = withPreferences { _, prefs ->
        assertEquals(listOf("built.chat"), SelectionCache(prefs).read().apply(setOf("built.chat")))
        prefs.edit().putString("policy", "invalid").commit()
        assertEquals(listOf("built.chat"), SelectionCache(prefs).read().apply(setOf("built.chat")))
    }
    @Test fun preferenceAndProviderRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SelectionStore(context)
        val before = store.read()
        try {
            store.write(SelectionPolicy.defaults().withVisible("com.example.chat", false).withVisible("system.settings", true))
            context.contentResolver.query(SelectionStore.URI, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val policy = SelectionStore.decode(cursor.getString(0))
                assertFalse(policy.isVisible("com.example.chat", emptySet()))
                assertTrue(policy.isVisible("system.settings", emptySet()))
            }
            assertTrue(SelectionStore(context).read().excluded.contains("com.example.chat"))
        } finally { store.write(before) }
    }
    @Test fun freshInstallDoesNotAutomaticallyAddUserApps() = withPreferences { context, prefs ->
        val store = SelectionStore(context, prefs)
        assertTrue(store.read().included.isEmpty())
        assertTrue(store.read().apply(emptySet()).isEmpty())
    }
    @Test fun legacyChoicesMigrateOnceAndResetStaysBuiltInOnly() = withPreferences { context, prefs ->
        prefs.edit().putStringSet("included", setOf("manual.system"))
            .putStringSet("excluded", setOf(context.packageName)).commit()
        val store = SelectionStore(context, prefs)
        val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        assertTrue(store.read().included.contains(testPackage))
        assertTrue(store.read().included.contains("manual.system"))
        assertFalse(store.read().isVisible(context.packageName, emptySet()))
        store.write(SelectionPolicy.defaults())
        val reopened = SelectionStore(context, prefs)
        assertTrue(reopened.read().included.isEmpty())
        assertTrue(reopened.read().excluded.isEmpty())
    }
    @Test fun resetPersistsAndUsesUpdatedBuiltInList() = withPreferences { context, prefs ->
        val store = SelectionStore(context, prefs)
        store.saveBaseline(setOf("built.chat", "system.gallery"))
        store.write(SelectionPolicy.defaults().withVisible("extra.user", true).withVisible("built.chat", false))
        store.write(SelectionPolicy.defaults())
        val reopened = SelectionStore(context, prefs)
        assertEquals(setOf("built.chat", "system.gallery"), reopened.read().apply(reopened.baseline()).toSet())
        reopened.saveBaseline(setOf("built.updated"))
        assertEquals(listOf("built.updated"), reopened.read().apply(reopened.baseline()))
    }
    private fun withPreferences(block: (android.content.Context, android.content.SharedPreferences) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("selection-test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try { block(context, prefs) } finally { prefs.edit().clear().commit() }
    }
    @Test fun chineseAndEnglishBucketsUseInitials() {
        val index = AlphabeticIndex<String>(Locale.SIMPLIFIED_CHINESE).addLabels(Locale.ENGLISH).buildImmutableIndex()
        assertEquals("W", index.getBucket(index.getBucketIndex("微信")).label)
        assertEquals("Z", index.getBucket(index.getBucketIndex("支付宝")).label)
        assertEquals("C", index.getBucket(index.getBucketIndex("Chrome")).label)
    }
}
