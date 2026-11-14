package com.example.roy.presentation.auth.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_consent_checked

@Composable
internal fun ConsentCheckbox(
    isChecked: Boolean,
    isEnabled: Boolean,
    policyVersion: String?,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val hasPolicy = !policyVersion.isNullOrBlank()
    val checked = isChecked && hasPolicy
    val enabled = isEnabled && hasPolicy
    val dimensions = RoyTheme.dimensions
    Box(
        Modifier.size(dimensions.minimumTouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Image(
                painterResource(Res.drawable.auth_consent_checked),
                contentDescription = null,
                modifier = Modifier.size(dimensions.iconSize).alpha(if (enabled) 1f else 0.5f),
            )
        } else {
            Box(
                Modifier.size(dimensions.iconSize).border(
                    1.dp,
                    if (enabled) RoyTheme.colors.textSecondary else RoyTheme.colors.outline,
                    RoundedCornerShape(7.dp),
                ),
            )
        }
    }
}
