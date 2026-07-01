package com.gemwallet.android.application.assets.coordinators

import com.wallet.core.primitives.ChartPeriod
import com.wallet.core.primitives.Currency
import com.wallet.core.primitives.PortfolioData

interface GetPortfolioData {
    suspend fun getPortfolioData(
        period: ChartPeriod,
        currency: Currency,
    ): PortfolioData
}
