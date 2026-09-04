package com.swaraj429.firefly3smsscanner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.SendStatus
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Compact transaction card for the timeline view.
 * Shows amount, merchant, account, category, and status in a sleek horizontal layout.
 */
@Composable
fun TransactionCard(
    transaction: ParsedTransaction,
    onClick: () -> Unit,
    onRestore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "card_scale"
    )

    val isExpense = transaction.isExpense
    val isTransfer = transaction.effectiveType == TransactionType.TRANSFER
    val isDismissed = transaction.status == SendStatus.DISMISSED
    val amountColor by animateColorAsState(
        targetValue = when {
            isDismissed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            isTransfer -> Primary
            isExpense -> DebitRed
            else -> CreditGreen
        },
        animationSpec = tween(200),
        label = "amount_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDismissed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDismissed) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isDismissed -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            isTransfer -> Primary.copy(alpha = 0.15f)
                            isExpense -> DebitRedContainer
                            else -> CreditGreenContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isDismissed -> Icons.Outlined.Block
                        isTransfer -> Icons.Outlined.SwapHoriz
                        isExpense -> Icons.Outlined.ArrowUpward
                        else -> Icons.Outlined.ArrowDownward
                    },
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Merchant + metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description.ifBlank {
                        transaction.rawMessage.take(30)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isDismissed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Account badge
                    val accountName = if (isExpense) {
                        transaction.sourceAccountName
                    } else {
                        transaction.destinationAccountName
                    }
                    if (accountName != null) {
                        Text(
                            text = accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    // Payment mode badge (UPI / Card / ATM / NetBanking)
                    val mode = transaction.paymentMode
                    if (mode != null) {
                        if (accountName != null) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = mode,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    if (transaction.categoryName != null) {
                        if (accountName != null || mode != null) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = transaction.categoryName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Amount + status / restore
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${when { isTransfer -> "↔"; isExpense -> "-"; else -> "+" }}${formatCurrency(transaction.effectiveAmount)}",
                    style = AmountSmallStyle,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        status = transaction.status,
                        compact = true,
                        customLabel = if (isDismissed) {
                            transaction.dismissReason?.badgeLabel ?: "Dismissed"
                        } else null
                    )
                    if (isDismissed && onRestore != null) {
                        Spacer(Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = onRestore,
                            modifier = Modifier.size(24.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = "Restore",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Status badge for transaction states
 */
@Composable
fun StatusBadge(
    status: SendStatus,
    compact: Boolean = false,
    customLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, defaultText, icon) = when (status) {
        SendStatus.PENDING -> StatusInfo(
            WarningAmber.copy(alpha = 0.15f),
            WarningAmber,
            "Pending",
            Icons.Filled.Schedule
        )
        SendStatus.SENDING -> StatusInfo(
            Primary.copy(alpha = 0.15f),
            Primary,
            "Sending",
            Icons.Filled.Sync
        )
        SendStatus.SENT -> StatusInfo(
            SuccessGreen.copy(alpha = 0.15f),
            SuccessGreen,
            "Sent",
            Icons.Filled.CheckCircle
        )
        SendStatus.FAILED -> StatusInfo(
            ErrorCrimson.copy(alpha = 0.15f),
            ErrorCrimson,
            "Failed",
            Icons.Filled.Error
        )
        SendStatus.DISMISSED -> StatusInfo(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Dismissed",
            Icons.Filled.Block
        )
    }

    val text = customLabel ?: defaultText

    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 2.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 10.dp else 14.dp),
                tint = textColor
            )
            if (!compact) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
        }
    }
}

private data class StatusInfo(
    val bgColor: Color,
    val textColor: Color,
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * Format currency in Indian format
 */
fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getInstance(Locale("en", "IN"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return "₹${formatter.format(amount)}"
}

/**
 * Date section header for timeline grouping
 */
@Composable
fun DateSectionHeader(
    dateLabel: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = dateLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

/**
 * Groups transactions by date for timeline display
 */
fun groupTransactionsByDate(transactions: List<ParsedTransaction>): Map<String, List<ParsedTransaction>> {
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    return transactions
        .sortedByDescending { it.timestamp }
        .groupBy { txn ->
            val cal = Calendar.getInstance().apply { timeInMillis = txn.timestamp }
            when (dayFormat.format(cal.time)) {
                dayFormat.format(today.time) -> "Today"
                dayFormat.format(yesterday.time) -> "Yesterday"
                else -> dateFormat.format(Date(txn.timestamp))
            }
        }
}
