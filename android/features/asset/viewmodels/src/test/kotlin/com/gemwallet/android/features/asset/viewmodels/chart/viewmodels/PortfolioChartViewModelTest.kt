package com.gemwallet.android.features.asset.viewmodels.chart.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.assets.coordinators.GetPortfolioData
import com.gemwallet.android.application.session.coordinators.GetCurrentCurrency
import com.gemwallet.android.ui.models.chart.ChartViewState
import com.wallet.core.primitives.ChartDateValue
import com.wallet.core.primitives.ChartPeriod
import com.wallet.core.primitives.ChartValuePercentage
import com.wallet.core.primitives.Currency
import com.wallet.core.primitives.PortfolioChartData
import com.wallet.core.primitives.PortfolioChartType
import com.wallet.core.primitives.PortfolioData
import com.wallet.core.primitives.PortfolioStatistic
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioChartViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currencyFlow = MutableStateFlow(Currency.USD)
    private val viewModels = mutableListOf<ViewModel>()

    private val getCurrentCurrency = mockk<GetCurrentCurrency>(relaxed = true) {
        every { getCurrency() } returns currencyFlow
    }
    private val getPortfolioData = mockk<GetPortfolioData>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { viewModel ->
            val job = viewModel.viewModelScope.coroutineContext.job
            job.cancel()
            while (!job.isCompleted) {
                testDispatcher.scheduler.advanceUntilIdle()
            }
        }
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `renders chart when portfolio has values`() = runTest(testDispatcher) {
        coEvery { getPortfolioData.getPortfolioData(ChartPeriod.All, Currency.USD) } returns portfolioData(listOf(10f, 12f, 14f))

        val viewModel = createViewModel()
        val uiModel = viewModel.chartUIModel.first { it.chartPoints.size == 3 }

        assertEquals(3, uiModel.chartPoints.size)
        assertEquals(ChartViewState.Ready, viewModel.chartUIState.value.viewState)
    }

    @Test
    fun `initial request uses all period by default`() = runTest(testDispatcher) {
        coEvery { getPortfolioData.getPortfolioData(ChartPeriod.All, Currency.USD) } returns portfolioData(listOf(1f, 2f))

        val viewModel = createViewModel()
        viewModel.chartUIModel.first { it.chartPoints.size == 2 }

        assertEquals(ChartPeriod.All, viewModel.chartUIState.value.period)
        coVerify(exactly = 1) { getPortfolioData.getPortfolioData(ChartPeriod.All, Currency.USD) }
    }

    @Test
    fun `selecting period updates state and refetches`() = runTest(testDispatcher) {
        coEvery { getPortfolioData.getPortfolioData(any(), any()) } returns portfolioData(listOf(1f, 2f))
        coEvery { getPortfolioData.getPortfolioData(ChartPeriod.Month, Currency.USD) } returns portfolioData(listOf(1f, 2f, 3f))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.chartUIModel.collect {} }

        viewModel.setPeriod(ChartPeriod.Month)
        val model = viewModel.chartUIModel.first { it.chartPoints.size == 3 }

        assertEquals(ChartPeriod.Month, model.period)
        coVerify { getPortfolioData.getPortfolioData(ChartPeriod.Month, Currency.USD) }
    }

    @Test
    fun `exposes all time statistics from portfolio`() = runTest(testDispatcher) {
        val allTimeHigh = ChartValuePercentage(date = 1L, value = 99f, percentage = 5f)
        coEvery {
            getPortfolioData.getPortfolioData(ChartPeriod.All, Currency.USD)
        } returns portfolioData(listOf(1f, 2f), statistics = listOf(PortfolioStatistic.AllTimeHigh(allTimeHigh)))

        val viewModel = createViewModel()
        val statistics = viewModel.statistics.first { it.isNotEmpty() }

        assertEquals(listOf(PortfolioStatistic.AllTimeHigh(allTimeHigh)), statistics)
    }

    private fun portfolioData(
        values: List<Float>,
        statistics: List<PortfolioStatistic> = emptyList(),
    ) = PortfolioData(
        charts = listOf(
            PortfolioChartData(
                chartType = PortfolioChartType.Value,
                values = values.mapIndexed { index, value ->
                    ChartDateValue(date = TimeUnit.SECONDS.toMillis((index + 1).toLong()), value = value.toDouble())
                },
            ),
        ),
        statistics = statistics,
        availablePeriods = emptyList(),
    )

    private fun createViewModel() = PortfolioChartViewModel(
        getCurrentCurrency = getCurrentCurrency,
        getPortfolioData = getPortfolioData,
    ).also(viewModels::add)
}
