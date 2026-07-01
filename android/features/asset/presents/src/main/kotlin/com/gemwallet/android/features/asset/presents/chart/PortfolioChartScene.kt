package com.gemwallet.android.features.asset.presents.chart

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.features.asset.viewmodels.chart.models.portfolioChartHeader
import com.gemwallet.android.features.asset.viewmodels.chart.viewmodels.PortfolioChartViewModel
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.chart.ChartStateView
import com.gemwallet.android.ui.components.chart.GemLineChart
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.models.chart.ChartViewState
import com.wallet.core.primitives.PortfolioChartType
import com.wallet.core.primitives.PortfolioType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioChartScene(
    onCancel: () -> Unit,
    viewModel: PortfolioChartViewModel = hiltViewModel(),
) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val showSegmentedControl by viewModel.showSegmentedControl.collectAsStateWithLifecycle()
    val selectedChartType by viewModel.selectedChartType.collectAsStateWithLifecycle()
    val showChartTypePicker by viewModel.showChartTypePicker.collectAsStateWithLifecycle()
    val state by viewModel.chartUIState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    Scene(
        titleContent = {
            if (showSegmentedControl) {
                PortfolioTypeSelector(selected = selectedType, onSelect = viewModel::setType)
            } else {
                Text(stringResource(selectedType.titleRes()))
            }
        },
        onClose = onCancel,
        actions = {
            if (showChartTypePicker) {
                ChartTypeSelector(selected = selectedChartType, onSelect = viewModel::setChartType)
            }
        },
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            LazyColumn {
                item { PortfolioChart(viewModel) }
                if (state.viewState == ChartViewState.Ready || state.viewState == ChartViewState.Empty) {
                    portfolioStatistics(currency, statistics)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortfolioTypeSelector(selected: PortfolioType, onSelect: (PortfolioType) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        val types = PortfolioType.entries
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                label = { Text(stringResource(type.titleRes())) },
            )
        }
    }
}

@Composable
private fun ChartTypeSelector(selected: PortfolioChartType, onSelect: (PortfolioChartType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(stringResource(selected.titleRes()), fontWeight = FontWeight.SemiBold)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        PortfolioChartType.entries.forEach { type ->
            DropdownMenuItem(
                text = { Text(stringResource(type.titleRes())) },
                onClick = {
                    onSelect(type)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun PortfolioChart(viewModel: PortfolioChartViewModel) {
    val uiModel by viewModel.chartUIModel.collectAsStateWithLifecycle()
    val state by viewModel.chartUIState.collectAsStateWithLifecycle()
    val periods by viewModel.availablePeriods.collectAsStateWithLifecycle()
    val showHeaderValue by viewModel.showHeaderValue.collectAsStateWithLifecycle()

    key(state.period) {
        var selectedIndex by remember { mutableStateOf<Int?>(null) }

        val displayState = when {
            state.period != uiModel.period -> ChartViewState.Loading
            else -> state.viewState
        }
        val chartPoints = uiModel.chartPoints
        val selectedPoint = if (displayState == ChartViewState.Ready) {
            selectedIndex?.let { chartPoints.getOrNull(it) }
        } else {
            null
        }

        ChartStateView(
            state = displayState,
            header = portfolioChartHeader(uiModel, selectedPoint, showHeaderValue),
            period = state.period,
            onPeriodSelect = viewModel::setPeriod,
            periods = periods,
        ) {
            GemLineChart(
                points = uiModel.renderPoints,
                lineColor = MaterialTheme.colorScheme.primary,
                selectedIndex = selectedIndex,
                onSelectionChanged = { selectedIndex = it },
                minLabel = uiModel.minLabel,
                maxLabel = uiModel.maxLabel,
            )
        }
    }
}

private fun PortfolioType.titleRes(): Int = when (this) {
    PortfolioType.Wallet -> R.string.wallet_portfolio_title
    PortfolioType.Perpetuals -> R.string.perpetuals_title
}

private fun PortfolioChartType.titleRes(): Int = when (this) {
    PortfolioChartType.Value -> R.string.perpetual_value
    PortfolioChartType.Pnl -> R.string.perpetual_pnl
}
