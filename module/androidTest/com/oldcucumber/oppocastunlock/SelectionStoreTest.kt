package com.oldcucumber.oppocastunlock

import android.icu.text.AlphabeticIndex
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class SelectionStoreTest {
    @Test fun preferenceAndProviderRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SelectionStore(context)
        val before = store.read()
        try {
            store.write(SelectionPolicy.defaults().withVisible("com.example.chat", false).withVisible("system.settings", true))
            context.contentResolver.query(SelectionStore.URI, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val policy = SelectionStore.decode(cursor.getString(0))
                assertFalse(policy.isVisible("com.example.chat", true, emptySet()))
                assertTrue(policy.isVisible("system.settings", false, emptySet()))
            }
            assertTrue(SelectionStore(context).read().excluded.contains("com.example.chat"))
        } finally { store.write(before) }
    }
    @Test fun chineseAndEnglishBucketsUseInitials() {
        val index = AlphabeticIndex<String>(Locale.SIMPLIFIED_CHINESE).addLabels(Locale.ENGLISH).buildImmutableIndex()
        assertEquals("W", index.getBucket(index.getBucketIndex("微信")).label)
        assertEquals("Z", index.getBucket(index.getBucketIndex("支付宝")).label)
        assertEquals("C", index.getBucket(index.getBucketIndex("Chrome")).label)
    }
}
