package com.budgetmanager.presentation.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.AccountType
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editingAccount: Account? = null,
    val showDeleteConfirm: Long? = null,
    val showArchived: Boolean = false
)

class AccountsScreenState {
    var uiState by mutableStateOf(AccountsUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val accountRepo by lazy { getKoin().get<com.budgetmanager.data.repository.AccountRepository>() }

    init { loadData() }

    private fun loadData() {
        scope.launch {
            accountRepo.getAllAccounts().collectLatest { accounts ->
                // Total balance only counts active accounts
                val total = accounts.filter { it.isActive }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.balance) }
                uiState = uiState.copy(accounts = accounts, totalBalance = total, isLoading = false)
            }
        }
    }

    fun toggleShowArchived() {
        uiState = uiState.copy(showArchived = !uiState.showArchived)
    }

    fun restoreAccount(id: Long) {
        val account = uiState.accounts.find { it.id == id } ?: return
        scope.launch {
            accountRepo.restoreAccount(id)
            UndoBus.show(UndoableAction(
                message = "Compte \"${account.name}\" restaure",
                onUndo = { accountRepo.deleteAccount(id) }
            ))
        }
    }

    fun showAddDialog() { uiState = uiState.copy(showAddDialog = true, editingAccount = null) }
    fun showEditDialog(account: Account) { uiState = uiState.copy(editingAccount = account, showAddDialog = true) }
    fun hideDialog() { uiState = uiState.copy(showAddDialog = false, editingAccount = null) }
    fun showDeleteConfirm(id: Long) { uiState = uiState.copy(showDeleteConfirm = id) }
    fun hideDeleteConfirm() { uiState = uiState.copy(showDeleteConfirm = null) }

    fun saveAccount(
        name: String,
        type: AccountType,
        balance: BigDecimal,
        color: String?,
        initialCapital: BigDecimal? = null,
        taxRate: Float = 0.30f,
        currencyCode: String = "EUR"
    ) {
        scope.launch {
            val editing = uiState.editingAccount
            if (editing != null) {
                accountRepo.updateAccount(editing.copy(
                    name = name, accountType = type, balance = balance, color = color,
                    initialCapital = if (type == AccountType.INVESTMENT) initialCapital else null,
                    taxRate = taxRate,
                    currencyCode = currencyCode
                ))
            } else {
                accountRepo.createAccount(Account(
                    name = name, accountType = type, balance = balance, color = color,
                    initialCapital = if (type == AccountType.INVESTMENT) initialCapital else null,
                    taxRate = taxRate,
                    currencyCode = currencyCode
                ))
            }
            hideDialog()
        }
    }

    fun deleteAccount(id: Long) {
        scope.launch {
            val account = uiState.accounts.find { it.id == id }
            accountRepo.deleteAccount(id)
            hideDeleteConfirm()
            if (account != null) {
                UndoBus.show(UndoableAction(
                    message = "Compte \"${account.name}\" supprime",
                    onUndo = { accountRepo.restoreAccount(id) }
                ))
            }
        }
    }

    fun moveUp(id: Long) {
        val sorted = uiState.accounts.sortedBy { it.displayOrder }
        val idx = sorted.indexOfFirst { it.id == id }
        if (idx <= 0) return
        val current = sorted[idx]
        val previous = sorted[idx - 1]
        scope.launch { accountRepo.swapDisplayOrder(current.id, previous.id) }
    }

    fun moveDown(id: Long) {
        val sorted = uiState.accounts.sortedBy { it.displayOrder }
        val idx = sorted.indexOfFirst { it.id == id }
        if (idx < 0 || idx >= sorted.size - 1) return
        val current = sorted[idx]
        val next = sorted[idx + 1]
        scope.launch { accountRepo.swapDisplayOrder(current.id, next.id) }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun AccountsScreen(navigationState: NavigationState) {
    val state = remember { AccountsScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    // Ouvre automatiquement le dialog si on vient du tableau de bord
    LaunchedEffect(Unit) {
        if (navigationState.openAddAccountDialog) {
            state.showAddDialog()
            navigationState.openAddAccountDialog = false
        }
    }

    val ui = state.uiState

    Column(modifier = Modifier.fillMaxSize()) {
            // Header — aligné en haut comme Accueil
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comptes",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeumorphicTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                NeumorphicButton(
                    text = "Ajouter un compte",
                    icon = Icons.Filled.Add,
                    onClick = { state.showAddDialog() }
                )
            }

            // Total balance
            BalanceCard(
                title = "Solde total de tous les comptes",
                amount = ui.totalBalance,
                icon = Icons.Filled.AccountBalance,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Filter: show archived (soft-deleted) accounts
            val archivedCount = ui.accounts.count { !it.isActive }
            if (archivedCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = ui.showArchived,
                        onCheckedChange = { state.toggleShowArchived() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = NeumorphicPrimary,
                            checkedThumbColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Afficher les comptes archives ($archivedCount)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeumorphicTextSecondary
                    )
                }
            }

            val visibleAccounts = if (ui.showArchived) ui.accounts else ui.accounts.filter { it.isActive }

            if (visibleAccounts.isEmpty() && !ui.isLoading) {
                EmptyState(
                    message = "Aucun compte créé.\nAjoutez votre premier compte bancaire !",
                    icon = Icons.Filled.AccountBalance,
                    actionText = "Ajouter un compte",
                    onAction = { state.showAddDialog() }
                )
            } else {
                // Accounts grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleAccounts) { account ->
                        AccountCard(
                            account = account,
                            onEdit = { state.showEditDialog(account) },
                            onDelete = { state.showDeleteConfirm(account.id) },
                            onRestore = { state.restoreAccount(account.id) },
                            onMoveUp = { state.moveUp(account.id) },
                            onMoveDown = { state.moveDown(account.id) }
                        )
                    }
                }
            }
    }

    // Add/Edit Dialog
    if (ui.showAddDialog) {
        AccountFormDialog(
            account = ui.editingAccount,
            onSave = { name, type, balance, color, initialCapital, taxRate, currencyCode ->
                state.saveAccount(name, type, balance, color, initialCapital, taxRate, currencyCode)
            },
            onDismiss = { state.hideDialog() }
        )
    }

    // Delete confirmation
    ui.showDeleteConfirm?.let { id ->
        ConfirmDialog(
            title = "Supprimer le compte",
            message = "Êtes-vous sûr de vouloir supprimer ce compte ? Cette action est irréversible.",
            onConfirm = { state.deleteAccount(id) },
            onDismiss = { state.hideDeleteConfirm() }
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    NeumorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource),
        elevation = if (isHovered) 10.dp else 7.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (account.isActive) 1f else 0.55f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(parseColor(account.color).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (account.accountType) {
                        AccountType.CHECKING -> Icons.Filled.AccountBalance
                        AccountType.SAVINGS -> Icons.Filled.Savings
                        AccountType.CASH -> Icons.Filled.Money
                        AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
                        AccountType.INVESTMENT -> Icons.Filled.TrendingUp
                    },
                    contentDescription = null,
                    tint = parseColor(account.color),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NeumorphicTextPrimary
                )
                Text(
                    text = when (account.accountType) {
                        AccountType.CHECKING -> "Compte courant"
                        AccountType.SAVINGS -> "Épargne"
                        AccountType.CASH -> "Espèces"
                        AccountType.CREDIT_CARD -> "Carte de crédit"
                        AccountType.INVESTMENT -> "Investissement"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NeumorphicTextTertiary
                )
                // Investment-specific info: gain % + tax provision
                if (account.accountType == AccountType.INVESTMENT && account.initialCapital != null) {
                    Spacer(Modifier.height(4.dp))
                    val pct = account.gainPercent * 100f
                    val isPositive = account.gainAbsolute >= BigDecimal.ZERO
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = (if (isPositive) "+" else "") + String.format("%.2f", pct) + "%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPositive) IncomeColor else ExpenseColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Capital: ${String.format("%.2f", account.initialCapital)} EUR",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeumorphicTextTertiary
                        )
                    }
                    if (account.gainAbsolute > BigDecimal.ZERO) {
                        Text(
                            text = "Provision impots (${(account.taxRate * 100).toInt()}%): ${String.format("%.2f", account.taxProvision)} EUR",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeumorphicBudgetWarning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            CurrencyAmount(
                amount = account.balance,
                style = MaterialTheme.typography.titleLarge,
                currencyCode = account.currencyCode
            )

            Spacer(Modifier.width(8.dp))

            // Actions
            if (account.isActive) {
                if (isHovered) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Monter", tint = NeumorphicTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Descendre", tint = NeumorphicTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, "Modifier", tint = NeumorphicTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                // Archived account — always show restore button
                Text(
                    "Archive",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicTextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp)
                )
                IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Restore, "Restaurer", tint = IncomeColor, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountFormDialog(
    account: Account?,
    onSave: (String, AccountType, BigDecimal, String?, BigDecimal?, Float, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.accountType ?: AccountType.CHECKING) }
    var balanceText by remember { mutableStateOf(account?.balance?.toPlainString() ?: "0") }
    var initialCapitalText by remember { mutableStateOf(account?.initialCapital?.toPlainString() ?: "") }
    var taxRateText by remember { mutableStateOf(((account?.taxRate ?: 0.30f) * 100).toInt().toString()) }
    var currencyCode by remember { mutableStateOf(account?.currencyCode ?: "EUR") }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (account != null) "Modifier le compte" else "Nouveau compte",
                style = MaterialTheme.typography.headlineMedium,
                color = NeumorphicTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Name
                NeumorphicTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nom du compte",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Type
                Box {
                    OutlinedTextField(
                        value = when (type) {
                            AccountType.CHECKING -> "Compte courant"
                            AccountType.SAVINGS -> "Épargne"
                            AccountType.CASH -> "Espèces"
                            AccountType.CREDIT_CARD -> "Carte de crédit"
                            AccountType.INVESTMENT -> "Investissement"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type de compte") },
                        trailingIcon = {
                            IconButton(onClick = { typeDropdownExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, "Type")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = readOnlyTextFieldColors()
                    )
                    DropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        AccountType.entries.forEach { accountType ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (accountType) {
                                        AccountType.CHECKING -> "Compte courant"
                                        AccountType.SAVINGS -> "Épargne"
                                        AccountType.CASH -> "Espèces"
                                        AccountType.CREDIT_CARD -> "Carte de crédit"
                                        AccountType.INVESTMENT -> "Investissement"
                                    })
                                },
                                onClick = { type = accountType; typeDropdownExpanded = false }
                            )
                        }
                    }
                }

                // Currency
                Box {
                    OutlinedTextField(
                        value = currencyCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Devise") },
                        trailingIcon = {
                            IconButton(onClick = { currencyDropdownExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, "Devise")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = readOnlyTextFieldColors()
                    )
                    DropdownMenu(
                        expanded = currencyDropdownExpanded,
                        onDismissRequest = { currencyDropdownExpanded = false }
                    ) {
                        listOf("EUR", "USD", "GBP", "CHF", "CAD", "JPY", "AUD", "CNY").forEach { cur ->
                            DropdownMenuItem(
                                text = { Text(cur) },
                                onClick = { currencyCode = cur; currencyDropdownExpanded = false }
                            )
                        }
                    }
                }

                // Balance
                NeumorphicTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' || c == '-' || c == ',' } },
                    label = if (type == AccountType.INVESTMENT) "Valeur actuelle du portefeuille ($currencyCode)" else "Solde initial ($currencyCode)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Investment-specific fields
                if (type == AccountType.INVESTMENT) {
                    NeumorphicTextField(
                        value = initialCapitalText,
                        onValueChange = { initialCapitalText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = "Capital initial investi (EUR)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NeumorphicTextField(
                        value = taxRateText,
                        onValueChange = { taxRateText = it.filter { c -> c.isDigit() } },
                        label = "Taux d'imposition sur les gains (%)",
                        suffix = "%",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Le rendement (%) et la provision impots seront calcules automatiquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeumorphicTextTertiary
                    )
                }

                // Color picker
                Text("Couleur", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryColors.take(8).forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColorIndex = index }
                                .then(
                                    if (index == selectedColorIndex)
                                        Modifier.padding(2.dp).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(color)
                                    else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            NeumorphicButton(
                text = if (account != null) "Modifier" else "Créer",
                onClick = {
                    val balance = balanceText.replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val colorHex = CategoryColors.getOrNull(selectedColorIndex)?.let {
                        String.format("#%06X", 0xFFFFFF and it.hashCode())
                    }
                    val initialCapital = if (type == AccountType.INVESTMENT) {
                        initialCapitalText.replace(",", ".").toBigDecimalOrNull()
                    } else null
                    val taxRate = (taxRateText.toIntOrNull()?.coerceIn(0, 100) ?: 30) / 100f
                    onSave(name, type, balance, colorHex, initialCapital, taxRate, currencyCode)
                },
                enabled = name.isNotBlank()
            )
        },
        dismissButton = {
            NeumorphicButton(text = "Annuler", onClick = onDismiss, isPrimary = false)
        },
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
