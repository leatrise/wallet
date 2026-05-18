package com.gemwallet.android.features.activities.presents.details.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gemwallet.android.domains.asset.chain
import com.gemwallet.android.domains.transaction.values.TransactionDetailsValue
import com.gemwallet.android.ext.asset
import com.gemwallet.android.model.ValueFormatter
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.list_item.ListItemDefaults
import com.gemwallet.android.ui.components.list_item.listItem
import com.gemwallet.android.ui.components.progress.CircularProgressIndicator16
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.theme.alpha10
import com.gemwallet.android.ui.theme.compactIconSize
import com.gemwallet.android.ui.theme.iconSize
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.pendingColor
import com.gemwallet.android.ui.theme.space2
import com.gemwallet.android.ui.theme.space6
import com.gemwallet.android.ui.theme.space8
import com.gemwallet.android.ui.theme.space24
import com.wallet.core.primitives.TransactionState

@Composable
internal fun SwapProgressItem(progress: TransactionDetailsValue.SwapProgress) {
    val chainName = progress.fromAsset.chain.asset().name
    val transferValue = ValueFormatter(style = ValueFormatter.Style.Auto)
        .string(progress.fromValue.toBigInteger(), progress.fromAsset)
    val swapStatus = progress.state.swapProgressStatus()

    Row(
        modifier = Modifier
            .listItem(ListPosition.Single)
            .fillMaxWidth()
            .padding(horizontal = ListItemDefaults.contentSpacing, vertical = paddingDefault),
        horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.contentSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        Timeline(
            transferStatus = SwapProgressStatus.Completed,
            swapStatus = swapStatus,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(space6),
        ) {
            ProgressStep(
                title = stringResource(R.string.transfer_title),
                subtitle = "$transferValue ($chainName)",
                status = SwapProgressStatus.Completed,
            )
            ProgressStep(
                title = stringResource(R.string.wallet_swap),
                subtitle = progress.providerName,
                status = swapStatus,
            )
        }
    }
}

@Composable
private fun ProgressStep(
    title: String,
    subtitle: String,
    status: SwapProgressStatus,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space6),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            StatusTag(status = status)
        }
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Timeline(
    transferStatus: SwapProgressStatus,
    swapStatus: SwapProgressStatus,
) {
    Column(
        modifier = Modifier.width(iconSize),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressMarker(transferStatus)
        Connector(color = transferStatus.connectorColor())
        ProgressMarker(swapStatus)
    }
}

@Composable
private fun Connector(color: Color) {
    Box(
        modifier = Modifier
            .width(DividerDefaults.Thickness)
            .height(space24)
            .background(color),
    )
}

@Composable
private fun ProgressMarker(status: SwapProgressStatus) {
    val color = status.color()
    val markerModifier = Modifier
        .size(iconSize)
        .then(if (status.isFilled) Modifier.background(color.copy(alpha = alpha10), CircleShape) else Modifier)
        .border(DividerDefaults.Thickness, color, CircleShape)

    Box(
        modifier = markerModifier,
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            SwapProgressStatus.Completed -> Icon(
                modifier = Modifier.size(compactIconSize),
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = color,
            )
            SwapProgressStatus.Pending -> CircularProgressIndicator16(color = color)
            SwapProgressStatus.Failed -> Icon(
                modifier = Modifier.size(compactIconSize),
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = color,
            )
        }
    }
}

@Composable
private fun StatusTag(status: SwapProgressStatus) {
    val color = status.color()
    Text(
        modifier = Modifier
            .background(color = color.copy(alpha = alpha10), shape = RoundedCornerShape(space6))
            .padding(horizontal = space8, vertical = space2),
        text = stringResource(status.labelRes()),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
    )
}

private enum class SwapProgressStatus {
    Completed,
    Pending,
    Failed,
}

private fun TransactionState.swapProgressStatus(): SwapProgressStatus {
    return when (this) {
        TransactionState.Confirmed -> SwapProgressStatus.Completed
        TransactionState.InTransit,
        TransactionState.Pending -> SwapProgressStatus.Pending
        TransactionState.Failed,
        TransactionState.Reverted -> SwapProgressStatus.Failed
    }
}

@Composable
private fun SwapProgressStatus.color(): Color {
    return when (this) {
        SwapProgressStatus.Completed -> MaterialTheme.colorScheme.tertiary
        SwapProgressStatus.Pending -> pendingColor
        SwapProgressStatus.Failed -> MaterialTheme.colorScheme.error
    }
}

private val SwapProgressStatus.isFilled: Boolean
    get() = this != SwapProgressStatus.Pending

@StringRes
private fun SwapProgressStatus.labelRes(): Int {
    return when (this) {
        SwapProgressStatus.Completed -> R.string.transaction_status_completed
        SwapProgressStatus.Pending -> R.string.transaction_status_pending
        SwapProgressStatus.Failed -> R.string.transaction_status_failed
    }
}

@Composable
private fun SwapProgressStatus.connectorColor(): Color {
    return when (this) {
        SwapProgressStatus.Completed -> MaterialTheme.colorScheme.tertiary
        SwapProgressStatus.Pending,
        SwapProgressStatus.Failed -> MaterialTheme.colorScheme.outlineVariant
    }
}
