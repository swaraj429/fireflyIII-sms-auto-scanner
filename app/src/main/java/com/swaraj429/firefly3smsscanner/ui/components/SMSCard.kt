package com.swaraj429.firefly3smsscanner.ui.components

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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.SmsMessage
import com.swaraj429.firefly3smsscanner.model.TransactionType

import com.swaraj429.firefly3smsscanner.ui.theme.*

/**
 * Smart SMS card that highlights the parsed amount, merchant, and account.
 * Tapping opens the Transaction Editor bottom sheet.
 */
@Composable
fun SMSCard(
    sms: SmsMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "sms_card_scale"
    )

    // Quick-parse to extract info for display
    val parsedInfo = remember(sms) {
        SmsCardInfo.extract(sms)
    }

    val isDebit = parsedInfo.type == TransactionType.DEBIT
    val amountColor = if (isDebit) DebitRed else CreditGreen

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
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top row: sender + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Bank icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = sms.sender,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = sms.dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // Amount row
            if (parsedInfo.amount != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatCurrency(parsedInfo.amount),
                        style = AmountMediumStyle,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    // Type chip
                    Surface(
                        color = if (isDebit) DebitRedContainer else CreditGreenContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isDebit) "DEBIT" else "CREDIT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = amountColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            // Merchant / Description preview
            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Account + action row
            if (parsedInfo.accountFragment != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "A/C ••${parsedInfo.accountFragment}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Create button
                    Surface(
                        color = Primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        onClick = onClick
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Create",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Primary
                            )
                            Icon(
                                imageVector = Icons.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracted info from SMS for card display (lightweight, no full parsing)
 */
data class SmsCardInfo(
    val amount: Double?,
    val type: TransactionType,
    val accountFragment: String?,
    val merchant: String?
) {
    companion object {
        private val amountPattern = Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
        private val accountPattern = Regex("""[Xx\*]+(\d{3,6})\b""")

        fun extract(sms: SmsMessage): SmsCardInfo {
            val body = sms.body
            val amountMatch = amountPattern.find(body)
            val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

            val lowerBody = body.lowercase()
            val type = when {
                listOf("debited", "deducted", "withdrawn", "sent", "paid", "spent", "purchase").any { lowerBody.contains(it) } ->
                    TransactionType.DEBIT
                listOf("credited", "received", "deposited", "refund", "cashback").any { lowerBody.contains(it) } ->
                    TransactionType.CREDIT
                else -> TransactionType.UNKNOWN
            }

            val accountMatch = accountPattern.find(body)
            val accountFragment = accountMatch?.groupValues?.get(1)

            return SmsCardInfo(amount, type, accountFragment, null)
        }
    }
}
