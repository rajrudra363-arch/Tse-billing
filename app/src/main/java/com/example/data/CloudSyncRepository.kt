package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Date
import java.util.Locale

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    GET,
    LIST,
    WRITE
}

data class CloudWorkspaceInfo(
    val workspaceId: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val techShareCode: String = "",
    val superShareCode: String = "",
    val deviceRole: String = "admin",
    val companyName: String = ""
)

data class CloudWorkspaceSnapshot(
    val workspace: CloudWorkspaceInfo,
    val companySettings: CompanySettings,
    val upiSettings: UpiSettings,
    val voiceAndNoteSettings: VoiceAndNoteSettings,
    val accounts: List<UserAccountEntity>,
    val products: List<ProductEntity>,
    val customers: List<CustomerEntity>,
    val bills: List<BillEntity>,
    val helpRequests: List<CustomerHelpEntity>,
    val settlements: List<TechnicianSettlementEntity>,
    val expenses: List<ExpenseEntity> = emptyList(),
    val trashItems: List<TrashItemEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList()
)

class CloudSyncRepository(private val context: Context) {
    companion object {
        private const val TAG = "CloudSyncRepository"
        private const val SHARE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val MAX_BASE64_CHARS = 180_000
        private const val OFFLINE_WRITE_WAIT_MS = 1500L
    }

    private val random = SecureRandom()
    private val registrations = mutableListOf<ListenerRegistration>()
    @Volatile
    private var cacheConfigured = false

    private fun getAuth(): FirebaseAuth? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        FirebaseAuth.getInstance()
    }.getOrNull()

    private fun getFirestore(): FirebaseFirestore? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val dbId = context.getString(R.string.firestore_database_id)
        val instance = FirebaseFirestore.getInstance(dbId)
        if (!cacheConfigured) {
            runCatching {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
                instance.firestoreSettings = settings
            }
            cacheConfigured = true
        }
        instance
    }.getOrNull()

    private suspend fun <T> Task<T>.awaitOfflineSafe(timeoutMs: Long = OFFLINE_WRITE_WAIT_MS): T? =
        withTimeoutOrNull(timeoutMs) { await() }

    private fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String) {
        val user = getAuth()?.currentUser
        val payload = runCatching {
            JSONObject().apply {
                put("operationType", operationType.name)
                put("path", path)
                put("uid", user?.uid ?: "none")
                put("email", user?.email ?: "none")
                put("message", exception.message ?: exception.javaClass.simpleName)
            }.toString()
        }.getOrElse {
            "FirestoreError[${operationType.name}] path=$path msg=${exception.message}"
        }
        Log.e(TAG, "FirestoreError $payload", exception)
    }

    private fun generateShareCode(prefix: String): String {
        val sb = StringBuilder(prefix)
        repeat(8) {
            sb.append(SHARE_ALPHABET[random.nextInt(SHARE_ALPHABET.length)])
        }
        return sb.toString()
    }

    private fun sanitizeBase64(b64: String): String =
        if (b64.length > MAX_BASE64_CHARS) "" else b64

    fun stopListeners() {
        registrations.forEach { reg -> runCatching { reg.remove() } }
        registrations.clear()
    }

    suspend fun discoverWorkspaceForSignedInUser(): Result<CloudWorkspaceInfo?> = withContext(Dispatchers.IO) {
        val auth = getAuth()
        val uid = auth?.currentUser?.uid
        val db = getFirestore()
        if (uid.isNullOrBlank() || db == null) {
            return@withContext Result.success(null)
        }
        try {
            val owned = db.collection("workspaces")
                .whereEqualTo("ownerUid", uid)
                .limit(1)
                .get()
                .await()
            val ownerDoc = owned.documents.firstOrNull()
            if (ownerDoc != null && ownerDoc.exists()) {
                return@withContext Result.success(parseWorkspaceDoc(ownerDoc, "admin"))
            }

            val memberQuery = db.collection("workspaces")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()
            val memberDoc = memberQuery.documents.firstOrNull()
            if (memberDoc != null && memberDoc.exists()) {
                val memberSnap = db.collection("workspaces")
                    .document(memberDoc.id)
                    .collection("members")
                    .document(uid)
                    .get()
                    .await()
                val role = memberSnap.getString("role")?.takeIf { it.isNotBlank() } ?: "technician"
                return@withContext Result.success(parseWorkspaceDoc(memberDoc, role))
            }
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "workspaces")
            Result.failure(e)
        }
    }

    suspend fun ensureAdminWorkspace(
        company: CompanySettings,
        upi: UpiSettings,
        voice: VoiceAndNoteSettings,
        existingWorkspaceId: String = ""
    ): Result<CloudWorkspaceInfo> = withContext(Dispatchers.IO) {
        val auth = getAuth()
        val user = auth?.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Please sign in with Google first."))
        val db = getFirestore()
            ?: return@withContext Result.failure(IllegalStateException("Cloud database is not configured."))
        val uid = user.uid

        try {
            if (existingWorkspaceId.isNotBlank()) {
                val existingDoc = db.collection("workspaces").document(existingWorkspaceId).get().await()
                if (existingDoc.exists() && existingDoc.getString("ownerUid") == uid) {
                    return@withContext Result.success(parseWorkspaceDoc(existingDoc, "admin"))
                }
            }

            val owned = db.collection("workspaces")
                .whereEqualTo("ownerUid", uid)
                .limit(1)
                .get()
                .await()
            val found = owned.documents.firstOrNull()
            if (found != null && found.exists()) {
                return@withContext Result.success(parseWorkspaceDoc(found, "admin"))
            }

            val wsId = "ws_${uid.take(8)}_${System.currentTimeMillis() % 100000}"
            val techCode = generateShareCode("TSE-T-")
            val superCode = generateShareCode("TSE-S-")
            val safeCompany = company.companyName.trim().ifBlank { BillingRepository.DEFAULT_COMPANY_NAME }

            val wsPayload = hashMapOf<String, Any>(
                "workspaceId" to wsId,
                "ownerUid" to uid,
                "ownerEmail" to user.email.orEmpty(),
                "techShareCode" to techCode,
                "superShareCode" to superCode,
                "companyName" to safeCompany,
                "mobile" to company.mobile,
                "email" to company.email,
                "address" to company.address,
                "upiId" to upi.upiId,
                "payeeName" to upi.payeeName,
                "logoBase64" to sanitizeBase64(company.logoBase64),
                "authorisedSignatureBase64" to sanitizeBase64(company.authorisedSignatureBase64),
                "fallbackQrBase64" to sanitizeBase64(upi.fallbackQrBase64),
                "headerNote" to voice.headerNote,
                "voiceLogin" to voice.loginVoice,
                "voicePostLogin" to voice.postLoginVoice,
                "voiceBillStart" to voice.billGeneratingVoice,
                "voiceBillDone" to voice.billCompletedVoice,
                "voiceModelId" to voice.voiceModelId,
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspaces").document(wsId).set(wsPayload).await()

            val ownerMember = hashMapOf<String, Any>(
                "userId" to uid,
                "email" to user.email.orEmpty(),
                "displayName" to user.displayName.orEmpty().ifBlank { "Admin" },
                "role" to "admin",
                "inviteCode" to "OWNER",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspaces").document(wsId)
                .collection("members").document(uid)
                .set(ownerMember).await()

            ensureShareDirectories(db, wsId, uid, safeCompany, techCode, superCode)

            Result.success(
                CloudWorkspaceInfo(
                    workspaceId = wsId,
                    ownerUid = uid,
                    ownerEmail = user.email.orEmpty(),
                    techShareCode = techCode,
                    superShareCode = superCode,
                    deviceRole = "admin",
                    companyName = safeCompany
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "workspaces")
            Result.failure(e)
        }
    }

    private suspend fun ensureShareDirectories(
        db: FirebaseFirestore,
        workspaceId: String,
        ownerUid: String,
        companyName: String,
        techCode: String,
        superCode: String
    ) {
        val techDir = hashMapOf<String, Any>(
            "shareCode" to techCode,
            "workspaceId" to workspaceId,
            "ownerUid" to ownerUid,
            "role" to "technician",
            "companyName" to companyName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val superDir = hashMapOf<String, Any>(
            "shareCode" to superCode,
            "workspaceId" to workspaceId,
            "ownerUid" to ownerUid,
            "role" to "super_admin",
            "companyName" to companyName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("workspaceDirectories").document(techCode).set(techDir).await()
        db.collection("workspaceDirectories").document(superCode).set(superDir).await()
    }

    suspend fun joinWorkspaceByInviteCode(rawInviteCode: String): Result<CloudWorkspaceInfo> =
        withContext(Dispatchers.IO) {
            val cleanCode = rawInviteCode.trim().uppercase(Locale.US)
            if (cleanCode.length < 8) {
                return@withContext Result.failure(IllegalArgumentException("Enter the Invite Code shared by Admin."))
            }
            val auth = getAuth()
            val user = auth?.currentUser
                ?: return@withContext Result.failure(IllegalStateException("Please sign in with Google first."))
            val db = getFirestore()
                ?: return@withContext Result.failure(IllegalStateException("Cloud database is not initialized."))

            try {
                val dirSnap = db.collection("workspaceDirectories").document(cleanCode).get().await()
                if (!dirSnap.exists()) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid or expired Invite Code."))
                }
                val wsId = dirSnap.getString("workspaceId").orEmpty()
                val role = dirSnap.getString("role").orEmpty().ifBlank { "technician" }
                if (wsId.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Workspace record missing."))
                }

                var resolvedRole = role
                val existingMember = db.collection("workspaces").document(wsId)
                    .collection("members").document(user.uid).get().await()
                if (!existingMember.exists()) {
                    val memberData = hashMapOf<String, Any>(
                        "userId" to user.uid,
                        "email" to user.email.orEmpty(),
                        "displayName" to user.displayName.orEmpty().ifBlank { role },
                        "role" to role,
                        "inviteCode" to cleanCode,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("workspaces").document(wsId)
                        .collection("members").document(user.uid)
                        .set(memberData).await()
                } else {
                    resolvedRole = existingMember.getString("role")?.takeIf { it.isNotBlank() } ?: role
                }

                val wsSnap = db.collection("workspaces").document(wsId).get().await()
                if (!wsSnap.exists()) {
                    return@withContext Result.failure(IllegalStateException("Admin workspace no longer exists."))
                }
                Result.success(parseWorkspaceDoc(wsSnap, resolvedRole))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, "workspaceDirectories/$cleanCode")
                Result.failure(e)
            }
        }

    suspend fun uploadFullWorkspaceSnapshot(
        workspaceId: String,
        company: CompanySettings,
        upi: UpiSettings,
        voice: VoiceAndNoteSettings,
        accounts: List<UserAccountEntity>,
        products: List<ProductEntity>,
        customers: List<CustomerEntity>,
        bills: List<BillEntity>,
        helpRequests: List<CustomerHelpEntity>,
        settlements: List<TechnicianSettlementEntity>,
        expenses: List<ExpenseEntity> = emptyList(),
        trashItems: List<TrashItemEntity> = emptyList(),
        auditLogs: List<AuditLogEntity> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (workspaceId.isBlank() || getAuth()?.currentUser == null) {
            return@withContext Result.success(Unit)
        }
        try {
            pushWorkspaceConfig(workspaceId, company, upi, voice)
            accounts.forEach { pushAccount(workspaceId, it) }
            products.forEach { pushProduct(workspaceId, it) }
            customers.forEach { pushCustomer(workspaceId, it) }
            bills.forEach { pushBill(workspaceId, it) }
            helpRequests.forEach { pushHelpRequest(workspaceId, it, isTechnicianStatusUpdate = false) }
            settlements.forEach { pushSettlement(workspaceId, it) }
            expenses.forEach { pushExpense(workspaceId, it) }
            trashItems.forEach { pushTrashItem(workspaceId, it) }
            auditLogs.forEach { pushAuditLog(workspaceId, it) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId")
            Result.failure(e)
        }
    }

    suspend fun fetchFullWorkspaceSnapshot(workspaceId: String, deviceRole: String): Result<CloudWorkspaceSnapshot> =
        withContext(Dispatchers.IO) {
            val uid = getAuth()?.currentUser?.uid
            val db = getFirestore()
            if (uid.isNullOrBlank() || db == null || workspaceId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Sign-in or Workspace not active."))
            }
            val wsRef = db.collection("workspaces").document(workspaceId)
            val normRole = BillingRepository.normalizeRole(deviceRole)
            try {
                val wsSnap = wsRef.get().await()
                if (!wsSnap.exists()) {
                    return@withContext Result.failure(IllegalStateException("Workspace not found."))
                }
                val accountsSnap = wsRef.collection("accounts").get().await()
                val productsSnap = wsRef.collection("products").get().await()
                val customersSnap = wsRef.collection("customers").get().await()
                val helpSnap = wsRef.collection("helpRequests").get().await()

                val billsList = if (normRole == "admin" || normRole == "super_admin") {
                    runCatching { wsRef.collection("bills").get().await().documents.mapNotNull(::parseBillDoc) }
                        .getOrElse { emptyList() }
                        .sortedByDescending { it.updatedAt }
                } else emptyList()

                val settlementsList = if (normRole == "admin" || normRole == "super_admin") {
                    runCatching { wsRef.collection("settlements").get().await().documents.mapNotNull(::parseSettlementDoc) }
                        .getOrElse { emptyList() }
                        .sortedByDescending { it.createdAt }
                } else emptyList()

                val expensesList = if (normRole == "admin" || normRole == "super_admin") {
                    runCatching { wsRef.collection("expenses").get().await().documents.mapNotNull(::parseExpenseDoc) }
                        .getOrElse { emptyList() }
                        .sortedByDescending { it.updatedAt }
                } else emptyList()

                val trashList = if (normRole == "admin") {
                    runCatching { wsRef.collection("trash").get().await().documents.mapNotNull(::parseTrashDoc) }
                        .getOrElse { emptyList() }
                        .sortedByDescending { it.deletedAt }
                } else emptyList()

                val auditList = if (normRole == "admin" || normRole == "super_admin") {
                    runCatching { wsRef.collection("auditLogs").get().await().documents.mapNotNull(::parseAuditLogDoc) }
                        .getOrElse { emptyList() }
                        .sortedByDescending { it.createdAt }
                } else emptyList()

                val snapshot = CloudWorkspaceSnapshot(
                    workspace = parseWorkspaceDoc(wsSnap, deviceRole),
                    companySettings = parseCompanySettings(wsSnap),
                    upiSettings = parseUpiSettings(wsSnap),
                    voiceAndNoteSettings = parseVoiceSettings(wsSnap),
                    accounts = accountsSnap.documents.mapNotNull(::parseAccountDoc),
                    products = productsSnap.documents.mapNotNull(::parseProductDoc),
                    customers = customersSnap.documents.mapNotNull(::parseCustomerDoc),
                    bills = billsList,
                    helpRequests = helpSnap.documents.mapNotNull(::parseHelpDoc).sortedByDescending { it.updatedAt },
                    settlements = settlementsList,
                    expenses = expensesList,
                    trashItems = trashList,
                    auditLogs = auditList
                )
                Result.success(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "workspaces/$workspaceId")
                Result.failure(e)
            }
        }

    fun startRealtimeWorkspaceSync(
        workspaceId: String,
        deviceRole: String,
        onWorkspaceMetaChanged: (CloudWorkspaceInfo, CompanySettings, UpiSettings, VoiceAndNoteSettings) -> Unit,
        onAccountsChanged: (List<UserAccountEntity>) -> Unit,
        onProductsChanged: (List<ProductEntity>) -> Unit,
        onCustomersChanged: (List<CustomerEntity>) -> Unit,
        onBillsChanged: (List<BillEntity>) -> Unit,
        onHelpRequestsChanged: (List<CustomerHelpEntity>) -> Unit,
        onSettlementsChanged: (List<TechnicianSettlementEntity>) -> Unit,
        onExpensesChanged: (List<ExpenseEntity>) -> Unit = {},
        onTrashChanged: (List<TrashItemEntity>) -> Unit = {},
        onAuditLogsChanged: (List<AuditLogEntity>) -> Unit = {}
    ) {
        stopListeners()
        val uid = getAuth()?.currentUser?.uid ?: return
        val db = getFirestore() ?: return
        if (workspaceId.isBlank() || uid.isBlank()) return
        val wsRef = db.collection("workspaces").document(workspaceId)
        val normRole = BillingRepository.normalizeRole(deviceRole)

        registrations += wsRef.addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.GET, "workspaces/$workspaceId")
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                onWorkspaceMetaChanged(
                    parseWorkspaceDoc(snap, deviceRole),
                    parseCompanySettings(snap),
                    parseUpiSettings(snap),
                    parseVoiceSettings(snap)
                )
            }
        }

        registrations += wsRef.collection("accounts").addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/accounts")
                return@addSnapshotListener
            }
            if (snap != null) {
                onAccountsChanged(snap.documents.mapNotNull(::parseAccountDoc))
            }
        }

        registrations += wsRef.collection("products").addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/products")
                return@addSnapshotListener
            }
            if (snap != null) {
                onProductsChanged(snap.documents.mapNotNull(::parseProductDoc))
            }
        }

        registrations += wsRef.collection("customers").addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/customers")
                return@addSnapshotListener
            }
            if (snap != null) {
                onCustomersChanged(snap.documents.mapNotNull(::parseCustomerDoc))
            }
        }

        registrations += wsRef.collection("helpRequests").addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/helpRequests")
                return@addSnapshotListener
            }
            if (snap != null) {
                onHelpRequestsChanged(snap.documents.mapNotNull(::parseHelpDoc).sortedByDescending { it.updatedAt })
            }
        }

        if (normRole == "admin" || normRole == "super_admin") {
            registrations += wsRef.collection("bills").addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/bills")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onBillsChanged(snap.documents.mapNotNull(::parseBillDoc).sortedByDescending { it.updatedAt })
                }
            }

            registrations += wsRef.collection("settlements").addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/settlements")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onSettlementsChanged(snap.documents.mapNotNull(::parseSettlementDoc).sortedByDescending { it.createdAt })
                }
            }

            registrations += wsRef.collection("expenses").addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/expenses")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onExpensesChanged(snap.documents.mapNotNull(::parseExpenseDoc).sortedByDescending { it.updatedAt })
                }
            }

            registrations += wsRef.collection("auditLogs").addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/auditLogs")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onAuditLogsChanged(snap.documents.mapNotNull(::parseAuditLogDoc).sortedByDescending { it.createdAt })
                }
            }
        }

        if (normRole == "admin") {
            registrations += wsRef.collection("trash").addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/trash")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onTrashChanged(snap.documents.mapNotNull(::parseTrashDoc).sortedByDescending { it.deletedAt })
                }
            }
        }
    }

    suspend fun pushWorkspaceConfig(
        workspaceId: String,
        company: CompanySettings,
        upi: UpiSettings,
        voice: VoiceAndNoteSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = getFirestore() ?: return@withContext Result.success(Unit)
        if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
        try {
            val updates = hashMapOf<String, Any>(
                "companyName" to company.companyName.trim().ifBlank { BillingRepository.DEFAULT_COMPANY_NAME },
                "mobile" to company.mobile,
                "email" to company.email,
                "address" to company.address,
                "upiId" to upi.upiId,
                "payeeName" to upi.payeeName,
                "logoBase64" to sanitizeBase64(company.logoBase64),
                "authorisedSignatureBase64" to sanitizeBase64(company.authorisedSignatureBase64),
                "fallbackQrBase64" to sanitizeBase64(upi.fallbackQrBase64),
                "headerNote" to voice.headerNote,
                "voiceLogin" to voice.loginVoice,
                "voicePostLogin" to voice.postLoginVoice,
                "voiceBillStart" to voice.billGeneratingVoice,
                "voiceBillDone" to voice.billCompletedVoice,
                "voiceModelId" to voice.voiceModelId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspaces").document(workspaceId).update(updates).awaitOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "workspaces/$workspaceId")
            Result.failure(e)
        }
    }

    suspend fun pushAccount(workspaceId: String, account: UserAccountEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = account.username.trim().lowercase(Locale.ROOT)
            try {
                val data = hashMapOf<String, Any>(
                    "username" to account.username.trim(),
                    "displayName" to account.displayName,
                    "passwordHash" to account.passwordHash,
                    "role" to BillingRepository.normalizeRole(account.role),
                    "phone" to account.phone,
                    "photoBase64" to sanitizeBase64(account.photoBase64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("accounts").document(docId)
                    .set(data, SetOptions.merge()).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/accounts/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteAccount(workspaceId: String, username: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = username.trim().lowercase(Locale.ROOT)
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("accounts").document(docId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/accounts/$docId")
                Result.failure(e)
            }
        }

    suspend fun pushProduct(workspaceId: String, product: ProductEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = if (product.id > 0L) "p_${product.id}" else "p_${product.name.hashCode()}_${System.currentTimeMillis()}"
            try {
                val data = hashMapOf<String, Any>(
                    "productId" to docId,
                    "name" to product.name.trim(),
                    "unit" to product.unit.ifBlank { "Nos" },
                    "defaultRate" to product.defaultRate.coerceAtLeast(0.0),
                    "folder" to product.folder.ifBlank { "General" },
                    "stockQty" to product.stockQty.coerceAtLeast(0.0),
                    "minStockAlert" to product.minStockAlert.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("products").document(docId)
                    .set(data).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/products/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteProduct(workspaceId: String, productId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = "p_$productId"
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("products").document(docId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/products/$docId")
                Result.failure(e)
            }
        }

    suspend fun pushCustomer(workspaceId: String, customer: CustomerEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val uid = getAuth()?.currentUser?.uid
            val db = getFirestore()
            if (workspaceId.isBlank() || uid.isNullOrBlank() || db == null) return@withContext Result.success(Unit)
            val docId = customerDocId(customer)
            try {
                val data = hashMapOf<String, Any>(
                    "customerId" to docId,
                    "name" to customer.name.trim().ifBlank { "Walk-in Customer" },
                    "details" to customer.details,
                    "phone" to customer.phone,
                    "address" to customer.address,
                    "locationUrl" to customer.locationUrl,
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("customers").document(docId)
                    .set(data).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/customers/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteCustomer(workspaceId: String, customer: CustomerEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = customerDocId(customer)
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("customers").document(docId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/customers/$docId")
                Result.failure(e)
            }
        }

    suspend fun pushBill(workspaceId: String, bill: BillEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val uid = getAuth()?.currentUser?.uid
            val db = getFirestore()
            if (workspaceId.isBlank() || uid.isNullOrBlank() || db == null) return@withContext Result.success(Unit)
            val docId = bill.id.ifBlank { "b_${System.currentTimeMillis()}" }
            try {
                val docRef = db.collection("workspaces").document(workspaceId)
                    .collection("bills").document(docId)
                val existing = runCatching { withTimeoutOrNull(1000L) { docRef.get().await() } }.getOrNull()
                val preservedCreatorUid = existing?.getString("createdByUid")?.takeIf { it.isNotBlank() } ?: uid
                val data = hashMapOf<String, Any>(
                    "billId" to docId,
                    "invoiceNo" to bill.invoiceNo.ifBlank { "TSE-001" },
                    "date" to bill.date,
                    "customerName" to bill.customerName,
                    "customerDetails" to bill.customerDetails,
                    "itemsJson" to bill.itemsJson,
                    "subtotal" to bill.subtotal.coerceAtLeast(0.0),
                    "discount" to bill.discount.coerceAtLeast(0.0),
                    "grandTotal" to bill.grandTotal.coerceAtLeast(0.0),
                    "paidAmount" to bill.paidAmount.coerceAtLeast(0.0),
                    "dueAmount" to bill.dueAmount.coerceAtLeast(0.0),
                    "paymentMode" to bill.paymentMode,
                    "signatureBase64" to sanitizeBase64(bill.signatureBase64),
                    "customQrText" to bill.customQrText,
                    "createdBy" to bill.createdBy.ifBlank { "Technician" },
                    "createdByUid" to preservedCreatorUid,
                    "extraChargeType" to bill.extraChargeType,
                    "extraChargeAmount" to bill.extraChargeAmount.coerceAtLeast(0.0),
                    "secondaryChargeType" to bill.secondaryChargeType,
                    "secondaryChargeAmount" to bill.secondaryChargeAmount.coerceAtLeast(0.0),
                    "staffRating" to bill.staffRating.coerceIn(0, 5),
                    "staffReviewNote" to bill.staffReviewNote,
                    "documentType" to if (bill.documentType.equals("QUOTATION", ignoreCase = true)) "QUOTATION" else "BILL",
                    "helpBill" to bill.isHelpBill,
                    "helpRequestId" to bill.helpRequestId.coerceAtLeast(0L),
                    "quotationNote" to bill.quotationNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/bills/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteBill(workspaceId: String, billId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("bills").document(billId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/bills/$billId")
                Result.failure(e)
            }
        }

    suspend fun pushHelpRequest(workspaceId: String, req: CustomerHelpEntity, isTechnicianStatusUpdate: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            val uid = getAuth()?.currentUser?.uid
            val db = getFirestore()
            if (workspaceId.isBlank() || uid.isNullOrBlank() || db == null) return@withContext Result.success(Unit)
            val docId = req.id.toString()
            val ref = db.collection("workspaces").document(workspaceId).collection("helpRequests").document(docId)
            try {
                if (isTechnicianStatusUpdate) {
                    val existing = runCatching { withTimeoutOrNull(1000L) { ref.get().await() } }.getOrNull()
                    val existingAcceptedUid = existing?.getString("acceptedByUid").orEmpty()
                    val finalAcceptedUid = existingAcceptedUid.ifBlank { if (req.acceptedBy.isNotBlank()) uid else "" }
                    val partial = hashMapOf<String, Any>(
                        "status" to req.status,
                        "acceptedBy" to req.acceptedBy,
                        "acceptedByUid" to finalAcceptedUid,
                        "acceptedByName" to req.acceptedByName,
                        "acceptedByPhone" to req.acceptedByPhone,
                        "acceptedAt" to req.acceptedAt,
                        "solvedAt" to req.solvedAt,
                        "billId" to req.billId,
                        "updatedAt" to req.updatedAt
                    )
                    ref.update(partial).awaitOfflineSafe()
                } else {
                    val data = hashMapOf<String, Any>(
                        "helpId" to req.id,
                        "customerName" to req.customerName,
                        "phone" to req.phone,
                        "address" to req.address,
                        "locationUrl" to req.locationUrl,
                        "helpDescription" to req.helpDescription,
                        "status" to req.status,
                        "createdAt" to req.createdAt,
                        "updatedAt" to req.updatedAt,
                        "acceptedBy" to req.acceptedBy,
                        "acceptedByUid" to "",
                        "acceptedByName" to req.acceptedByName,
                        "acceptedByPhone" to req.acceptedByPhone,
                        "acceptedAt" to req.acceptedAt,
                        "solvedAt" to req.solvedAt,
                        "billId" to req.billId
                    )
                    ref.set(data).awaitOfflineSafe()
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/helpRequests/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteHelpRequest(workspaceId: String, helpId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("helpRequests").document(helpId.toString())
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/helpRequests/$helpId")
                Result.failure(e)
            }
        }

    suspend fun pushSettlement(workspaceId: String, settlement: TechnicianSettlementEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = if (settlement.id > 0L) "s_${settlement.id}" else "s_${settlement.createdAt}"
            try {
                val data = hashMapOf<String, Any>(
                    "settlementId" to docId,
                    "technicianUsername" to settlement.technicianUsername,
                    "amount" to settlement.amount,
                    "note" to settlement.note,
                    "recordedBy" to settlement.recordedBy,
                    "createdAt" to settlement.createdAt
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("settlements").document(docId)
                    .set(data).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/settlements/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteSettlement(workspaceId: String, settlementId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = "s_$settlementId"
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("settlements").document(docId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/settlements/$docId")
                Result.failure(e)
            }
        }

    suspend fun pushExpense(workspaceId: String, expense: ExpenseEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = expense.id.ifBlank { "exp_${System.currentTimeMillis()}" }
            try {
                val data = hashMapOf<String, Any>(
                    "expenseId" to docId,
                    "title" to expense.title.trim(),
                    "category" to expense.category.ifBlank { "General" },
                    "amount" to expense.amount.coerceAtLeast(0.0),
                    "date" to expense.date,
                    "note" to expense.note,
                    "recordedBy" to expense.recordedBy,
                    "createdAt" to expense.createdAt,
                    "updatedAt" to expense.updatedAt,
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("expenses").document(docId)
                    .set(data).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/expenses/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteExpense(workspaceId: String, expenseId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("expenses").document(expenseId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/expenses/$expenseId")
                Result.failure(e)
            }
        }

    suspend fun pushTrashItem(workspaceId: String, item: TrashItemEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = item.id
            val ref = db.collection("workspaces").document(workspaceId)
                .collection("trash").document(docId)
            try {
                if (item.status == "RESTORED") {
                    val updates = hashMapOf<String, Any>(
                        "status" to "RESTORED",
                        "restoredAt" to item.restoredAt,
                        "restoredBy" to item.restoredBy
                    )
                    ref.update(updates).awaitOfflineSafe()
                } else {
                    val data = hashMapOf<String, Any>(
                        "trashId" to docId,
                        "entityType" to item.entityType,
                        "entityId" to item.entityId,
                        "title" to item.title,
                        "summary" to item.summary,
                        "payloadJson" to item.payloadJson,
                        "deletedBy" to item.deletedBy,
                        "deletedByRole" to item.deletedByRole,
                        "status" to item.status,
                        "deletedAt" to FieldValue.serverTimestamp(),
                        "deletedAtMillis" to item.deletedAt,
                        "expireAt" to Timestamp(Date(item.expireAt)),
                        "restoredAt" to item.restoredAt,
                        "restoredBy" to item.restoredBy
                    )
                    ref.set(data).awaitOfflineSafe()
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/trash/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteTrashItem(workspaceId: String, trashId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            try {
                db.collection("workspaces").document(workspaceId)
                    .collection("trash").document(trashId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/trash/$trashId")
                Result.failure(e)
            }
        }

    suspend fun pushAuditLog(workspaceId: String, log: AuditLogEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            try {
                val data = hashMapOf<String, Any>(
                    "logId" to log.id,
                    "action" to log.action,
                    "entityType" to log.entityType,
                    "entityId" to log.entityId,
                    "title" to log.title,
                    "actorUsername" to log.actorUsername,
                    "actorRole" to log.actorRole,
                    "details" to log.details,
                    "createdAt" to log.createdAt,
                    "serverCreatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("auditLogs").document(log.id)
                    .set(data).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/auditLogs/${log.id}")
                Result.failure(e)
            }
        }

    private fun customerDocId(c: CustomerEntity): String {
        val digits = c.phone.filter { it.isDigit() }
        if (digits.length >= 7) return "c_${digits.takeLast(10)}"
        val slug = c.name.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return "c_${slug.ifBlank { c.id.toString() }}"
    }

    private fun parseWorkspaceDoc(doc: DocumentSnapshot, deviceRole: String): CloudWorkspaceInfo =
        CloudWorkspaceInfo(
            workspaceId = doc.getString("workspaceId").orEmpty().ifBlank { doc.id },
            ownerUid = doc.getString("ownerUid").orEmpty(),
            ownerEmail = doc.getString("ownerEmail").orEmpty(),
            techShareCode = doc.getString("techShareCode").orEmpty(),
            superShareCode = doc.getString("superShareCode").orEmpty(),
            deviceRole = deviceRole,
            companyName = doc.getString("companyName").orEmpty().ifBlank { BillingRepository.DEFAULT_COMPANY_NAME }
        )

    private fun parseCompanySettings(doc: DocumentSnapshot): CompanySettings = CompanySettings(
        companyName = doc.getString("companyName").orEmpty().ifBlank { BillingRepository.DEFAULT_COMPANY_NAME },
        mobile = doc.getString("mobile").orEmpty().ifBlank { BillingRepository.DEFAULT_COMPANY_MOBILE },
        email = doc.getString("email").orEmpty().ifBlank { BillingRepository.DEFAULT_COMPANY_EMAIL },
        address = doc.getString("address").orEmpty().ifBlank { BillingRepository.DEFAULT_COMPANY_ADDRESS },
        authorisedSignatureBase64 = doc.getString("authorisedSignatureBase64").orEmpty(),
        logoBase64 = doc.getString("logoBase64").orEmpty()
    )

    private fun parseUpiSettings(doc: DocumentSnapshot): UpiSettings = UpiSettings(
        upiId = doc.getString("upiId").orEmpty(),
        payeeName = doc.getString("payeeName").orEmpty().ifBlank { BillingRepository.DEFAULT_PAYEE_NAME },
        fallbackQrBase64 = doc.getString("fallbackQrBase64").orEmpty()
    )

    private fun parseVoiceSettings(doc: DocumentSnapshot): VoiceAndNoteSettings {
        val d = VoiceAndNoteSettings()
        return VoiceAndNoteSettings(
            headerNote = doc.getString("headerNote").orEmpty().ifBlank { d.headerNote },
            loginVoice = doc.getString("voiceLogin").orEmpty().ifBlank { d.loginVoice },
            postLoginVoice = doc.getString("voicePostLogin").orEmpty().ifBlank { d.postLoginVoice },
            billGeneratingVoice = doc.getString("voiceBillStart").orEmpty().ifBlank { d.billGeneratingVoice },
            billCompletedVoice = doc.getString("voiceBillDone").orEmpty().ifBlank { d.billCompletedVoice },
            voiceModelId = doc.getString("voiceModelId").orEmpty().ifBlank { d.voiceModelId }
        )
    }

    private fun parseAccountDoc(doc: DocumentSnapshot): UserAccountEntity? {
        val uname = doc.getString("username")?.trim().orEmpty()
        val passHash = doc.getString("passwordHash")?.trim().orEmpty()
        if (uname.isEmpty() || passHash.isEmpty()) return null
        return UserAccountEntity(
            username = uname,
            displayName = doc.getString("displayName").orEmpty().ifBlank { uname },
            passwordHash = passHash,
            role = BillingRepository.normalizeRole(doc.getString("role").orEmpty()),
            phone = doc.getString("phone").orEmpty(),
            photoBase64 = doc.getString("photoBase64").orEmpty()
        )
    }

    private fun parseProductDoc(doc: DocumentSnapshot): ProductEntity? {
        val name = doc.getString("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        val idNum = doc.getString("productId")?.removePrefix("p_")?.toLongOrNull()
            ?: (doc.id.hashCode().toLong() and 0x7FFFFFFFL)
        return ProductEntity(
            id = idNum,
            name = name,
            unit = doc.getString("unit").orEmpty().ifBlank { "Nos" },
            defaultRate = (doc.get("defaultRate") as? Number)?.toDouble() ?: 0.0,
            folder = doc.getString("folder").orEmpty().ifBlank { "General" },
            stockQty = (doc.get("stockQty") as? Number)?.toDouble() ?: 0.0,
            minStockAlert = (doc.get("minStockAlert") as? Number)?.toDouble() ?: 0.0
        )
    }

    private fun parseCustomerDoc(doc: DocumentSnapshot): CustomerEntity? {
        val name = doc.getString("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        val numericId = (doc.id.hashCode().toLong() and 0x7FFFFFFFL).coerceAtLeast(1L)
        return CustomerEntity(
            id = numericId,
            name = name,
            details = doc.getString("details").orEmpty(),
            phone = doc.getString("phone").orEmpty(),
            address = doc.getString("address").orEmpty(),
            locationUrl = doc.getString("locationUrl").orEmpty()
        )
    }

    private fun parseBillDoc(doc: DocumentSnapshot): BillEntity? {
        val id = doc.getString("billId").orEmpty().ifBlank { doc.id }
        val inv = doc.getString("invoiceNo").orEmpty()
        if (inv.isBlank()) return null
        val updatedTs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: System.currentTimeMillis()
        return BillEntity(
            id = id,
            invoiceNo = inv,
            date = doc.getString("date").orEmpty(),
            customerName = doc.getString("customerName").orEmpty(),
            customerDetails = doc.getString("customerDetails").orEmpty(),
            itemsJson = doc.getString("itemsJson").orEmpty().ifBlank { "[]" },
            subtotal = (doc.get("subtotal") as? Number)?.toDouble() ?: 0.0,
            discount = (doc.get("discount") as? Number)?.toDouble() ?: 0.0,
            grandTotal = (doc.get("grandTotal") as? Number)?.toDouble() ?: 0.0,
            paidAmount = (doc.get("paidAmount") as? Number)?.toDouble() ?: 0.0,
            dueAmount = (doc.get("dueAmount") as? Number)?.toDouble() ?: 0.0,
            paymentMode = doc.getString("paymentMode").orEmpty(),
            signatureBase64 = doc.getString("signatureBase64").orEmpty(),
            customQrText = doc.getString("customQrText").orEmpty(),
            createdBy = doc.getString("createdBy").orEmpty(),
            updatedAt = updatedTs,
            extraChargeType = doc.getString("extraChargeType").orEmpty().ifBlank { "NONE" },
            extraChargeAmount = (doc.get("extraChargeAmount") as? Number)?.toDouble() ?: 0.0,
            secondaryChargeType = doc.getString("secondaryChargeType").orEmpty().ifBlank { "NONE" },
            secondaryChargeAmount = (doc.get("secondaryChargeAmount") as? Number)?.toDouble() ?: 0.0,
            staffRating = (doc.get("staffRating") as? Number)?.toInt()?.coerceIn(0, 5) ?: 0,
            staffReviewNote = doc.getString("staffReviewNote").orEmpty(),
            documentType = doc.getString("documentType").orEmpty().ifBlank { "BILL" },
            isHelpBill = doc.getBoolean("helpBill") ?: false,
            helpRequestId = (doc.get("helpRequestId") as? Number)?.toLong() ?: 0L,
            quotationNote = doc.getString("quotationNote").orEmpty()
        )
    }

    private fun parseHelpDoc(doc: DocumentSnapshot): CustomerHelpEntity? {
        val helpId = (doc.get("helpId") as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: return null
        return CustomerHelpEntity(
            id = helpId,
            customerName = doc.getString("customerName").orEmpty(),
            phone = doc.getString("phone").orEmpty(),
            address = doc.getString("address").orEmpty(),
            locationUrl = doc.getString("locationUrl").orEmpty(),
            helpDescription = doc.getString("helpDescription").orEmpty(),
            status = doc.getString("status").orEmpty().ifBlank { "OPEN" },
            createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (doc.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
            acceptedBy = doc.getString("acceptedBy").orEmpty(),
            acceptedByName = doc.getString("acceptedByName").orEmpty(),
            acceptedByPhone = doc.getString("acceptedByPhone").orEmpty(),
            acceptedAt = (doc.get("acceptedAt") as? Number)?.toLong() ?: 0L,
            solvedAt = (doc.get("solvedAt") as? Number)?.toLong() ?: 0L,
            billId = doc.getString("billId").orEmpty()
        )
    }

    private fun parseSettlementDoc(doc: DocumentSnapshot): TechnicianSettlementEntity? {
        val username = doc.getString("technicianUsername")?.trim().orEmpty()
        if (username.isEmpty()) return null
        val idNum = doc.getString("settlementId")?.removePrefix("s_")?.toLongOrNull()
            ?: (doc.id.hashCode().toLong() and 0x7FFFFFFFL)
        return TechnicianSettlementEntity(
            id = idNum,
            technicianUsername = username,
            amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
            note = doc.getString("note").orEmpty(),
            recordedBy = doc.getString("recordedBy").orEmpty(),
            createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun parseExpenseDoc(doc: DocumentSnapshot): ExpenseEntity? {
        val title = doc.getString("title")?.trim().orEmpty()
        if (title.isEmpty()) return null
        val id = doc.getString("expenseId").orEmpty().ifBlank { doc.id }
        return ExpenseEntity(
            id = id,
            title = title,
            category = doc.getString("category").orEmpty().ifBlank { "General" },
            amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
            date = doc.getString("date").orEmpty(),
            note = doc.getString("note").orEmpty(),
            recordedBy = doc.getString("recordedBy").orEmpty(),
            createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (doc.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun parseTrashDoc(doc: DocumentSnapshot): TrashItemEntity? {
        val trashId = doc.getString("trashId").orEmpty().ifBlank { doc.id }
        val entityType = doc.getString("entityType").orEmpty()
        val payload = doc.getString("payloadJson").orEmpty()
        if (entityType.isBlank() || payload.isBlank()) return null
        val deletedMillis = doc.getTimestamp("deletedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time
            ?: (doc.get("deletedAtMillis") as? Number)?.toLong()
            ?: System.currentTimeMillis()
        val expireMillis = doc.getTimestamp("expireAt")?.toDate()?.time
            ?: (deletedMillis + 86_400_000L)
        return TrashItemEntity(
            id = trashId,
            entityType = entityType,
            entityId = doc.getString("entityId").orEmpty(),
            title = doc.getString("title").orEmpty(),
            summary = doc.getString("summary").orEmpty(),
            payloadJson = payload,
            deletedBy = doc.getString("deletedBy").orEmpty(),
            deletedByRole = doc.getString("deletedByRole").orEmpty(),
            status = doc.getString("status").orEmpty().ifBlank { "TRASHED" },
            deletedAt = deletedMillis,
            expireAt = expireMillis,
            restoredAt = (doc.get("restoredAt") as? Number)?.toLong() ?: 0L,
            restoredBy = doc.getString("restoredBy").orEmpty()
        )
    }

    private fun parseAuditLogDoc(doc: DocumentSnapshot): AuditLogEntity? {
        val logId = doc.getString("logId").orEmpty().ifBlank { doc.id }
        val action = doc.getString("action").orEmpty()
        if (action.isBlank()) return null
        return AuditLogEntity(
            id = logId,
            action = action,
            entityType = doc.getString("entityType").orEmpty(),
            entityId = doc.getString("entityId").orEmpty(),
            title = doc.getString("title").orEmpty(),
            actorUsername = doc.getString("actorUsername").orEmpty(),
            actorRole = doc.getString("actorRole").orEmpty(),
            details = doc.getString("details").orEmpty(),
            createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}
