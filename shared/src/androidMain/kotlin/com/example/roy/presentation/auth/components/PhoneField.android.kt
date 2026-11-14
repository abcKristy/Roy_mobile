package com.example.roy.presentation.auth.components

import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.example.roy.presentation.theme.RoyTheme

@Composable
internal actual fun PhoneField(
    value: String,
    label: String,
    contentDescription: String,
    errorText: String?,
    isEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onImeNext: () -> Unit,
    modifier: Modifier,
    focusRequest: Int,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val typeface by LocalFontFamilyResolver.current.resolve(
        typography.body.fontFamily,
        typography.body.fontWeight ?: FontWeight.Normal,
    )
    val labelTypeface by LocalFontFamilyResolver.current.resolve(
        typography.caption.fontFamily,
        typography.caption.fontWeight ?: FontWeight.Normal,
    )
    val editorId = remember { View.generateViewId() }
    val dimensions = RoyTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radii.textField)
    var focused by remember { mutableStateOf(false) }
    var phoneEditor by remember { mutableStateOf<EditText?>(null) }
    val currentError by rememberUpdatedState(errorText)
    LaunchedEffect(focusRequest) {
        if (focusRequest != 0 && isEnabled) phoneEditor?.focusAndShowKeyboard()
    }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnImeNext by rememberUpdatedState(onImeNext)
    val borderColor = when {
        !isEnabled -> colors.outline
        errorText != null -> colors.error
        focused -> colors.accent
        else -> colors.outline
    }
    val borderWidth = if (isEnabled && (focused || errorText != null)) 2.dp else 1.dp

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacing.extraSmall)) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = dimensions.textFieldMinHeight)
                .background(if (isEnabled) colors.surfaceElevated else colors.surface, shape)
                .border(borderWidth, borderColor, shape)
                .pointerInput(isEnabled) {
                    if (isEnabled) {
                        detectTapGestures {
                            phoneEditor?.focusAndShowKeyboard()
                        }
                    }
                }
                .padding(horizontal = dimensions.spacing.medium, vertical = dimensions.spacing.small),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    TextView(context).apply {
                        labelFor = editorId
                        includeFontPadding = false
                    }
                },
                update = { caption ->
                    caption.text = label
                    caption.typeface = labelTypeface as Typeface
                    caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, typography.caption.fontSize.value)
                    caption.setTextColor((if (isEnabled) colors.textSecondary else colors.textDisabled).toArgb())
                },
            )
            AndroidView(
                modifier = modifier.fillMaxWidth(),
                factory = { context ->
                    EditText(context).apply {
                        phoneEditor = this
                        id = editorId
                        setSingleLine()
                        inputType = InputType.TYPE_CLASS_PHONE
                        imeOptions = EditorInfo.IME_ACTION_NEXT
                        background = null
                        setPadding(0, 0, 0, 0)
                        includeFontPadding = false
                        // Use the native hint to name the editor without replacing its editable value.
                        setHintTextColor(android.graphics.Color.TRANSPARENT)
                        accessibilityDelegate = object : View.AccessibilityDelegate() {
                            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                super.onInitializeAccessibilityNodeInfo(host, info)
                                currentError?.let {
                                    info.isContentInvalid = true
                                    info.error = it
                                }
                            }
                        }
                        setOnFocusChangeListener { _, hasFocus -> focused = hasFocus }
                        setOnEditorActionListener { _, action, _ ->
                            if (action != EditorInfo.IME_ACTION_NEXT) return@setOnEditorActionListener false
                            currentOnImeNext()
                            true
                        }
                        doAfterTextChanged { updated ->
                            val newValue = updated.toString()
                            if (newValue != tag) currentOnValueChange(newValue)
                        }
                    }
                },
                update = { editor ->
                    editor.isEnabled = isEnabled
                    editor.hint = contentDescription
                    editor.typeface = typeface as Typeface
                    editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, typography.body.fontSize.value)
                    editor.setTextColor((if (isEnabled) colors.textPrimary else colors.textDisabled).toArgb())
                    editor.tag = value
                    if (editor.text.toString() != value) {
                        val selection = editor.selectionStart
                        editor.setText(value)
                        editor.setSelection(if (selection < 0) value.length else selection.coerceAtMost(value.length))
                    }
                },
            )
        }
        if (errorText != null) {
            BasicText(
                text = errorText,
                modifier = Modifier.padding(horizontal = dimensions.spacing.medium),
                style = typography.bodySmall.copy(color = colors.error),
            )
        }
    }
}

private fun EditText.focusAndShowKeyboard() {
    if (requestFocus()) {
        post {
            if (isFocused) context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
        }
    }
}
