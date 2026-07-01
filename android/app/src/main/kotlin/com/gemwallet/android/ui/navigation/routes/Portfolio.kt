package com.gemwallet.android.ui.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.gemwallet.android.features.asset.presents.chart.PortfolioChartScene
import kotlinx.serialization.Serializable

@Serializable
data object PortfolioChartRoute : NavKey

fun EntryProviderScope<NavKey>.portfolioChartScreen(
    onCancel: () -> Unit,
) {
    entry<PortfolioChartRoute> {
        PortfolioChartScene(onCancel = onCancel)
    }
}
