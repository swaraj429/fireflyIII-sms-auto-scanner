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
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "card_scale"
    )

    val isDebit = transaction.effectiveType == TransactionType.DEBIT
    val amountColor by animateColorAsState(
        targetValue = if (isDebit) DebitRed else CreditGreen,
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        if (isDebit) DebitRedContainer
                        else CreditGreenContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDebit) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    tint = if (isDebit) DebitRed else CreditGreen,
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Account badge
                    val accountName = if (isDebit) {
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
                    if (transaction.categoryName != null) {
                        if (accountName != null) {
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

            // Amount + status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isDebit) "-" else "+"}${formatCurrency(transaction.effectiveAmount)}",
                    style = AmountSmallStyle,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Spacer(Modifier.height(2.dp))
                StatusBadge(status = transaction.status, compact = true)
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
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, text, icon) = when (status) {
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
    }

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
