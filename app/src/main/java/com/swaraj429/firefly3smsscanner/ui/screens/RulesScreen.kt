package com.swaraj429.firefly3smsscanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.ParsingRule
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.parser.RuleEngine
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.RulesViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.VendorDisplayItem
import com.swaraj429.firefly3smsscanner.viewmodel.VendorsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Rule Engine UI:
 *   - Tab 1: Smart Rules (create/edit IF/THEN rules with description templates & auto-send)
 *   - Tab 2: Detected Vendors (directory of unique merchants detected from SMS with quick rule creation)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    rulesViewModel: RulesViewModel,
    fireflyDataViewModel: FireflyDataViewModel,
    vendorsViewModel: VendorsViewModel? = null
) {
    val rules = rulesViewModel.rules
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ParsingRule?>(null) }

    // Refresh vendors when switching to the tab
    LaunchedEffect(selectedTab, rules.size) {
        if (selectedTab == 1 && vendorsViewModel != null) {
            vendorsViewModel.loadVendors(rules = rulesViewModel.rules)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Top Tabs: Rules vs Detected Vendors ───
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rules (${rules.size})", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storefront, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Detected Vendors (${vendorsViewModel?.totalCount ?: 0})",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }

        if (selectedTab == 0) {
            // ─── TAB 0: SMART RULES ───
            RulesTabContent(
                rules = rules,
                onAddRule = {
                    editingRule = ParsingRule()
                    showEditor = true
                },
                onEditRule = { rule ->
                    editingRule = rule
                    showEditor = true
                },
                onDeleteRule = { ruleId -> rulesViewModel.deleteRule(ruleId) },
                onToggleRule = { ruleId, enabled -> rulesViewModel.toggleRule(ruleId, enabled) }
            )
        } else {
            // ─── TAB 1: DETECTED VENDORS ───
            if (vendorsViewModel != null) {
                VendorsTabContent(
                    vendorsViewModel = vendorsViewModel,
                    onCreateRule = { vendorName ->
                        val existing = rulesViewModel.getRuleForVendor(vendorName)
                        if (existing != null) {
                            editingRule = existing
                        } else {
                            editingRule = ParsingRule(
                                keyword = vendorName,
                                descriptionTemplate = "{vendor} spent {amount} for groceries",
                                autoSendToFirefly = false
                            )
                        }
                        showEditor = true
                    }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
        }
    }

    // ─── Rule Editor Dialog ───
    if (showEditor && editingRule != null) {
        RuleEditorDialog(
            rule = editingRule!!,
            fireflyData = fireflyDataViewModel,
            onSave = { savedRule ->
                if (rules.any { it.id == savedRule.id }) {
                    rulesViewModel.updateRule(savedRule)
                } else {
                    rulesViewModel.addRule(savedRule)
                }
                vendorsViewModel?.loadVendors(rules = rulesViewModel.rules)
                showEditor = false
                editingRule = null
            },
            onDismiss = {
                showEditor = false
                editingRule = null
            }
        )
    }
}

// ─── Rules Tab Content ───────────────────────────────────────────────────────

@Composable
private fun RulesTabContent(
    rules: List<ParsingRule>,
    onAddRule: () -> Unit,
    onEditRule: (ParsingRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRule: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Header ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Automation Rules",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${rules.count { it.isEnabled }} of ${rules.size} rules active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onAddRule,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Rule", fontWeight = FontWeight.SemiBold)
            }
        }

        // ─── Info card ───
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), Primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Rules auto-fill category, destination account, description templates with variables, & can auto-send directly to Firefly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Rules List ───
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Rule, null,
                        Modifier.size(64.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No rules yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add rules or create them from Detected Vendors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { onEditRule(rule) },
                        onDelete = { onDeleteRule(rule.id) },
                        onToggle = { enabled -> onToggleRule(rule.id, enabled) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: ParsingRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row: IF keyword + Auto-send badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "IF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SMS contains ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "\"${rule.keyword}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (rule.autoSendToFirefly) {
                    Surface(
                        color = Primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Bolt, null, Modifier.size(14.dp), Primary)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Auto-Send",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // THEN row
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = SuccessGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "THEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (rule.descriptionTemplate.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EditNote, null, Modifier.size(15.dp), Primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "\"${rule.descriptionTemplate}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Primary
                            )
                        }
                    }
                    if (rule.categoryName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Category, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.categoryName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rule.destinationAccountName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalance, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.destinationAccountName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rule.tags.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalOffer, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Actions row
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp), ErrorCrimson)
                }
            }
        }
    }
}

// ─── Vendors Tab Content ─────────────────────────────────────────────────────

@Composable
private fun VendorsTabContent(
    vendorsViewModel: VendorsViewModel,
    onCreateRule: (String) -> Unit
) {
    val vendors = vendorsViewModel.filteredVendors

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = vendorsViewModel.searchQuery,
            onValueChange = { vendorsViewModel.searchQuery = it },
            placeholder = { Text("Search detected merchants & payees...") },
            leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
            trailingIcon = {
                if (vendorsViewModel.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vendorsViewModel.searchQuery = "" }) {
                        Icon(Icons.Filled.Close, "Clear", Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Summary metrics row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${vendorsViewModel.totalCount} Unique Merchants Detected",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${vendorsViewModel.mappedCount} active rules · ${vendorsViewModel.unmappedCount} unmapped",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (vendors.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Storefront, null,
                        Modifier.size(64.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (vendorsViewModel.searchQuery.isNotBlank()) "No matching merchants" else "No merchants detected yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Scan SMS on the Transactions tab to auto-extract merchants",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vendors, key = { it.name }) { vendor ->
                    VendorCard(
                        vendor = vendor,
                        onAction = { onCreateRule(vendor.name) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun VendorCard(
    vendor: VendorDisplayItem,
    onAction: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val lastSeenDate = dateFormat.format(Date(vendor.lastSeen))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Storefront icon pill
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (vendor.hasActiveRule) SuccessGreen.copy(alpha = 0.12f)
                            else Primary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Storefront, null,
                        tint = if (vendor.hasActiveRule) SuccessGreen else Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        vendor.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "${vendor.transactionCount} txn${if (vendor.transactionCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (vendor.lastAmount != null && vendor.lastAmount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Last: ₹${"%.2f".format(vendor.lastAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action: Edit Rule (if mapped) or Create Rule (if unmapped)
            if (vendor.hasActiveRule) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = SuccessGreen)
                    Spacer(Modifier.width(4.dp))
                    Text("Rule Active", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create Rule", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Rule Editor Dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorDialog(
    rule: ParsingRule,
    fireflyData: FireflyDataViewModel,
    onSave: (ParsingRule) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf(rule.keyword) }
    var descriptionTemplate by remember { mutableStateOf(rule.descriptionTemplate) }
    var autoSendToFirefly by remember { mutableStateOf(rule.autoSendToFirefly) }
    var selectedCategory by remember { mutableStateOf(rule.categoryName) }
    var selectedDestId by remember { mutableStateOf(rule.destinationAccountId) }
    var selectedDestName by remember { mutableStateOf(rule.destinationAccountName) }
    var selectedTags by remember { mutableStateOf(rule.tags.toList()) }

    // Dropdown expanded state
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    // Live preview calculation
    val sampleTxn = remember(keyword) {
        ParsedTransaction(
            amount = 450.0,
            type = TransactionType.WITHDRAWAL,
            rawMessage = "Spent INR 450.00 at ${keyword.ifBlank { "Mayur Super Market" }} on Card XX1234",
            vendor = keyword.ifBlank { "Mayur Super Market" },
            destinationAccountName = selectedDestName.ifBlank { null }
        )
    }
    val sampleRule = remember(keyword, selectedCategory, selectedDestName) {
        rule.copy(
            keyword = keyword,
            categoryName = selectedCategory,
            destinationAccountName = selectedDestName
        )
    }

    val livePreview = remember(descriptionTemplate, keyword, selectedCategory, selectedDestName) {
        if (descriptionTemplate.isNotBlank()) {
            RuleEngine.interpolateTemplate(descriptionTemplate, sampleTxn, sampleRule)
        } else {
            ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.keyword.isBlank()) "New Rule" else "Edit Rule", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Keyword ──
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("IF SMS contains") },
                    placeholder = { Text("e.g., SWIGGY or MAYUR") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    "THEN assign & configure:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )

                // ── Description Template Field ──
                OutlinedTextField(
                    value = descriptionTemplate,
                    onValueChange = { descriptionTemplate = it },
                    label = { Text("Description Template (Optional)") },
                    placeholder = { Text("e.g., {vendor} spent {amount} for groceries") },
                    leadingIcon = { Icon(Icons.Filled.EditNote, null, Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick insertion variable chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val variables = listOf("{vendor}", "{amount}", "{account}", "{category}", "{date}")
                    variables.forEach { variable ->
                        SuggestionChip(
                            onClick = {
                                descriptionTemplate = if (descriptionTemplate.isBlank()) {
                                    variable
                                } else {
                                    "$descriptionTemplate $variable"
                                }
                            },
                            label = { Text(variable, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Dynamic Live Preview
                if (livePreview.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Visibility, null, Modifier.size(16.dp), Primary)
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("Preview:", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                                Text(livePreview, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // ── Auto-Send Switch ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (autoSendToFirefly) Primary.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Bolt, null,
                                tint = if (autoSendToFirefly) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Auto-send to Firefly", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Instantly sends to Firefly in background without asking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = autoSendToFirefly,
                            onCheckedChange = { autoSendToFirefly = it }
                        )
                    }
                }

                // ── Category dropdown ──
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = { selectedCategory = it },
                        readOnly = fireflyData.categories.isNotEmpty(),
                        label = { Text("Category") },
                        leadingIcon = { Icon(Icons.Filled.Category, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (fireflyData.categories.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    if (fireflyData.categories.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { selectedCategory = ""; categoryExpanded = false }
                            )
                            fireflyData.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            cat.name,
                                            fontWeight = if (cat.name == selectedCategory) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { selectedCategory = cat.name; categoryExpanded = false },
                                    trailingIcon = {
                                        if (cat.name == selectedCategory) {
                                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), Primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Destination Account dropdown ──
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDestName,
                        onValueChange = { selectedDestName = it },
                        readOnly = fireflyData.expenseAccounts.isNotEmpty(),
                        label = { Text("Destination Account") },
                        leadingIcon = { Icon(Icons.Filled.AccountBalance, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (fireflyData.expenseAccounts.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    if (fireflyData.expenseAccounts.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { selectedDestId = null; selectedDestName = ""; accountExpanded = false }
                            )
                            fireflyData.expenseAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            acc.name,
                                            fontWeight = if (acc.id == selectedDestId) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedDestId = acc.id
                                        selectedDestName = acc.name
                                        accountExpanded = false
                                    },
                                    trailingIcon = {
                                        if (acc.id == selectedDestId) {
                                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), Primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Tags ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTagPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalOffer, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tags", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (selectedTags.isEmpty()) "Select tags" else selectedTags.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTags.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        rule.copy(
                            keyword = keyword.trim(),
                            descriptionTemplate = descriptionTemplate.trim(),
                            autoSendToFirefly = autoSendToFirefly,
                            categoryName = selectedCategory.trim(),
                            destinationAccountId = selectedDestId,
                            destinationAccountName = selectedDestName.trim(),
                            tags = selectedTags
                        )
                    )
                },
                enabled = keyword.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    // Tag picker sub-dialog
    if (showTagPicker) {
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text("Select Tags") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (fireflyData.tags.isEmpty()) {
                        Text("No tags synced. Sync Firefly data first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        fireflyData.tags.forEach { tag ->
                            val checked = tag.name in selectedTags
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTags = if (checked) selectedTags - tag.name else selectedTags + tag.name
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedTags = if (it) selectedTags + tag.name else selectedTags - tag.name
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTagPicker = false }) { Text("Done") } }
        )
    }
}
