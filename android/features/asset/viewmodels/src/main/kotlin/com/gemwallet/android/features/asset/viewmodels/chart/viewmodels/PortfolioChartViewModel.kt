package com.gemwallet.android.features.asset.viewmodels.chart.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.assets.coordinators.GetPortfolioData
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

private val defaultPeriods = listOf(
    ChartPeriod.Day,
    ChartPeriod.Week,
    ChartPeriod.Month,
    ChartPeriod.Year,
    ChartPeriod.All,
)

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
    private val viewState = MutableStateFlow<ChartViewState>(ChartViewState.Loading)
    private val refreshTrigger = MutableStateFlow(0L)
    private val refreshState = MutableStateFlow(false)

    val showSegmentedControl = observePerpetualWallet()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showChartTypePicker = selectedType
        .map { it == PortfolioType.Perpetuals }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialType == PortfolioType.Perpetuals)

    val showHeaderValue = combine(selectedType, selectedChartType) { type, chartType ->
        type == PortfolioType.Wallet || chartType == PortfolioChartType.Value
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialType == PortfolioType.Wallet)

    val chartUIState = combine(selectedPeriod, viewState) { period, viewState ->
        ChartUIModel.State(period = period, viewState = viewState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChartUIModel.State())

    val isRefreshing = refreshState.asStateFlow()

    private val portfolio = combine(
        selectedType,
        selectedPeriod,
        getCurrentCurrency.getCurrency().distinctUntilChanged(),
        refreshTrigger,
    ) { type, period, currency, _ -> Triple(type, period, currency) }
        .mapLatest { (type, period, currency) ->
            try {
                val data = getPortfolioData.getPortfolioData(type, period, currency)
                val periods = data.availablePeriods
                if (periods.isNotEmpty() && !periods.contains(period)) {
                    selectedPeriod.value = periods.first()
                    null
                } else {
                    viewState.value = data.chartState(_selectedChartType.value)
                    data to type.displayCurrency(currency)
                }
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

    val chartUIModel = combine(selectedPeriod, selectedChartType, portfolio) { period, chartType, data ->
        val (portfolioData, currency) = data ?: return@combine ChartUIModel(period = period)
        ChartUIModel.from(portfolioData.chartValues(chartType), period, currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ChartUIModel())

    val statistics = portfolio
        .map { it?.first?.statistics.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), emptyList())

    val currency = portfolio
        .map { it?.second ?: Currency.USD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), Currency.USD)

    val availablePeriods = portfolio
        .map { it?.first?.availablePeriods?.takeIf { periods -> periods.isNotEmpty() } ?: defaultPeriods }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), defaultPeriods)

    fun setType(type: PortfolioType) {
        if (type == _selectedType.value) {
            return
        }
        _selectedType.value = type
        viewState.value = ChartViewState.Loading
    }

    fun setChartType(chartType: PortfolioChartType) {
        if (chartType == _selectedChartType.value) {
            return
        }
        _selectedChartType.value = chartType
        portfolio.value?.first?.let { viewState.value = it.chartState(chartType) }
    }

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

private fun PortfolioData.chartValues(chartType: PortfolioChartType): List<ChartDateValue> =
    (charts.firstOrNull { it.chartType == chartType } ?: charts.firstOrNull())?.values.orEmpty()

private fun PortfolioData.chartState(chartType: PortfolioChartType): ChartViewState {
    val values = chartValues(chartType).map { it.value }
    val hasVariation = (values.minOrNull() ?: 0.0) != (values.maxOrNull() ?: 0.0)
    return if (values.size < MinChartPoints || !hasVariation) ChartViewState.Empty else ChartViewState.Ready
}

private fun PortfolioType.displayCurrency(fetchCurrency: Currency): Currency =
    if (this == PortfolioType.Perpetuals) Currency.USD else fetchCurrency
