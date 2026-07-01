package com.gemwallet.android.features.asset.viewmodels.chart.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.assets.coordinators.GetPortfolioData
import com.gemwallet.android.application.session.coordinators.GetCurrentCurrency
import com.gemwallet.android.features.asset.viewmodels.chart.models.ChartUIModel
import com.gemwallet.android.features.asset.viewmodels.chart.models.from
import com.gemwallet.android.ui.models.chart.ChartViewState
import com.wallet.core.primitives.ChartPeriod
import com.wallet.core.primitives.Currency
import com.wallet.core.primitives.PortfolioChartType
import com.wallet.core.primitives.PortfolioData
import com.wallet.core.primitives.PortfolioType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val MinChartPoints = 2
private const val StopTimeoutMillis = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PortfolioChartViewModel @Inject constructor(
    getCurrentCurrency: GetCurrentCurrency,
    private val getPortfolioData: GetPortfolioData,
) : ViewModel() {
    private val selectedPeriod = MutableStateFlow(ChartPeriod.All)
    private val viewState = MutableStateFlow<ChartViewState>(ChartViewState.Loading)
    private val refreshTrigger = MutableStateFlow(0L)
    private val refreshState = MutableStateFlow(false)

    val chartUIState = combine(selectedPeriod, viewState) { period, viewState ->
        ChartUIModel.State(period = period, viewState = viewState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChartUIModel.State())

    val isRefreshing = refreshState.asStateFlow()

    private val portfolio = combine(
        selectedPeriod,
        getCurrentCurrency.getCurrency().distinctUntilChanged(),
        refreshTrigger,
    ) { period, currency, _ -> period to currency }
        .mapLatest { (period, currency) ->
            try {
                val data = getPortfolioData.getPortfolioData(PortfolioType.Wallet, period, currency)
                viewState.value = if (data.chartValues().size < MinChartPoints) ChartViewState.Empty else ChartViewState.Ready
                data to currency
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                viewState.value = ChartViewState.Error
                null
            } finally {
                refreshState.value = false
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), null)

    val chartUIModel = combine(selectedPeriod, portfolio) { period, data ->
        val (portfolioData, currency) = data ?: return@combine ChartUIModel(period = period)
        ChartUIModel.from(portfolioData.chartValues(), period, currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ChartUIModel())

    val statistics = portfolio
        .map { it?.first?.statistics.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), emptyList())

    val currency = portfolio
        .map { it?.second ?: Currency.USD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), Currency.USD)

    fun setPeriod(period: ChartPeriod) {
        if (period == selectedPeriod.value) {
            return
        }
        selectedPeriod.value = period
        viewState.value = ChartViewState.Loading
    }

    fun refresh() {
        refreshState.value = true
        viewState.value = ChartViewState.Loading
        refreshTrigger.value = refreshTrigger.value + 1
    }
}

private fun PortfolioData.chartValues() =
    charts.firstOrNull { it.chartType == PortfolioChartType.Value }?.values.orEmpty()
