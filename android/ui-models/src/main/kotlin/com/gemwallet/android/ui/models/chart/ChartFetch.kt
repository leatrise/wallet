package com.gemwallet.android.ui.models.chart

const val MinChartPoints = 2

class ChartFetch<Request, Data>(
    val request: Request,
    val data: Data?,
) {
    fun matches(request: Request): Boolean = this.request == request
}

fun <Request, Data> ChartFetch<Request, Data>?.viewState(
    request: Request,
    dataState: (Data) -> ChartViewState,
): ChartViewState = when {
    this?.matches(request) != true -> ChartViewState.Loading
    data == null -> ChartViewState.Error
    else -> dataState(data)
}
