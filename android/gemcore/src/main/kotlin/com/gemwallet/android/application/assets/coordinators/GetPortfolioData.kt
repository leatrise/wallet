package com.gemwallet.android.application.assets.coordinators

import com.wallet.core.primitives.ChartPeriod
import com.wallet.core.primitives.Currency
import com.wallet.core.primitives.PortfolioData
import com.wallet.core.primitives.PortfolioType

interface GetPortfolioData {
    suspend fun getPortfolioData(
        type: PortfolioType,
        period: ChartPeriod,
        currency: Currency,
    ): PortfolioData
}
