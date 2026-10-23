package com.oldcucumber.oppocastunlock

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SelectionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val apps = ('A'..'Z').map { letter ->
        val label = when (letter) { 'C' -> "Chrome"; 'W' -> "微信"; 'Z' -> "Zeta"; else -> "$letter 应用" }
        AppEntry("com.example.${letter.lowercaseChar()}", label, true, "$letter")
    } + AppEntry("com.example.ticket", "12306", true, "#")
    private fun screen() {
        compose.setContent {
            var state by remember { mutableStateOf(SelectionUiState(apps = apps, loading = false)) }
            CastTheme { SelectionScreen(state,
                onToggle = { pkg, value -> state = state.copy(policy = state.policy.withVisible(pkg, value)) },
                onBatch = { selected, value ->
                    var next = state.policy; selected.forEach { next = next.withVisible(it.packageName, value) }
                    state = state.copy(policy = next)
                }, onReset = { state = state.copy(policy = SelectionPolicy.defaults()) }) }
        }
    }
    @Test fun searchMatchesNameAndPackage() {
        screen()
        compose.onNodeWithTag("search").performTextInput("Chrome")
        compose.onNodeWithTag("app:com.example.c").assertIsDisplayed()
        compose.onNodeWithTag("app:com.example.a").assertDoesNotExist()
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.onNodeWithTag("search").performTextInput("com.example.z")
        compose.onNodeWithTag("app:com.example.z").assertIsDisplayed()
    }
    @Test fun togglingAppMovesItToHiddenFilter() {
        screen()
        compose.onNodeWithTag("app:com.example.a").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("已隐藏").performClick()
        compose.onNodeWithTag("app:com.example.a").assertIsDisplayed()
        compose.onNodeWithTag("app:com.example.b").assertDoesNotExist()
    }
    @Test fun letterClickAndDragNavigateList() {
        screen()
        compose.onNodeWithTag("letter-Z").performClick()
        compose.onNodeWithTag("app:com.example.z").assertIsDisplayed()
        compose.onNodeWithTag("alphabet-rail").performTouchInput {
            swipe(Offset(center.x, height * .2f), Offset(center.x, height * .99f), 500)
        }
        compose.onNodeWithTag("app:com.example.ticket").assertIsDisplayed()
    }
    @Test fun chineseInitialCanBeSearched() {
        screen()
        compose.onNodeWithTag("search").performTextInput("微信")
        compose.onNodeWithTag("app:com.example.w").assertIsDisplayed()
    }
    @Test fun screenshot() {
        screen(); compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
        File(folder, "selection-page.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
