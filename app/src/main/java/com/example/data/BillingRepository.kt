package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class BackupImportSummary(
    val billsCount: Int,
    val customersCount: Int,
    val productsCount: Int,
    val accountsCount: Int,
    val helpCount: Int,
    val settlementsCount: Int = 0,
    val expensesCount: Int = 0
)

data class PendingAdminSignup(
    val uid: String = "",
    val email: String = "",
    val adminName: String = "",
    val mobile: String = "",
    val twoFactorHash: String = ""
) {
    val isPresent: Boolean
        get() = uid.isNotBlank() && email.isNotBlank()
}

object AuthHasher {
    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

class BillingRepository(
    private val db: AppDatabase,
    val cloudSync: CloudSyncRepository? = null
) {
    private val billDao = db.billDao()
    private val customerDao = db.customerDao()
    private val productDao = db.productDao()
    private val userDao = db.userAccountDao()
    private val configDao = db.appConfigDao()
    private val helpDao = db.customerHelpDao()
    private val settlementDao = db.technicianSettlementDao()
    private val expenseDao = db.expenseDao()
    private val trashDao = db.trashItemDao()
    private val auditDao = db.auditLogDao()
    private val comboFileDao = db.productComboFileDao()
    private val upiTxDao = db.upiTransactionDao()
    private val employeeDao = db.employeeDao()
    private val salaryPaymentDao = db.salaryPaymentDao()

    private val _volatileCloudActive = MutableStateFlow(false)
    val isVolatileCloudMode: StateFlow<Boolean> = _volatileCloudActive.asStateFlow()

    private val _volatileWorkspaceId = MutableStateFlow("")
    private val _volatileDeviceRole = MutableStateFlow("")
    private val _volatileBills = MutableStateFlow<List<BillEntity>>(emptyList())
    private val _volatileCustomers = MutableStateFlow<List<CustomerEntity>>(emptyList())
    private val _volatileProducts = MutableStateFlow<List<ProductEntity>>(emptyList())
    private val _volatileAccounts = MutableStateFlow<List<UserAccountEntity>>(emptyList())
    private val _volatileHelpRequests = MutableStateFlow<List<CustomerHelpEntity>>(emptyList())
    private val _volatileSettlements = MutableStateFlow<List<TechnicianSettlementEntity>>(emptyList())
    private val _volatileExpenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private val _volatileTrashItems = MutableStateFlow<List<TrashItemEntity>>(emptyList())
    private val _volatileAuditLogs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    private val _volatileComboFiles = MutableStateFlow<List<ProductComboFileEntity>>(emptyList())
    private val _volatileUpiTransactions = MutableStateFlow<List<UpiTransactionEntity>>(emptyList())
    private val _volatileEmployees = MutableStateFlow<List<EmployeeEntity>>(emptyList())
    private val _volatileSalaryPayments = MutableStateFlow<List<SalaryPaymentEntity>>(emptyList())
    private val _volatileCompany = MutableStateFlow<CompanySettings?>(null)
    private val _volatileUpi = MutableStateFlow<UpiSettings?>(null)
    private val _volatileVoice = MutableStateFlow<VoiceAndNoteSettings?>(null)

    val bills: Flow<List<BillEntity>> = combine(
        billDao.observeAll(),
        _volatileCloudActive,
        _volatileBills
    ) { local, volatile, cloud ->
        if (volatile) cloud else local
    }

    val customers: Flow<List<CustomerEntity>> = combine(
        customerDao.observeAll(),
        _volatileCloudActive,
        _volatileCustomers
    ) { local, volatile, cloud ->
        if (volatile) cloud else local
    }

    private val rawProducts: Flow<List<ProductEntity>> = combine(
        productDao.observeAll(),
        _volatileCloudActive,
        _volatileProducts
    ) { local, volatile, cloud ->
        if (volatile) cloud else local
    }

    val products: Flow<List<ProductEntity>> = rawProducts.map { list ->
        list.filter { !it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
    }

    private val localNormalizedAccounts: Flow<List<UserAccountEntity>> = userDao.observeAll().map { raw ->
        normalizeAccountList(raw)
    }

    // Guarantee at most ONE Admin account is ever surfaced in UI/dropdowns
    val accounts: Flow<List<UserAccountEntity>> = combine(
        localNormalizedAccounts,
        _volatileCloudActive,
        _volatileAccounts
    ) { local, volatile, cloud ->
        if (volatile) normalizeAccountList(cloud) else local
    }

    val helpRequests: Flow<List<CustomerHelpEntity>> = combine(
        helpDao.observeAll(),
        _volatileCloudActive,
        _volatileHelpRequests
    ) { local, volatile, cloud ->
        if (volatile) cloud else local
    }

    val settlements: Flow<List<TechnicianSettlementEntity>> = combine(
        settlementDao.observeAll(),
        _volatileCloudActive,
        _volatileSettlements
    ) { local, volatile, cloud ->
        if (volatile) cloud else local
    }

    val expenses: Flow<List<ExpenseEntity>> = combine(
        expenseDao.observeAll(),
        _volatileCloudActive,
        _volatileExpenses
    ) { local, volatile, cloud ->
        if (volatile) cloud else local
    }

    val trashItems: Flow<List<TrashItemEntity>> = combine(
        trashDao.observeActive(),
        _volatileCloudActive,
        _volatileTrashItems
    ) { local, volatile, cloud ->
        if (volatile) cloud.filter { it.status == "TRASHED" } else local
    }

    val auditLogs: Flow<List<AuditLogEntity>> = combine(
        auditDao.observeRecent(),
        _volatileCloudActive,
        _volatileAuditLogs
    ) { local, volatile, cloud ->
        if (volatile) cloud else local
    }

    val configMap: Flow<Map<String, String>> = configDao.observeAll().map { list ->
        list.associate { it.key to it.value }
    }

    val isStaffOnlyApk: Flow<Boolean> = configMap.map { m ->
        val role = m[KEY_CLOUD_DEVICE_ROLE].orEmpty()
        m[KEY_STAFF_APK_MODE] == "1" ||
            role.equals("technician", ignoreCase = true) ||
            role.equals("super_admin", ignoreCase = true)
    }

    val prefillInviteCode: Flow<String> = configMap.map { m ->
        m[KEY_PREFILL_INVITE_CODE].orEmpty()
    }

    val productComboFiles: Flow<List<ProductComboFile>> = combine(
        comboFileDao.observeAll(),
        rawProducts,
        _volatileCloudActive,
        _volatileComboFiles
    ) { localCombos, rawList, volatile, cloudCombos ->
        val primary = (if (volatile) cloudCombos else localCombos).map { ProductComboFileJson.fromEntity(it) }
        if (primary.isNotEmpty() || volatile) {
            primary.sortedByDescending { it.updatedAt }
        } else {
            // Fallback display for unmigrated legacy combos until migration completes
            rawList
                .filter { it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
                .mapNotNull { row ->
                    ComboPackageJson.fromSingleJson(row.unit)?.let { pkg ->
                        ProductComboFile(
                            comboFileId = pkg.id,
                            companyId = "",
                            name = row.name,
                            description = pkg.description,
                            items = pkg.items.map { itm ->
                                ProductComboItem(
                                    productId = itm.productId,
                                    productName = itm.productName,
                                    unit = itm.unit,
                                    qty = itm.qty,
                                    catalogRate = itm.rate,
                                    folder = itm.folder
                                )
                            },
                            comboPrice = pkg.totalAmount,
                            createdAt = pkg.createdAt,
                            updatedAt = pkg.createdAt
                        )
                    }
                }
                .sortedByDescending { it.updatedAt }
        }
    }

    val upiTransactions: Flow<List<UpiTransactionEntity>> = combine(
        upiTxDao.observeAll(),
        _volatileCloudActive,
        _volatileUpiTransactions
    ) { local, volatile, cloud ->
        (if (volatile) cloud else local).sortedByDescending { it.updatedAt }
    }

    // Super Admin ONLY: cleared/empty for Admin and Technician
    val employees: Flow<List<EmployeeEntity>> = combine(
        employeeDao.observeAll(),
        _volatileCloudActive,
        _volatileDeviceRole,
        _volatileEmployees
    ) { local, volatile, role, cloud ->
        val norm = normalizeRole(role)
        when {
            volatile && norm == "super_admin" -> cloud.sortedBy { it.name.lowercase(Locale.ROOT) }
            volatile -> emptyList()
            else -> local
        }
    }

    val salaryPayments: Flow<List<SalaryPaymentEntity>> = combine(
        salaryPaymentDao.observeAll(),
        _volatileCloudActive,
        _volatileDeviceRole,
        _volatileSalaryPayments
    ) { local, volatile, role, cloud ->
        val norm = normalizeRole(role)
        when {
            volatile && norm == "super_admin" -> cloud.sortedByDescending { it.createdAt }
            volatile -> emptyList()
            else -> local
        }
    }

    val comboPackages: Flow<List<ComboPackage>> = combine(
        rawProducts,
        configMap,
        _volatileCloudActive
    ) { rawList, m, volatile ->
        val syncedCombos = rawList
            .filter { it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
            .mapNotNull { row ->
                ComboPackageJson.fromSingleJson(row.unit)?.copy(name = row.name)
            }
            .sortedByDescending { it.createdAt }
        if (syncedCombos.isNotEmpty() || volatile) {
            syncedCombos
        } else {
            val rawCfg = m[KEY_COMBO_PACKAGES].orEmpty()
            if (rawCfg.isBlank()) emptyList() else ComboPackageJson.fromJson(rawCfg)
        }
    }

    val cloudWorkspaceInfo: Flow<CloudWorkspaceInfo> = configMap.map { m ->
        CloudWorkspaceInfo(
            workspaceId = m[KEY_CLOUD_WORKSPACE_ID].orEmpty(),
            ownerUid = m[KEY_CLOUD_OWNER_UID].orEmpty(),
            techShareCode = m[KEY_CLOUD_TECH_CODE].orEmpty(),
            superShareCode = m[KEY_CLOUD_SUPER_CODE].orEmpty(),
            deviceRole = m[KEY_CLOUD_DEVICE_ROLE].orEmpty().ifBlank { "admin" },
            companyName = AppIdentity.OFFICIAL_COMPANY_NAME
        )
    }

    private val localCompanySettings: Flow<CompanySettings> = configMap.map { m ->
        CompanySettings(
            companyName = AppIdentity.OFFICIAL_COMPANY_NAME,
            mobile = m[KEY_COMPANY_MOBILE]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_MOBILE,
            email = m[KEY_COMPANY_EMAIL]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_EMAIL,
            address = m[KEY_COMPANY_ADDRESS]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_ADDRESS,
            authorisedSignatureBase64 = m[KEY_AUTHORISED_SIGNATURE] ?: "",
            logoBase64 = m[KEY_COMPANY_LOGO] ?: ""
        )
    }

    val companySettings: Flow<CompanySettings> = combine(
        localCompanySettings,
        _volatileCloudActive,
        _volatileCompany
    ) { local, volatile, cloud ->
        val base = if (volatile && cloud != null) cloud else local
        base.copy(companyName = AppIdentity.OFFICIAL_COMPANY_NAME)
    }

    private val localVoiceSettings: Flow<VoiceAndNoteSettings> = configMap.map { m ->
        val defaults = VoiceAndNoteSettings()
        VoiceAndNoteSettings(
            headerNote = m[KEY_HEADER_NOTE] ?: defaults.headerNote,
            loginVoice = m[KEY_VOICE_LOGIN]?.takeIf { it.isNotBlank() } ?: defaults.loginVoice,
            postLoginVoice = m[KEY_VOICE_POST_LOGIN]?.takeIf { it.isNotBlank() } ?: defaults.postLoginVoice,
            billGeneratingVoice = m[KEY_VOICE_BILL_START]?.takeIf { it.isNotBlank() } ?: defaults.billGeneratingVoice,
            billCompletedVoice = m[KEY_VOICE_BILL_DONE]?.takeIf { it.isNotBlank() } ?: defaults.billCompletedVoice,
            voiceModelId = m[KEY_VOICE_MODEL_ID]?.takeIf { it.isNotBlank() } ?: defaults.voiceModelId
        )
    }

    val voiceAndNoteSettings: Flow<VoiceAndNoteSettings> = combine(
        localVoiceSettings,
        _volatileCloudActive,
        _volatileVoice
    ) { local, volatile, cloud ->
        if (volatile && cloud != null) cloud else local
    }

    private val localUpiSettings: Flow<UpiSettings> = configMap.map { m ->
        UpiSettings(
            upiId = m[KEY_UPI_ID] ?: "",
            payeeName = m[KEY_UPI_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_PAYEE_NAME,
            fallbackQrBase64 = m[KEY_FALLBACK_QR] ?: ""
        )
    }

    val upiSettings: Flow<UpiSettings> = combine(
        localUpiSettings,
        _volatileCloudActive,
        _volatileUpi
    ) { local, volatile, cloud ->
        if (volatile && cloud != null) cloud else local
    }

    val deviceAuthEnabledByUser: Flow<Map<String, Boolean>> = configMap.map { m ->
        m.entries
            .filter { it.key.startsWith(KEY_DEVICE_AUTH_USER_PREFIX) }
            .associate {
                it.key.removePrefix(KEY_DEVICE_AUTH_USER_PREFIX) to (it.value == "1")
            }
    }

    val isAdminConfigured: Flow<Boolean> = accounts.map { users ->
        users.any { it.role.equals("admin", ignoreCase = true) }
    }

    val hasAdminVerificationCode: Flow<Boolean> = configMap.map { m ->
        !m[KEY_FINAL_CODE_HASH].isNullOrBlank()
    }

    val soundEnabled: Flow<Boolean> = configMap.map { m ->
        m[KEY_SOUND_ENABLED] != "0"
    }

    val language: Flow<AppLanguage> = configMap.map { AppLanguage.EN }

    val loginSecurityState: Flow<LoginSecurityState> = configMap.map { m ->
        LoginSecurityState(
            failedAttempts = m[KEY_LOGIN_FAILED_ATTEMPTS]?.toIntOrNull() ?: 0,
            lockUntilMillis = m[KEY_LOGIN_LOCK_UNTIL_MS]?.toLongOrNull() ?: 0L,
            maxAttempts = LoginSecurityState.MAX_ATTEMPTS,
            lockDurationMs = LoginSecurityState.LOCK_DURATION_MS
        )
    }

    // Device-local Admin security state (3 wrong attempts -> 15 min lock on THIS phone only)
    val adminLoginSecurityState: Flow<LoginSecurityState> = configMap.map { m ->
        LoginSecurityState(
            failedAttempts = m[KEY_ADMIN_FAILED_ATTEMPTS]?.toIntOrNull() ?: 0,
            lockUntilMillis = m[KEY_ADMIN_LOCK_UNTIL_MS]?.toLongOrNull() ?: 0L,
            maxAttempts = LoginSecurityState.ADMIN_MAX_ATTEMPTS,
            lockDurationMs = LoginSecurityState.ADMIN_LOCK_DURATION_MS
        )
    }

    val techActivationState: Flow<TechActivationState> = configMap.map { m ->
        val email = m[KEY_TECH_VERIFIED_EMAIL].orEmpty().trim().lowercase(Locale.US)
        TechActivationState(
            verified = email.isNotBlank() && m[KEY_TECH_VERIFIED_USERNAME].orEmpty().isNotBlank(),
            email = email,
            username = m[KEY_TECH_VERIFIED_USERNAME].orEmpty().trim(),
            name = m[KEY_TECH_VERIFIED_NAME].orEmpty().trim(),
            workspaceId = m[KEY_TECH_VERIFIED_WORKSPACE_ID].orEmpty().trim(),
            companyName = AppIdentity.OFFICIAL_COMPANY_NAME,
            techId = m[KEY_TECH_VERIFIED_TECH_ID].orEmpty().trim(),
            phone = m[KEY_TECH_VERIFIED_PHONE].orEmpty().trim(),
            mustChangePassword = m[KEY_TECH_MUST_CHANGE_PASSWORD] == "1"
        )
    }

    suspend fun saveTechActivationState(state: TechActivationState) {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_TECH_VERIFIED_EMAIL, state.email.trim().lowercase(Locale.US)),
                AppConfigEntity(KEY_TECH_VERIFIED_USERNAME, state.username.trim()),
                AppConfigEntity(KEY_TECH_VERIFIED_NAME, state.name.trim()),
                AppConfigEntity(KEY_TECH_VERIFIED_WORKSPACE_ID, state.workspaceId.trim()),
                AppConfigEntity(KEY_TECH_VERIFIED_COMPANY, AppIdentity.OFFICIAL_COMPANY_NAME),
                AppConfigEntity(KEY_TECH_VERIFIED_TECH_ID, state.techId.trim()),
                AppConfigEntity(KEY_TECH_VERIFIED_PHONE, state.phone.trim()),
                AppConfigEntity(KEY_TECH_MUST_CHANGE_PASSWORD, if (state.mustChangePassword) "1" else "0")
            )
        )
    }

    suspend fun clearTechActivationState() {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_TECH_VERIFIED_EMAIL, ""),
                AppConfigEntity(KEY_TECH_VERIFIED_USERNAME, ""),
                AppConfigEntity(KEY_TECH_VERIFIED_NAME, ""),
                AppConfigEntity(KEY_TECH_VERIFIED_WORKSPACE_ID, ""),
                AppConfigEntity(KEY_TECH_VERIFIED_COMPANY, ""),
                AppConfigEntity(KEY_TECH_VERIFIED_TECH_ID, ""),
                AppConfigEntity(KEY_TECH_VERIFIED_PHONE, ""),
                AppConfigEntity(KEY_TECH_MUST_CHANGE_PASSWORD, "0")
            )
        )
    }

    private fun normalizeAccountList(raw: List<UserAccountEntity>): List<UserAccountEntity> {
        var adminSeen = false
        return buildList {
            for (acc in raw) {
                val normalizedRole = normalizeRole(acc.role)
                if (normalizedRole == "admin") {
                    if (!adminSeen) {
                        adminSeen = true
                        add(acc.copy(role = "admin"))
                    }
                } else {
                    add(acc.copy(role = normalizedRole))
                }
            }
        }
    }

    suspend fun seedDefaultsIfNeeded() {
        val seeded = configDao.getValue(KEY_SEEDED) == "1"
        if (!seeded && productDao.getAll().isEmpty()) {
            productDao.insertAll(STARTER_PRODUCTS)
            val defaults = VoiceAndNoteSettings()
            configDao.setAll(
                listOf(
                    AppConfigEntity(KEY_SEEDED, "1"),
                    AppConfigEntity(KEY_COMPANY_NAME, DEFAULT_COMPANY_NAME),
                    AppConfigEntity(KEY_COMPANY_MOBILE, DEFAULT_COMPANY_MOBILE),
                    AppConfigEntity(KEY_COMPANY_EMAIL, DEFAULT_COMPANY_EMAIL),
                    AppConfigEntity(KEY_COMPANY_ADDRESS, DEFAULT_COMPANY_ADDRESS),
                    AppConfigEntity(KEY_UPI_NAME, DEFAULT_PAYEE_NAME),
                    AppConfigEntity(KEY_SOUND_ENABLED, "1"),
                    AppConfigEntity(KEY_LANGUAGE, "en"),
                    AppConfigEntity(KEY_HEADER_NOTE, defaults.headerNote),
                    AppConfigEntity(KEY_VOICE_LOGIN, defaults.loginVoice),
                    AppConfigEntity(KEY_VOICE_POST_LOGIN, defaults.postLoginVoice),
                    AppConfigEntity(KEY_VOICE_BILL_START, defaults.billGeneratingVoice),
                    AppConfigEntity(KEY_VOICE_BILL_DONE, defaults.billCompletedVoice),
                    AppConfigEntity(KEY_VOICE_MODEL_ID, defaults.voiceModelId)
                )
            )
        }
    }

    suspend fun setupAdmin(
        username: String,
        password: String,
        finalCode: String = "",
        displayName: String = "Admin",
        phone: String = "",
        email: String = "",
        uid: String = "",
        companyId: String = "",
        pushToCloud: Boolean = true
    ) {
        val cleanUser = AuthPolicy.CANONICAL_ADMIN_USERNAME
        val adminEntity = UserAccountEntity(
            username = cleanUser,
            displayName = displayName.ifBlank { "Admin" },
            passwordHash = AuthHasher.sha256(password),
            role = "admin",
            phone = phone.trim(),
            email = email.trim().lowercase(Locale.ROOT),
            uid = uid.trim(),
            companyId = companyId.trim(),
            loginEnabled = true
        )
        _volatileCloudActive.value = false
        _volatileDeviceRole.value = "admin"
        db.withTransaction {
            // Strictly enforce single Admin account (@TotalSolutionEnterprises)
            userDao.deleteAllAdmins()
            userDao.upsert(adminEntity)
            if (finalCode.isNotBlank()) {
                configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, AuthHasher.sha256(finalCode)))
            }
        }
        val wsId = companyId.ifBlank { getActiveWorkspaceId() }
        if (pushToCloud && wsId.isNotBlank()) {
            cloudSync?.pushAccount(wsId, adminEntity)
        }
    }

    suspend fun savePendingAdminSignup(
        uid: String,
        email: String,
        adminName: String,
        mobile: String,
        twoFactorPassword: String
    ) {
        val tfHash = twoFactorPassword.trim().takeIf { it.isNotEmpty() }?.let { AuthHasher.sha256(it) }.orEmpty()
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_PENDING_ADMIN_UID, uid.trim()),
                AppConfigEntity(KEY_PENDING_ADMIN_EMAIL, email.trim().lowercase(Locale.US)),
                AppConfigEntity(KEY_PENDING_ADMIN_NAME, adminName.trim()),
                AppConfigEntity(KEY_PENDING_ADMIN_MOBILE, mobile.trim()),
                AppConfigEntity(KEY_PENDING_ADMIN_TWO_FACTOR_HASH, tfHash)
            )
        )
    }

    suspend fun getPendingAdminSignup(): PendingAdminSignup = PendingAdminSignup(
        uid = configDao.getValue(KEY_PENDING_ADMIN_UID).orEmpty().trim(),
        email = configDao.getValue(KEY_PENDING_ADMIN_EMAIL).orEmpty().trim().lowercase(Locale.US),
        adminName = configDao.getValue(KEY_PENDING_ADMIN_NAME).orEmpty().trim(),
        mobile = configDao.getValue(KEY_PENDING_ADMIN_MOBILE).orEmpty().trim(),
        twoFactorHash = configDao.getValue(KEY_PENDING_ADMIN_TWO_FACTOR_HASH).orEmpty().trim()
    )

    suspend fun verifyPendingTwoFactor(code: String): Boolean {
        val stored = configDao.getValue(KEY_PENDING_ADMIN_TWO_FACTOR_HASH).orEmpty().trim()
        if (stored.isBlank()) return false
        return stored == AuthHasher.sha256(code.trim())
    }

    suspend fun clearPendingAdminSignup() {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_PENDING_ADMIN_UID, ""),
                AppConfigEntity(KEY_PENDING_ADMIN_EMAIL, ""),
                AppConfigEntity(KEY_PENDING_ADMIN_NAME, ""),
                AppConfigEntity(KEY_PENDING_ADMIN_MOBILE, ""),
                AppConfigEntity(KEY_PENDING_ADMIN_TWO_FACTOR_HASH, "")
            )
        )
    }

    suspend fun saveVerifiedCloudAdmin(
        displayName: String,
        phone: String,
        email: String,
        uid: String,
        companyId: String,
        rawPassword: String = "",
        rawTwoFactorCode: String = ""
    ) {
        val cleanUser = AuthPolicy.CANONICAL_ADMIN_USERNAME
        val passwordHash = if (rawPassword.isNotBlank()) {
            AuthHasher.sha256(rawPassword)
        } else {
            AuthPolicy.FIREBASE_EMAIL_AUTH_MARKER
        }
        val pendingTfHash = configDao.getValue(KEY_PENDING_ADMIN_TWO_FACTOR_HASH).orEmpty().trim()
        val resolvedTfHash = when {
            rawTwoFactorCode.isNotBlank() -> AuthHasher.sha256(rawTwoFactorCode.trim())
            pendingTfHash.isNotBlank() -> pendingTfHash
            else -> ""
        }
        val adminEntity = UserAccountEntity(
            username = cleanUser,
            displayName = displayName.ifBlank { "Admin" },
            passwordHash = passwordHash,
            role = "admin",
            phone = phone.trim(),
            email = email.trim().lowercase(Locale.ROOT),
            uid = uid.trim(),
            companyId = companyId.trim(),
            loginEnabled = true
        )
        _volatileCloudActive.value = false
        _volatileDeviceRole.value = "admin"
        db.withTransaction {
            userDao.deleteAllAdmins()
            userDao.upsert(adminEntity)
            if (resolvedTfHash.isNotBlank()) {
                configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, resolvedTfHash))
            }
        }
    }

    suspend fun authenticate(username: String, password: String): UserSession? {
        return authenticateChecked(username, password).getOrNull()
    }

    suspend fun authenticateChecked(username: String, password: String): Result<UserSession> {
        val clean = username.trim()
        val list = if (_volatileCloudActive.value) _volatileAccounts.value else userDao.getAll()
        val account = list.firstOrNull {
            it.username.equals(clean, ignoreCase = true) ||
                (it.email.isNotBlank() && it.email.equals(clean, ignoreCase = true))
        } ?: if (clean.equals(AuthPolicy.CANONICAL_ADMIN_USERNAME, ignoreCase = true) ||
            clean.equals("TotalSolutionEnterprises", ignoreCase = true)
        ) {
            list.firstOrNull { normalizeRole(it.role) == "admin" }
        } else {
            null
        } ?: userDao.findByUsername(clean)
            ?: return Result.failure(IllegalArgumentException("Invalid username or password."))

        val normRole = normalizeRole(account.role)
        if (normRole == "technician" && !account.loginEnabled) {
            return Result.failure(IllegalStateException(AuthPolicy.TECH_LOGIN_DISABLED_MESSAGE))
        }
        val hash = AuthHasher.sha256(password)
        return if (account.passwordHash == hash) {
            Result.success(
                UserSession(
                    username = account.username,
                    displayName = account.displayName.ifBlank { account.username },
                    role = normRole,
                    phone = account.phone,
                    photoBase64 = account.photoBase64,
                    email = account.email,
                    uid = account.uid,
                    techId = account.techId,
                    companyId = account.companyId
                )
            )
        } else {
            Result.failure(IllegalArgumentException("Invalid username or password."))
        }
    }

    suspend fun getLoginSecurityState(): LoginSecurityState {
        val attempts = configDao.getValue(KEY_LOGIN_FAILED_ATTEMPTS)?.toIntOrNull() ?: 0
        val lockUntil = configDao.getValue(KEY_LOGIN_LOCK_UNTIL_MS)?.toLongOrNull() ?: 0L
        return LoginSecurityState(
            failedAttempts = attempts,
            lockUntilMillis = lockUntil,
            maxAttempts = LoginSecurityState.MAX_ATTEMPTS,
            lockDurationMs = LoginSecurityState.LOCK_DURATION_MS
        )
    }

    suspend fun recordLoginFailure(nowMillis: Long = System.currentTimeMillis()): LoginSecurityState {
        val current = getLoginSecurityState()
        if (current.isLocked(nowMillis)) {
            return current
        }
        val baseAttempts = current.effectiveFailedAttempts(nowMillis)
        val nextAttempts = (baseAttempts + 1).coerceAtMost(LoginSecurityState.MAX_ATTEMPTS)
        val nextLockUntil = if (nextAttempts >= LoginSecurityState.MAX_ATTEMPTS) {
            nowMillis + LoginSecurityState.LOCK_DURATION_MS
        } else {
            0L
        }
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_LOGIN_FAILED_ATTEMPTS, nextAttempts.toString()),
                AppConfigEntity(KEY_LOGIN_LOCK_UNTIL_MS, nextLockUntil.toString())
            )
        )
        return LoginSecurityState(
            failedAttempts = nextAttempts,
            lockUntilMillis = nextLockUntil,
            maxAttempts = LoginSecurityState.MAX_ATTEMPTS,
            lockDurationMs = LoginSecurityState.LOCK_DURATION_MS
        )
    }

    suspend fun resetLoginSecurityState() {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_LOGIN_FAILED_ATTEMPTS, "0"),
                AppConfigEntity(KEY_LOGIN_LOCK_UNTIL_MS, "0")
            )
        )
    }

    suspend fun getAdminLoginSecurityState(): LoginSecurityState {
        val attempts = configDao.getValue(KEY_ADMIN_FAILED_ATTEMPTS)?.toIntOrNull() ?: 0
        val lockUntil = configDao.getValue(KEY_ADMIN_LOCK_UNTIL_MS)?.toLongOrNull() ?: 0L
        return LoginSecurityState(
            failedAttempts = attempts,
            lockUntilMillis = lockUntil,
            maxAttempts = LoginSecurityState.ADMIN_MAX_ATTEMPTS,
            lockDurationMs = LoginSecurityState.ADMIN_LOCK_DURATION_MS
        )
    }

    suspend fun recordAdminLoginFailure(nowMillis: Long = System.currentTimeMillis()): LoginSecurityState {
        val current = getAdminLoginSecurityState()
        if (current.isLocked(nowMillis)) {
            return current
        }
        val baseAttempts = current.effectiveFailedAttempts(nowMillis)
        val nextAttempts = (baseAttempts + 1).coerceAtMost(LoginSecurityState.ADMIN_MAX_ATTEMPTS)
        val nextLockUntil = if (nextAttempts >= LoginSecurityState.ADMIN_MAX_ATTEMPTS) {
            nowMillis + LoginSecurityState.ADMIN_LOCK_DURATION_MS
        } else {
            0L
        }
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_ADMIN_FAILED_ATTEMPTS, nextAttempts.toString()),
                AppConfigEntity(KEY_ADMIN_LOCK_UNTIL_MS, nextLockUntil.toString())
            )
        )
        return LoginSecurityState(
            failedAttempts = nextAttempts,
            lockUntilMillis = nextLockUntil,
            maxAttempts = LoginSecurityState.ADMIN_MAX_ATTEMPTS,
            lockDurationMs = LoginSecurityState.ADMIN_LOCK_DURATION_MS
        )
    }

    suspend fun resetAdminLoginSecurityState() {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_ADMIN_FAILED_ATTEMPTS, "0"),
                AppConfigEntity(KEY_ADMIN_LOCK_UNTIL_MS, "0")
            )
        )
    }

    suspend fun setDeviceAuthEnabled(username: String, enabled: Boolean) {
        val key = "$KEY_DEVICE_AUTH_USER_PREFIX${username.trim().lowercase(Locale.ROOT)}"
        configDao.setValue(AppConfigEntity(key, if (enabled) "1" else "0"))
    }

    suspend fun isDeviceAuthEnabledForUser(username: String): Boolean {
        val key = "$KEY_DEVICE_AUTH_USER_PREFIX${username.trim().lowercase(Locale.ROOT)}"
        return configDao.getValue(key) == "1"
    }

    suspend fun isAdminVerificationCodeSet(): Boolean =
        !configDao.getValue(KEY_FINAL_CODE_HASH).isNullOrBlank()

    suspend fun verifyFinalCode(code: String): Boolean {
        val stored = configDao.getValue(KEY_FINAL_CODE_HASH) ?: return false
        return stored == AuthHasher.sha256(code.trim())
    }

    suspend fun updateFinalCode(newCode: String) {
        val trimmed = newCode.trim()
        if (trimmed.isEmpty()) {
            configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, ""))
        } else {
            configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, AuthHasher.sha256(trimmed)))
        }
    }

    suspend fun hasPromptedPermissions(): Boolean =
        configDao.getValue(KEY_PERMISSIONS_PROMPTED) == "1"

    suspend fun markPermissionsPrompted() {
        configDao.setValue(AppConfigEntity(KEY_PERMISSIONS_PROMPTED, "1"))
    }

    suspend fun setStaffOnlyInviteMode(role: String = "", inviteCode: String = "") {
        val entries = mutableListOf(AppConfigEntity(KEY_STAFF_APK_MODE, "1"))
        if (inviteCode.isNotBlank()) {
            entries += AppConfigEntity(KEY_PREFILL_INVITE_CODE, inviteCode.trim().uppercase(Locale.US))
        }
        if (role.isNotBlank()) {
            val norm = normalizeRole(role)
            if (norm == "technician" || norm == "super_admin") {
                entries += AppConfigEntity(KEY_CLOUD_DEVICE_ROLE, norm)
            }
        }
        configDao.setAll(entries)
    }

    suspend fun addTechnician(
        displayName: String,
        phone: String,
        username: String,
        password: String,
        photoBase64: String = ""
    ): Result<Unit> {
        val existing = userDao.getAll()
        val cleanUser = username.trim()
        val cleanName = displayName.trim()
        val cleanPhone = phone.trim()
        val cleanPhoto = photoBase64.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Enter technician full name"))
        }
        if (cleanPhone.filter { it.isDigit() }.length < 7) {
            return Result.failure(IllegalArgumentException("Enter valid technician mobile number"))
        }
        if (cleanUser.length < 3) {
            return Result.failure(IllegalArgumentException("Technician username must be at least 3 characters"))
        }
        AuthPolicy.evaluatePassword(password).firstMissingRequirement()?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        if (cleanPhoto.isBlank()) {
            return Result.failure(IllegalArgumentException("Please upload or capture Technician photo"))
        }
        if (existing.any { it.username.equals(cleanUser, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Username already exists"))
        }
        val entity = UserAccountEntity(
            username = cleanUser,
            displayName = cleanName,
            passwordHash = AuthHasher.sha256(password),
            role = "technician",
            phone = cleanPhone,
            photoBase64 = cleanPhoto
        )
        userDao.upsert(entity)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushAccount(wsId, entity)
        }
        return Result.success(Unit)
    }

    suspend fun updateTechnician(
        oldUsername: String,
        newUsername: String,
        displayName: String,
        phone: String,
        newPassword: String,
        newPhotoBase64: String? = null
    ): Result<Unit> {
        val cleanOld = oldUsername.trim()
        val cleanNew = newUsername.trim()
        val cleanName = displayName.trim()
        val cleanPhone = phone.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Enter technician full name"))
        }
        if (cleanPhone.filter { it.isDigit() }.length < 7) {
            return Result.failure(IllegalArgumentException("Enter valid technician mobile number"))
        }
        if (cleanNew.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters"))
        }
        if (newPassword.isNotEmpty()) {
            AuthPolicy.evaluatePassword(newPassword).firstMissingRequirement()?.let { msg ->
                return Result.failure(IllegalArgumentException(msg))
            }
        }

        val existing = userDao.findByUsername(cleanOld)
            ?: return Result.failure(IllegalStateException("Technician account not found"))
        if (!cleanNew.equals(cleanOld, ignoreCase = true)) {
            val conflict = userDao.findByUsername(cleanNew)
            if (conflict != null) {
                return Result.failure(IllegalStateException("Target username @$cleanNew already exists"))
            }
        }

        val updatedPasswordHash = if (newPassword.isNotBlank()) {
            AuthHasher.sha256(newPassword)
        } else {
            existing.passwordHash
        }
        val finalPhoto = if (!newPhotoBase64.isNullOrBlank()) newPhotoBase64.trim() else existing.photoBase64

        val updatedEntity = UserAccountEntity(
            username = cleanNew,
            displayName = cleanName,
            passwordHash = updatedPasswordHash,
            role = normalizeRole(existing.role),
            phone = cleanPhone,
            photoBase64 = finalPhoto
        )
        db.withTransaction {
            if (!cleanNew.equals(cleanOld, ignoreCase = true)) {
                userDao.deleteByUsername(existing.username)
                billDao.reassignCreator(existing.username, cleanNew)
                settlementDao.reassignTechnician(existing.username, cleanNew)
                val oldAuthKey = "$KEY_DEVICE_AUTH_USER_PREFIX${existing.username.lowercase(Locale.ROOT)}"
                val oldAuthVal = configDao.getValue(oldAuthKey)
                if (oldAuthVal != null) {
                    configDao.setValue(
                        AppConfigEntity(
                            "$KEY_DEVICE_AUTH_USER_PREFIX${cleanNew.lowercase(Locale.ROOT)}",
                            oldAuthVal
                        )
                    )
                }
            }
            userDao.upsert(updatedEntity)
            helpDao.updateTechnicianInfo(existing.username, cleanNew, cleanName, cleanPhone)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            if (!cleanNew.equals(cleanOld, ignoreCase = true)) {
                cloudSync?.deleteAccount(wsId, existing.username)
            }
            cloudSync?.pushAccount(wsId, updatedEntity)
        }
        return Result.success(Unit)
    }

    suspend fun removeTechnician(username: String) {
        userDao.deleteByUsername(username)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteAccount(wsId, username)
        }
    }

    suspend fun addSuperAdmin(
        displayName: String,
        phone: String,
        username: String,
        password: String
    ): Result<Unit> {
        val existing = userDao.getAll()
        val cleanUser = username.trim()
        val cleanName = displayName.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Enter Super Admin full name"))
        }
        if (cleanUser.length < 3) {
            return Result.failure(IllegalArgumentException("Super Admin username must be at least 3 characters"))
        }
        AuthPolicy.evaluatePassword(password).firstMissingRequirement()?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        if (existing.any { it.username.equals(cleanUser, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Username already exists"))
        }
        val entity = UserAccountEntity(
            username = cleanUser,
            displayName = cleanName,
            passwordHash = AuthHasher.sha256(password),
            role = "super_admin",
            phone = phone.trim()
        )
        userDao.upsert(entity)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushAccount(wsId, entity)
        }
        return Result.success(Unit)
    }

    suspend fun removeSuperAdmin(username: String) {
        userDao.deleteByUsername(username)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteAccount(wsId, username)
        }
    }

    suspend fun resetAdminPasswordWithRecovery(
        recoveryPassword: String,
        targetAdminUsername: String? = null,
        newPassword: String,
        newFinalCode: String? = null
    ): Result<String> {
        if (AuthHasher.sha256(recoveryPassword.trim()) != AuthHasher.sha256(DEFAULT_ADMIN_RECOVERY_PASSWORD)) {
            return Result.failure(IllegalArgumentException("Incorrect default recovery password."))
        }
        val cleanNewPass = newPassword
        AuthPolicy.evaluatePassword(cleanNewPass).firstMissingRequirement()?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        if (!newFinalCode.isNullOrBlank() && newFinalCode.length < 4) {
            return Result.failure(IllegalArgumentException("Code must be at least 4 characters."))
        }
        val allUsers = userDao.getAll()
        val adminAccount = targetAdminUsername
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { uname ->
                allUsers.firstOrNull { it.username.equals(uname, ignoreCase = true) && it.role.equals("admin", ignoreCase = true) }
            }
            ?: allUsers.firstOrNull { it.role.equals("admin", ignoreCase = true) }
            ?: return Result.failure(IllegalStateException("Admin account not found."))

        val updatedAdmin = adminAccount.copy(passwordHash = AuthHasher.sha256(cleanNewPass))
        userDao.upsert(updatedAdmin)
        resetAdminLoginSecurityState()
        resetLoginSecurityState()
        if (!newFinalCode.isNullOrBlank()) {
            configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, AuthHasher.sha256(newFinalCode)))
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushAccount(wsId, updatedAdmin)
        }
        return Result.success(updatedAdmin.username)
    }

    suspend fun updateAdminCredentials(
        currentPassword: String,
        newPassword: String,
        newVerifyCode: String
    ): Result<Unit> {
        val allUsers = userDao.getAll()
        val adminAccount = allUsers.firstOrNull { normalizeRole(it.role) == "admin" }
            ?: return Result.failure(IllegalStateException("Admin account not found."))
        if (adminAccount.passwordHash != AuthHasher.sha256(currentPassword)) {
            return Result.failure(IllegalArgumentException("Incorrect current Admin password."))
        }
        val cleanNewCode = newVerifyCode.trim()
        if (newPassword.isEmpty() && cleanNewCode.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a new password and/or new 2-Way Verification Code."))
        }
        if (newPassword.isNotEmpty()) {
            AuthPolicy.evaluatePassword(newPassword).firstMissingRequirement()?.let { msg ->
                return Result.failure(IllegalArgumentException(msg))
            }
        }
        if (cleanNewCode.isNotEmpty() && cleanNewCode.length < 4) {
            return Result.failure(IllegalArgumentException("2-Way Verification Code must be at least 4 characters."))
        }
        val updatedAdmin = if (newPassword.isNotEmpty()) {
            adminAccount.copy(passwordHash = AuthHasher.sha256(newPassword))
        } else {
            adminAccount
        }
        db.withTransaction {
            if (newPassword.isNotEmpty()) {
                userDao.upsert(updatedAdmin)
            }
            if (cleanNewCode.isNotEmpty()) {
                configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, AuthHasher.sha256(cleanNewCode)))
            }
        }
        if (newPassword.isNotEmpty()) {
            val wsId = getActiveWorkspaceId()
            if (wsId.isNotBlank()) {
                cloudSync?.pushAccount(wsId, updatedAdmin)
            }
        }
        return Result.success(Unit)
    }

    private fun formatStockValue(qty: Double): String =
        if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format(Locale.US, "%.2f", qty)

    suspend fun validateStockForBill(
        items: List<InvoiceItem>,
        existingBillId: String,
        docType: DocumentType
    ): Result<Map<String, Double>> {
        if (docType != DocumentType.BILL) {
            return Result.success(emptyMap())
        }
        val requestedByName = linkedMapOf<String, Double>()
        items.forEach { item ->
            val key = item.description.trim().lowercase(Locale.ROOT)
            if (key.isNotEmpty() && item.qty > 0.0) {
                requestedByName[key] = (requestedByName[key] ?: 0.0) + item.qty
            }
        }
        val existingBill = if (existingBillId.isBlank()) {
            null
        } else if (_volatileCloudActive.value) {
            _volatileBills.value.firstOrNull { it.id == existingBillId }
        } else {
            billDao.findById(existingBillId)
        }
        val oldByName = billedQtyByProductName(existingBill)
        val allKeys = requestedByName.keys + oldByName.keys
        if (allKeys.isEmpty()) {
            return Result.success(emptyMap())
        }

        val currentProducts = (if (_volatileCloudActive.value) {
            _volatileProducts.value
        } else {
            productDao.getAll()
        }).filter { !it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
        val byLowerName = currentProducts.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val docDeltas = linkedMapOf<String, Double>()

        for (key in allKeys) {
            val product = byLowerName[key] ?: continue
            val tracked = if (_volatileCloudActive.value) {
                product.stockQty > 0.0 || product.minStockAlert > 0.0
            } else {
                isStockTrackedFor(product)
            }
            if (!tracked) continue

            val newQty = requestedByName[key] ?: 0.0
            val oldQty = oldByName[key] ?: 0.0
            val deltaNeeded = newQty - oldQty
            if (deltaNeeded > 1e-6 && product.stockQty + 1e-6 < deltaNeeded) {
                val effectiveAvail = product.stockQty + oldQty
                return Result.failure(
                    IllegalStateException(
                        "Insufficient stock for ${product.name}! Available: ${formatStockValue(effectiveAvail)} ${product.unit}"
                    )
                )
            }
            if (product.id > 0L && kotlin.math.abs(deltaNeeded) > 1e-6) {
                docDeltas["p_${product.id}"] = deltaNeeded
            }
        }
        return Result.success(docDeltas)
    }

    suspend fun saveBill(bill: BillEntity, customerLocationUrl: String = ""): Result<Unit> {
        val wsId = getActiveWorkspaceId()
        val docType = DocumentType.fromString(bill.documentType)
        val parsedItems = InvoiceItemJson.fromJson(bill.itemsJson)
        val existingBeforeSave = if (_volatileCloudActive.value) {
            _volatileBills.value.firstOrNull { it.id == bill.id }
        } else {
            billDao.findById(bill.id)
        }
        val skipStockValidation = existingBeforeSave == null && bill.stockDeducted
        val docDeltas = if (skipStockValidation) {
            emptyMap()
        } else {
            val stockValidation = validateStockForBill(parsedItems, bill.id, docType)
            if (stockValidation.isFailure) {
                return Result.failure(stockValidation.exceptionOrNull()!!)
            }
            stockValidation.getOrDefault(emptyMap())
        }

        val normalizedPaid = bill.paidAmount.coerceAtLeast(0.0)
        val normalizedDue = (bill.grandTotal.coerceAtLeast(0.0) - normalizedPaid).coerceAtLeast(0.0)
        val computedPayStatus = if (bill.paymentStatus.isNotBlank()) {
            bill.paymentStatus
        } else {
            BillPaymentStatus.compute(bill.grandTotal, normalizedPaid).code
        }
        val targetStockDeducted = docType == DocumentType.BILL

        if (_volatileCloudActive.value) {
            // Technician / non-Admin cloud device: NEVER write company/customer/bill data to Room SQLite!
            val existing = _volatileBills.value.firstOrNull { it.id == bill.id }
            val prepped = bill.copy(
                paidAmount = normalizedPaid,
                dueAmount = normalizedDue,
                paymentStatus = computedPayStatus,
                stockDeducted = existing?.stockDeducted == true || bill.stockDeducted || targetStockDeducted
            )
            val safeBill = if (existing != null && existing.staffRating in 1..5) {
                prepped.copy(staffRating = existing.staffRating, staffReviewNote = existing.staffReviewNote)
            } else {
                prepped
            }
            val shouldDeductLocally = !(existing == null && bill.stockDeducted)
            if (wsId.isNotBlank() && cloudSync != null && docType == DocumentType.BILL && docDeltas.isNotEmpty() && shouldDeductLocally) {
                val txRes = cloudSync.commitBillWithAtomicStockTransaction(wsId, safeBill, docDeltas)
                if (txRes.isFailure) {
                    return txRes
                }
            } else if (wsId.isNotBlank()) {
                cloudSync?.pushBill(wsId, safeBill)
            }
            if (shouldDeductLocally) {
                adjustVolatileStockForBillChange(existing, safeBill, wsId, pushToCloud = docDeltas.isEmpty())
            }
            _volatileBills.value = (listOf(safeBill) + _volatileBills.value.filterNot { it.id == safeBill.id })
                .sortedByDescending { it.updatedAt }

            val custName = bill.customerName.trim()
            if (custName.isNotEmpty()) {
                val (parsedPhone, parsedAddress) = parsePhoneAndAddress(bill.customerDetails)
                val old = findMatchingCustomersByPhone(_volatileCustomers.value, parsedPhone).firstOrNull()
                    ?: _volatileCustomers.value.firstOrNull { it.name.equals(custName, ignoreCase = true) }
                val cleanLoc = customerLocationUrl.trim()
                val syncedCust = if (old != null) {
                    old.copy(
                        name = custName.ifBlank { old.name },
                        details = bill.customerDetails.ifBlank { old.details },
                        phone = parsedPhone.ifBlank { old.phone },
                        address = parsedAddress.ifBlank { old.address },
                        locationUrl = cleanLoc.ifBlank { old.locationUrl }
                    )
                } else {
                    CustomerEntity(
                        id = (custName.hashCode().toLong() and 0x7FFFFFFFL).coerceAtLeast(1L),
                        name = custName,
                        details = bill.customerDetails,
                        phone = parsedPhone,
                        address = parsedAddress,
                        locationUrl = cleanLoc
                    )
                }
                if (wsId.isNotBlank()) {
                    cloudSync?.pushCustomer(wsId, syncedCust)
                }
                _volatileCustomers.value = (listOf(syncedCust) + _volatileCustomers.value.filterNot {
                    it.id == syncedCust.id || it.name.equals(syncedCust.name, ignoreCase = true)
                })
            }
            if (safeBill.helpRequestId > 0L) {
                linkBillToHelpRequest(safeBill.helpRequestId, safeBill.id, docType)
            }
            return Result.success(Unit)
        }

        val existing = billDao.findById(bill.id)
        val alreadyServerDeductedOnWebhook = existing == null && bill.stockDeducted
        val prepped = bill.copy(
            paidAmount = normalizedPaid,
            dueAmount = normalizedDue,
            paymentStatus = computedPayStatus,
            stockDeducted = existing?.stockDeducted == true || bill.stockDeducted || targetStockDeducted
        )
        val safeBill = if (existing != null && existing.staffRating in 1..5) {
            prepped.copy(
                staffRating = existing.staffRating,
                staffReviewNote = existing.staffReviewNote
            )
        } else {
            prepped
        }
        var atomicCloudDone = false
        if (!alreadyServerDeductedOnWebhook && wsId.isNotBlank() && cloudSync != null && docType == DocumentType.BILL && docDeltas.isNotEmpty()) {
            val txRes = cloudSync.commitBillWithAtomicStockTransaction(wsId, safeBill, docDeltas)
            if (txRes.isFailure) {
                return txRes
            }
            atomicCloudDone = true
        }
        val modifiedProducts = if (!alreadyServerDeductedOnWebhook) {
            adjustLocalStockForBillChange(existing, safeBill)
        } else {
            emptyList()
        }
        billDao.upsert(safeBill)
        if (wsId.isNotBlank() && !atomicCloudDone) {
            cloudSync?.pushBill(wsId, safeBill)
            modifiedProducts.forEach { cloudSync?.pushProduct(wsId, it) }
        }
        val custName = bill.customerName.trim()
        if (custName.isNotEmpty()) {
            val (parsedPhone, parsedAddress) = parsePhoneAndAddress(bill.customerDetails)
            val allCustomers = customerDao.getAll()
            val old = findMatchingCustomersByPhone(allCustomers, parsedPhone).firstOrNull()
                ?: customerDao.findByName(custName)
            val cleanLoc = customerLocationUrl.trim()
            val custEntity = if (old != null) {
                old.copy(
                    name = custName.ifBlank { old.name },
                    details = bill.customerDetails.ifBlank { old.details },
                    phone = parsedPhone.ifBlank { old.phone },
                    address = parsedAddress.ifBlank { old.address },
                    locationUrl = cleanLoc.ifBlank { old.locationUrl }
                )
            } else {
                CustomerEntity(
                    name = custName,
                    details = bill.customerDetails,
                    phone = parsedPhone,
                    address = parsedAddress,
                    locationUrl = cleanLoc
                )
            }
            customerDao.upsert(custEntity)
            if (wsId.isNotBlank()) {
                cloudSync?.pushCustomer(wsId, custEntity)
            }
        }
        if (safeBill.helpRequestId > 0L) {
            linkBillToHelpRequest(safeBill.helpRequestId, safeBill.id, docType)
        }
        return Result.success(Unit)
    }

    private fun billedQtyByProductName(bill: BillEntity?): Map<String, Double> {
        if (bill == null || DocumentType.fromString(bill.documentType) != DocumentType.BILL) return emptyMap()
        val totals = linkedMapOf<String, Double>()
        InvoiceItemJson.fromJson(bill.itemsJson).forEach { item ->
            val key = item.description.trim().lowercase(Locale.ROOT)
            if (key.isNotEmpty() && item.qty > 0.0) {
                totals[key] = (totals[key] ?: 0.0) + item.qty
            }
        }
        return totals
    }

    private suspend fun isStockTrackedFor(product: ProductEntity): Boolean {
        if (product.stockQty > 0.0 || product.minStockAlert > 0.0) return true
        val key = "$KEY_STOCK_TRACKED_PREFIX${product.name.trim().lowercase(Locale.ROOT)}"
        return configDao.getValue(key) == "1"
    }

    private suspend fun markStockTrackedIfNeeded(product: ProductEntity) {
        if (product.stockQty > 0.0 || product.minStockAlert > 0.0) {
            val key = "$KEY_STOCK_TRACKED_PREFIX${product.name.trim().lowercase(Locale.ROOT)}"
            configDao.setValue(AppConfigEntity(key, "1"))
        }
    }

    private suspend fun adjustLocalStockForBillChange(oldBill: BillEntity?, newBill: BillEntity?): List<ProductEntity> {
        val oldMap = billedQtyByProductName(oldBill)
        val newMap = billedQtyByProductName(newBill)
        val keys = oldMap.keys + newMap.keys
        if (keys.isEmpty()) return emptyList()

        val allProducts = productDao.getAll()
            .filter { !it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
        val byLowerName = allProducts.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val updatedList = mutableListOf<ProductEntity>()
        for (k in keys) {
            val delta = (oldMap[k] ?: 0.0) - (newMap[k] ?: 0.0)
            if (kotlin.math.abs(delta) < 1e-6) continue
            val product = byLowerName[k] ?: continue
            val tracked = isStockTrackedFor(product)
            if (!tracked) continue
            markStockTrackedIfNeeded(product)
            val updatedQty = (product.stockQty + delta).coerceAtLeast(0.0)
            if (kotlin.math.abs(updatedQty - product.stockQty) >= 1e-6) {
                val updatedProduct = product.copy(stockQty = updatedQty)
                productDao.upsert(updatedProduct)
                updatedList += updatedProduct
            }
        }
        return updatedList
    }

    private suspend fun adjustVolatileStockForBillChange(
        oldBill: BillEntity?,
        newBill: BillEntity?,
        wsId: String,
        pushToCloud: Boolean = true
    ) {
        val oldMap = billedQtyByProductName(oldBill)
        val newMap = billedQtyByProductName(newBill)
        val keys = oldMap.keys + newMap.keys
        if (keys.isEmpty()) return

        val currentProducts = _volatileProducts.value.toMutableList()
        var changed = false
        for (k in keys) {
            val delta = (oldMap[k] ?: 0.0) - (newMap[k] ?: 0.0)
            if (kotlin.math.abs(delta) < 1e-6) continue
            val idx = currentProducts.indexOfFirst {
                !it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) &&
                    it.name.trim().lowercase(Locale.ROOT) == k
            }
            if (idx < 0) continue
            val product = currentProducts[idx]
            if (product.stockQty <= 0.0 && product.minStockAlert <= 0.0) continue
            val updatedQty = (product.stockQty + delta).coerceAtLeast(0.0)
            if (kotlin.math.abs(updatedQty - product.stockQty) >= 1e-6) {
                val updatedProduct = product.copy(stockQty = updatedQty)
                currentProducts[idx] = updatedProduct
                changed = true
                if (pushToCloud && wsId.isNotBlank()) {
                    cloudSync?.pushProductStockAdjustment(wsId, updatedProduct)
                }
            }
        }
        if (changed) {
            _volatileProducts.value = currentProducts
        }
    }

    suspend fun deleteBill(
        id: String,
        actorUsername: String = "Admin",
        actorRole: String = "admin",
        forceAdmin: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        // Strict Admin-only bill deletion guard (Technicians and Super Admins cannot delete bills)
        if (normalizeRole(actorRole) != "admin" && !forceAdmin) {
            return false
        }
        val existing = billDao.findById(id) ?: return true
        if (existing.staffRating in 1..5 && !forceAdmin) {
            return false
        }
        val trashItem = TrashItemEntity(
            id = "tr_bill_${existing.id}_$nowMillis",
            entityType = ENTITY_BILL,
            entityId = existing.id,
            title = "${existing.invoiceNo} — ${existing.customerName.ifBlank { "Customer" }}",
            summary = "${DocumentType.fromString(existing.documentType).label} · Total ₹${String.format(Locale.US, "%.2f", existing.grandTotal)}",
            payloadJson = billToJson(existing).toString(),
            deletedBy = actorUsername.trim().ifBlank { "Admin" },
            deletedByRole = normalizeRole(actorRole),
            status = "TRASHED",
            deletedAt = nowMillis,
            expireAt = nowMillis + TRASH_RETENTION_MS
        )
        val audit = buildAuditLog(
            action = "DELETE",
            entityType = ENTITY_BILL,
            entityId = existing.id,
            title = trashItem.title,
            actorUsername = trashItem.deletedBy,
            actorRole = trashItem.deletedByRole,
            details = "Moved to Trash (auto-deletes in 24h)",
            createdAt = nowMillis
        )
        var restoredProducts: List<ProductEntity> = emptyList()
        db.withTransaction {
            restoredProducts = adjustLocalStockForBillChange(oldBill = existing, newBill = null)
            billDao.deleteById(id)
            trashDao.upsert(trashItem)
            auditDao.insert(audit)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteBill(wsId, id)
            cloudSync?.pushTrashItem(wsId, trashItem)
            cloudSync?.pushAuditLog(wsId, audit)
            restoredProducts.forEach { cloudSync?.pushProduct(wsId, it) }
        }
        return true
    }

    suspend fun getBillById(id: String): BillEntity? {
        if (_volatileCloudActive.value) {
            return _volatileBills.value.firstOrNull { it.id == id }
        }
        return billDao.findById(id)
    }

    suspend fun updateBillSettlementAndReview(
        billId: String,
        discount: Double,
        grandTotal: Double,
        paidAmount: Double,
        dueAmount: Double,
        staffRating: Int,
        staffReviewNote: String
    ): BillEntity? {
        val existing = billDao.findById(billId) ?: return null
        val lockedRating = existing.staffRating in 1..5
        val safeGrand = grandTotal.coerceAtLeast(0.0)
        val safePaid = paidAmount.coerceAtLeast(0.0)
        val safeDue = dueAmount.coerceAtLeast(0.0)
        val updated = existing.copy(
            discount = discount.coerceAtLeast(0.0),
            grandTotal = safeGrand,
            paidAmount = safePaid,
            dueAmount = safeDue,
            paymentStatus = BillPaymentStatus.compute(safeGrand, safePaid).code,
            staffRating = if (lockedRating) existing.staffRating else staffRating.coerceIn(0, 5),
            staffReviewNote = if (lockedRating) existing.staffReviewNote else staffReviewNote.trim(),
            updatedAt = System.currentTimeMillis()
        )
        billDao.upsert(updated)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushBill(wsId, updated)
        }
        return updated
    }

    suspend fun recordTechnicianSettlement(
        technicianUsername: String,
        amount: Double,
        note: String,
        recordedBy: String
    ): Result<Unit> {
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter a settlement amount greater than zero"))
        }
        val entity = TechnicianSettlementEntity(
            technicianUsername = technicianUsername.trim(),
            amount = amount,
            note = note.trim(),
            recordedBy = recordedBy.trim(),
            createdAt = System.currentTimeMillis()
        )
        val rowId = settlementDao.insert(entity)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushSettlement(wsId, entity.copy(id = rowId))
        }
        return Result.success(Unit)
    }

    suspend fun saveCustomer(
        name: String,
        details: String,
        phone: String = "",
        address: String = "",
        locationUrl: String = "",
        id: Long = 0L
    ) {
        val clean = name.trim()
        val cleanPhone = phone.trim()
        val cleanAddress = address.trim()
        val cleanLoc = locationUrl.trim()
        val combinedDetails = details.trim().ifBlank {
            listOf(cleanAddress, cleanPhone).filter { it.isNotBlank() }.joinToString("\n")
        }
        val (fallbackPhone, fallbackAddress) = parsePhoneAndAddress(combinedDetails)
        val finalPhone = cleanPhone.ifBlank { fallbackPhone }
        val finalAddress = cleanAddress.ifBlank { fallbackAddress }

        val savedEntity: CustomerEntity = if (id > 0L) {
            val entity = CustomerEntity(
                id = id,
                name = clean,
                details = combinedDetails,
                phone = finalPhone,
                address = finalAddress,
                locationUrl = cleanLoc
            )
            customerDao.upsert(entity)
            entity
        } else {
            val existing = findMatchingCustomersByPhone(customerDao.getAll(), finalPhone).firstOrNull()
                ?: customerDao.findByName(clean)
            if (existing != null) {
                val updated = existing.copy(
                    name = clean.ifBlank { existing.name },
                    details = combinedDetails.ifBlank { existing.details },
                    phone = finalPhone.ifBlank { existing.phone },
                    address = finalAddress.ifBlank { existing.address },
                    locationUrl = cleanLoc.ifBlank { existing.locationUrl }
                )
                customerDao.upsert(updated)
                updated
            } else {
                val created = CustomerEntity(
                    name = clean,
                    details = combinedDetails,
                    phone = finalPhone,
                    address = finalAddress,
                    locationUrl = cleanLoc
                )
                val rowId = customerDao.upsert(created)
                created.copy(id = rowId)
            }
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushCustomer(wsId, savedEntity)
        }
    }

    private fun parsePhoneAndAddress(details: String): Pair<String, String> {
        val lines = details.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return "" to ""
        val phoneLine = lines.firstOrNull { line ->
            val digits = line.count { it.isDigit() }
            digits in 7..15 && line.length <= 24
        }
        val addressLines = lines.filter { it != phoneLine }
        return (phoneLine ?: "") to addressLines.joinToString(", ")
    }

    suspend fun deleteCustomer(
        id: Long,
        actorUsername: String = "Admin",
        actorRole: String = "admin",
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val existing = customerDao.findById(id) ?: customerDao.getAll().firstOrNull { it.id == id } ?: return
        val trashItem = TrashItemEntity(
            id = "tr_cust_${existing.id}_$nowMillis",
            entityType = ENTITY_CUSTOMER,
            entityId = existing.id.toString(),
            title = existing.name,
            summary = existing.phone.ifBlank { existing.address.ifBlank { "Saved Customer" } },
            payloadJson = customerToJson(existing).toString(),
            deletedBy = actorUsername.trim().ifBlank { "Admin" },
            deletedByRole = normalizeRole(actorRole),
            status = "TRASHED",
            deletedAt = nowMillis,
            expireAt = nowMillis + TRASH_RETENTION_MS
        )
        val audit = buildAuditLog(
            action = "DELETE",
            entityType = ENTITY_CUSTOMER,
            entityId = existing.id.toString(),
            title = existing.name,
            actorUsername = trashItem.deletedBy,
            actorRole = trashItem.deletedByRole,
            details = "Customer moved to Trash (24h retention)",
            createdAt = nowMillis
        )
        db.withTransaction {
            customerDao.deleteById(id)
            trashDao.upsert(trashItem)
            auditDao.insert(audit)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteCustomer(wsId, existing)
            cloudSync?.pushTrashItem(wsId, trashItem)
            cloudSync?.pushAuditLog(wsId, audit)
        }
    }

    suspend fun saveHelpRequest(
        id: Long = 0L,
        customerName: String,
        phone: String,
        address: String,
        locationUrl: String,
        helpDescription: String,
        status: String = "OPEN",
        category: String = HelpCategory.REGULAR_SERVICE.code,
        quotationStatus: String = "",
        installationDate: String = "",
        assignedTechnician: String = ""
    ): CustomerHelpEntity {
        val existingCustomers = if (_volatileCloudActive.value) _volatileCustomers.value else customerDao.getAll()
        val matched = findMatchingCustomersByPhone(existingCustomers, phone).firstOrNull()
        val resolvedName = customerName.trim().ifBlank { matched?.name ?: "" }
        val resolvedPhone = if (matched != null) {
            val matchedPhone = matched.phone.ifBlank { parsePhoneAndAddress(matched.details).first }
            val inputDigits = phone.filter { it.isDigit() }
            if (inputDigits.length <= 5 && matchedPhone.isNotBlank()) matchedPhone else phone.trim().ifBlank { matchedPhone }
        } else {
            phone.trim()
        }
        val resolvedAddress = address.trim().ifBlank {
            matched?.let { it.address.ifBlank { parsePhoneAndAddress(it.details).second.ifBlank { it.details } } } ?: ""
        }
        val resolvedLocation = locationUrl.trim().ifBlank { matched?.locationUrl ?: "" }
        val now = System.currentTimeMillis()
        val existingHelp = if (id > 0L) {
            if (_volatileCloudActive.value) _volatileHelpRequests.value.firstOrNull { it.id == id }
            else helpDao.findById(id)
        } else {
            null
        }
        val normalizedCat = HelpCategory.fromString(category).code
        val resolvedQuotStatus = quotationStatus.ifBlank {
            existingHelp?.quotationStatus ?: if (normalizedCat == HelpCategory.QUOTATION.code) "PENDING" else ""
        }
        val resolvedInstallDate = installationDate.trim().ifBlank { existingHelp?.installationDate ?: "" }
        val resolvedAssignedTech = assignedTechnician.trim().ifBlank { existingHelp?.assignedTechnician ?: "" }

        if (_volatileCloudActive.value) {
            val volatileId = if (id > 0L) id else now
            val saved = CustomerHelpEntity(
                id = volatileId,
                customerName = resolvedName,
                phone = resolvedPhone,
                address = resolvedAddress,
                locationUrl = resolvedLocation,
                helpDescription = helpDescription.trim(),
                status = status.ifBlank { "OPEN" },
                createdAt = existingHelp?.createdAt ?: now,
                updatedAt = now,
                acceptedBy = existingHelp?.acceptedBy ?: "",
                acceptedByName = existingHelp?.acceptedByName ?: "",
                acceptedByPhone = existingHelp?.acceptedByPhone ?: "",
                acceptedAt = existingHelp?.acceptedAt ?: 0L,
                solvedAt = existingHelp?.solvedAt ?: 0L,
                billId = existingHelp?.billId ?: "",
                category = normalizedCat,
                quotationStatus = resolvedQuotStatus,
                installationDate = resolvedInstallDate,
                installationReminderSent = existingHelp?.installationReminderSent ?: false,
                rejectedReason = existingHelp?.rejectedReason ?: "",
                assignedTechnician = resolvedAssignedTech
            )
            _volatileHelpRequests.value = (listOf(saved) + _volatileHelpRequests.value.filterNot { it.id == saved.id })
                .sortedByDescending { it.createdAt }
            val wsId = getActiveWorkspaceId()
            if (wsId.isNotBlank()) {
                cloudSync?.pushHelpRequest(wsId, saved, isTechnicianStatusUpdate = false)
            }
            return saved
        }

        val entity = CustomerHelpEntity(
            id = id,
            customerName = resolvedName,
            phone = resolvedPhone,
            address = resolvedAddress,
            locationUrl = resolvedLocation,
            helpDescription = helpDescription.trim(),
            status = status.ifBlank { "OPEN" },
            createdAt = existingHelp?.createdAt ?: now,
            updatedAt = now,
            acceptedBy = existingHelp?.acceptedBy ?: "",
            acceptedByName = existingHelp?.acceptedByName ?: "",
            acceptedByPhone = existingHelp?.acceptedByPhone ?: "",
            acceptedAt = existingHelp?.acceptedAt ?: 0L,
            solvedAt = existingHelp?.solvedAt ?: 0L,
            billId = existingHelp?.billId ?: "",
            category = normalizedCat,
            quotationStatus = resolvedQuotStatus,
            installationDate = resolvedInstallDate,
            installationReminderSent = existingHelp?.installationReminderSent ?: false,
            rejectedReason = existingHelp?.rejectedReason ?: "",
            assignedTechnician = resolvedAssignedTech
        )
        val rowId = helpDao.upsert(entity)
        val saved = if (entity.id > 0L) entity else entity.copy(id = rowId)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, saved, isTechnicianStatusUpdate = false)
        }
        return saved
    }

    private fun generateHelpTokenId(requestId: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val suffix = ((requestId * 37L + (nowMillis % 10000L)) % 900000L + 100000L)
        return "TS-HT-$suffix"
    }

    suspend fun approveQuotationWithInstallationDate(
        requestId: Long,
        installationDate: String,
        assignedTechnician: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        quotationId: String = "",
        approvedQuotationJson: String = ""
    ): Result<CustomerHelpEntity> {
        val cleanDate = installationDate.trim()
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(cleanDate)) {
            return Result.failure(IllegalArgumentException("Enter valid Installation Date (yyyy-MM-dd)."))
        }
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Quotation request not found."))

        val cleanToken = current.helpTokenId.ifBlank { generateHelpTokenId(current.id, nowMillis) }
        val cleanQuotationId = quotationId.trim().ifBlank { current.quotationId.ifBlank { "QT-${current.id}" } }
        val cleanQuotationJson = approvedQuotationJson.trim().ifBlank { current.approvedQuotationJson }
        val updated = current.copy(
            category = HelpCategory.INSTALLATION.code,
            quotationStatus = "APPROVED",
            installationDate = cleanDate,
            installationReminderSent = false,
            assignedTechnician = assignedTechnician.trim().ifBlank { current.assignedTechnician },
            helpTokenId = cleanToken,
            quotationId = cleanQuotationId,
            approvedQuotationJson = cleanQuotationJson,
            status = HelpWorkflowStatus.OPEN,
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = false)
        }
        return Result.success(updated)
    }

    suspend fun postponeHelpRequest(
        requestId: Long,
        technicianUsername: String,
        reason: String,
        newInstallationDate: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Service request not found."))

        val updated = current.copy(
            status = HelpWorkflowStatus.POSTPONED,
            rejectedReason = reason.trim().ifBlank { "Postponed by ${technicianUsername.ifBlank { "Technician" }}" },
            installationDate = newInstallationDate.trim().ifBlank { current.installationDate },
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = true)
        }
        return Result.success(updated)
    }

    suspend fun startInstallationWork(
        requestId: Long,
        technicianUsername: String,
        isAdmin: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Installation request not found."))

        if (!isAdmin && current.acceptedBy.isNotBlank() &&
            !current.acceptedBy.equals(technicianUsername, ignoreCase = true)
        ) {
            return Result.failure(IllegalStateException("Only the assigned technician can start installation."))
        }
        val updated = current.copy(
            status = HelpWorkflowStatus.INSTALLATION_IN_PROGRESS,
            acceptedBy = current.acceptedBy.ifBlank { technicianUsername },
            acceptedAt = if (current.acceptedAt > 0L) current.acceptedAt else nowMillis,
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = !isAdmin)
        }
        return Result.success(updated)
    }

    suspend fun completeInstallationWork(
        requestId: Long,
        technicianUsername: String,
        isAdmin: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Installation request not found."))

        if (!isAdmin && current.acceptedBy.isNotBlank() &&
            !current.acceptedBy.equals(technicianUsername, ignoreCase = true)
        ) {
            return Result.failure(IllegalStateException("Only the assigned technician can complete installation."))
        }
        val updated = current.copy(
            status = HelpWorkflowStatus.INSTALLATION_COMPLETED,
            solvedAt = if (current.solvedAt == 0L) nowMillis else current.solvedAt,
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = !isAdmin)
        }
        return Result.success(updated)
    }

    suspend fun rejectQuotation(
        requestId: Long,
        reason: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Quotation request not found."))

        val updated = current.copy(
            quotationStatus = "REJECTED",
            status = HelpWorkflowStatus.REJECTED,
            rejectedReason = reason.trim(),
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = false)
        }
        return Result.success(updated)
    }

    suspend fun rejectHelpRequest(
        requestId: Long,
        actorUsername: String,
        isAdmin: Boolean,
        reason: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Request not found."))

        val updated = current.copy(
            status = HelpWorkflowStatus.REJECTED,
            rejectedReason = reason.trim().ifBlank { "Rejected by ${actorUsername.ifBlank { "Technician" }}" },
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = !isAdmin)
        }
        return Result.success(updated)
    }

    suspend fun markInstallationReminderSent(requestId: Long) {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return
        if (current.installationReminderSent) return
        val updated = current.copy(
            installationReminderSent = true,
            updatedAt = System.currentTimeMillis()
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank() && !_volatileCloudActive.value) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = false)
        }
    }

    suspend fun acceptHelpRequest(
        requestId: Long,
        technician: UserSession,
        acceptedAtMillis: Long = System.currentTimeMillis()
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Help request not found"))

        if (current.acceptedBy.isNotBlank() &&
            !current.acceptedBy.equals(technician.username, ignoreCase = true)
        ) {
            return Result.failure(IllegalStateException("Request already accepted by another technician"))
        }
        val account = if (_volatileCloudActive.value) {
            _volatileAccounts.value.firstOrNull { it.username.equals(technician.username, ignoreCase = true) }
        } else {
            userDao.findByUsername(technician.username)
        }
        val techPhone = account?.phone?.takeIf { it.isNotBlank() } ?: technician.phone
        val updated = current.copy(
            status = HelpWorkflowStatus.ACCEPTED,
            acceptedBy = technician.username,
            acceptedByName = technician.displayName.ifBlank { technician.username },
            acceptedByPhone = techPhone,
            acceptedAt = if (current.acceptedAt > 0L) current.acceptedAt else acceptedAtMillis,
            updatedAt = acceptedAtMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = !technician.isAdmin)
        }
        return Result.success(updated)
    }

    suspend fun markHelpSolved(
        requestId: Long,
        actorUsername: String,
        isAdmin: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        enforce15MinWait: Boolean = true
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Help request not found"))

        if (!isAdmin && current.acceptedBy.isNotBlank() &&
            !current.acceptedBy.equals(actorUsername, ignoreCase = true)
        ) {
            return Result.failure(IllegalStateException("Only the assigned technician can resolve this request"))
        }
        if (enforce15MinWait && !isAdmin && current.acceptedAt > 0L) {
            val elapsed = nowMillis - current.acceptedAt
            if (elapsed < HELP_SOLVE_WAIT_MS) {
                val remMin = ((HELP_SOLVE_WAIT_MS - elapsed + 59_999L) / 60_000L).coerceAtLeast(1L)
                return Result.failure(IllegalStateException("Please wait $remMin more min(s) before marking Solved"))
            }
        }
        val updated = current.copy(
            status = HelpWorkflowStatus.SOLVED,
            solvedAt = nowMillis,
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = !isAdmin)
        }
        return Result.success(updated)
    }

    suspend fun markHelpNotSolved(
        requestId: Long,
        actorUsername: String,
        isAdmin: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        enforce15MinWait: Boolean = true
    ): Result<CustomerHelpEntity> {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == requestId }
        } else {
            helpDao.findById(requestId)
        } ?: return Result.failure(IllegalStateException("Help request not found"))

        if (!isAdmin && current.acceptedBy.isNotBlank() &&
            !current.acceptedBy.equals(actorUsername, ignoreCase = true)
        ) {
            return Result.failure(IllegalStateException("Only the assigned technician can update this request"))
        }
        if (enforce15MinWait && !isAdmin && current.acceptedAt > 0L) {
            val elapsed = nowMillis - current.acceptedAt
            if (elapsed < HELP_SOLVE_WAIT_MS) {
                val remMin = ((HELP_SOLVE_WAIT_MS - elapsed + 59_999L) / 60_000L).coerceAtLeast(1L)
                return Result.failure(IllegalStateException("Please wait $remMin more min(s) before marking Not Solved"))
            }
        }
        val updated = current.copy(
            status = HelpWorkflowStatus.NOT_SOLVED,
            updatedAt = nowMillis
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = !isAdmin)
        }
        return Result.success(updated)
    }

    suspend fun linkBillToHelpRequest(
        helpRequestId: Long,
        billId: String,
        docType: DocumentType = DocumentType.BILL
    ) {
        val current = if (_volatileCloudActive.value) {
            _volatileHelpRequests.value.firstOrNull { it.id == helpRequestId }
        } else {
            helpDao.findById(helpRequestId)
        } ?: return
        val nextStatus = if (docType == DocumentType.BILL) {
            if (current.category == HelpCategory.INSTALLATION.code || current.helpTokenId.isNotBlank()) {
                HelpWorkflowStatus.BILLED
            } else if (current.status == HelpWorkflowStatus.OPEN || current.status == HelpWorkflowStatus.ACCEPTED) {
                HelpWorkflowStatus.SOLVED
            } else {
                current.status
            }
        } else {
            current.status
        }
        val updated = current.copy(
            billId = if (docType == DocumentType.BILL) billId else current.billId,
            quotationId = if (docType == DocumentType.QUOTATION) billId else current.quotationId,
            status = nextStatus,
            solvedAt = if (docType == DocumentType.BILL && current.solvedAt == 0L) System.currentTimeMillis() else current.solvedAt,
            updatedAt = System.currentTimeMillis()
        )
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = _volatileHelpRequests.value.map { if (it.id == updated.id) updated else it }
        } else {
            helpDao.upsert(updated)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = _volatileCloudActive.value)
        }
    }

    suspend fun updateHelpStatus(request: CustomerHelpEntity, newStatus: String) {
        val updated = request.copy(
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )
        helpDao.upsert(updated)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushHelpRequest(wsId, updated, isTechnicianStatusUpdate = false)
        }
    }

    suspend fun deleteHelpRequest(
        id: Long,
        actorUsername: String = "Admin",
        actorRole: String = "admin",
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val existing = helpDao.findById(id)
        if (existing == null) {
            helpDao.deleteById(id)
            return
        }
        val trashItem = TrashItemEntity(
            id = "tr_help_${existing.id}_$nowMillis",
            entityType = ENTITY_HELP,
            entityId = existing.id.toString(),
            title = "Help #${existing.id} — ${existing.customerName.ifBlank { existing.phone }}",
            summary = existing.helpDescription.take(80),
            payloadJson = helpToJson(existing).toString(),
            deletedBy = actorUsername.trim().ifBlank { "Admin" },
            deletedByRole = normalizeRole(actorRole),
            status = "TRASHED",
            deletedAt = nowMillis,
            expireAt = nowMillis + TRASH_RETENTION_MS
        )
        val audit = buildAuditLog(
            action = "DELETE",
            entityType = ENTITY_HELP,
            entityId = existing.id.toString(),
            title = trashItem.title,
            actorUsername = trashItem.deletedBy,
            actorRole = trashItem.deletedByRole,
            details = "Help request moved to Trash (24h retention)",
            createdAt = nowMillis
        )
        db.withTransaction {
            helpDao.deleteById(id)
            trashDao.upsert(trashItem)
            auditDao.insert(audit)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteHelpRequest(wsId, id)
            cloudSync?.pushTrashItem(wsId, trashItem)
            cloudSync?.pushAuditLog(wsId, audit)
        }
    }

    suspend fun addProduct(
        name: String,
        unit: String,
        rate: Double,
        folder: String = "General",
        stockQty: Double = 0.0,
        minStockAlert: Double = 0.0,
        purchasePrice: Double = 0.0,
        sku: String = "",
        barcode: String = ""
    ) {
        addProductValidated(name, unit, rate, folder, stockQty, minStockAlert, purchasePrice, sku, barcode)
    }

    suspend fun addProductValidated(
        name: String,
        unit: String,
        rate: Double,
        folder: String = "General",
        stockQty: Double = 0.0,
        minStockAlert: Double = 0.0,
        purchasePrice: Double = 0.0,
        sku: String = "",
        barcode: String = ""
    ): Result<ProductEntity> {
        val cleanName = name.trim()
        val cleanSku = sku.trim()
        val cleanBarcode = barcode.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter product name"))
        }
        val existingList = (if (_volatileCloudActive.value) _volatileProducts.value else productDao.getAll())
            .filter { !it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
        if (existingList.any { it.name.trim().equals(cleanName, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Product '$cleanName' already exists"))
        }
        if (cleanSku.isNotBlank() && existingList.any { it.sku.trim().equals(cleanSku, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Duplicate SKU '$cleanSku' already assigned to another product"))
        }
        if (cleanBarcode.isNotBlank() && existingList.any { it.barcode.trim().equals(cleanBarcode, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Duplicate Barcode '$cleanBarcode' already assigned to another product"))
        }
        val entity = ProductEntity(
            name = cleanName,
            unit = unit.trim().ifEmpty { "Nos" },
            defaultRate = rate.coerceAtLeast(0.0),
            folder = folder.trim().ifEmpty { "General" },
            stockQty = stockQty.coerceAtLeast(0.0),
            minStockAlert = minStockAlert.coerceAtLeast(0.0),
            purchasePrice = purchasePrice.coerceAtLeast(0.0),
            sku = cleanSku,
            barcode = cleanBarcode
        )
        val rowId = productDao.upsert(entity)
        markStockTrackedIfNeeded(entity)
        val saved = entity.copy(id = rowId)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushProduct(wsId, saved)
        }
        return Result.success(saved)
    }

    suspend fun updateProduct(
        id: Long,
        name: String,
        unit: String,
        rate: Double,
        folder: String = "General",
        stockQty: Double? = null,
        minStockAlert: Double? = null,
        purchasePrice: Double? = null,
        sku: String? = null,
        barcode: String? = null
    ) {
        updateProductValidated(id, name, unit, rate, folder, stockQty, minStockAlert, purchasePrice, sku, barcode)
    }

    suspend fun updateProductValidated(
        id: Long,
        name: String,
        unit: String,
        rate: Double,
        folder: String = "General",
        stockQty: Double? = null,
        minStockAlert: Double? = null,
        purchasePrice: Double? = null,
        sku: String? = null,
        barcode: String? = null
    ): Result<Unit> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter product name"))
        }
        val old = productDao.findById(id)
        val finalSku = (sku ?: old?.sku ?: "").trim()
        val finalBarcode = (barcode ?: old?.barcode ?: "").trim()
        val existingList = (if (_volatileCloudActive.value) _volatileProducts.value else productDao.getAll())
            .filter { it.id != id && !it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
        if (existingList.any { it.name.trim().equals(cleanName, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Another product named '$cleanName' already exists"))
        }
        if (finalSku.isNotBlank() && existingList.any { it.sku.trim().equals(finalSku, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Duplicate SKU '$finalSku' already assigned"))
        }
        if (finalBarcode.isNotBlank() && existingList.any { it.barcode.trim().equals(finalBarcode, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Duplicate Barcode '$finalBarcode' already assigned"))
        }
        val entity = ProductEntity(
            id = id,
            name = cleanName,
            unit = unit.trim().ifEmpty { "Nos" },
            defaultRate = rate.coerceAtLeast(0.0),
            folder = folder.trim().ifEmpty { "General" },
            stockQty = (stockQty ?: old?.stockQty ?: 0.0).coerceAtLeast(0.0),
            minStockAlert = (minStockAlert ?: old?.minStockAlert ?: 0.0).coerceAtLeast(0.0),
            purchasePrice = (purchasePrice ?: old?.purchasePrice ?: 0.0).coerceAtLeast(0.0),
            sku = finalSku,
            barcode = finalBarcode
        )
        productDao.upsert(entity)
        markStockTrackedIfNeeded(entity)
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushProduct(wsId, entity)
        }
        return Result.success(Unit)
    }

    suspend fun deleteProduct(
        id: Long,
        actorUsername: String = "Admin",
        actorRole: String = "admin",
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val existing = productDao.findById(id)
        if (existing == null) {
            productDao.deleteById(id)
            return
        }
        val trashItem = TrashItemEntity(
            id = "tr_prod_${existing.id}_$nowMillis",
            entityType = ENTITY_PRODUCT,
            entityId = existing.id.toString(),
            title = existing.name,
            summary = "${existing.folder} · ₹${String.format(Locale.US, "%.0f", existing.defaultRate)}/${existing.unit}",
            payloadJson = productToJson(existing).toString(),
            deletedBy = actorUsername.trim().ifBlank { "Admin" },
            deletedByRole = normalizeRole(actorRole),
            status = "TRASHED",
            deletedAt = nowMillis,
            expireAt = nowMillis + TRASH_RETENTION_MS
        )
        val audit = buildAuditLog(
            action = "DELETE",
            entityType = ENTITY_PRODUCT,
            entityId = existing.id.toString(),
            title = existing.name,
            actorUsername = trashItem.deletedBy,
            actorRole = trashItem.deletedByRole,
            details = "Product moved to Trash (24h retention)",
            createdAt = nowMillis
        )
        db.withTransaction {
            productDao.deleteById(id)
            trashDao.upsert(trashItem)
            auditDao.insert(audit)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteProduct(wsId, id)
            cloudSync?.pushTrashItem(wsId, trashItem)
            cloudSync?.pushAuditLog(wsId, audit)
        }
    }

    /**
     * Part 11: PRODUCT COMBO FILE CRUD — NEVER modifies original product prices, purchase prices, or stock.
     */
    suspend fun saveProductComboFile(combo: ProductComboFile): Result<ProductComboFile> {
        val cleanName = combo.name.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Enter a Combo File Name (at least 2 characters)"))
        }
        val cleanItems = combo.items
            .filter { it.productName.isNotBlank() && it.qty > 0.0 }
            .map {
                it.copy(
                    productName = it.productName.trim(),
                    unit = it.unit.trim().ifBlank { "Nos" },
                    qty = it.qty.coerceAtLeast(0.01),
                    catalogRate = it.catalogRate.coerceAtLeast(0.0)
                )
            }
        if (cleanItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select at least 1 product for the Combo File"))
        }
        if (combo.comboPrice < 0.0) {
            return Result.failure(IllegalArgumentException("Combo price cannot be negative"))
        }
        val wsId = getActiveWorkspaceId()
        val now = System.currentTimeMillis()
        val finalCombo = combo.copy(
            comboFileId = combo.comboFileId.ifBlank { "combo_$now" },
            companyId = combo.companyId.ifBlank { wsId },
            name = cleanName,
            description = combo.description.trim(),
            items = cleanItems,
            comboPrice = combo.comboPrice.coerceAtLeast(0.0),
            updatedAt = now
        )
        val entity = ProductComboFileJson.toEntity(finalCombo)
        if (_volatileCloudActive.value) {
            _volatileComboFiles.value = (listOf(entity) + _volatileComboFiles.value.filterNot { it.comboFileId == entity.comboFileId })
                .sortedByDescending { it.updatedAt }
        } else {
            comboFileDao.upsert(entity)
        }
        if (wsId.isNotBlank()) {
            cloudSync?.pushProductComboFile(wsId, entity)
        }
        return Result.success(finalCombo)
    }

    suspend fun deleteProductComboFile(comboFileId: String) {
        if (_volatileCloudActive.value) {
            _volatileComboFiles.value = _volatileComboFiles.value.filterNot { it.comboFileId == comboFileId }
        } else {
            comboFileDao.deleteById(comboFileId)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteProductComboFile(wsId, comboFileId)
        }
    }

    suspend fun migrateLegacyComboPackagesIfNeeded() {
        if (_volatileCloudActive.value) return
        val existingCombos = comboFileDao.getAll()
        if (existingCombos.isNotEmpty()) return
        val legacyRows = productDao.getAll().filter { it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
        val fromCfg = configDao.getValue(KEY_COMBO_PACKAGES).orEmpty()
        val wsId = getActiveWorkspaceId()
        val migrated = buildList {
            legacyRows.forEach { row ->
                ComboPackageJson.fromSingleJson(row.unit)?.let { pkg ->
                    add(
                        ProductComboFile(
                            comboFileId = pkg.id,
                            companyId = wsId,
                            name = row.name,
                            description = pkg.description,
                            items = pkg.items.map {
                                ProductComboItem(it.productId, it.productName, it.unit, it.qty, it.rate, it.folder)
                            },
                            comboPrice = pkg.totalAmount,
                            createdAt = pkg.createdAt,
                            updatedAt = pkg.createdAt
                        )
                    )
                }
            }
            if (isEmpty() && fromCfg.isNotBlank()) {
                ComboPackageJson.fromJson(fromCfg).forEach { pkg ->
                    add(
                        ProductComboFile(
                            comboFileId = pkg.id,
                            companyId = wsId,
                            name = pkg.name,
                            description = pkg.description,
                            items = pkg.items.map {
                                ProductComboItem(it.productId, it.productName, it.unit, it.qty, it.rate, it.folder)
                            },
                            comboPrice = pkg.totalAmount,
                            createdAt = pkg.createdAt,
                            updatedAt = pkg.createdAt
                        )
                    )
                }
            }
        }
        migrated.forEach { saveProductComboFile(it) }
    }

    suspend fun saveComboPackage(pkg: ComboPackage) {
        val converted = ProductComboFile(
            comboFileId = pkg.id,
            companyId = getActiveWorkspaceId(),
            name = pkg.name,
            description = pkg.description,
            items = pkg.items.map {
                ProductComboItem(it.productId, it.productName, it.unit, it.qty, it.rate, it.folder)
            },
            comboPrice = pkg.totalAmount,
            createdAt = pkg.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        saveProductComboFile(converted)
    }

    suspend fun deleteComboPackage(id: String) {
        deleteProductComboFile(id)
        val existingCombos = productDao.getAll().filter { it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
        val matched = existingCombos.filter { row ->
            ComboPackageJson.fromSingleJson(row.unit)?.id == id
        }
        val wsId = getActiveWorkspaceId()
        matched.forEach { row ->
            productDao.deleteById(row.id)
            if (wsId.isNotBlank()) {
                cloudSync?.deleteProduct(wsId, row.id)
            }
        }
        val legacyRaw = configDao.getValue(KEY_COMBO_PACKAGES).orEmpty()
        if (legacyRaw.isNotBlank()) {
            val remaining = ComboPackageJson.fromJson(legacyRaw).filterNot { it.id == id }
            configDao.setValue(AppConfigEntity(KEY_COMBO_PACKAGES, ComboPackageJson.toJson(remaining)))
        }
    }

    suspend fun getComboPackagesList(): List<ComboPackage> {
        val comboFiles = comboFileDao.getAll()
        if (comboFiles.isNotEmpty()) {
            return comboFiles.map { cf ->
                val model = ProductComboFileJson.fromEntity(cf)
                ComboPackage(
                    id = model.comboFileId,
                    name = model.name,
                    description = model.description,
                    items = model.items.map {
                        ComboPackageItem(it.productId, it.productName, it.unit, it.qty, it.catalogRate, it.folder)
                    },
                    createdAt = model.createdAt
                )
            }
        }
        val synced = productDao.getAll()
            .filter { it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
            .mapNotNull { row -> ComboPackageJson.fromSingleJson(row.unit)?.copy(name = row.name) }
            .sortedByDescending { it.createdAt }
        if (synced.isNotEmpty()) return synced
        val raw = configDao.getValue(KEY_COMBO_PACKAGES).orEmpty()
        return if (raw.isBlank()) emptyList() else ComboPackageJson.fromJson(raw)
    }

    suspend fun saveExpense(
        expense: ExpenseEntity,
        actorUsername: String = "Admin",
        actorRole: String = "admin"
    ): Result<ExpenseEntity> {
        val cleanTitle = expense.title.trim()
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Enter an expense title"))
        }
        if (expense.amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter expense amount greater than 0"))
        }
        val now = System.currentTimeMillis()
        val finalExpense = expense.copy(
            id = expense.id.ifBlank { "exp_$now" },
            title = cleanTitle,
            category = expense.category.trim().ifBlank { "General" },
            amount = expense.amount.coerceAtLeast(0.0),
            recordedBy = expense.recordedBy.ifBlank { actorUsername },
            updatedAt = now
        )
        val audit = buildAuditLog(
            action = if (expense.id.isBlank()) "EXPENSE_ADD" else "EXPENSE_EDIT",
            entityType = ENTITY_EXPENSE,
            entityId = finalExpense.id,
            title = finalExpense.title,
            actorUsername = actorUsername,
            actorRole = normalizeRole(actorRole),
            details = "₹${String.format(Locale.US, "%.2f", finalExpense.amount)} (${finalExpense.category})",
            createdAt = now
        )
        db.withTransaction {
            expenseDao.upsert(finalExpense)
            auditDao.insert(audit)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.pushExpense(wsId, finalExpense)
            cloudSync?.pushAuditLog(wsId, audit)
        }
        return Result.success(finalExpense)
    }

    suspend fun deleteExpense(
        id: String,
        actorUsername: String = "Admin",
        actorRole: String = "admin",
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val existing = expenseDao.findById(id) ?: return
        val trashItem = TrashItemEntity(
            id = "tr_exp_${existing.id}_$nowMillis",
            entityType = ENTITY_EXPENSE,
            entityId = existing.id,
            title = existing.title,
            summary = "${existing.category} · ₹${String.format(Locale.US, "%.2f", existing.amount)}",
            payloadJson = expenseToJson(existing).toString(),
            deletedBy = actorUsername.trim().ifBlank { "Admin" },
            deletedByRole = normalizeRole(actorRole),
            status = "TRASHED",
            deletedAt = nowMillis,
            expireAt = nowMillis + TRASH_RETENTION_MS
        )
        val audit = buildAuditLog(
            action = "DELETE",
            entityType = ENTITY_EXPENSE,
            entityId = existing.id,
            title = existing.title,
            actorUsername = trashItem.deletedBy,
            actorRole = trashItem.deletedByRole,
            details = "Expense moved to Trash (24h retention)",
            createdAt = nowMillis
        )
        db.withTransaction {
            expenseDao.deleteById(id)
            trashDao.upsert(trashItem)
            auditDao.insert(audit)
        }
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.deleteExpense(wsId, id)
            cloudSync?.pushTrashItem(wsId, trashItem)
            cloudSync?.pushAuditLog(wsId, audit)
        }
    }

    suspend fun restoreTrashItem(
        trashId: String,
        actorUsername: String = "Admin",
        actorRole: String = "admin",
        nowMillis: Long = System.currentTimeMillis()
    ): Result<String> {
        val item = trashDao.findById(trashId)
            ?: return Result.failure(IllegalStateException("Trash item not found."))
        if (item.status != "TRASHED") {
            return Result.failure(IllegalStateException("Item is already restored."))
        }
        if (nowMillis >= item.expireAt) {
            purgeExpiredTrash(nowMillis)
            return Result.failure(IllegalStateException("24-hour restore window expired; item has been permanently deleted."))
        }

        val updatedTrash = item.copy(
            status = "RESTORED",
            restoredAt = nowMillis,
            restoredBy = actorUsername.trim().ifBlank { "Admin" }
        )
        val audit = buildAuditLog(
            action = "RESTORE",
            entityType = item.entityType,
            entityId = item.entityId,
            title = item.title,
            actorUsername = updatedTrash.restoredBy,
            actorRole = normalizeRole(actorRole),
            details = "Restored from Trash before 24h expiry",
            createdAt = nowMillis
        )
        val obj = JSONObject(item.payloadJson)
        val wsId = getActiveWorkspaceId()
        var modifiedProducts: List<ProductEntity> = emptyList()
        var restoredBill: BillEntity? = null
        var restoredCustomer: CustomerEntity? = null
        var restoredProduct: ProductEntity? = null
        var restoredExpense: ExpenseEntity? = null
        var restoredHelp: CustomerHelpEntity? = null

        db.withTransaction {
            when (item.entityType) {
                ENTITY_BILL -> {
                    val b = billFromJson(obj)
                    modifiedProducts = adjustLocalStockForBillChange(oldBill = null, newBill = b)
                    billDao.upsert(b)
                    restoredBill = b
                }
                ENTITY_CUSTOMER -> {
                    val c = customerFromJson(obj)
                    val newId = customerDao.upsert(c)
                    restoredCustomer = if (c.id > 0L) c else c.copy(id = newId)
                }
                ENTITY_PRODUCT -> {
                    val p = productFromJson(obj)
                    val newId = productDao.upsert(p)
                    markStockTrackedIfNeeded(p)
                    restoredProduct = if (p.id > 0L) p else p.copy(id = newId)
                }
                ENTITY_EXPENSE -> {
                    val e = expenseFromJson(obj)
                    expenseDao.upsert(e)
                    restoredExpense = e
                }
                ENTITY_HELP -> {
                    val h = helpFromJson(obj)
                    val newId = helpDao.upsert(h)
                    restoredHelp = if (h.id > 0L) h else h.copy(id = newId)
                }
                ENTITY_SETTLEMENT -> {
                    val s = settlementFromJson(obj)
                    settlementDao.insert(s)
                }
            }
            trashDao.upsert(updatedTrash)
            auditDao.insert(audit)
        }

        if (wsId.isNotBlank()) {
            restoredBill?.let { cloudSync?.pushBill(wsId, it) }
            modifiedProducts.forEach { cloudSync?.pushProduct(wsId, it) }
            restoredCustomer?.let { cloudSync?.pushCustomer(wsId, it) }
            restoredProduct?.let { cloudSync?.pushProduct(wsId, it) }
            restoredExpense?.let { cloudSync?.pushExpense(wsId, it) }
            restoredHelp?.let { cloudSync?.pushHelpRequest(wsId, it, isTechnicianStatusUpdate = false) }
            cloudSync?.pushTrashItem(wsId, updatedTrash)
            cloudSync?.pushAuditLog(wsId, audit)
        }
        return Result.success("Restored: ${item.title}")
    }

    suspend fun purgeExpiredTrash(nowMillis: Long = System.currentTimeMillis()): Int {
        if (_volatileCloudActive.value) return 0
        val wsId = getActiveWorkspaceId()
        if (wsId.isNotBlank()) {
            cloudSync?.purgeExpiredTrashOnServer(wsId)
        }
        val expired = trashDao.getAll().filter { it.status == "TRASHED" && nowMillis >= it.expireAt }
        if (expired.isEmpty()) return 0
        val auditEntries = mutableListOf<AuditLogEntity>()
        db.withTransaction {
            expired.forEach { item ->
                trashDao.deleteById(item.id)
                val log = buildAuditLog(
                    action = "AUTO_PURGE_24H",
                    entityType = item.entityType,
                    entityId = item.entityId,
                    title = item.title,
                    actorUsername = "System",
                    actorRole = "system",
                    details = "Permanently deleted after 24h retention window",
                    createdAt = nowMillis
                )
                auditDao.insert(log)
                auditEntries += log
            }
        }
        if (wsId.isNotBlank()) {
            expired.forEach { cloudSync?.deleteTrashItem(wsId, it.id) }
            auditEntries.forEach { cloudSync?.pushAuditLog(wsId, it) }
        }
        return expired.size
    }

    private fun buildAuditLog(
        action: String,
        entityType: String,
        entityId: String,
        title: String,
        actorUsername: String,
        actorRole: String,
        details: String,
        createdAt: Long
    ): AuditLogEntity = AuditLogEntity(
        id = "log_${createdAt}_${entityId.take(12)}_${kotlin.math.abs((action + title).hashCode()) % 10000}",
        action = action,
        entityType = entityType,
        entityId = entityId,
        title = title,
        actorUsername = actorUsername,
        actorRole = actorRole,
        details = details,
        createdAt = createdAt
    )

    suspend fun saveCompanySettings(settings: CompanySettings, syncCloud: Boolean = true) {
        val entries = mutableListOf(
            AppConfigEntity(KEY_COMPANY_NAME, AppIdentity.OFFICIAL_COMPANY_NAME),
            AppConfigEntity(KEY_COMPANY_MOBILE, settings.mobile.trim().ifEmpty { DEFAULT_COMPANY_MOBILE }),
            AppConfigEntity(KEY_COMPANY_EMAIL, settings.email.trim().ifEmpty { DEFAULT_COMPANY_EMAIL }),
            AppConfigEntity(KEY_COMPANY_ADDRESS, settings.address.trim().ifEmpty { DEFAULT_COMPANY_ADDRESS })
        )
        if (settings.authorisedSignatureBase64.isNotEmpty()) {
            entries.add(AppConfigEntity(KEY_AUTHORISED_SIGNATURE, settings.authorisedSignatureBase64))
        }
        if (settings.logoBase64.isNotEmpty()) {
            entries.add(AppConfigEntity(KEY_COMPANY_LOGO, settings.logoBase64))
        }
        configDao.setAll(entries)
        if (syncCloud) pushCurrentWorkspaceConfigToCloud()
    }

    suspend fun saveVoiceAndNoteSettings(settings: VoiceAndNoteSettings, syncCloud: Boolean = true) {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_HEADER_NOTE, settings.headerNote.trim()),
                AppConfigEntity(KEY_VOICE_LOGIN, settings.loginVoice.trim()),
                AppConfigEntity(KEY_VOICE_POST_LOGIN, settings.postLoginVoice.trim()),
                AppConfigEntity(KEY_VOICE_BILL_START, settings.billGeneratingVoice.trim()),
                AppConfigEntity(KEY_VOICE_BILL_DONE, settings.billCompletedVoice.trim()),
                AppConfigEntity(KEY_VOICE_MODEL_ID, settings.voiceModelId.trim().ifEmpty { VoicePreset.CLEAR.id })
            )
        )
        if (syncCloud) pushCurrentWorkspaceConfigToCloud()
    }

    suspend fun saveUpiSettings(upiId: String, payeeName: String, syncCloud: Boolean = true) {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_UPI_ID, upiId.trim()),
                AppConfigEntity(KEY_UPI_NAME, payeeName.trim().ifEmpty { DEFAULT_PAYEE_NAME })
            )
        )
        if (syncCloud) pushCurrentWorkspaceConfigToCloud()
    }

    suspend fun clearUpiId(syncCloud: Boolean = true) {
        configDao.setValue(AppConfigEntity(KEY_UPI_ID, ""))
        if (syncCloud) pushCurrentWorkspaceConfigToCloud()
    }

    suspend fun saveFallbackQrBase64(base64: String, syncCloud: Boolean = true) {
        configDao.setValue(AppConfigEntity(KEY_FALLBACK_QR, base64))
        if (syncCloud) pushCurrentWorkspaceConfigToCloud()
    }

    suspend fun saveAuthorisedSignatureBase64(base64: String, syncCloud: Boolean = true) {
        configDao.setValue(AppConfigEntity(KEY_AUTHORISED_SIGNATURE, base64))
        if (syncCloud) pushCurrentWorkspaceConfigToCloud()
    }

    suspend fun saveCompanyLogoBase64(base64: String, syncCloud: Boolean = true) {
        configDao.setValue(AppConfigEntity(KEY_COMPANY_LOGO, base64))
        if (syncCloud) pushCurrentWorkspaceConfigToCloud()
    }

    private suspend fun pushCurrentWorkspaceConfigToCloud() {
        if (_volatileCloudActive.value) return
        val wsId = getActiveWorkspaceId()
        if (wsId.isBlank()) return
        val cfgMap = configDao.getAll().associate { it.key to it.value }
        val company = CompanySettings(
            companyName = cfgMap[KEY_COMPANY_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_NAME,
            mobile = cfgMap[KEY_COMPANY_MOBILE]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_MOBILE,
            email = cfgMap[KEY_COMPANY_EMAIL]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_EMAIL,
            address = cfgMap[KEY_COMPANY_ADDRESS]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_ADDRESS,
            authorisedSignatureBase64 = cfgMap[KEY_AUTHORISED_SIGNATURE].orEmpty(),
            logoBase64 = cfgMap[KEY_COMPANY_LOGO].orEmpty()
        )
        val upi = UpiSettings(
            upiId = cfgMap[KEY_UPI_ID].orEmpty(),
            payeeName = cfgMap[KEY_UPI_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_PAYEE_NAME,
            fallbackQrBase64 = cfgMap[KEY_FALLBACK_QR].orEmpty()
        )
        val defaults = VoiceAndNoteSettings()
        val voice = VoiceAndNoteSettings(
            headerNote = cfgMap[KEY_HEADER_NOTE] ?: defaults.headerNote,
            loginVoice = cfgMap[KEY_VOICE_LOGIN]?.takeIf { it.isNotBlank() } ?: defaults.loginVoice,
            postLoginVoice = cfgMap[KEY_VOICE_POST_LOGIN]?.takeIf { it.isNotBlank() } ?: defaults.postLoginVoice,
            billGeneratingVoice = cfgMap[KEY_VOICE_BILL_START]?.takeIf { it.isNotBlank() } ?: defaults.billGeneratingVoice,
            billCompletedVoice = cfgMap[KEY_VOICE_BILL_DONE]?.takeIf { it.isNotBlank() } ?: defaults.billCompletedVoice,
            voiceModelId = cfgMap[KEY_VOICE_MODEL_ID]?.takeIf { it.isNotBlank() } ?: defaults.voiceModelId
        )
        cloudSync?.pushWorkspaceConfig(wsId, company, upi, voice)
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        configDao.setValue(AppConfigEntity(KEY_SOUND_ENABLED, if (enabled) "1" else "0"))
    }

    suspend fun setLanguage(@Suppress("UNUSED_PARAMETER") language: AppLanguage) {
        configDao.setValue(AppConfigEntity(KEY_LANGUAGE, "en"))
    }

    fun computeNextInvoiceNo(bills: List<BillEntity>): String {
        var max = 0
        val regex = Regex("(\\d+)$")
        bills.forEach { b ->
            val match = regex.find(b.invoiceNo)
            if (match != null) {
                val n = match.groupValues[1].toIntOrNull() ?: 0
                if (n > max) max = n
            }
        }
        val next = maxOf(max, bills.size) + 1
        return String.format(Locale.US, "TSE-%03d", next)
    }

    suspend fun exportBackupJson(): String {
        val useVolatile = _volatileCloudActive.value
        val bills = if (useVolatile) _volatileBills.value else billDao.getAll()
        val customers = if (useVolatile) _volatileCustomers.value else customerDao.getAll()
        val products = if (useVolatile) _volatileProducts.value else productDao.getAll()
        val users = if (useVolatile) _volatileAccounts.value else userDao.getAll()
        val allConfigs = configDao.getAll()
        val cfgMap = allConfigs.associate { it.key to it.value }.toMutableMap()
        if (useVolatile) {
            _volatileCompany.value?.let { c ->
                cfgMap[KEY_COMPANY_NAME] = c.companyName
                cfgMap[KEY_COMPANY_MOBILE] = c.mobile
                cfgMap[KEY_COMPANY_EMAIL] = c.email
                cfgMap[KEY_COMPANY_ADDRESS] = c.address
                cfgMap[KEY_AUTHORISED_SIGNATURE] = c.authorisedSignatureBase64
                cfgMap[KEY_COMPANY_LOGO] = c.logoBase64
            }
            _volatileUpi.value?.let { u ->
                cfgMap[KEY_UPI_ID] = u.upiId
                cfgMap[KEY_UPI_NAME] = u.payeeName
                cfgMap[KEY_FALLBACK_QR] = u.fallbackQrBase64
            }
        }
        val helpList = if (useVolatile) _volatileHelpRequests.value else helpDao.getAll()
        val settlementList = if (useVolatile) _volatileSettlements.value else settlementDao.getAll()
        val expenseList = if (useVolatile) _volatileExpenses.value else expenseDao.getAll()
        val trashList = if (useVolatile) _volatileTrashItems.value else trashDao.getAll()
        val auditList = if (useVolatile) _volatileAuditLogs.value else auditDao.getAll()
        val comboList = if (useVolatile) _volatileComboFiles.value else comboFileDao.getAll()

        val root = JSONObject()
        root.put("schemaVersion", 8)
        root.put("appName", AppIdentity.OFFICIAL_APP_NAME)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("bills", JSONArray().apply { bills.forEach { put(billToJson(it)) } })
        root.put("customers", JSONArray().apply { customers.forEach { put(customerToJson(it)) } })
        root.put("products", JSONArray().apply { products.forEach { put(productToJson(it)) } })
        root.put("productComboFiles", JSONArray().apply {
            comboList.forEach { c ->
                put(JSONObject().apply {
                    put("comboFileId", c.comboFileId)
                    put("companyId", c.companyId)
                    put("name", c.name)
                    put("description", c.description)
                    put("itemsJson", c.itemsJson)
                    put("comboPrice", c.comboPrice)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                })
            }
        })
        root.put("settings", JSONObject().apply {
            put("company", AppIdentity.OFFICIAL_COMPANY_NAME)
            put("mobile", cfgMap[KEY_COMPANY_MOBILE] ?: DEFAULT_COMPANY_MOBILE)
            put("email", cfgMap[KEY_COMPANY_EMAIL] ?: DEFAULT_COMPANY_EMAIL)
            put("address", cfgMap[KEY_COMPANY_ADDRESS] ?: DEFAULT_COMPANY_ADDRESS)
            put("authorisedSignature", cfgMap[KEY_AUTHORISED_SIGNATURE] ?: "")
            put("logo", cfgMap[KEY_COMPANY_LOGO] ?: "")
        })
        root.put("upi", JSONObject().apply {
            put("id", cfgMap[KEY_UPI_ID] ?: "")
            put("name", cfgMap[KEY_UPI_NAME] ?: DEFAULT_PAYEE_NAME)
        })
        root.put("qr", cfgMap[KEY_FALLBACK_QR] ?: "")
        root.put("appConfig", JSONObject().apply {
            allConfigs.forEach { entry ->
                put(entry.key, entry.value)
            }
        })
        root.put("accounts", JSONArray().apply {
            users.forEach { u ->
                put(JSONObject().apply {
                    put("username", u.username)
                    put("displayName", u.displayName)
                    put("passwordHash", u.passwordHash)
                    put("role", normalizeRole(u.role))
                    put("phone", u.phone)
                    put("photoBase64", u.photoBase64)
                    put("email", u.email)
                    put("uid", u.uid)
                    put("techId", u.techId)
                    put("companyId", u.companyId)
                    put("loginEnabled", u.loginEnabled)
                })
            }
        })
        root.put("helpRequests", JSONArray().apply { helpList.forEach { put(helpToJson(it)) } })
        root.put("settlements", JSONArray().apply { settlementList.forEach { put(settlementToJson(it)) } })
        root.put("expenses", JSONArray().apply { expenseList.forEach { put(expenseToJson(it)) } })
        root.put("trashItems", JSONArray().apply { trashList.forEach { put(trashToJson(it)) } })
        root.put("auditLogs", JSONArray().apply { auditList.forEach { put(auditToJson(it)) } })
        return root.toString(2)
    }

    suspend fun importBackupJson(rawJson: String): Result<BackupImportSummary> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(rawJson.trim())
            val hasRecognizedSection = root.has("bills") ||
                root.has("products") ||
                root.has("customers") ||
                root.has("accounts") ||
                root.has("settings") ||
                root.has("helpRequests") ||
                root.has("expenses")
            if (!hasRecognizedSection) {
                return@withContext Result.failure(IllegalArgumentException("Not a valid TS – TOTAL SOLUTION backup file"))
            }
            val billsArr = root.optJSONArray("bills") ?: JSONArray()
            val productsArr = root.optJSONArray("products") ?: JSONArray()

            var billsCount = 0
            var customersCount = 0
            var productsCount = 0
            var accountsCount = 0
            var helpCount = 0
            var settlementsCount = 0
            var expensesCount = 0

            db.withTransaction {
                billDao.deleteAll()
                for (i in 0 until billsArr.length()) {
                    val obj = billsArr.getJSONObject(i)
                    val itemsStr = obj.optJSONArray("items")?.toString() ?: "[]"
                    val sub = obj.optDouble("subtotal", 0.0)
                    val disc = obj.optDouble("discount", 0.0)
                    val grand = if (obj.has("grandTotal")) obj.optDouble("grandTotal", 0.0) else maxOf(0.0, sub - disc)
                    val paid = if (obj.has("paid")) obj.optDouble("paid", 0.0) else obj.optDouble("advance", 0.0)
                    val due = if (obj.has("due")) obj.optDouble("due", 0.0) else maxOf(0.0, grand - paid)
                    val payStatus = obj.optString("paymentStatus", "").ifBlank {
                        BillPaymentStatus.compute(grand, paid).code
                    }
                    billDao.upsert(
                        BillEntity(
                            id = obj.optString("id", "b${System.currentTimeMillis()}_$i"),
                            invoiceNo = obj.optString("no", "TSE-${i + 1}"),
                            date = obj.optString("date", ""),
                            customerName = obj.optString("customer", ""),
                            customerDetails = obj.optString("details", ""),
                            itemsJson = itemsStr,
                            subtotal = sub,
                            discount = disc,
                            grandTotal = grand,
                            paidAmount = paid,
                            dueAmount = due,
                            paymentMode = obj.optString("paymentMode", PaymentMode.ONLINE.label),
                            signatureBase64 = obj.optString("signature", ""),
                            customQrText = obj.optString("customQrText", ""),
                            createdBy = obj.optString("createdBy", "Admin"),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            extraChargeType = obj.optString("extraChargeType", "NONE"),
                            extraChargeAmount = obj.optDouble("extraChargeAmount", 0.0),
                            secondaryChargeType = obj.optString("secondaryChargeType", "NONE"),
                            secondaryChargeAmount = obj.optDouble("secondaryChargeAmount", 0.0),
                            staffRating = obj.optInt("staffRating", 0).coerceIn(0, 5),
                            staffReviewNote = obj.optString("staffReviewNote", ""),
                            documentType = obj.optString("documentType", DocumentType.BILL.name),
                            isHelpBill = obj.optBoolean("isHelpBill", false),
                            helpRequestId = obj.optLong("helpRequestId", 0L),
                            quotationNote = obj.optString("quotationNote", ""),
                            paymentStatus = payStatus,
                            linkedPaymentId = obj.optString("linkedPaymentId", ""),
                            stockDeducted = obj.optBoolean("stockDeducted", obj.optString("documentType", DocumentType.BILL.name) == DocumentType.BILL.name)
                        )
                    )
                    billsCount++
                }

                root.optJSONArray("customers")?.let { custArr ->
                    customerDao.deleteAll()
                    for (i in 0 until custArr.length()) {
                        val obj = custArr.getJSONObject(i)
                        val name = obj.optString("name", "").trim()
                        if (name.isNotEmpty()) {
                            customerDao.upsert(
                                CustomerEntity(
                                    name = name,
                                    details = obj.optString("details", ""),
                                    phone = obj.optString("phone", ""),
                                    address = obj.optString("address", ""),
                                    locationUrl = obj.optString("locationUrl", "")
                                )
                            )
                            customersCount++
                        }
                    }
                }

                productDao.deleteAll()
                val importedProducts = mutableListOf<ProductEntity>()
                for (i in 0 until productsArr.length()) {
                    val obj = productsArr.getJSONObject(i)
                    val name = obj.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        val p = productFromJson(obj).copy(
                            id = 0L,
                            folder = obj.optString("folder", "").ifBlank { inferFolderForName(name) }
                        )
                        importedProducts.add(p)
                    }
                }
                if (importedProducts.isNotEmpty()) {
                    productDao.insertAll(importedProducts)
                    productsCount = importedProducts.size
                }

                root.optJSONObject("settings")?.let { s ->
                    saveCompanySettings(
                        CompanySettings(
                            companyName = s.optString("company", DEFAULT_COMPANY_NAME),
                            mobile = s.optString("mobile", DEFAULT_COMPANY_MOBILE),
                            email = s.optString("email", DEFAULT_COMPANY_EMAIL),
                            address = s.optString("address", DEFAULT_COMPANY_ADDRESS)
                        )
                    )
                    if (s.has("authorisedSignature")) {
                        saveAuthorisedSignatureBase64(s.optString("authorisedSignature", ""))
                    }
                    if (s.has("logo")) {
                        saveCompanyLogoBase64(s.optString("logo", ""))
                    }
                }

                root.optJSONObject("upi")?.let { u ->
                    saveUpiSettings(
                        upiId = u.optString("id", ""),
                        payeeName = u.optString("name", DEFAULT_PAYEE_NAME)
                    )
                }

                if (root.has("qr")) {
                    saveFallbackQrBase64(root.optString("qr", ""))
                }

                root.optJSONObject("appConfig")?.let { cfgObj ->
                    val entries = mutableListOf<AppConfigEntity>()
                    val keys = cfgObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        entries.add(AppConfigEntity(key = k, value = cfgObj.optString(k, "")))
                    }
                    if (entries.isNotEmpty()) {
                        configDao.setAll(entries)
                    }
                }

                root.optJSONArray("accounts")?.let { accArr ->
                    if (accArr.length() > 0) {
                        userDao.deleteAll()
                        var adminImported = false
                        for (i in 0 until accArr.length()) {
                            val u = accArr.optJSONObject(i) ?: continue
                            val uname = u.optString("username", "").trim()
                            val passHash = u.optString("passwordHash", "").trim()
                            if (uname.isEmpty() || passHash.isEmpty()) continue
                            val normalizedRole = normalizeRole(u.optString("role", "technician"))
                            if (normalizedRole == "admin") {
                                if (adminImported) continue
                                adminImported = true
                            }
                            userDao.upsert(
                                UserAccountEntity(
                                    username = uname,
                                    displayName = u.optString("displayName", uname),
                                    passwordHash = passHash,
                                    role = normalizedRole,
                                    phone = u.optString("phone", ""),
                                    photoBase64 = u.optString("photoBase64", "")
                                )
                            )
                            accountsCount++
                        }
                    }
                }

                root.optJSONArray("helpRequests")?.let { helpArr ->
                    helpDao.deleteAll()
                    for (i in 0 until helpArr.length()) {
                        val h = helpArr.getJSONObject(i)
                        helpDao.upsert(
                            CustomerHelpEntity(
                                id = h.optLong("id", 0L),
                                customerName = h.optString("customerName", ""),
                                phone = h.optString("phone", ""),
                                address = h.optString("address", ""),
                                locationUrl = h.optString("locationUrl", ""),
                                helpDescription = h.optString("helpDescription", ""),
                                status = h.optString("status", "OPEN"),
                                createdAt = h.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = h.optLong("updatedAt", System.currentTimeMillis()),
                                acceptedBy = h.optString("acceptedBy", ""),
                                acceptedByName = h.optString("acceptedByName", ""),
                                acceptedByPhone = h.optString("acceptedByPhone", ""),
                                acceptedAt = h.optLong("acceptedAt", 0L),
                                solvedAt = h.optLong("solvedAt", 0L),
                                billId = h.optString("billId", "")
                            )
                        )
                        helpCount++
                    }
                }

                root.optJSONArray("settlements")?.let { setArr ->
                    settlementDao.deleteAll()
                    for (i in 0 until setArr.length()) {
                        val s = setArr.getJSONObject(i)
                        settlementDao.insert(settlementFromJson(s).copy(id = 0L))
                        settlementsCount++
                    }
                }

                root.optJSONArray("expenses")?.let { expArr ->
                    expenseDao.deleteAll()
                    for (i in 0 until expArr.length()) {
                        val e = expArr.getJSONObject(i)
                        expenseDao.upsert(expenseFromJson(e))
                        expensesCount++
                    }
                }

                root.optJSONArray("trashItems")?.let { trArr ->
                    trashDao.deleteAll()
                    for (i in 0 until trArr.length()) {
                        trashDao.upsert(trashFromJson(trArr.getJSONObject(i)))
                    }
                }

                root.optJSONArray("auditLogs")?.let { logArr ->
                    auditDao.deleteAll()
                    for (i in 0 until logArr.length()) {
                        auditDao.insert(auditFromJson(logArr.getJSONObject(i)))
                    }
                }
                root.optJSONArray("productComboFiles")?.let { comboArr ->
                    comboFileDao.deleteAll()
                    for (i in 0 until comboArr.length()) {
                        val c = comboArr.getJSONObject(i)
                        val cid = c.optString("comboFileId", "").trim()
                        val cname = c.optString("name", "").trim()
                        if (cid.isNotBlank() && cname.isNotBlank()) {
                            comboFileDao.upsert(
                                ProductComboFileEntity(
                                    comboFileId = cid,
                                    companyId = c.optString("companyId", ""),
                                    name = cname,
                                    description = c.optString("description", ""),
                                    itemsJson = c.optString("itemsJson", "[]"),
                                    comboPrice = c.optDouble("comboPrice", 0.0).coerceAtLeast(0.0),
                                    createdAt = c.optLong("createdAt", System.currentTimeMillis()),
                                    updatedAt = c.optLong("updatedAt", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                }
            }

            Result.success(
                BackupImportSummary(
                    billsCount = billsCount,
                    customersCount = customersCount,
                    productsCount = productsCount,
                    accountsCount = accountsCount,
                    helpCount = helpCount,
                    settlementsCount = settlementsCount,
                    expensesCount = expensesCount
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun getActiveWorkspaceId(): String =
        _volatileWorkspaceId.value.ifBlank { configDao.getValue(KEY_CLOUD_WORKSPACE_ID).orEmpty() }

    suspend fun saveWorkspaceMetadata(info: CloudWorkspaceInfo) {
        _volatileWorkspaceId.value = info.workspaceId
        _volatileDeviceRole.value = info.deviceRole
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_CLOUD_WORKSPACE_ID, info.workspaceId),
                AppConfigEntity(KEY_CLOUD_OWNER_UID, info.ownerUid),
                AppConfigEntity(KEY_CLOUD_TECH_CODE, info.techShareCode),
                AppConfigEntity(KEY_CLOUD_SUPER_CODE, info.superShareCode),
                AppConfigEntity(KEY_CLOUD_DEVICE_ROLE, info.deviceRole),
                AppConfigEntity(KEY_COMPANY_NAME, AppIdentity.OFFICIAL_COMPANY_NAME)
            )
        )
    }

    suspend fun enableVolatileCloudMode(deviceRole: String, workspaceId: String = "") {
        _volatileCloudActive.value = true
        _volatileDeviceRole.value = deviceRole
        if (workspaceId.isNotBlank()) {
            _volatileWorkspaceId.value = workspaceId
        }
        wipeNonAdminLocalCompanyData()
    }

    suspend fun disableVolatileCloudMode() {
        _volatileCloudActive.value = false
        _volatileDeviceRole.value = "admin"
    }

    suspend fun clearVolatileSessionCache() {
        _volatileBills.value = emptyList()
        _volatileCustomers.value = emptyList()
        _volatileHelpRequests.value = emptyList()
        _volatileSettlements.value = emptyList()
        _volatileExpenses.value = emptyList()
        _volatileTrashItems.value = emptyList()
        _volatileAuditLogs.value = emptyList()
        _volatileComboFiles.value = emptyList()
        _volatileUpiTransactions.value = emptyList()
        _volatileEmployees.value = emptyList()
        _volatileSalaryPayments.value = emptyList()
        if (_volatileCloudActive.value && _volatileDeviceRole.value.equals("technician", ignoreCase = true)) {
            wipeNonAdminLocalCompanyData()
        }
    }

    suspend fun wipeNonAdminLocalCompanyData() {
        db.withTransaction {
            billDao.deleteAll()
            customerDao.deleteAll()
            helpDao.deleteAll()
            settlementDao.deleteAll()
            userDao.deleteAll()
            productDao.deleteAll()
            expenseDao.deleteAll()
            trashDao.deleteAll()
            auditDao.deleteAll()
            comboFileDao.deleteAll()
            upiTxDao.deleteAll()
            employeeDao.deleteAll()
            salaryPaymentDao.deleteAll()
        }
    }

    suspend fun applyCloudSnapshot(snapshot: CloudWorkspaceSnapshot) {
        saveWorkspaceMetadata(snapshot.workspace)
        val role = snapshot.workspace.deviceRole.lowercase(Locale.ROOT)
        if (role == "admin") {
            _volatileCloudActive.value = false
            mergeCloudMetaIntoAdmin(
                snapshot.companySettings,
                snapshot.upiSettings,
                snapshot.voiceAndNoteSettings
            )
            mergeCloudAccountsIntoAdmin(snapshot.accounts)
            mergeCloudProductsIntoAdmin(snapshot.products)
            mergeCloudCustomersIntoAdmin(snapshot.customers)
            mergeCloudBillsIntoAdmin(snapshot.bills)
            mergeCloudHelpsIntoAdmin(snapshot.helpRequests)
            mergeCloudSettlementsIntoAdmin(snapshot.settlements)
            mergeCloudExpensesIntoAdmin(snapshot.expenses)
            mergeCloudTrashIntoAdmin(snapshot.trashItems)
            mergeCloudAuditLogsIntoAdmin(snapshot.auditLogs)
            mergeCloudComboFiles(snapshot.comboFiles)
            mergeCloudUpiTransactions(snapshot.upiTransactions)
            // Admin MUST NEVER receive Super Admin employee/salary records
            _volatileEmployees.value = emptyList()
            _volatileSalaryPayments.value = emptyList()
        } else {
            enableVolatileCloudMode(role, snapshot.workspace.workspaceId)
            _volatileCompany.value = snapshot.companySettings
            _volatileUpi.value = snapshot.upiSettings
            _volatileVoice.value = snapshot.voiceAndNoteSettings
            _volatileAccounts.value = snapshot.accounts
            _volatileProducts.value = snapshot.products
            _volatileCustomers.value = snapshot.customers
            _volatileBills.value = if (role == "super_admin" || role == "technician") snapshot.bills else emptyList()
            _volatileHelpRequests.value = snapshot.helpRequests
            _volatileSettlements.value = if (role == "super_admin") snapshot.settlements else emptyList()
            _volatileExpenses.value = if (role == "super_admin") snapshot.expenses else emptyList()
            _volatileTrashItems.value = emptyList()
            _volatileAuditLogs.value = if (role == "super_admin") snapshot.auditLogs else emptyList()
            _volatileComboFiles.value = snapshot.comboFiles
            _volatileUpiTransactions.value = snapshot.upiTransactions
            _volatileEmployees.value = if (role == "super_admin") snapshot.employees else emptyList()
            _volatileSalaryPayments.value = if (role == "super_admin") snapshot.salaryPayments else emptyList()
        }
    }

    suspend fun mergeCloudMetaIntoAdmin(
        company: CompanySettings,
        upi: UpiSettings,
        voice: VoiceAndNoteSettings
    ) {
        if (_volatileCloudActive.value) {
            _volatileCompany.value = company
            _volatileUpi.value = upi
            _volatileVoice.value = voice
            return
        }
        saveCompanySettings(company, syncCloud = false)
        saveUpiSettings(upi.upiId, upi.payeeName, syncCloud = false)
        if (upi.fallbackQrBase64.isNotBlank()) saveFallbackQrBase64(upi.fallbackQrBase64, syncCloud = false)
        saveVoiceAndNoteSettings(voice, syncCloud = false)
    }

    suspend fun mergeCloudAccountsIntoAdmin(cloudAccounts: List<UserAccountEntity>) {
        if (_volatileCloudActive.value) {
            _volatileAccounts.value = cloudAccounts
            return
        }
        if (cloudAccounts.isEmpty()) return
        db.withTransaction {
            cloudAccounts.forEach { acc ->
                userDao.upsert(acc)
            }
        }
    }

    suspend fun mergeCloudProductsIntoAdmin(cloudProducts: List<ProductEntity>) {
        if (_volatileCloudActive.value) {
            _volatileProducts.value = cloudProducts
            return
        }
        if (cloudProducts.isEmpty()) return
        val existingAll = productDao.getAll()
        val existingNormalByName = existingAll
            .filter { !it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
            .associateBy { it.name.lowercase(Locale.ROOT) }
        val existingCombos = existingAll
            .filter { it.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true) }
        cloudProducts.forEach { cp ->
            if (cp.folder.equals(COMBO_STORAGE_FOLDER, ignoreCase = true)) {
                val cpComboId = ComboPackageJson.fromSingleJson(cp.unit)?.id
                val oldCombo = existingCombos.firstOrNull { ex ->
                    (cpComboId != null && ComboPackageJson.fromSingleJson(ex.unit)?.id == cpComboId) ||
                        ex.name.equals(cp.name, ignoreCase = true)
                }
                productDao.upsert(cp.copy(id = oldCombo?.id ?: cp.id))
            } else {
                val old = existingNormalByName[cp.name.lowercase(Locale.ROOT)]
                productDao.upsert(cp.copy(id = old?.id ?: cp.id))
            }
        }
    }

    suspend fun mergeCloudCustomersIntoAdmin(cloudCustomers: List<CustomerEntity>) {
        if (_volatileCloudActive.value) {
            _volatileCustomers.value = cloudCustomers
            return
        }
        cloudCustomers.forEach { c ->
            val old = customerDao.findByName(c.name)
            if (old != null) {
                customerDao.upsert(
                    old.copy(
                        details = c.details.ifBlank { old.details },
                        phone = c.phone.ifBlank { old.phone },
                        address = c.address.ifBlank { old.address },
                        locationUrl = c.locationUrl.ifBlank { old.locationUrl }
                    )
                )
            } else {
                customerDao.upsert(c.copy(id = 0L))
            }
        }
    }

    suspend fun mergeCloudBillsIntoAdmin(cloudBills: List<BillEntity>) {
        if (_volatileCloudActive.value) {
            if (_volatileDeviceRole.value.equals("super_admin", ignoreCase = true) ||
                _volatileDeviceRole.value.equals("technician", ignoreCase = true)
            ) {
                _volatileBills.value = cloudBills
            }
            return
        }
        cloudBills.forEach { cb ->
            val old = billDao.findById(cb.id)
            if (old == null || cb.updatedAt >= old.updatedAt) {
                billDao.upsert(cb)
            }
        }
    }

    suspend fun mergeCloudHelpsIntoAdmin(cloudHelps: List<CustomerHelpEntity>) {
        if (_volatileCloudActive.value) {
            _volatileHelpRequests.value = cloudHelps
            return
        }
        cloudHelps.forEach { ch ->
            val old = helpDao.findById(ch.id)
            if (old == null || ch.updatedAt >= old.updatedAt) {
                helpDao.upsert(ch)
            }
        }
    }

    suspend fun mergeCloudSettlementsIntoAdmin(cloudSettlements: List<TechnicianSettlementEntity>) {
        if (_volatileCloudActive.value) {
            if (_volatileDeviceRole.value.equals("super_admin", ignoreCase = true)) {
                _volatileSettlements.value = cloudSettlements
            }
            return
        }
        if (cloudSettlements.isEmpty()) return
        val existing = settlementDao.getAll()
        cloudSettlements.forEach { cs ->
            val match = existing.any {
                it.technicianUsername.equals(cs.technicianUsername, ignoreCase = true) &&
                    it.createdAt == cs.createdAt &&
                    kotlin.math.abs(it.amount - cs.amount) < 0.01
            }
            if (!match) {
                settlementDao.insert(cs.copy(id = 0L))
            }
        }
    }

    suspend fun mergeCloudExpensesIntoAdmin(cloudExpenses: List<ExpenseEntity>) {
        if (_volatileCloudActive.value) {
            if (_volatileDeviceRole.value.equals("super_admin", ignoreCase = true)) {
                _volatileExpenses.value = cloudExpenses
            }
            return
        }
        cloudExpenses.forEach { ce ->
            val old = expenseDao.findById(ce.id)
            if (old == null || ce.updatedAt >= old.updatedAt) {
                expenseDao.upsert(ce)
            }
        }
    }

    suspend fun mergeCloudTrashIntoAdmin(cloudTrash: List<TrashItemEntity>) {
        if (_volatileCloudActive.value) return
        cloudTrash.forEach { ct ->
            val old = trashDao.findById(ct.id)
            if (old == null || ct.restoredAt >= old.restoredAt) {
                trashDao.upsert(ct)
            }
        }
    }

    suspend fun mergeCloudAuditLogsIntoAdmin(cloudLogs: List<AuditLogEntity>) {
        if (_volatileCloudActive.value) {
            if (_volatileDeviceRole.value.equals("super_admin", ignoreCase = true)) {
                _volatileAuditLogs.value = cloudLogs
            }
            return
        }
        cloudLogs.forEach { log ->
            auditDao.insert(log)
        }
    }

    suspend fun mergeCloudComboFiles(cloudCombos: List<ProductComboFileEntity>) {
        if (_volatileCloudActive.value) {
            _volatileComboFiles.value = cloudCombos
            return
        }
        cloudCombos.forEach { cf ->
            val old = comboFileDao.findById(cf.comboFileId)
            if (old == null || cf.updatedAt >= old.updatedAt) {
                comboFileDao.upsert(cf)
            }
        }
    }

    suspend fun mergeCloudUpiTransactions(cloudTx: List<UpiTransactionEntity>) {
        if (_volatileCloudActive.value) {
            _volatileUpiTransactions.value = cloudTx
            return
        }
        cloudTx.forEach { tx ->
            val old = upiTxDao.findById(tx.paymentId)
            if (old == null || tx.updatedAt >= old.updatedAt) {
                upiTxDao.upsert(tx)
            }
        }
    }

    suspend fun mergeCloudEmployees(cloudEmployees: List<EmployeeEntity>) {
        if (!normalizeRole(_volatileDeviceRole.value).equals("super_admin", ignoreCase = true)) {
            _volatileEmployees.value = emptyList()
            return
        }
        if (_volatileCloudActive.value) {
            _volatileEmployees.value = cloudEmployees
            return
        }
        cloudEmployees.forEach { emp ->
            employeeDao.upsert(emp)
        }
    }

    suspend fun mergeCloudSalaryPayments(cloudPayments: List<SalaryPaymentEntity>) {
        if (!normalizeRole(_volatileDeviceRole.value).equals("super_admin", ignoreCase = true)) {
            _volatileSalaryPayments.value = emptyList()
            return
        }
        if (_volatileCloudActive.value) {
            _volatileSalaryPayments.value = cloudPayments
            return
        }
        cloudPayments.forEach { pay ->
            salaryPaymentDao.upsert(pay)
        }
    }

    suspend fun saveLocalUpiTransaction(tx: UpiTransactionEntity) {
        if (_volatileCloudActive.value) {
            _volatileUpiTransactions.value = (listOf(tx) + _volatileUpiTransactions.value.filterNot { it.paymentId == tx.paymentId })
                .sortedByDescending { it.updatedAt }
        } else {
            upiTxDao.upsert(tx)
        }
    }

    /**
     * Parts 16–17: Super Admin ONLY Employee Management. Admin and Technician are strictly denied.
     */
    suspend fun saveEmployee(
        actorRole: String,
        actorUid: String,
        companyId: String,
        employee: EmployeeEntity
    ): Result<EmployeeEntity> {
        if (normalizeRole(actorRole) != "super_admin") {
            return Result.failure(SecurityException("Employee Management is restricted to Super Admin only."))
        }
        val cleanName = employee.name.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Enter employee full name."))
        }
        if (employee.monthlySalary <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter monthly salary greater than 0."))
        }
        if (SalaryCalculationEngine.parseUtcCalendar(employee.hiringDate) == null) {
            return Result.failure(IllegalArgumentException("Enter valid Hiring Date (yyyy-MM-dd)."))
        }
        if (SalaryCalculationEngine.parseUtcCalendar(employee.workStartDate) == null) {
            return Result.failure(IllegalArgumentException("Enter valid Work Start Date (yyyy-MM-dd)."))
        }
        if (employee.workStopDate.isNotBlank() && SalaryCalculationEngine.parseUtcCalendar(employee.workStopDate) == null) {
            return Result.failure(IllegalArgumentException("Enter valid Work Stop Date (yyyy-MM-dd) or leave empty."))
        }
        val now = System.currentTimeMillis()
        val finalId = employee.employeeId.ifBlank { "emp_$now" }
        val finalCode = employee.employeeCode.ifBlank {
            "TS-EMP-${((now % 9000L) + 1000L)}"
        }
        val wsId = companyId.ifBlank { getActiveWorkspaceId() }
        val saved = employee.copy(
            employeeId = finalId,
            employeeCode = finalCode,
            companyId = wsId,
            name = cleanName,
            mobile = employee.mobile.trim(),
            hiringDate = employee.hiringDate.trim(),
            workStartDate = employee.workStartDate.trim(),
            workStopDate = employee.workStopDate.trim(),
            address = employee.address.trim(),
            notes = employee.notes.trim(),
            createdByUid = employee.createdByUid.ifBlank { actorUid },
            updatedAt = now
        )
        if (_volatileCloudActive.value) {
            _volatileEmployees.value = (listOf(saved) + _volatileEmployees.value.filterNot { it.employeeId == saved.employeeId })
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        } else {
            employeeDao.upsert(saved)
        }
        cloudSync?.pushEmployee(saved)
        return Result.success(saved)
    }

    suspend fun deleteEmployee(actorRole: String, employeeId: String): Result<Unit> {
        if (normalizeRole(actorRole) != "super_admin") {
            return Result.failure(SecurityException("Employee Management is restricted to Super Admin only."))
        }
        if (_volatileCloudActive.value) {
            _volatileEmployees.value = _volatileEmployees.value.filterNot { it.employeeId == employeeId }
        } else {
            employeeDao.deleteById(employeeId)
        }
        cloudSync?.deleteEmployee(employeeId)
        return Result.success(Unit)
    }

    suspend fun recordSalaryPayment(
        actorRole: String,
        actorUid: String,
        companyId: String,
        employeeId: String,
        amount: Double,
        paymentDate: String,
        method: String,
        referenceNo: String,
        notes: String,
        cycleStart: String,
        cycleEnd: String
    ): Result<SalaryPaymentEntity> {
        if (normalizeRole(actorRole) != "super_admin") {
            return Result.failure(SecurityException("Salary recording is restricted to Super Admin only."))
        }
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter salary payment amount greater than 0."))
        }
        if (SalaryCalculationEngine.parseUtcCalendar(paymentDate) == null) {
            return Result.failure(IllegalArgumentException("Enter valid payment date (yyyy-MM-dd)."))
        }
        val existingList = if (_volatileCloudActive.value) _volatileSalaryPayments.value else salaryPaymentDao.getAll()
        if (SalaryCalculationEngine.isDuplicatePayment(existingList, employeeId, paymentDate, amount, referenceNo)) {
            return Result.failure(IllegalStateException("Duplicate salary payment detected for the same date, amount, and reference."))
        }
        val now = System.currentTimeMillis()
        val wsId = companyId.ifBlank { getActiveWorkspaceId() }
        val payment = SalaryPaymentEntity(
            paymentId = "sal_${now}_${kotlin.math.abs((employeeId + referenceNo).hashCode()) % 1000}",
            employeeId = employeeId,
            companyId = wsId,
            amount = amount,
            paymentDate = paymentDate.trim(),
            method = method.trim().ifBlank { "Cash" },
            referenceNo = referenceNo.trim(),
            notes = notes.trim(),
            cycleStart = cycleStart.trim(),
            cycleEnd = cycleEnd.trim(),
            createdAt = now,
            createdByUid = actorUid
        )
        if (_volatileCloudActive.value) {
            _volatileSalaryPayments.value = (listOf(payment) + _volatileSalaryPayments.value)
                .sortedByDescending { it.createdAt }
        } else {
            salaryPaymentDao.upsert(payment)
        }
        cloudSync?.pushSalaryPayment(payment)
        return Result.success(payment)
    }

    suspend fun pushLocalDatasetToWorkspace(workspaceId: String) {
        val sync = cloudSync ?: return
        val cfgMap = configDao.getAll().associate { it.key to it.value }
        val company = CompanySettings(
            companyName = AppIdentity.OFFICIAL_COMPANY_NAME,
            mobile = cfgMap[KEY_COMPANY_MOBILE]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_MOBILE,
            email = cfgMap[KEY_COMPANY_EMAIL]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_EMAIL,
            address = cfgMap[KEY_COMPANY_ADDRESS]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_ADDRESS,
            authorisedSignatureBase64 = cfgMap[KEY_AUTHORISED_SIGNATURE].orEmpty(),
            logoBase64 = cfgMap[KEY_COMPANY_LOGO].orEmpty()
        )
        val upi = UpiSettings(
            upiId = cfgMap[KEY_UPI_ID].orEmpty(),
            payeeName = cfgMap[KEY_UPI_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_PAYEE_NAME,
            fallbackQrBase64 = cfgMap[KEY_FALLBACK_QR].orEmpty()
        )
        val defaults = VoiceAndNoteSettings()
        val voice = VoiceAndNoteSettings(
            headerNote = cfgMap[KEY_HEADER_NOTE] ?: defaults.headerNote,
            loginVoice = cfgMap[KEY_VOICE_LOGIN]?.takeIf { it.isNotBlank() } ?: defaults.loginVoice,
            postLoginVoice = cfgMap[KEY_VOICE_POST_LOGIN]?.takeIf { it.isNotBlank() } ?: defaults.postLoginVoice,
            billGeneratingVoice = cfgMap[KEY_VOICE_BILL_START]?.takeIf { it.isNotBlank() } ?: defaults.billGeneratingVoice,
            billCompletedVoice = cfgMap[KEY_VOICE_BILL_DONE]?.takeIf { it.isNotBlank() } ?: defaults.billCompletedVoice,
            voiceModelId = cfgMap[KEY_VOICE_MODEL_ID]?.takeIf { it.isNotBlank() } ?: defaults.voiceModelId
        )
        sync.uploadFullWorkspaceSnapshot(
            workspaceId = workspaceId,
            company = company,
            upi = upi,
            voice = voice,
            accounts = userDao.getAll(),
            products = productDao.getAll(),
            customers = customerDao.getAll(),
            bills = billDao.getAll(),
            helpRequests = helpDao.getAll(),
            settlements = settlementDao.getAll(),
            expenses = expenseDao.getAll(),
            trashItems = trashDao.getAll(),
            auditLogs = auditDao.getAll()
        )
        comboFileDao.getAll().forEach { combo ->
            sync.pushProductComboFile(workspaceId, combo)
        }
    }

    private fun billToJson(b: BillEntity): JSONObject = JSONObject().apply {
        put("id", b.id)
        put("no", b.invoiceNo)
        put("date", b.date)
        put("customer", b.customerName)
        put("details", b.customerDetails)
        put("items", JSONArray(b.itemsJson))
        put("subtotal", b.subtotal)
        put("discount", b.discount)
        put("grandTotal", b.grandTotal)
        put("paid", b.paidAmount)
        put("due", b.dueAmount)
        put("paymentMode", b.paymentMode)
        put("signature", b.signatureBase64)
        put("customQrText", b.customQrText)
        put("createdBy", b.createdBy)
        put("updatedAt", b.updatedAt)
        put("extraChargeType", b.extraChargeType)
        put("extraChargeAmount", b.extraChargeAmount)
        put("secondaryChargeType", b.secondaryChargeType)
        put("secondaryChargeAmount", b.secondaryChargeAmount)
        put("staffRating", b.staffRating)
        put("staffReviewNote", b.staffReviewNote)
        put("documentType", b.documentType)
        put("isHelpBill", b.isHelpBill)
        put("helpRequestId", b.helpRequestId)
        put("quotationNote", b.quotationNote)
        put("paymentStatus", b.paymentStatus)
        put("linkedPaymentId", b.linkedPaymentId)
        put("stockDeducted", b.stockDeducted)
    }

    private fun billFromJson(obj: JSONObject): BillEntity {
        val itemsStr = obj.optJSONArray("items")?.toString() ?: "[]"
        val sub = obj.optDouble("subtotal", 0.0)
        val disc = obj.optDouble("discount", 0.0)
        val grand = if (obj.has("grandTotal")) obj.optDouble("grandTotal", 0.0) else maxOf(0.0, sub - disc)
        val paid = if (obj.has("paid")) obj.optDouble("paid", 0.0) else obj.optDouble("advance", 0.0)
        val due = if (obj.has("due")) obj.optDouble("due", 0.0) else maxOf(0.0, grand - paid)
        val docType = obj.optString("documentType", DocumentType.BILL.name)
        return BillEntity(
            id = obj.optString("id", "b${System.currentTimeMillis()}"),
            invoiceNo = obj.optString("no", "TSE-001"),
            date = obj.optString("date", ""),
            customerName = obj.optString("customer", ""),
            customerDetails = obj.optString("details", ""),
            itemsJson = itemsStr,
            subtotal = sub,
            discount = disc,
            grandTotal = grand,
            paidAmount = paid,
            dueAmount = due,
            paymentMode = obj.optString("paymentMode", PaymentMode.ONLINE.label),
            signatureBase64 = obj.optString("signature", ""),
            customQrText = obj.optString("customQrText", ""),
            createdBy = obj.optString("createdBy", "Admin"),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            extraChargeType = obj.optString("extraChargeType", "NONE"),
            extraChargeAmount = obj.optDouble("extraChargeAmount", 0.0),
            secondaryChargeType = obj.optString("secondaryChargeType", "NONE"),
            secondaryChargeAmount = obj.optDouble("secondaryChargeAmount", 0.0),
            staffRating = obj.optInt("staffRating", 0).coerceIn(0, 5),
            staffReviewNote = obj.optString("staffReviewNote", ""),
            documentType = docType,
            isHelpBill = obj.optBoolean("isHelpBill", false),
            helpRequestId = obj.optLong("helpRequestId", 0L),
            quotationNote = obj.optString("quotationNote", ""),
            paymentStatus = obj.optString("paymentStatus", "").ifBlank { BillPaymentStatus.compute(grand, paid).code },
            linkedPaymentId = obj.optString("linkedPaymentId", ""),
            stockDeducted = obj.optBoolean("stockDeducted", docType == DocumentType.BILL.name)
        )
    }

    private fun customerToJson(c: CustomerEntity): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("details", c.details)
        put("phone", c.phone)
        put("address", c.address)
        put("locationUrl", c.locationUrl)
    }

    private fun customerFromJson(obj: JSONObject): CustomerEntity = CustomerEntity(
        id = obj.optLong("id", 0L),
        name = obj.optString("name", ""),
        details = obj.optString("details", ""),
        phone = obj.optString("phone", ""),
        address = obj.optString("address", ""),
        locationUrl = obj.optString("locationUrl", "")
    )

    private fun productToJson(p: ProductEntity): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("unit", p.unit)
        put("rate", p.defaultRate)
        put("folder", p.folder)
        put("stockQty", p.stockQty)
        put("minStockAlert", p.minStockAlert)
        put("purchasePrice", p.purchasePrice)
        put("sku", p.sku)
        put("barcode", p.barcode)
    }

    private fun productFromJson(obj: JSONObject): ProductEntity {
        val name = obj.optString("name", "").trim()
        return ProductEntity(
            id = obj.optLong("id", 0L),
            name = name,
            unit = obj.optString("unit", "Nos"),
            defaultRate = obj.optDouble("rate", 0.0),
            folder = obj.optString("folder", "").ifBlank { inferFolderForName(name) },
            stockQty = obj.optDouble("stockQty", 0.0).coerceAtLeast(0.0),
            minStockAlert = obj.optDouble("minStockAlert", 0.0).coerceAtLeast(0.0),
            purchasePrice = obj.optDouble("purchasePrice", 0.0).coerceAtLeast(0.0),
            sku = obj.optString("sku", ""),
            barcode = obj.optString("barcode", "")
        )
    }

    private fun helpToJson(h: CustomerHelpEntity): JSONObject = JSONObject().apply {
        put("id", h.id)
        put("customerName", h.customerName)
        put("phone", h.phone)
        put("address", h.address)
        put("locationUrl", h.locationUrl)
        put("helpDescription", h.helpDescription)
        put("status", h.status)
        put("createdAt", h.createdAt)
        put("updatedAt", h.updatedAt)
        put("acceptedBy", h.acceptedBy)
        put("acceptedByName", h.acceptedByName)
        put("acceptedByPhone", h.acceptedByPhone)
        put("acceptedAt", h.acceptedAt)
        put("solvedAt", h.solvedAt)
        put("billId", h.billId)
        put("category", h.category)
        put("quotationStatus", h.quotationStatus)
        put("installationDate", h.installationDate)
        put("installationReminderSent", h.installationReminderSent)
        put("rejectedReason", h.rejectedReason)
        put("assignedTechnician", h.assignedTechnician)
        put("helpTokenId", h.helpTokenId)
        put("quotationId", h.quotationId)
        put("approvedQuotationJson", h.approvedQuotationJson)
    }

    private fun helpFromJson(h: JSONObject): CustomerHelpEntity = CustomerHelpEntity(
        id = h.optLong("id", 0L),
        customerName = h.optString("customerName", ""),
        phone = h.optString("phone", ""),
        address = h.optString("address", ""),
        locationUrl = h.optString("locationUrl", ""),
        helpDescription = h.optString("helpDescription", ""),
        status = h.optString("status", "OPEN"),
        createdAt = h.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = h.optLong("updatedAt", System.currentTimeMillis()),
        acceptedBy = h.optString("acceptedBy", ""),
        acceptedByName = h.optString("acceptedByName", ""),
        acceptedByPhone = h.optString("acceptedByPhone", ""),
        acceptedAt = h.optLong("acceptedAt", 0L),
        solvedAt = h.optLong("solvedAt", 0L),
        billId = h.optString("billId", ""),
        category = HelpCategory.fromString(h.optString("category", "REGULAR_SERVICE")).code,
        quotationStatus = h.optString("quotationStatus", ""),
        installationDate = h.optString("installationDate", ""),
        installationReminderSent = h.optBoolean("installationReminderSent", false),
        rejectedReason = h.optString("rejectedReason", ""),
        assignedTechnician = h.optString("assignedTechnician", ""),
        helpTokenId = h.optString("helpTokenId", ""),
        quotationId = h.optString("quotationId", ""),
        approvedQuotationJson = h.optString("approvedQuotationJson", "")
    )

    private fun settlementToJson(s: TechnicianSettlementEntity): JSONObject = JSONObject().apply {
        put("technicianUsername", s.technicianUsername)
        put("amount", s.amount)
        put("note", s.note)
        put("recordedBy", s.recordedBy)
        put("createdAt", s.createdAt)
    }

    private fun settlementFromJson(s: JSONObject): TechnicianSettlementEntity = TechnicianSettlementEntity(
        technicianUsername = s.optString("technicianUsername", ""),
        amount = s.optDouble("amount", 0.0),
        note = s.optString("note", ""),
        recordedBy = s.optString("recordedBy", "Admin"),
        createdAt = s.optLong("createdAt", System.currentTimeMillis())
    )

    private fun expenseToJson(e: ExpenseEntity): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("title", e.title)
        put("category", e.category)
        put("amount", e.amount)
        put("date", e.date)
        put("note", e.note)
        put("recordedBy", e.recordedBy)
        put("createdAt", e.createdAt)
        put("updatedAt", e.updatedAt)
    }

    private fun expenseFromJson(e: JSONObject): ExpenseEntity = ExpenseEntity(
        id = e.optString("id", "exp_${System.currentTimeMillis()}"),
        title = e.optString("title", ""),
        category = e.optString("category", "General"),
        amount = e.optDouble("amount", 0.0),
        date = e.optString("date", ""),
        note = e.optString("note", ""),
        recordedBy = e.optString("recordedBy", "Admin"),
        createdAt = e.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = e.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun trashToJson(t: TrashItemEntity): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("entityType", t.entityType)
        put("entityId", t.entityId)
        put("title", t.title)
        put("summary", t.summary)
        put("payloadJson", t.payloadJson)
        put("deletedBy", t.deletedBy)
        put("deletedByRole", t.deletedByRole)
        put("status", t.status)
        put("deletedAt", t.deletedAt)
        put("expireAt", t.expireAt)
        put("restoredAt", t.restoredAt)
        put("restoredBy", t.restoredBy)
    }

    private fun trashFromJson(t: JSONObject): TrashItemEntity = TrashItemEntity(
        id = t.optString("id", "tr_${System.currentTimeMillis()}"),
        entityType = t.optString("entityType", ENTITY_BILL),
        entityId = t.optString("entityId", ""),
        title = t.optString("title", ""),
        summary = t.optString("summary", ""),
        payloadJson = t.optString("payloadJson", "{}"),
        deletedBy = t.optString("deletedBy", "Admin"),
        deletedByRole = t.optString("deletedByRole", "admin"),
        status = t.optString("status", "TRASHED"),
        deletedAt = t.optLong("deletedAt", System.currentTimeMillis()),
        expireAt = t.optLong("expireAt", System.currentTimeMillis() + TRASH_RETENTION_MS),
        restoredAt = t.optLong("restoredAt", 0L),
        restoredBy = t.optString("restoredBy", "")
    )

    private fun auditToJson(a: AuditLogEntity): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("action", a.action)
        put("entityType", a.entityType)
        put("entityId", a.entityId)
        put("title", a.title)
        put("actorUsername", a.actorUsername)
        put("actorRole", a.actorRole)
        put("details", a.details)
        put("createdAt", a.createdAt)
    }

    private fun auditFromJson(a: JSONObject): AuditLogEntity = AuditLogEntity(
        id = a.optString("id", "log_${System.currentTimeMillis()}"),
        action = a.optString("action", "DELETE"),
        entityType = a.optString("entityType", ""),
        entityId = a.optString("entityId", ""),
        title = a.optString("title", ""),
        actorUsername = a.optString("actorUsername", "System"),
        actorRole = a.optString("actorRole", "system"),
        details = a.optString("details", ""),
        createdAt = a.optLong("createdAt", System.currentTimeMillis())
    )

    companion object {
        const val HELP_SOLVE_WAIT_MS = 15 * 60 * 1000L
        const val TRASH_RETENTION_MS = 24L * 60L * 60L * 1000L

        const val ENTITY_BILL = "BILL"
        const val ENTITY_CUSTOMER = "CUSTOMER"
        const val ENTITY_PRODUCT = "PRODUCT"
        const val ENTITY_EXPENSE = "EXPENSE"
        const val ENTITY_HELP = "HELP"
        const val ENTITY_SETTLEMENT = "SETTLEMENT"

        const val KEY_SEEDED = "seeded_v1"
        const val KEY_STAFF_APK_MODE = "staff_apk_mode_v1"
        const val KEY_COMBO_PACKAGES = "combo_packages_json"
        const val COMBO_STORAGE_FOLDER = "__COMBO_PKG__"
        const val KEY_PREFILL_INVITE_CODE = "prefill_invite_code_v1"
        const val KEY_STOCK_TRACKED_PREFIX = "stock_tracked_"
        const val KEY_CLOUD_WORKSPACE_ID = "cloud_workspace_id"
        const val KEY_CLOUD_OWNER_UID = "cloud_owner_uid"
        const val KEY_CLOUD_TECH_CODE = "cloud_tech_share_code"
        const val KEY_CLOUD_SUPER_CODE = "cloud_super_share_code"
        const val KEY_CLOUD_DEVICE_ROLE = "cloud_device_role"
        const val KEY_COMPANY_NAME = "company_name"
        const val KEY_COMPANY_MOBILE = "company_mobile"
        const val KEY_COMPANY_EMAIL = "company_email"
        const val KEY_COMPANY_ADDRESS = "company_address"
        const val KEY_AUTHORISED_SIGNATURE = "authorised_signature_b64"
        const val KEY_COMPANY_LOGO = "company_logo_b64"
        const val KEY_UPI_ID = "upi_id"
        const val KEY_UPI_NAME = "upi_name"
        const val KEY_FALLBACK_QR = "fallback_qr"
        const val KEY_FINAL_CODE_HASH = "final_code_hash"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_LANGUAGE = "language"
        const val KEY_HEADER_NOTE = "header_marquee_note"
        const val KEY_VOICE_LOGIN = "voice_login_script"
        const val KEY_VOICE_POST_LOGIN = "voice_post_login_script"
        const val KEY_VOICE_BILL_START = "voice_bill_start_script"
        const val KEY_VOICE_BILL_DONE = "voice_bill_done_script"
        const val KEY_VOICE_MODEL_ID = "voice_model_id"
        const val KEY_DEVICE_AUTH_USER_PREFIX = "device_auth_user_"
        const val KEY_LOGIN_FAILED_ATTEMPTS = "login_failed_attempts"
        const val KEY_LOGIN_LOCK_UNTIL_MS = "login_lock_until_ms"
        const val KEY_ADMIN_FAILED_ATTEMPTS = "admin_login_failed_attempts"
        const val KEY_ADMIN_LOCK_UNTIL_MS = "admin_login_lock_until_ms"
        const val KEY_PERMISSIONS_PROMPTED = "permissions_prompted_v1"
        const val KEY_TECH_VERIFIED_EMAIL = "tech_verified_email_v1"
        const val KEY_TECH_VERIFIED_USERNAME = "tech_verified_username_v1"
        const val KEY_TECH_VERIFIED_NAME = "tech_verified_name_v1"
        const val KEY_TECH_VERIFIED_WORKSPACE_ID = "tech_verified_workspace_id_v1"
        const val KEY_TECH_VERIFIED_COMPANY = "tech_verified_company_v1"
        const val KEY_TECH_VERIFIED_TECH_ID = "tech_verified_tech_id_v1"
        const val KEY_TECH_VERIFIED_PHONE = "tech_verified_phone_v1"
        const val KEY_TECH_MUST_CHANGE_PASSWORD = "tech_must_change_password_v1"
        const val KEY_PENDING_ADMIN_UID = "pending_admin_uid_v1"
        const val KEY_PENDING_ADMIN_EMAIL = "pending_admin_email_v1"
        const val KEY_PENDING_ADMIN_NAME = "pending_admin_name_v1"
        const val KEY_PENDING_ADMIN_MOBILE = "pending_admin_mobile_v1"
        const val KEY_PENDING_ADMIN_TWO_FACTOR_HASH = "pending_admin_two_factor_hash_v1"

        const val DEFAULT_ADMIN_RECOVERY_PASSWORD = "Manoj2007"
        const val DEFAULT_COMPANY_NAME = AppIdentity.OFFICIAL_COMPANY_NAME
        const val DEFAULT_COMPANY_MOBILE = "7083805120"
        const val DEFAULT_COMPANY_EMAIL = "totalsolution7020@gmail.com"
        const val DEFAULT_COMPANY_ADDRESS = "B1, Santa Anna Holiday Homes, Naika Vaddo, Calangute, North Goa 403516."
        const val DEFAULT_PAYEE_NAME = AppIdentity.OFFICIAL_COMPANY_NAME

        fun normalizeRole(raw: String): String {
            return when (raw.trim().lowercase(Locale.ROOT)) {
                "admin" -> "admin"
                "super_admin", "superadmin" -> "super_admin"
                else -> "technician"
            }
        }

        private fun inferFolderForName(name: String): String {
            val low = name.lowercase(Locale.ROOT)
            return when {
                low.contains("camera") -> "CCTV Cameras"
                low.contains("dvr") || low.contains("nvr") -> "DVR & NVR"
                low.contains("hard disk") || low.contains("smps") || low.contains("power") -> "Storage & Power"
                low.contains("cable") || low.contains("connector") || low.contains("box") || low.contains("rack") || low.contains("hdmi") -> "Cables & Accessories"
                low.contains("installation") || low.contains("service") || low.contains("support") -> "Services"
                else -> "General"
            }
        }

        val STARTER_PRODUCTS = listOf(
            ProductEntity(name = "2MP HD Dome Camera", unit = "Nos", defaultRate = 0.0, folder = "CCTV Cameras"),
            ProductEntity(name = "2MP HD Bullet Camera", unit = "Nos", defaultRate = 0.0, folder = "CCTV Cameras"),
            ProductEntity(name = "5MP HD Dome Camera", unit = "Nos", defaultRate = 0.0, folder = "CCTV Cameras"),
            ProductEntity(name = "5MP HD Bullet Camera", unit = "Nos", defaultRate = 0.0, folder = "CCTV Cameras"),
            ProductEntity(name = "IP Camera 2MP", unit = "Nos", defaultRate = 0.0, folder = "CCTV Cameras"),
            ProductEntity(name = "IP Camera 4MP", unit = "Nos", defaultRate = 0.0, folder = "CCTV Cameras"),
            ProductEntity(name = "4 Channel DVR", unit = "Nos", defaultRate = 0.0, folder = "DVR & NVR"),
            ProductEntity(name = "8 Channel DVR", unit = "Nos", defaultRate = 0.0, folder = "DVR & NVR"),
            ProductEntity(name = "16 Channel DVR", unit = "Nos", defaultRate = 0.0, folder = "DVR & NVR"),
            ProductEntity(name = "4 Channel NVR", unit = "Nos", defaultRate = 0.0, folder = "DVR & NVR"),
            ProductEntity(name = "8 Channel NVR", unit = "Nos", defaultRate = 0.0, folder = "DVR & NVR"),
            ProductEntity(name = "1TB Surveillance Hard Disk", unit = "Nos", defaultRate = 0.0, folder = "Storage & Power"),
            ProductEntity(name = "2TB Surveillance Hard Disk", unit = "Nos", defaultRate = 0.0, folder = "Storage & Power"),
            ProductEntity(name = "4TB Surveillance Hard Disk", unit = "Nos", defaultRate = 0.0, folder = "Storage & Power"),
            ProductEntity(name = "4 Ch Power Supply (SMPS)", unit = "Nos", defaultRate = 0.0, folder = "Storage & Power"),
            ProductEntity(name = "8 Ch Power Supply (SMPS)", unit = "Nos", defaultRate = 0.0, folder = "Storage & Power"),
            ProductEntity(name = "3+1 CCTV Cable", unit = "Mtr", defaultRate = 0.0, folder = "Cables & Accessories"),
            ProductEntity(name = "CAT6 Networking Cable", unit = "Mtr", defaultRate = 0.0, folder = "Cables & Accessories"),
            ProductEntity(name = "BNC Connector", unit = "Nos", defaultRate = 0.0, folder = "Cables & Accessories"),
            ProductEntity(name = "DC Pin Connector", unit = "Nos", defaultRate = 0.0, folder = "Cables & Accessories"),
            ProductEntity(name = "PVC Junction Box (4x4)", unit = "Nos", defaultRate = 0.0, folder = "Cables & Accessories"),
            ProductEntity(name = "DVR Rack / Cabinet", unit = "Nos", defaultRate = 0.0, folder = "Cables & Accessories"),
            ProductEntity(name = "HDMI Cable", unit = "Nos", defaultRate = 0.0, folder = "Cables & Accessories"),
            ProductEntity(name = "Camera Installation & Configuration Charges", unit = "Job", defaultRate = 0.0, folder = "Services")
        )

        fun extractCustomerPhonesDigits(customer: CustomerEntity): List<String> {
            return buildList {
                val pDigits = customer.phone.filter { it.isDigit() }
                if (pDigits.isNotEmpty()) add(pDigits)
                customer.details.lines().forEach { line ->
                    val d = line.filter { it.isDigit() }
                    if (d.length >= 5) add(d)
                }
            }.distinct()
        }

        fun filterCustomers(customers: List<CustomerEntity>, rawQuery: String): List<CustomerEntity> {
            val q = rawQuery.trim()
            if (q.isEmpty()) return customers
            val qDigits = q.filter { it.isDigit() }
            return customers.filter { cust ->
                cust.name.contains(q, ignoreCase = true) ||
                    cust.address.contains(q, ignoreCase = true) ||
                    cust.details.contains(q, ignoreCase = true) ||
                    cust.phone.contains(q, ignoreCase = true) ||
                    (qDigits.isNotEmpty() && extractCustomerPhonesDigits(cust).any { digits ->
                        digits.endsWith(qDigits) || digits.contains(qDigits)
                    })
            }
        }

        private val RELATED_PRODUCT_GROUPS = listOf(
            setOf("cam", "camera", "cameras", "cctv", "dome", "bullet", "ip", "ptz", "nightvision", "colorvu", "hd"),
            setOf("dvr", "nvr", "xvr", "recorder", "channel", "ch"),
            setOf("hdd", "harddisk", "hard", "disk", "storage", "tb", "ssd", "memory"),
            setOf("wire", "cable", "taar", "3+1", "cat6", "hdmi", "coaxial", "lan"),
            setOf("power", "smps", "supply", "adapter", "adaptor", "psu"),
            setOf("connector", "bnc", "dc", "pin", "jack", "rj45", "box", "junction", "rack", "cabinet"),
            setOf("combo", "kit", "package", "set", "setup", "quotation", "bundle"),
            setOf("install", "installation", "service", "repair", "fitting", "support", "maintenance", "labour", "labor", "charge")
        )

        fun filterProducts(products: List<ProductEntity>, rawQuery: String): List<ProductEntity> {
            val q = rawQuery.trim().lowercase(Locale.ROOT)
            if (q.isEmpty()) return products
            val tokens = q.split(Regex("[\\s,/+\\-]+")).filter { it.isNotBlank() }

            val relatedTermsByToken = tokens.map { tok ->
                val groupMatches = RELATED_PRODUCT_GROUPS
                    .filter { grp ->
                        tok in grp || (tok.length >= 3 && grp.any { word -> word.startsWith(tok) || tok.startsWith(word) })
                    }
                    .flatten()
                    .toSet()
                groupMatches - tok
            }

            return products.mapNotNull { prod ->
                val nameLow = prod.name.lowercase(Locale.ROOT)
                val skuLow = prod.sku.trim().lowercase(Locale.ROOT)
                val barcodeLow = prod.barcode.trim().lowercase(Locale.ROOT)
                val folderLow = prod.folder.ifBlank { "General" }.lowercase(Locale.ROOT)
                val unitLow = prod.unit.lowercase(Locale.ROOT)
                val haystack = "$nameLow $skuLow $barcodeLow $folderLow $unitLow"
                val words = haystack.split(Regex("[\\s,/+\\-()]+")).filter { it.isNotBlank() }

                var score = 0
                if (skuLow.isNotEmpty() && skuLow == q) score += 1600
                else if (skuLow.isNotEmpty() && skuLow.contains(q)) score += 950
                if (barcodeLow.isNotEmpty() && barcodeLow == q) score += 1600
                else if (barcodeLow.isNotEmpty() && barcodeLow.contains(q)) score += 950

                if (nameLow.contains(q)) {
                    score += 1000
                    if (nameLow.startsWith(q)) score += 200
                }
                if (folderLow.contains(q)) score += 600

                if (tokens.isNotEmpty() && tokens.all { haystack.contains(it) }) {
                    score += 450
                }

                var directTokenHits = 0
                tokens.forEach { tok ->
                    if (haystack.contains(tok)) {
                        directTokenHits++
                        score += 120
                    } else if (tok.length >= 2 && words.any { w -> w.startsWith(tok) || (w.length >= 3 && tok.startsWith(w)) }) {
                        directTokenHits++
                        score += 75
                    }
                }

                relatedTermsByToken.forEach { synonyms ->
                    if (synonyms.any { syn -> haystack.contains(syn) }) {
                        score += if (directTokenHits > 0) 45 else 35
                    }
                }

                if (score > 0) prod to score else null
            }
                .sortedWith(
                    compareByDescending<Pair<ProductEntity, Int>> { it.second }
                        .thenBy { !it.first.folder.equals("Combo", ignoreCase = true) }
                        .thenBy { it.first.name.lowercase(Locale.ROOT) }
                )
                .map { it.first }
        }

        fun filterBillsByPaymentFilter(
            bills: List<BillEntity>,
            upiTransactions: List<UpiTransactionEntity>,
            filter: BillPaymentFilter
        ): List<BillEntity> {
            val successBillIds = upiTransactions
                .filter { PaymentGatewayStatus.fromString(it.status) == PaymentGatewayStatus.SUCCESS }
                .mapTo(mutableSetOf()) { it.billId }
            val pendingBillIds = upiTransactions
                .filter { PaymentGatewayStatus.fromString(it.status) == PaymentGatewayStatus.PENDING }
                .mapTo(mutableSetOf()) { it.billId }
            val failedBillIds = upiTransactions
                .filter {
                    val s = PaymentGatewayStatus.fromString(it.status)
                    s == PaymentGatewayStatus.FAILED || s == PaymentGatewayStatus.EXPIRED || s == PaymentGatewayStatus.CANCELLED
                }
                .mapTo(mutableSetOf()) { it.billId }

            return when (filter) {
                BillPaymentFilter.ALL -> bills
                BillPaymentFilter.SUCCESSFUL_PAYMENTS -> bills.filter { b ->
                    b.id in successBillIds ||
                        (b.linkedPaymentId.isNotBlank() && BillPaymentStatus.resolveForBill(b) == BillPaymentStatus.PAID)
                }
                BillPaymentFilter.PENDING_PAYMENTS -> bills.filter { b ->
                    b.id in pendingBillIds && b.id !in successBillIds
                }
                BillPaymentFilter.FAILED_PAYMENTS -> bills.filter { b ->
                    b.id in failedBillIds && b.id !in successBillIds
                }
                BillPaymentFilter.PAID_BILLS -> bills.filter { b ->
                    DocumentType.fromString(b.documentType) == DocumentType.BILL &&
                        BillPaymentStatus.resolveForBill(b) == BillPaymentStatus.PAID
                }
                BillPaymentFilter.UNPAID_BILLS -> bills.filter { b ->
                    DocumentType.fromString(b.documentType) == DocumentType.BILL &&
                        BillPaymentStatus.resolveForBill(b) == BillPaymentStatus.UNPAID
                }
                BillPaymentFilter.PARTIALLY_PAID_BILLS -> bills.filter { b ->
                    DocumentType.fromString(b.documentType) == DocumentType.BILL &&
                        BillPaymentStatus.resolveForBill(b) == BillPaymentStatus.PARTIAL
                }
            }
        }

        fun findMatchingCustomersByPhone(customers: List<CustomerEntity>, phoneInput: String): List<CustomerEntity> {
            val qDigits = phoneInput.filter { it.isDigit() }
            if (qDigits.length < 5) return emptyList()
            return customers.filter { cust ->
                extractCustomerPhonesDigits(cust).any { digits ->
                    digits == qDigits || digits.endsWith(qDigits) || (qDigits.length >= 7 && digits.contains(qDigits))
                }
            }
        }
    }
}
