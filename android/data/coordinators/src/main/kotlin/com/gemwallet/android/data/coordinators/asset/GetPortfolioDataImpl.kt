package com.gemwallet.android.data.coordinators.asset

import com.gemwallet.android.application.assets.coordinators.GetPortfolioData
import com.gemwallet.android.data.repositories.assets.AssetsRepository
import com.gemwallet.android.data.services.gemapi.GemDeviceApiClient
import com.gemwallet.android.model.getTotalAmount
import com.wallet.core.primitives.ChartDateValue
import com.wallet.core.primitives.ChartPeriod
import com.wallet.core.primitives.Currency
import com.wallet.core.primitives.PortfolioAsset
import com.wallet.core.primitives.PortfolioAssetsRequest
import com.wallet.core.primitives.PortfolioChartData
import com.wallet.core.primitives.PortfolioChartType
import com.wallet.core.primitives.PortfolioData
import com.wallet.core.primitives.PortfolioStatistic
import kotlinx.coroutines.flow.firstOrNull
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class GetPortfolioDataImpl(
    private val gemDeviceApiClient: GemDeviceApiClient,
    private val assetsRepository: AssetsRepository,
) : GetPortfolioData {

    override suspend fun getPortfolioData(
        period: ChartPeriod,
        currency: Currency,
    ): PortfolioData {
        val assets = assetsRepository.getAssetsInfo().firstOrNull().orEmpty()
            .mapNotNull { assetInfo ->
                val total = assetInfo.balance.balance.getTotalAmount()
                if (total <= BigInteger.ZERO) return@mapNotNull null
                PortfolioAsset(assetId = assetInfo.asset.id, value = total.toString())
            }
        val portfolio = gemDeviceApiClient.getPortfolioAssets(
            period = period.string,
            request = PortfolioAssetsRequest(assets = assets),
        )
        val rate = assetsRepository.getCurrencyRate(currency).firstOrNull()?.rate
            ?: return PortfolioData(charts = emptyList(), statistics = emptyList(), availablePeriods = emptyList())

        val values = portfolio.values
            .sortedBy { it.timestamp }
            .map { ChartDateValue(date = TimeUnit.SECONDS.toMillis(it.timestamp.toLong()), value = it.value * rate) }
        val statistics = listOfNotNull(
            portfolio.allTimeHigh?.let { PortfolioStatistic.AllTimeHigh(it) },
            portfolio.allTimeLow?.let { PortfolioStatistic.AllTimeLow(it) },
        )
        return PortfolioData(
            charts = listOf(PortfolioChartData(chartType = PortfolioChartType.Value, values = values)),
            statistics = statistics,
            availablePeriods = emptyList(),
        )
    }
}
