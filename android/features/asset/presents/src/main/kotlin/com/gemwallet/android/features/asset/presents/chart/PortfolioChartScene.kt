package com.gemwallet.android.features.asset.presents.chart

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.features.asset.viewmodels.chart.models.AllTimeUIModel
import com.gemwallet.android.features.asset.viewmodels.chart.models.chartHeader
import com.gemwallet.android.features.asset.viewmodels.chart.viewmodels.PortfolioChartViewModel
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.chart.ChartStateView
import com.gemwallet.android.ui.components.chart.GemLineChart
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.models.chart.ChartViewState
import com.wallet.core.primitives.ChartPeriod
import com.wallet.core.primitives.PortfolioStatistic

private val portfolioChartPeriods = listOf(
    ChartPeriod.Day,
    ChartPeriod.Week,
    ChartPeriod.Month,
    ChartPeriod.Year,
    ChartPeriod.All,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioChartScene(
    onCancel: () -> Unit,
    viewModel: PortfolioChartViewModel = hiltViewModel(),
) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    Scene(
        title = stringResource(R.string.wallet_portfolio_title),
        onClose = onCancel,
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
                allTimeProperties(currency, statistics.toAllTimeItems())
            }
        }
    }
}

@Composable
private fun PortfolioChart(viewModel: PortfolioChartViewModel) {
    val uiModel by viewModel.chartUIModel.collectAsStateWithLifecycle()
    val state by viewModel.chartUIState.collectAsStateWithLifecycle()

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
            header = chartHeader(uiModel, selectedPoint),
            period = state.period,
            onPeriodSelect = viewModel::setPeriod,
            periods = portfolioChartPeriods,
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

private fun List<PortfolioStatistic>.toAllTimeItems(): List<AllTimeUIModel> = mapNotNull { statistic ->
    when (statistic) {
        is PortfolioStatistic.AllTimeHigh ->
            AllTimeUIModel.High(statistic.content.date, statistic.content.value.toDouble(), statistic.content.percentage.toDouble())
        is PortfolioStatistic.AllTimeLow ->
            AllTimeUIModel.Low(statistic.content.date, statistic.content.value.toDouble(), statistic.content.percentage.toDouble())
        else -> null
    }
}
