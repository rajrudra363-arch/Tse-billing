package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
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
    val settlements: List<TechnicianSettlementEntity>
)

class CloudSyncRepository(private val context: Context) {
    companion object {
        private const val TAG = "CloudSyncRepository"
        private const val SHARE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val MAX_BASE64_CHARS = 180_000
    }

    private val random = SecureRandom()
    private val registrations = mutableListOf<ListenerRegistration>()

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
        FirebaseFirestore.getInstance(dbId)
    }.getOrNull()

    private fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String) {
        val auth = getAuth()
        val user = auth?.currentUser
        val details = "FirestoreError[$operationType] path=$path uid=${user?.uid ?: "none"} email=${user?.email ?: "none"} msg=${exception.message}"
        Log.e(TAG, details, exception)
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
        settlements: List<TechnicianSettlementEntity>
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
            try {
                val wsSnap = wsRef.get().await()
                if (!wsSnap.exists()) {
                    return@withContext Result.failure(IllegalStateException("Workspace not found."))
                }
                val accountsSnap = wsRef.collection("accounts").get().await()
                val productsSnap = wsRef.collection("products").get().await()
                val customersSnap = wsRef.collection("customers").get().await()
                val billsSnap = wsRef.collection("bills").get().await()
                val helpSnap = wsRef.collection("helpRequests").get().await()
                val settlementsSnap = wsRef.collection("settlements").get().await()

                val snapshot = CloudWorkspaceSnapshot(
                    workspace = parseWorkspaceDoc(wsSnap, deviceRole),
                    companySettings = parseCompanySettings(wsSnap),
                    upiSettings = parseUpiSettings(wsSnap),
                    voiceAndNoteSettings = parseVoiceSettings(wsSnap),
                    accounts = accountsSnap.documents.mapNotNull(::parseAccountDoc),
                    products = productsSnap.documents.mapNotNull(::parseProductDoc),
                    customers = customersSnap.documents.mapNotNull(::parseCustomerDoc),
                    bills = billsSnap.documents.mapNotNull(::parseBillDoc).sortedByDescending { it.updatedAt },
                    helpRequests = helpSnap.documents.mapNotNull(::parseHelpDoc).sortedByDescending { it.updatedAt },
                    settlements = settlementsSnap.documents.mapNotNull(::parseSettlementDoc).sortedByDescending { it.createdAt }
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
        onSettlementsChanged: (List<TechnicianSettlementEntity>) -> Unit
    ) {
        stopListeners()
        val uid = getAuth()?.currentUser?.uid ?: return
        val db = getFirestore() ?: return
        if (workspaceId.isBlank() || uid.isBlank()) return
        val wsRef = db.collection("workspaces").document(workspaceId)

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

        registrations += wsRef.collection("bills").addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/bills")
                return@addSnapshotListener
            }
            if (snap != null) {
                onBillsChanged(snap.documents.mapNotNull(::parseBillDoc).sortedByDescending { it.updatedAt })
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

        registrations += wsRef.collection("settlements").addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/settlements")
                return@addSnapshotListener
            }
            if (snap != null) {
                onSettlementsChanged(snap.documents.mapNotNull(::parseSettlementDoc).sortedByDescending { it.createdAt })
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
            db.collection("workspaces").document(workspaceId).update(updates).await()
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
                    .set(data, SetOptions.merge()).await()
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
                    .delete().await()
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
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("products").document(docId)
                    .set(data).await()
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
                    .delete().await()
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
                    .set(data).await()
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
                    .delete().await()
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
                val existing = runCatching { docRef.get().await() }.getOrNull()
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
                    "staffRating" to bill.staffRating.coerceIn(0, 5),
                    "staffReviewNote" to bill.staffReviewNote,
                    "documentType" to if (bill.documentType.equals("QUOTATION", ignoreCase = true)) "QUOTATION" else "BILL",
                    "helpBill" to bill.isHelpBill,
                    "helpRequestId" to bill.helpRequestId.coerceAtLeast(0L),
                    "quotationNote" to bill.quotationNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).await()
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
                    .delete().await()
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
                    val existing = ref.get().await()
                    val existingAcceptedUid = existing.getString("acceptedByUid").orEmpty()
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
                    ref.update(partial).await()
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
                    ref.set(data).await()
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
                    .delete().await()
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
                    .set(data).await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/settlements/$docId")
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
            defaultRate = doc.getDouble("defaultRate") ?: 0.0,
            folder = doc.getString("folder").orEmpty().ifBlank { "General" }
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
            subtotal = doc.getDouble("subtotal") ?: 0.0,
            discount = doc.getDouble("discount") ?: 0.0,
            grandTotal = doc.getDouble("grandTotal") ?: 0.0,
            paidAmount = doc.getDouble("paidAmount") ?: 0.0,
            dueAmount = doc.getDouble("dueAmount") ?: 0.0,
            paymentMode = doc.getString("paymentMode").orEmpty(),
            signatureBase64 = doc.getString("signatureBase64").orEmpty(),
            customQrText = doc.getString("customQrText").orEmpty(),
            createdBy = doc.getString("createdBy").orEmpty(),
            updatedAt = updatedTs,
            extraChargeType = doc.getString("extraChargeType").orEmpty().ifBlank { "NONE" },
            extraChargeAmount = doc.getDouble("extraChargeAmount") ?: 0.0,
            staffRating = (doc.getLong("staffRating") ?: 0L).toInt().coerceIn(0, 5),
            staffReviewNote = doc.getString("staffReviewNote").orEmpty(),
            documentType = doc.getString("documentType").orEmpty().ifBlank { "BILL" },
            isHelpBill = doc.getBoolean("helpBill") ?: false,
            helpRequestId = doc.getLong("helpRequestId") ?: 0L,
            quotationNote = doc.getString("quotationNote").orEmpty()
        )
    }

    private fun parseHelpDoc(doc: DocumentSnapshot): CustomerHelpEntity? {
        val helpId = doc.getLong("helpId") ?: doc.id.toLongOrNull() ?: return null
        return CustomerHelpEntity(
            id = helpId,
            customerName = doc.getString("customerName").orEmpty(),
            phone = doc.getString("phone").orEmpty(),
            address = doc.getString("address").orEmpty(),
            locationUrl = doc.getString("locationUrl").orEmpty(),
            helpDescription = doc.getString("helpDescription").orEmpty(),
            status = doc.getString("status").orEmpty().ifBlank { "OPEN" },
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            acceptedBy = doc.getString("acceptedBy").orEmpty(),
            acceptedByName = doc.getString("acceptedByName").orEmpty(),
            acceptedByPhone = doc.getString("acceptedByPhone").orEmpty(),
            acceptedAt = doc.getLong("acceptedAt") ?: 0L,
            solvedAt = doc.getLong("solvedAt") ?: 0L,
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
            amount = doc.getDouble("amount") ?: 0.0,
            note = doc.getString("note").orEmpty(),
            recordedBy = doc.getString("recordedBy").orEmpty(),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        )
    }
}
