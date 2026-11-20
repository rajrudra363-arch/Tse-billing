package com.example.ui.components

import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SafeTextToolbar(
    private val defaultToolbar: TextToolbar,
    private val view: View
) : TextToolbar {
    private var hiding = false

    override val status: TextToolbarStatus
        get() = defaultToolbar.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        if (!view.isAttachedToWindow) return
        runCatching {
            defaultToolbar.showMenu(
                rect,
                onCopyRequested,
                onPasteRequested,
                onCutRequested,
                onSelectAllRequested
            )
        }
    }

    override fun hide() {
        if (hiding) return
        if (defaultToolbar.status != TextToolbarStatus.Shown) return
        hiding = true
        try {
            if (view.isAttachedToWindow) {
                defaultToolbar.hide()
            }
        } catch (_: Throwable) {
        } finally {
            hiding = false
        }
    }
}

@Composable
fun ProvideSafeTextToolbar(content: @Composable () -> Unit) {
    val defaultToolbar = LocalTextToolbar.current
    val view = LocalView.current
    val safeToolbar = remember(defaultToolbar, view) {
        SafeTextToolbar(defaultToolbar, view)
    }
    DisposableEffect(safeToolbar) {
        onDispose {
            safeToolbar.hide()
        }
    }
    CompositionLocalProvider(LocalTextToolbar provides safeToolbar) {
        content()
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    this
        .bringIntoViewRequester(requester)
        .onFocusEvent { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(180)
                    runCatching { requester.bringIntoView() }
                }
            }
        }
}

fun clearFocusAndHideToolbar(focusManager: FocusManager, textToolbar: TextToolbar) {
    runCatching { textToolbar.hide() }
    runCatching { focusManager.clearFocus(force = true) }
}
