package com.gemwallet.android.features.asset.viewmodels.chart.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.assets.coordinators.GetPortfolioData
import com.gemwallet.android.application.assets.coordinators.walletChartPeriods
import com.gemwallet.android.application.session.coordinators.GetCurrentCurrency
import com.gemwallet.android.data.repositories.perpetual.ObservePerpetualWallet
import com.gemwallet.android.features.asset.viewmodels.chart.models.ChartUIModel
import com.gemwallet.android.features.asset.viewmodels.chart.models.from
import com.gemwallet.android.ui.models.chart.ChartViewState
import com.wallet.core.primitives.ChartDateValue
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
class PortfolioChartViewModel internal constructor(
    getCurrentCurrency: GetCurrentCurrency,
    observePerpetualWallet: ObservePerpetualWallet,
    private val getPortfolioData: GetPortfolioData,
    initialType: PortfolioType,
) : ViewModel() {
    private val _selectedType = MutableStateFlow(initialType)
    val selectedType = _selectedType.asStateFlow()

    private val _selectedChartType = MutableStateFlow(PortfolioChartType.Pnl)
    val selectedChartType = _selectedChartType.asStateFlow()

    private val selectedPeriod = MutableStateFlow(ChartPeriod.All)
    private val refreshTrigger = MutableStateFlow(0L)
    private val refreshState = MutableStateFlow(false)

    val showSegmentedControl = observePerpetualWallet()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), false)

    val isRefreshing = refreshState.asStateFlow()

    private val portfolio = combine(
        selectedType,
        selectedPeriod,
        getCurrentCurrency.getCurrency().distinctUntilChanged(),
        refreshTrigger,
    ) { type, period, currency, _ -> Triple(type, period, currency) }
        .mapLatest { (type, period, currency) ->
            val data = try {
                getPortfolioData.getPortfolioData(type, period, currency)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
            refreshState.value = false
            val periods = data?.availablePeriods.orEmpty()
            if (data != null && periods.isNotEmpty() && !periods.contains(period)) {
                selectedPeriod.compareAndSet(period, periods.first())
                null
            } else {
                PortfolioFetch(type, period, data, type.displayCurrency(currency))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), null)

    val chartUIState = combine(selectedType, selectedPeriod, selectedChartType, portfolio) { type, period, chartType, fetch ->
        ChartUIModel.State(period = period, viewState = fetch.viewState(type, period, chartType))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ChartUIModel.State())

    val chartUIModel = combine(selectedType, selectedPeriod, selectedChartType, portfolio) { type, period, chartType, fetch ->
        if (fetch?.matches(type, period) != true || fetch.data == null) {
            ChartUIModel(period = period)
        } else {
            ChartUIModel.from(
                values = fetch.data.chartValues(chartType),
                period = period,
                currency = fetch.currency,
                showHeaderValue = type == PortfolioType.Wallet || chartType == PortfolioChartType.Value,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ChartUIModel())

    val statistics = portfolio
        .map { it?.data?.statistics.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), emptyList())

    val currency = portfolio
        .map { it?.currency ?: Currency.USD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), Currency.USD)

    val availablePeriods = portfolio
        .map { it?.data?.availablePeriods?.takeIf { periods -> periods.isNotEmpty() } ?: walletChartPeriods }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), walletChartPeriods)

    fun setType(type: PortfolioType) {
        _selectedType.value = type
    }

    fun setChartType(chartType: PortfolioChartType) {
        _selectedChartType.value = chartType
    }

    fun setPeriod(period: ChartPeriod) {
        selectedPeriod.value = period
    }

    fun refresh() {
        refreshState.value = true
        refreshTrigger.value = refreshTrigger.value + 1
    }

    @Inject
    constructor(
        getCurrentCurrency: GetCurrentCurrency,
        observePerpetualWallet: ObservePerpetualWallet,
        getPortfolioData: GetPortfolioData,
        savedStateHandle: SavedStateHandle,
    ) : this(
        getCurrentCurrency = getCurrentCurrency,
        observePerpetualWallet = observePerpetualWallet,
        getPortfolioData = getPortfolioData,
        initialType = savedStateHandle.portfolioType(),
    )
}

private class PortfolioFetch(
    val type: PortfolioType,
    val period: ChartPeriod,
    val data: PortfolioData?,
    val currency: Currency,
) {
    fun matches(type: PortfolioType, period: ChartPeriod): Boolean =
        this.type == type && this.period == period
}

private fun PortfolioFetch?.viewState(
    type: PortfolioType,
    period: ChartPeriod,
    chartType: PortfolioChartType,
): ChartViewState = when {
    this?.matches(type, period) != true -> ChartViewState.Loading
    data == null -> ChartViewState.Error
    else -> data.chartState(chartType)
}

private fun PortfolioData.chartValues(chartType: PortfolioChartType): List<ChartDateValue> =
    (charts.firstOrNull { it.chartType == chartType } ?: charts.firstOrNull())?.values.orEmpty()

private fun PortfolioData.chartState(chartType: PortfolioChartType): ChartViewState {
    val values = chartValues(chartType)
    val hasVariation = values.size >= MinChartPoints &&
        values.minOf { it.value } != values.maxOf { it.value }
    return if (hasVariation) ChartViewState.Ready else ChartViewState.Empty
}

private fun PortfolioType.displayCurrency(fetchCurrency: Currency): Currency =
    if (this == PortfolioType.Perpetuals) Currency.USD else fetchCurrency
