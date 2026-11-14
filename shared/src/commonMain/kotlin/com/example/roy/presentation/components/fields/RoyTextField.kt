package com.example.roy.presentation.components.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription as describeContent
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun RoyTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    isEnabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeAction: ((ImeAction) -> Unit)? = null,
    contentDescription: String = label,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val dimensions = RoyTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radii.textField)
    val interactions = remember { MutableInteractionSource() }
    val isFocused by interactions.collectIsFocusedAsState()
    val borderColor = when {
        !isEnabled -> colors.outline
        errorText != null -> colors.error
        isFocused -> colors.accent
        else -> colors.outline
    }
    val borderWidth = if (isEnabled && (isFocused || errorText != null)) 2.dp else 1.dp
    val valueColor = if (isEnabled) colors.textPrimary else colors.textDisabled
    val labelColor = if (isEnabled) colors.textSecondary else colors.textDisabled

    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimensions.spacing.extraSmall)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    describeContent = contentDescription
                    errorText?.let { error(it) }
                },
            enabled = isEnabled,
            textStyle = typography.body.copy(color = valueColor),
            keyboardOptions = keyboardOptions,
            keyboardActions = onImeAction?.let(::keyboardActionsFor) ?: KeyboardActions.Default,
            singleLine = true,
            visualTransformation = visualTransformation,
            interactionSource = interactions,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerField ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensions.textFieldMinHeight)
                        .background(if (isEnabled) colors.surfaceElevated else colors.surface, shape)
                        .border(borderWidth, borderColor, shape)
                        .padding(horizontal = dimensions.spacing.medium, vertical = dimensions.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
                ) {
                    BasicText(
                        text = label,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = typography.caption.copy(color = labelColor),
                    )
                    innerField()
                }
            },
        )
        if (errorText != null) {
            BasicText(
                text = errorText,
                modifier = Modifier.padding(horizontal = dimensions.spacing.medium),
                style = typography.bodySmall.copy(color = colors.error),
            )
        }
    }
}

private fun keyboardActionsFor(onAction: (ImeAction) -> Unit) = KeyboardActions(
    onDone = { onAction(ImeAction.Done) },
    onGo = { onAction(ImeAction.Go) },
    onNext = { onAction(ImeAction.Next) },
    onPrevious = { onAction(ImeAction.Previous) },
    onSearch = { onAction(ImeAction.Search) },
    onSend = { onAction(ImeAction.Send) },
)
