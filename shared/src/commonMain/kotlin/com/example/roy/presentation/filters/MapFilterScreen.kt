package com.example.roy.presentation.filters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.map.PointFilter
import com.example.roy.presentation.core.CollectMviActions

@Composable
internal fun MapFilterScreen(
    viewModel: MapFilterViewModel,
    onBack: () -> Unit,
    onApplied: (PointFilter) -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            is MapFilterAction.Applied -> onApplied(action.filter)
            MapFilterAction.Back -> onBack()
        }
    }
    MapFilterView(viewState, eventHandler)
}
