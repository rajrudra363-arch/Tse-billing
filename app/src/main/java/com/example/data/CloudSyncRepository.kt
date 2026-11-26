package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.util.configureAppCheck
import com.example.util.formatFirebaseErrorWithAppCheckHint
import com.example.util.isAppCheckError
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
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
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val comboFiles: List<ProductComboFileEntity> = emptyList(),
    val upiTransactions: List<UpiTransactionEntity> = emptyList(),
    val employees: List<EmployeeEntity> = emptyList(),
    val salaryPayments: List<SalaryPaymentEntity> = emptyList()
)

data class CloudTechCreationResult(
    val uid: String,
    val email: String,
    val name: String,
    val username: String,
    val role: String,
    val workspaceId: String,
    val mustChangePassword: Boolean,
    val activationPasskey: String = "",
    val techId: String = "",
    val loginEnabled: Boolean = true
)

data class CloudTechSignInResult(
    val uid: String,
    val email: String,
    val name: String,
    val username: String,
    val phone: String,
    val photoBase64: String,
    val role: String,
    val workspaceId: String,
    val mustChangePassword: Boolean,
    val companyName: String,
    val techId: String = "",
    val loginEnabled: Boolean = true
)

data class OtpRequestResult(
    val challengeId: String,
    val email: String,
    val purpose: String,
    val expiresAtMillis: Long,
    val resendAvailableAtMillis: Long = 0L,
    val maskedEmail: String
)

data class OtpVerifyResult(
    val verified: Boolean,
    val challengeId: String,
    val email: String,
    val purpose: String,
    val verificationToken: String
)

data class AdminSignUpResult(
    val uid: String,
    val companyId: String,
    val workspaceId: String,
    val email: String,
    val username: String,
    val adminName: String,
    val companyName: String,
    val role: String
)

data class AdminVerificationPendingResult(
    val uid: String,
    val email: String,
    val adminName: String,
    val emailSent: Boolean = true,
    val warningMessage: String = ""
)

class AdminEmailNotVerifiedException(
    val email: String,
    message: String = "Your email address ($email) is not verified yet. Please click the verification link in your email before accessing the Admin Dashboard."
) : IllegalStateException(message)

data class PasskeyChallengeResult(
    val challengeId: String,
    val rpId: String,
    val requestJson: String
)

data class PasskeyAuthVerifyResult(
    val verified: Boolean,
    val customToken: String,
    val uid: String,
    val email: String,
    val role: String,
    val workspaceId: String,
    val companyId: String
)

data class UpiPaymentInitiateResult(
    val paymentId: String,
    val paymentRef: String,
    val orderId: String,
    val billId: String,
    val invoiceNo: String,
    val amount: Double,
    val status: String,
    val gateway: String,
    val upiUri: String,
    val paymentUrl: String,
    val expiresAtMillis: Long
)

data class FinalizeBillServerResult(
    val billId: String,
    val paymentStatus: String,
    val stockDeducted: Boolean,
    val paidAmount: Double,
    val dueAmount: Double
)

data class CloudWorkspaceNotification(
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val targetRole: String,
    val targetUsername: String,
    val createdBy: String,
    val createdByUid: String,
    val createdAt: Long
)

class CloudSyncRepository(private val context: Context) {
    companion object {
        private const val TAG = "CloudSyncRepository"
        private const val FUNCTIONS_REGION = "asia-south1"
        private const val SHARE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val MAX_BASE64_CHARS = 180_000
        private const val OFFLINE_WRITE_WAIT_MS = 1500L
    }

    private val random = SecureRandom()
    private val registrations = mutableListOf<ListenerRegistration>()
    @Volatile
    private var cacheConfigured = false

    private fun ensureFirebaseWithAppCheck() {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        configureAppCheck(appContext)
    }

    private fun getAuth(): FirebaseAuth? = runCatching {
        ensureFirebaseWithAppCheck()
        FirebaseAuth.getInstance()
    }.getOrNull()

    private fun getFunctions(): FirebaseFunctions? = runCatching {
        ensureFirebaseWithAppCheck()
        FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    }.getOrNull()

    private fun getFirestore(): FirebaseFirestore? = runCatching {
        ensureFirebaseWithAppCheck()
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
            val safeCompany = AppIdentity.OFFICIAL_COMPANY_NAME

            val wsPayload = hashMapOf<String, Any>(
                "workspaceId" to wsId,
                "companyId" to wsId,
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
                "loginEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspaces").document(wsId)
                .collection("members").document(uid)
                .set(ownerMember).await()

            runCatching {
                db.collection("companies").document(wsId).set(
                    hashMapOf(
                        "companyId" to wsId,
                        "companyName" to AppIdentity.OFFICIAL_COMPANY_NAME,
                        "officialAppName" to AppIdentity.OFFICIAL_APP_NAME,
                        "ownerUid" to uid,
                        "ownerEmail" to user.email.orEmpty(),
                        "mobile" to company.mobile,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }

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
            val wrapped = if (isAppCheckError(e)) {
                IllegalStateException(
                    formatFirebaseErrorWithAppCheckHint(context, e, "App Check verification failed."),
                    e
                )
            } else {
                e
            }
            Result.failure(wrapped)
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

    /**
     * Calls the Firebase Admin SDK Cloud Function `createTechnicianAccount` without altering or
     * signing out the Admin's active FirebaseAuth Google Sign-In session.
     */
    suspend fun createTechnicianAccountViaCloud(
        workspaceId: String,
        name: String,
        email: String,
        initialPassword: String,
        phone: String = "",
        username: String = "",
        photoBase64: String = "",
        otpChallengeId: String = "",
        otpVerificationToken: String = "",
        requireOtpVerification: Boolean = false
    ): Result<CloudTechCreationResult> = withContext(Dispatchers.IO) {
        val currentUser = getAuth()?.currentUser
            ?: return@withContext Result.failure(
                IllegalStateException("Admin must sign in first before adding Technicians.")
            )
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        if (workspaceId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Activate Cloud Workspace before adding cloud technicians."))
        }

        try {
            runCatching { currentUser.getIdToken(false).await() }

            val payload = hashMapOf<String, Any>(
                "workspaceId" to workspaceId.trim(),
                "name" to name.trim(),
                "email" to email.trim().lowercase(Locale.US),
                "initialPassword" to initialPassword,
                "phone" to phone.trim(),
                "username" to username.trim(),
                "role" to "technician",
                "requireOtpVerification" to requireOtpVerification
            )
            if (otpChallengeId.isNotBlank()) payload["otpChallengeId"] = otpChallengeId.trim()
            if (otpVerificationToken.isNotBlank()) payload["otpVerificationToken"] = otpVerificationToken.trim()

            val result = functions
                .getHttpsCallable("createTechnicianAccount")
                .call(payload)
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Unexpected server response."))

            val createdUid = data["uid"]?.toString().orEmpty()
            val createdTechId = data["techId"]?.toString().orEmpty()
            val createdEmail = data["email"]?.toString().orEmpty().ifBlank { email.trim().lowercase(Locale.US) }
            val createdName = data["name"]?.toString().orEmpty().ifBlank { name.trim() }
            val createdUsername = data["username"]?.toString().orEmpty().ifBlank {
                AuthPolicy.deriveUsernameFromEmailOrName(createdEmail, createdName)
            }
            val createdRole = BillingRepository.normalizeRole(data["role"]?.toString().orEmpty())
            val createdWsId = data["workspaceId"]?.toString().orEmpty().ifBlank { workspaceId }
            val mustChange = (data["mustChangePassword"] as? Boolean) ?: true
            val passkey = data["activationPasskey"]?.toString().orEmpty()
            val loginEnabled = (data["loginEnabled"] as? Boolean) ?: true

            if (photoBase64.isNotBlank()) {
                pushAccount(
                    workspaceId = createdWsId,
                    account = UserAccountEntity(
                        username = createdUsername,
                        displayName = createdName,
                        passwordHash = AuthPolicy.FIREBASE_EMAIL_AUTH_MARKER,
                        role = "technician",
                        phone = phone.trim(),
                        photoBase64 = sanitizeBase64(photoBase64.trim()),
                        email = createdEmail,
                        uid = createdUid,
                        techId = createdTechId,
                        companyId = createdWsId,
                        loginEnabled = loginEnabled
                    )
                )
            }

            Result.success(
                CloudTechCreationResult(
                    uid = createdUid,
                    email = createdEmail,
                    name = createdName,
                    username = createdUsername,
                    role = createdRole,
                    workspaceId = createdWsId,
                    mustChangePassword = mustChange,
                    activationPasskey = passkey,
                    techId = createdTechId,
                    loginEnabled = loginEnabled
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            val friendly = when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    e.message ?: "Only a verified Admin can create technician accounts."
                FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                    e.message ?: "This email is already registered in Firebase Authentication."
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    "Admin Google session expired. Please sign in with Google again."
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    "Cloud Function 'createTechnicianAccount' not deployed yet or workspace missing."
                FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    e.message ?: "Invalid technician details."
                else -> e.message ?: "Failed to create technician account on server."
            }
            Log.e(TAG, "Cloud Function error (${e.code}): $friendly", e)
            Result.failure(IllegalStateException(friendly, e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call createTechnicianAccount", e)
            Result.failure(
                IllegalStateException(
                    e.message ?: "Could not reach Cloud Function. Ensure functions are deployed.",
                    e
                )
            )
        }
    }

    suspend fun verifyTechnicianActivationViaCloud(
        email: String,
        activationPasskey: String,
        workspaceIdHint: String = ""
    ): Result<TechActivationState> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!AuthPolicy.isValidEmail(cleanEmail)) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid authorized Technician email."))
        }
        val cleanPasskey = activationPasskey.trim().uppercase(Locale.US)
        if (cleanPasskey.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Enter the Activation Passkey provided by Admin."))
        }
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val payload = hashMapOf<String, Any>(
                "email" to cleanEmail,
                "activationPasskey" to cleanPasskey,
                "workspaceId" to workspaceIdHint.trim()
            )
            val result = functions
                .getHttpsCallable("verifyTechnicianActivation")
                .call(payload)
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Unexpected verification response."))
            val valid = (data["valid"] as? Boolean) ?: false
            if (!valid) {
                return@withContext Result.failure(IllegalStateException("Email or Activation Passkey is incorrect."))
            }
            val verifiedEmail = data["email"]?.toString().orEmpty().ifBlank { cleanEmail }
            val verifiedUsername = data["username"]?.toString().orEmpty()
            val verifiedName = data["name"]?.toString().orEmpty().ifBlank { "Technician" }
            val verifiedWsId = data["workspaceId"]?.toString().orEmpty()
            val companyName = data["companyName"]?.toString().orEmpty().ifBlank { BillingRepository.DEFAULT_COMPANY_NAME }
            Result.success(
                TechActivationState(
                    verified = true,
                    email = verifiedEmail,
                    username = verifiedUsername,
                    name = verifiedName,
                    workspaceId = verifiedWsId,
                    companyName = companyName
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            val friendly = when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    e.message ?: "Email is not authorized or Activation Passkey is incorrect."
                FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    e.message ?: "Enter valid Email and Activation Passkey."
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    "Activation service not deployed yet. Ask Admin to deploy Cloud Functions."
                else -> e.message ?: "Unable to verify Activation Passkey."
            }
            Log.e(TAG, "verifyTechnicianActivation error (${e.code}): $friendly", e)
            Result.failure(IllegalStateException(friendly, e))
        } catch (e: Exception) {
            Log.e(TAG, "verifyTechnicianActivation failed", e)
            Result.failure(IllegalStateException(e.message ?: "Unable to reach server for Passkey verification.", e))
        }
    }

    suspend fun regenerateTechnicianPasskeyViaCloud(
        workspaceId: String,
        technicianUid: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        if (workspaceId.isBlank() || technicianUid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Workspace or Technician ID missing."))
        }
        try {
            val res = functions.getHttpsCallable("regenerateTechnicianPasskey")
                .call(hashMapOf("workspaceId" to workspaceId, "technicianUid" to technicianUid))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
            val key = data?.get("activationPasskey")?.toString().orEmpty()
            if (key.isBlank()) {
                Result.failure(IllegalStateException("Server did not return a new Activation Passkey."))
            } else {
                Result.success(key)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to regenerate passkey.", e))
        }
    }

    suspend fun purgeExpiredTrashOnServer(workspaceId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (workspaceId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(0)
        val functions = getFunctions() ?: return@withContext Result.success(0)
        try {
            val res = functions.getHttpsCallable("purgeExpiredTrash")
                .call(hashMapOf("workspaceId" to workspaceId))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
            val count = (data?.get("purgedCount") as? Number)?.toInt() ?: 0
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Server trash purge skipped/failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun extractFunctionsMessage(e: Exception, fallback: String): String {
        if (e is FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                return "Firebase Cloud Functions service is not deployed yet. Please deploy functions and configure email provider secrets."
            }
            return e.message?.takeIf { it.isNotBlank() } ?: fallback
        }
        return e.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    suspend fun requestEmailOtp(email: String, purpose: String): Result<OtpRequestResult> =
        withContext(Dispatchers.IO) {
            val functions = getFunctions()
                ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
            try {
                val res = functions.getHttpsCallable("requestEmailOtp")
                    .call(hashMapOf("email" to email.trim().lowercase(Locale.US), "purpose" to purpose.trim().uppercase(Locale.US)))
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = res.getData() as? Map<String, Any?>
                    ?: return@withContext Result.failure(IllegalStateException("Unexpected OTP response."))
                val expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong() ?: (System.currentTimeMillis() + 5 * 60_000L)
                val resendAtMillis = (data["resendAvailableAtMillis"] as? Number)?.toLong()
                    ?: (System.currentTimeMillis() + 60_000L)
                Result.success(
                    OtpRequestResult(
                        challengeId = data["challengeId"]?.toString().orEmpty(),
                        email = data["email"]?.toString().orEmpty(),
                        purpose = data["purpose"]?.toString().orEmpty(),
                        expiresAtMillis = expiresAtMillis,
                        resendAvailableAtMillis = resendAtMillis,
                        maskedEmail = data["maskedEmail"]?.toString().orEmpty()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "requestEmailOtp failed", e)
                Result.failure(IllegalStateException(extractFunctionsMessage(e, "Failed to send Email OTP."), e))
            }
        }

    suspend fun verifyEmailOtp(
        challengeId: String,
        email: String,
        purpose: String,
        otp: String
    ): Result<OtpVerifyResult> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val res = functions.getHttpsCallable("verifyEmailOtp")
                .call(
                    hashMapOf(
                        "challengeId" to challengeId.trim(),
                        "email" to email.trim().lowercase(Locale.US),
                        "purpose" to purpose.trim().uppercase(Locale.US),
                        "otp" to otp.trim()
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Unexpected OTP verification response."))
            Result.success(
                OtpVerifyResult(
                    verified = (data["verified"] as? Boolean) ?: false,
                    challengeId = data["challengeId"]?.toString().orEmpty(),
                    email = data["email"]?.toString().orEmpty(),
                    purpose = data["purpose"]?.toString().orEmpty(),
                    verificationToken = data["verificationToken"]?.toString().orEmpty()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "verifyEmailOtp failed", e)
            Result.failure(IllegalStateException(extractFunctionsMessage(e, "Invalid or expired OTP."), e))
        }
    }

    suspend fun registerCompanyAdmin(
        adminName: String,
        mobile: String,
        email: String,
        password: String,
        confirmPassword: String,
        twoFactorPassword: String,
        confirmTwoFactorPassword: String,
        otpChallengeId: String,
        otpVerificationToken: String
    ): Result<AdminSignUpResult> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val res = functions.getHttpsCallable("registerCompanyAdmin")
                .call(
                    hashMapOf(
                        "companyName" to AppIdentity.OFFICIAL_COMPANY_NAME,
                        "adminName" to adminName.trim(),
                        "mobile" to mobile.trim(),
                        "email" to email.trim().lowercase(Locale.US),
                        "password" to password,
                        "confirmPassword" to confirmPassword,
                        "twoFactorPassword" to twoFactorPassword,
                        "confirmTwoFactorPassword" to confirmTwoFactorPassword,
                        "otpChallengeId" to otpChallengeId.trim(),
                        "otpVerificationToken" to otpVerificationToken.trim()
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Unexpected Admin registration response."))
            Result.success(
                AdminSignUpResult(
                    uid = data["uid"]?.toString().orEmpty(),
                    companyId = data["companyId"]?.toString().orEmpty(),
                    workspaceId = data["workspaceId"]?.toString().orEmpty(),
                    email = data["email"]?.toString().orEmpty(),
                    username = data["username"]?.toString().orEmpty(),
                    adminName = data["adminName"]?.toString().orEmpty(),
                    companyName = data["companyName"]?.toString().orEmpty().ifBlank { AppIdentity.OFFICIAL_COMPANY_NAME },
                    role = data["role"]?.toString().orEmpty().ifBlank { "admin" }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "registerCompanyAdmin failed", e)
            Result.failure(IllegalStateException(extractFunctionsMessage(e, "Admin Sign Up failed."), e))
        }
    }

    suspend fun verifyAdminSecondFactor(twoFactorPassword: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val functions = getFunctions()
                ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
            try {
                val res = functions.getHttpsCallable("verifyAdminSecondFactor")
                    .call(hashMapOf("twoFactorPassword" to twoFactorPassword))
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = res.getData() as? Map<String, Any?>
                Result.success((data?.get("verified") as? Boolean) == true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "verifyAdminSecondFactor failed", e)
                Result.failure(IllegalStateException(extractFunctionsMessage(e, "Incorrect Two-Factor Authentication Password."), e))
            }
        }

    suspend fun resetPasswordWithOtp(
        email: String,
        purpose: String,
        otpChallengeId: String,
        otpVerificationToken: String,
        newPassword: String,
        confirmPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            functions.getHttpsCallable("resetPasswordWithOtp")
                .call(
                    hashMapOf(
                        "email" to email.trim().lowercase(Locale.US),
                        "purpose" to purpose.trim().uppercase(Locale.US),
                        "otpChallengeId" to otpChallengeId.trim(),
                        "otpVerificationToken" to otpVerificationToken.trim(),
                        "newPassword" to newPassword,
                        "confirmPassword" to confirmPassword
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "resetPasswordWithOtp failed", e)
            Result.failure(IllegalStateException(extractFunctionsMessage(e, "Password reset failed."), e))
        }
    }

    suspend fun registerAdminWithEmailVerification(
        adminName: String,
        email: String,
        password: String
    ): Result<AdminVerificationPendingResult> = withContext(Dispatchers.IO) {
        val auth = getAuth()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        val cleanName = adminName.trim().ifBlank { "Admin" }
        val cleanEmail = email.trim().lowercase(Locale.US)
        try {
            val authRes = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = authRes.user
                ?: return@withContext Result.failure(IllegalStateException("Failed to create Firebase Admin account."))
            runCatching {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName)
                    .build()
                user.updateProfile(profileUpdates).await()
            }
            var emailSent = true
            var warning = ""
            try {
                user.sendEmailVerification().await()
            } catch (e: CancellationException) {
                throw e
            } catch (sendErr: Exception) {
                Log.w(TAG, "Initial sendEmailVerification failed for $cleanEmail", sendErr)
                emailSent = false
                warning = if (isAppCheckError(sendErr)) {
                    formatFirebaseErrorWithAppCheckHint(
                        context,
                        sendErr,
                        "Account created, but App Check blocked verification email delivery."
                    )
                } else {
                    sendErr.message?.takeIf { it.isNotBlank() }
                        ?: "Account created, but verification email could not be sent right now. Tap Resend Verification Email."
                }
            }
            Result.success(
                AdminVerificationPendingResult(
                    uid = user.uid,
                    email = cleanEmail,
                    adminName = cleanName,
                    emailSent = emailSent,
                    warningMessage = warning
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(
                IllegalStateException(
                    "An Admin account with this email already exists. Please use Admin Login (you can also resend your verification email there).",
                    e
                )
            )
        } catch (e: FirebaseAuthException) {
            val msg = when {
                isAppCheckError(e) -> formatFirebaseErrorWithAppCheckHint(context, e, "Admin Sign Up blocked by App Check.")
                e.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" ->
                    "An Admin account with this email already exists. Please use Admin Login (you can also resend your verification email there)."
                e.errorCode == "ERROR_INVALID_EMAIL" -> "Please enter a valid Email Address."
                e.errorCode == "ERROR_WEAK_PASSWORD" -> "Password is too weak. Choose a stronger password."
                else -> e.message?.takeIf { it.isNotBlank() } ?: "Admin Sign Up failed."
            }
            Result.failure(IllegalStateException(msg, e))
        } catch (e: FirebaseTooManyRequestsException) {
            Result.failure(
                IllegalStateException("Too many attempts right now. Please wait a moment and try again.", e)
            )
        } catch (e: Exception) {
            Log.e(TAG, "registerAdminWithEmailVerification failed", e)
            val msg = if (isAppCheckError(e)) {
                formatFirebaseErrorWithAppCheckHint(context, e, "Admin Sign Up blocked by App Check.")
            } else {
                e.message?.takeIf { it.isNotBlank() } ?: "Admin Sign Up failed."
            }
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun reloadAndCheckCurrentUserEmailVerified(): Result<Boolean> = withContext(Dispatchers.IO) {
        val auth = getAuth()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        val user = auth.currentUser
            ?: return@withContext Result.failure(
                IllegalStateException("Session expired. Please sign in on Admin Login to verify your email.")
            )
        try {
            user.reload().await()
            val refreshed = auth.currentUser ?: user
            if (refreshed.isEmailVerified) {
                runCatching { refreshed.getIdToken(true).await() }
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "reloadAndCheckCurrentUserEmailVerified failed", e)
            val msg = when {
                isAppCheckError(e) -> formatFirebaseErrorWithAppCheckHint(context, e, "Unable to check verification status.")
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network unavailable. Check your internet connection and tap Continue again."
                else -> e.message?.takeIf { it.isNotBlank() } ?: "Unable to check verification status."
            }
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun resendCurrentAdminVerificationEmail(): Result<Boolean> = withContext(Dispatchers.IO) {
        val auth = getAuth()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        val user = auth.currentUser
            ?: return@withContext Result.failure(
                IllegalStateException("No pending Admin session found. Sign in on Admin Login first.")
            )
        try {
            runCatching { user.reload().await() }
            val refreshed = auth.currentUser ?: user
            if (refreshed.isEmailVerified) {
                runCatching { refreshed.getIdToken(true).await() }
                return@withContext Result.success(true) // Already verified
            }
            refreshed.sendEmailVerification().await()
            Result.success(false) // Sent verification email; still waiting for user click
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseTooManyRequestsException) {
            Result.failure(
                IllegalStateException("Too many verification emails requested. Please wait a minute and check your inbox/spam folder.", e)
            )
        } catch (e: Exception) {
            Log.w(TAG, "resendCurrentAdminVerificationEmail failed", e)
            val raw = e.message.orEmpty()
            val msg = when {
                isAppCheckError(e) -> formatFirebaseErrorWithAppCheckHint(context, e, "Failed to send verification email.")
                raw.contains("too-many-requests", ignoreCase = true) ||
                    raw.contains("blocked all requests", ignoreCase = true) ->
                    "Too many verification emails requested. Please wait a few minutes and check your inbox/spam folder."
                else -> raw.ifBlank { "Failed to send verification email." }
            }
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun provisionVerifiedAdminWorkspace(
        adminName: String,
        mobile: String,
        company: CompanySettings,
        upi: UpiSettings,
        voice: VoiceAndNoteSettings
    ): Result<AdminSignUpResult> = withContext(Dispatchers.IO) {
        val auth = getAuth()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Please sign in first."))
        runCatching { user.reload().await() }
        val refreshed = auth.currentUser ?: user
        if (!refreshed.isEmailVerified) {
            return@withContext Result.failure(AdminEmailNotVerifiedException(refreshed.email.orEmpty()))
        }
        runCatching { refreshed.getIdToken(true).await() }

        val cleanName = adminName.trim().ifBlank { refreshed.displayName.orEmpty().ifBlank { "Admin" } }
        val cleanMobile = mobile.trim().ifBlank { company.mobile }
        val cleanEmail = refreshed.email.orEmpty().trim().lowercase(Locale.US)
        val mergedCompany = company.copy(
            companyName = AppIdentity.OFFICIAL_COMPANY_NAME,
            mobile = cleanMobile.ifBlank { company.mobile },
            email = cleanEmail.ifBlank { company.email }
        )

        val wsRes = ensureAdminWorkspace(
            company = mergedCompany,
            upi = upi,
            voice = voice
        )
        if (wsRes.isFailure) {
            return@withContext Result.failure(
                wsRes.exceptionOrNull() ?: IllegalStateException("Failed to initialize Admin workspace.")
            )
        }
        val ws = wsRes.getOrNull()!!
        val adminEntity = UserAccountEntity(
            username = AuthPolicy.CANONICAL_ADMIN_USERNAME,
            displayName = cleanName,
            passwordHash = AuthPolicy.FIREBASE_EMAIL_AUTH_MARKER,
            role = "admin",
            phone = cleanMobile,
            email = cleanEmail,
            uid = refreshed.uid,
            companyId = ws.workspaceId,
            loginEnabled = true
        )
        pushAccount(ws.workspaceId, adminEntity)

        Result.success(
            AdminSignUpResult(
                uid = refreshed.uid,
                companyId = ws.workspaceId,
                workspaceId = ws.workspaceId,
                email = cleanEmail,
                username = AuthPolicy.CANONICAL_ADMIN_USERNAME,
                adminName = cleanName,
                companyName = AppIdentity.OFFICIAL_COMPANY_NAME,
                role = "admin"
            )
        )
    }

    fun signOutCurrentUser() {
        runCatching { getAuth()?.signOut() }
    }

    suspend fun signInWithEmailPassword(
        email: String,
        password: String,
        requireEmailVerified: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val auth = getAuth()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        val cleanEmail = email.trim().lowercase(Locale.US)
        try {
            val authRes = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = authRes.user
                ?: return@withContext Result.failure(IllegalStateException("Sign-in failed."))
            if (requireEmailVerified) {
                runCatching { user.reload().await() }
                val refreshed = auth.currentUser ?: user
                if (!refreshed.isEmailVerified) {
                    return@withContext Result.failure(AdminEmailNotVerifiedException(cleanEmail))
                }
                runCatching { refreshed.getIdToken(true).await() }
            }
            val accessCheck = verifyMemberLoginAccess("")
            if (accessCheck.isFailure) {
                runCatching { auth.signOut() }
                return@withContext Result.failure(accessCheck.exceptionOrNull()!!)
            }
            Result.success(user.uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val rawMsg = e.message.orEmpty()
            val mappedMsg = when {
                isAppCheckError(e) -> formatFirebaseErrorWithAppCheckHint(context, e, "Sign-in blocked by App Check.")
                rawMsg.contains("USER_DISABLED", ignoreCase = true) ||
                    rawMsg.contains("disabled by an administrator", ignoreCase = true) ||
                    rawMsg.contains(AuthPolicy.TECH_LOGIN_DISABLED_MESSAGE, ignoreCase = true) ->
                    AuthPolicy.TECH_LOGIN_DISABLED_MESSAGE
                else -> rawMsg.ifBlank { "Invalid email or password." }
            }
            Result.failure(IllegalStateException(mappedMsg, e))
        }
    }

    suspend fun verifyMemberLoginAccess(workspaceId: String = ""): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val auth = getAuth()
            val uid = auth?.currentUser?.uid
                ?: return@withContext Result.failure(IllegalStateException("Not signed in."))
            val db = getFirestore() ?: return@withContext Result.success(true)
            try {
                val targetWsId = workspaceId.trim().ifBlank {
                    discoverWorkspaceForSignedInUser().getOrNull()?.workspaceId.orEmpty()
                }
                if (targetWsId.isBlank()) {
                    Result.success(true)
                } else {
                    val memberSnap = db.collection("workspaces")
                        .document(targetWsId)
                        .collection("members")
                        .document(uid)
                        .get()
                        .await()
                    if (memberSnap.exists() && memberSnap.getBoolean("loginEnabled") == false) {
                        Result.failure(IllegalStateException(AuthPolicy.TECH_LOGIN_DISABLED_MESSAGE))
                    } else {
                        Result.success(true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Result.success(true)
            }
        }

    suspend fun completeTechnicianFirstLogin(
        workspaceId: String,
        username: String,
        newPassword: String,
        photoBase64: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val res = functions.getHttpsCallable("completeTechnicianFirstLogin")
                .call(
                    hashMapOf(
                        "workspaceId" to workspaceId.trim(),
                        "username" to username.trim(),
                        "newPassword" to newPassword,
                        "photoBase64" to sanitizeBase64(photoBase64.trim())
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
            val techId = data?.get("techId")?.toString().orEmpty()
            Result.success(techId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to complete Technician setup.", e))
        }
    }

    suspend fun setTechnicianLoginStatus(
        workspaceId: String,
        technicianUid: String,
        enabled: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val res = functions.getHttpsCallable("setTechnicianLoginStatus")
                .call(
                    hashMapOf(
                        "workspaceId" to workspaceId.trim(),
                        "technicianUid" to technicianUid.trim(),
                        "enabled" to enabled
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
            Result.success((data?.get("loginEnabled") as? Boolean) ?: enabled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to update Technician login status.", e))
        }
    }

    suspend fun beginPasskeyRegistration(
        workspaceId: String,
        email: String,
        displayName: String,
        role: String,
        rpId: String = ""
    ): Result<PasskeyChallengeResult> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val payload = hashMapOf<String, Any>(
                "workspaceId" to workspaceId.trim(),
                "email" to email.trim().lowercase(Locale.US),
                "displayName" to displayName.trim(),
                "role" to role.trim().lowercase(Locale.US)
            )
            if (rpId.isNotBlank()) payload["rpId"] = rpId.trim()
            val res = functions.getHttpsCallable("beginPasskeyRegistration").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Invalid Passkey registration challenge."))
            Result.success(
                PasskeyChallengeResult(
                    challengeId = data["challengeId"]?.toString().orEmpty(),
                    rpId = data["rpId"]?.toString().orEmpty(),
                    requestJson = data["requestJson"]?.toString().orEmpty()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(extractFunctionsMessage(e, "Failed to initialize Passkey registration."), e))
        }
    }

    suspend fun finishPasskeyRegistration(
        challengeId: String,
        registrationResponseJson: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val res = functions.getHttpsCallable("finishPasskeyRegistration")
                .call(
                    hashMapOf(
                        "challengeId" to challengeId.trim(),
                        "registrationResponseJson" to registrationResponseJson
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
            Result.success(data?.get("credentialId")?.toString().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(extractFunctionsMessage(e, "Passkey verification failed on server."), e))
        }
    }

    suspend fun beginPasskeyAuthentication(
        email: String = "",
        expectedRole: String = "",
        rpId: String = ""
    ): Result<PasskeyChallengeResult> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val payload = hashMapOf<String, Any>(
                "email" to email.trim().lowercase(Locale.US),
                "expectedRole" to expectedRole.trim().lowercase(Locale.US)
            )
            if (rpId.isNotBlank()) payload["rpId"] = rpId.trim()
            val res = functions.getHttpsCallable("beginPasskeyAuthentication").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Invalid Passkey login challenge."))
            Result.success(
                PasskeyChallengeResult(
                    challengeId = data["challengeId"]?.toString().orEmpty(),
                    rpId = data["rpId"]?.toString().orEmpty(),
                    requestJson = data["requestJson"]?.toString().orEmpty()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(extractFunctionsMessage(e, "Unable to start Passkey sign-in."), e))
        }
    }

    suspend fun finishPasskeyAuthentication(
        challengeId: String,
        authenticationResponseJson: String
    ): Result<PasskeyAuthVerifyResult> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        val auth = getAuth()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        try {
            val res = functions.getHttpsCallable("finishPasskeyAuthentication")
                .call(
                    hashMapOf(
                        "challengeId" to challengeId.trim(),
                        "authenticationResponseJson" to authenticationResponseJson
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Invalid Passkey verification response."))
            val token = data["customToken"]?.toString().orEmpty()
            if (token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Server did not return an authentication token."))
            }
            auth.signInWithCustomToken(token).await()
            val result = PasskeyAuthVerifyResult(
                verified = (data["verified"] as? Boolean) ?: false,
                customToken = token,
                uid = data["uid"]?.toString().orEmpty(),
                email = data["email"]?.toString().orEmpty(),
                role = data["role"]?.toString().orEmpty(),
                workspaceId = data["workspaceId"]?.toString().orEmpty(),
                companyId = data["companyId"]?.toString().orEmpty()
            )
            val accessRes = verifyMemberLoginAccess(result.workspaceId)
            if (accessRes.isFailure) {
                runCatching { auth.signOut() }
                return@withContext Result.failure(accessRes.exceptionOrNull()!!)
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(extractFunctionsMessage(e, "Passkey sign-in failed."), e))
        }
    }

    suspend fun initiateUpiPayment(
        workspaceId: String,
        amount: Double,
        gateway: String = "RAZORPAY",
        billId: String = "",
        customerId: String = "",
        customerName: String = "Customer",
        customerDetails: String = "",
        customerEmail: String = "",
        customerPhone: String = "",
        draftSnapshot: Map<String, Any?>? = null
    ): Result<UpiPaymentInitiateResult> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val payload = hashMapOf<String, Any>(
                "workspaceId" to workspaceId.trim(),
                "amount" to amount,
                "gateway" to gateway.trim().uppercase(Locale.US),
                "billId" to billId.trim(),
                "customerId" to customerId.trim(),
                "customerName" to customerName.trim(),
                "customerDetails" to customerDetails.trim(),
                "customerEmail" to customerEmail.trim(),
                "customerPhone" to customerPhone.trim()
            )
            if (draftSnapshot != null) {
                payload["draftSnapshot"] = draftSnapshot
            }
            val res = functions.getHttpsCallable("initiateUpiPayment").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Unexpected payment initiation response."))
            Result.success(
                UpiPaymentInitiateResult(
                    paymentId = data["paymentId"]?.toString().orEmpty(),
                    paymentRef = data["paymentRef"]?.toString().orEmpty(),
                    orderId = data["orderId"]?.toString().orEmpty(),
                    billId = data["billId"]?.toString().orEmpty(),
                    invoiceNo = data["invoiceNo"]?.toString().orEmpty(),
                    amount = (data["amount"] as? Number)?.toDouble() ?: amount,
                    status = data["status"]?.toString().orEmpty().ifBlank { "PENDING" },
                    gateway = data["gateway"]?.toString().orEmpty().ifBlank { gateway },
                    upiUri = data["upiUri"]?.toString().orEmpty(),
                    paymentUrl = data["paymentUrl"]?.toString().orEmpty(),
                    expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong() ?: 0L
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to create Dynamic UPI QR order.", e))
        }
    }

    suspend fun cancelUpiPayment(
        workspaceId: String,
        paymentId: String,
        nextStatus: String = "CANCELLED"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            functions.getHttpsCallable("cancelUpiPayment")
                .call(
                    hashMapOf(
                        "workspaceId" to workspaceId.trim(),
                        "paymentId" to paymentId.trim(),
                        "nextStatus" to nextStatus.trim().uppercase(Locale.US)
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to update payment status.", e))
        }
    }

    suspend fun finalizeBillWithAtomicStock(
        workspaceId: String,
        bill: BillEntity
    ): Result<FinalizeBillServerResult> = withContext(Dispatchers.IO) {
        val functions = getFunctions()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Functions is not initialized."))
        try {
            val billPayload = hashMapOf<String, Any>(
                "billId" to bill.id,
                "invoiceNo" to bill.invoiceNo,
                "date" to bill.date,
                "customerName" to bill.customerName,
                "customerDetails" to bill.customerDetails,
                "itemsJson" to bill.itemsJson,
                "subtotal" to bill.subtotal,
                "discount" to bill.discount,
                "grandTotal" to bill.grandTotal,
                "paidAmount" to bill.paidAmount,
                "dueAmount" to bill.dueAmount,
                "paymentMode" to bill.paymentMode,
                "linkedPaymentId" to bill.linkedPaymentId,
                "signatureBase64" to sanitizeBase64(bill.signatureBase64),
                "customQrText" to bill.customQrText,
                "createdBy" to bill.createdBy,
                "extraChargeType" to bill.extraChargeType,
                "extraChargeAmount" to bill.extraChargeAmount,
                "secondaryChargeType" to bill.secondaryChargeType,
                "secondaryChargeAmount" to bill.secondaryChargeAmount,
                "staffRating" to bill.staffRating,
                "staffReviewNote" to bill.staffReviewNote,
                "documentType" to bill.documentType,
                "helpBill" to bill.isHelpBill,
                "helpRequestId" to bill.helpRequestId,
                "quotationNote" to bill.quotationNote
            )
            val res = functions.getHttpsCallable("finalizeBillWithAtomicStock")
                .call(hashMapOf("workspaceId" to workspaceId.trim(), "bill" to billPayload))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = res.getData() as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Unexpected server billing response."))
            Result.success(
                FinalizeBillServerResult(
                    billId = data["billId"]?.toString().orEmpty().ifBlank { bill.id },
                    paymentStatus = data["paymentStatus"]?.toString().orEmpty(),
                    stockDeducted = (data["stockDeducted"] as? Boolean) ?: false,
                    paidAmount = (data["paidAmount"] as? Number)?.toDouble() ?: bill.paidAmount,
                    dueAmount = (data["dueAmount"] as? Number)?.toDouble() ?: bill.dueAmount
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to finalize bill on server.", e))
        }
    }

    /**
     * Authenticates a Technician using Firebase Authentication Email/Password provider
     * and reads their workspace role and `mustChangePassword` flag from Firestore.
     */
    suspend fun signInTechnicianWithEmail(
        email: String,
        password: String
    ): Result<CloudTechSignInResult> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!AuthPolicy.isValidEmail(cleanEmail)) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid Technician email address."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter your password."))
        }
        val auth = getAuth()
            ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        val db = getFirestore()
            ?: return@withContext Result.failure(IllegalStateException("Cloud database is not initialized."))

        try {
            val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("Authentication failed."))
            val profileRes = resolveSignedInTechnicianProfile(user.uid, db)
            if (profileRes.isFailure) {
                runCatching { auth.signOut() }
            }
            profileRes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Email/Password sign-in failed for $cleanEmail", e)
            val msg = if (isAppCheckError(e)) {
                formatFirebaseErrorWithAppCheckHint(context, e, "Technician login blocked by App Check.")
            } else {
                e.message ?: "Invalid email or password, or Email/Password login is disabled."
            }
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun checkCurrentTechnicianPasswordChangeRequired(
        workspaceIdHint: String = ""
    ): Result<CloudTechSignInResult?> = withContext(Dispatchers.IO) {
        val user = getAuth()?.currentUser ?: return@withContext Result.success(null)
        val db = getFirestore() ?: return@withContext Result.success(null)
        val isGoogleProvider = user.providerData.any { it.providerId == "google.com" }
        val isPasswordProvider = user.providerData.any { it.providerId == "password" }
        if (!isPasswordProvider && isGoogleProvider) {
            return@withContext Result.success(null)
        }
        val resolved = resolveSignedInTechnicianProfile(user.uid, db, workspaceIdHint).getOrNull()
        if (resolved != null && resolved.role == "technician" && resolved.mustChangePassword) {
            Result.success(resolved)
        } else {
            Result.success(null)
        }
    }

    private suspend fun resolveSignedInTechnicianProfile(
        uid: String,
        db: FirebaseFirestore,
        workspaceIdHint: String = ""
    ): Result<CloudTechSignInResult> {
        return try {
            val wsDoc = if (workspaceIdHint.isNotBlank()) {
                val hintSnap = runCatching {
                    db.collection("workspaces").document(workspaceIdHint).get().await()
                }.getOrNull()
                if (hintSnap != null && hintSnap.exists()) hintSnap else null
            } else {
                null
            } ?: db.collection("workspaces")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()

            if (wsDoc == null || !wsDoc.exists()) {
                return Result.failure(IllegalStateException("No Technician workspace assigned to this account."))
            }

            val wsId = wsDoc.id
            val memberSnap = db.collection("workspaces").document(wsId)
                .collection("members").document(uid)
                .get()
                .await()
            if (!memberSnap.exists()) {
                return Result.failure(IllegalStateException("Technician membership record not found in workspace."))
            }

            val role = BillingRepository.normalizeRole(memberSnap.getString("role").orEmpty())
            val loginEnabled = memberSnap.getBoolean("loginEnabled") ?: true
            if (role == "technician" && !loginEnabled) {
                return Result.failure(IllegalStateException(AuthPolicy.TECH_LOGIN_DISABLED_MESSAGE))
            }
            val techId = memberSnap.getString("techId").orEmpty()
            val email = memberSnap.getString("email").orEmpty()
            val name = memberSnap.getString("name").orEmpty()
                .ifBlank { memberSnap.getString("displayName").orEmpty() }
                .ifBlank { "Technician" }
            val username = memberSnap.getString("username").orEmpty()
                .ifBlank { AuthPolicy.deriveUsernameFromEmailOrName(email, name) }
            val phone = memberSnap.getString("phone").orEmpty()
            val mustChangePassword = memberSnap.getBoolean("mustChangePassword") ?: false
            val companyName = AppIdentity.OFFICIAL_COMPANY_NAME

            val photoB64 = runCatching {
                db.collection("workspaces").document(wsId)
                    .collection("accounts").document(username.lowercase(Locale.US))
                    .get().await().getString("photoBase64").orEmpty()
            }.getOrDefault("")

            Result.success(
                CloudTechSignInResult(
                    uid = uid,
                    email = email,
                    name = name,
                    username = username,
                    phone = phone,
                    photoBase64 = photoB64,
                    role = role,
                    workspaceId = wsId,
                    mustChangePassword = mustChangePassword,
                    companyName = companyName,
                    techId = techId,
                    loginEnabled = loginEnabled
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "workspaces/members/$uid")
            Result.failure(e)
        }
    }

    /**
     * Updates Firebase Auth password for the currently logged-in Technician on first login
     * and clears `mustChangePassword` in Firestore cleanly.
     */
    suspend fun updateCurrentTechnicianPassword(
        workspaceId: String,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val auth = getAuth()
        val user = auth?.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Session expired. Please sign in again."))
        val db = getFirestore()
            ?: return@withContext Result.failure(IllegalStateException("Cloud database not initialized."))

        AuthPolicy.evaluatePassword(newPassword).firstMissingRequirement()?.let { msg ->
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        try {
            user.updatePassword(newPassword).await()

            val updates = hashMapOf<String, Any>(
                "mustChangePassword" to false,
                "passwordChangedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspaces").document(workspaceId)
                .collection("members").document(user.uid)
                .update(updates)
                .await()

            // Best-effort server confirmation via Cloud Function as well.
            runCatching {
                getFunctions()
                    ?.getHttpsCallable("completeTechnicianPasswordChange")
                    ?.call(hashMapOf("workspaceId" to workspaceId))
                    ?.await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating technician first-login password", e)
            Result.failure(
                IllegalStateException(
                    e.message ?: "Failed to update password. Please sign in again and retry.",
                    e
                )
            )
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

                val billsList = when (normRole) {
                    "admin", "super_admin" -> {
                        runCatching { wsRef.collection("bills").get().await().documents.mapNotNull(::parseBillDoc) }
                            .getOrElse { emptyList() }
                            .sortedByDescending { it.updatedAt }
                    }
                    else -> {
                        runCatching {
                            wsRef.collection("bills")
                                .whereEqualTo("createdByUid", uid)
                                .get()
                                .await()
                                .documents
                                .mapNotNull(::parseBillDoc)
                        }.getOrElse { emptyList() }.sortedByDescending { it.updatedAt }
                    }
                }

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

                val comboList = runCatching {
                    db.collection("companies").document(workspaceId)
                        .collection("productComboFiles").get().await()
                        .documents.mapNotNull(::parseProductComboFileDoc)
                }.getOrElse { emptyList() }

                val upiTxList = runCatching {
                    wsRef.collection("upiTransactions").get().await()
                        .documents.mapNotNull(::parseUpiTransactionDoc)
                }.getOrElse { emptyList() }

                // Strict Super Admin ONLY Employee & Salary fetch (never fetched for Admin or Technician)
                val employeeList = if (normRole == "super_admin") {
                    runCatching {
                        db.collection("employees")
                            .whereEqualTo("companyId", workspaceId)
                            .get().await()
                            .documents.mapNotNull(::parseEmployeeDoc)
                    }.getOrElse { emptyList() }
                } else emptyList()

                val salaryList = if (normRole == "super_admin") {
                    employeeList.flatMap { emp ->
                        runCatching {
                            db.collection("employees").document(emp.employeeId)
                                .collection("salaryPayments").get().await()
                                .documents.mapNotNull(::parseSalaryPaymentDoc)
                        }.getOrElse { emptyList() }
                    }
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
                    auditLogs = auditList,
                    comboFiles = comboList,
                    upiTransactions = upiTxList,
                    employees = employeeList,
                    salaryPayments = salaryList
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
        onAuditLogsChanged: (List<AuditLogEntity>) -> Unit = {},
        onNotificationsChanged: (List<CloudWorkspaceNotification>) -> Unit = {},
        onComboFilesChanged: (List<ProductComboFileEntity>) -> Unit = {},
        onUpiTransactionsChanged: (List<UpiTransactionEntity>) -> Unit = {},
        onEmployeesChanged: (List<EmployeeEntity>) -> Unit = {},
        onSalaryPaymentsChanged: (List<SalaryPaymentEntity>) -> Unit = {}
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
        } else {
            registrations += wsRef.collection("bills")
                .whereEqualTo("createdByUid", uid)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/bills")
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        onBillsChanged(snap.documents.mapNotNull(::parseBillDoc).sortedByDescending { it.updatedAt })
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

        registrations += wsRef.collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(25)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/notifications")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onNotificationsChanged(snap.documents.mapNotNull(::parseNotificationDoc))
                }
            }

        registrations += db.collection("companies").document(workspaceId)
            .collection("productComboFiles")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "companies/$workspaceId/productComboFiles")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onComboFilesChanged(snap.documents.mapNotNull(::parseProductComboFileDoc).sortedByDescending { it.updatedAt })
                }
            }

        registrations += wsRef.collection("upiTransactions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, "workspaces/$workspaceId/upiTransactions")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    onUpiTransactionsChanged(snap.documents.mapNotNull(::parseUpiTransactionDoc))
                }
            }

        // Strict Super Admin ONLY Employee & Salary real-time sync
        if (normRole == "super_admin") {
            val salaryRegs = mutableMapOf<String, ListenerRegistration>()
            val salaryByEmp = mutableMapOf<String, List<SalaryPaymentEntity>>()
            registrations += db.collection("employees")
                .whereEqualTo("companyId", workspaceId)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        handleFirestoreError(err, OperationType.LIST, "employees")
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        val emps = snap.documents.mapNotNull(::parseEmployeeDoc)
                        onEmployeesChanged(emps)
                        val activeIds = emps.map { it.employeeId }.toSet()
                        val removedIds = salaryRegs.keys.filter { it !in activeIds }
                        removedIds.forEach { eid ->
                            runCatching { salaryRegs.remove(eid)?.remove() }
                            salaryByEmp.remove(eid)
                        }
                        emps.forEach { emp ->
                            if (!salaryRegs.containsKey(emp.employeeId)) {
                                val reg = db.collection("employees").document(emp.employeeId)
                                    .collection("salaryPayments")
                                    .addSnapshotListener { pSnap, pErr ->
                                        if (pErr == null && pSnap != null) {
                                            salaryByEmp[emp.employeeId] = pSnap.documents.mapNotNull(::parseSalaryPaymentDoc)
                                            onSalaryPaymentsChanged(salaryByEmp.values.flatten().sortedByDescending { it.createdAt })
                                        }
                                    }
                                salaryRegs[emp.employeeId] = reg
                                registrations += reg
                            }
                        }
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
                "companyName" to AppIdentity.OFFICIAL_COMPANY_NAME,
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
                    "email" to account.email.trim().lowercase(Locale.US),
                    "uid" to account.uid.trim(),
                    "techId" to account.techId.trim(),
                    "companyId" to account.companyId.ifBlank { workspaceId },
                    "loginEnabled" to account.loginEnabled,
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
                    "purchasePrice" to product.purchasePrice.coerceAtLeast(0.0),
                    "sku" to product.sku.trim(),
                    "barcode" to product.barcode.trim(),
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

    /**
     * Partial stock-only update so Technicians can sync stock decrements/restorations
     * cleanly under zero-trust Firestore rules without modifying product price/metadata.
     */
    suspend fun pushProductStockAdjustment(workspaceId: String, product: ProductEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (workspaceId.isBlank() || getAuth()?.currentUser == null || product.id <= 0L) {
                return@withContext Result.success(Unit)
            }
            val docId = "p_${product.id}"
            try {
                val updates = hashMapOf<String, Any>(
                    "stockQty" to product.stockQty.coerceAtLeast(0.0),
                    "minStockAlert" to product.minStockAlert.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("workspaces").document(workspaceId)
                    .collection("products").document(docId)
                    .update(updates).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "workspaces/$workspaceId/products/$docId")
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
            val docType = if (bill.documentType.equals("QUOTATION", ignoreCase = true)) "QUOTATION" else "BILL"
            val paymentStatus = if (docType == "QUOTATION") "UNPAID" else BillPaymentStatus.resolveForBill(bill).code
            try {
                val docRef = db.collection("workspaces").document(workspaceId)
                    .collection("bills").document(docId)
                val existing = runCatching { withTimeoutOrNull(1000L) { docRef.get().await() } }.getOrNull()
                val preservedCreatorUid = existing?.getString("createdByUid")?.takeIf { it.isNotBlank() } ?: uid
                val data = hashMapOf<String, Any>(
                    "billId" to docId,
                    "invoiceNo" to bill.invoiceNo.ifBlank { "TS-001" },
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
                    "paymentStatus" to paymentStatus,
                    "linkedPaymentId" to bill.linkedPaymentId,
                    "stockDeducted" to (bill.stockDeducted && docType == "BILL"),
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
                    "documentType" to docType,
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

    suspend fun commitBillWithAtomicStockTransaction(
        workspaceId: String,
        bill: BillEntity,
        productDeltas: Map<String, Double>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getAuth()?.currentUser?.uid
        val db = getFirestore()
        if (workspaceId.isBlank() || uid.isNullOrBlank() || db == null) {
            return@withContext Result.success(Unit)
        }
        val docId = bill.id.ifBlank { "b_${System.currentTimeMillis()}" }
        val docType = if (bill.documentType.equals("QUOTATION", ignoreCase = true)) "QUOTATION" else "BILL"
        val paymentStatus = if (docType == "QUOTATION") "UNPAID" else BillPaymentStatus.resolveForBill(bill).code
        val wsRef = db.collection("workspaces").document(workspaceId)
        val billRef = wsRef.collection("bills").document(docId)
        try {
            db.runTransaction { tx ->
                val existingBillSnap = tx.get(billRef)
                val preservedCreatorUid = existingBillSnap.getString("createdByUid")
                    ?.takeIf { it.isNotBlank() } ?: uid

                val prodSnapshots = productDeltas.keys.map { prodDocId ->
                    val prodRef = wsRef.collection("products").document(prodDocId)
                    prodDocId to (prodRef to tx.get(prodRef))
                }.toMap()

                for ((prodDocId, deltaNeeded) in productDeltas) {
                    if (deltaNeeded <= 0.0) continue
                    val (_, prodSnap) = prodSnapshots[prodDocId] ?: continue
                    if (!prodSnap.exists()) continue
                    val currentStock = (prodSnap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                    val prodName = prodSnap.getString("name").orEmpty().ifBlank { prodDocId }
                    if (currentStock + 1e-6 < deltaNeeded) {
                        throw IllegalStateException(
                            "Insufficient stock for $prodName. Available: ${
                                if (currentStock % 1.0 == 0.0) currentStock.toInt().toString()
                                else String.format(Locale.US, "%.2f", currentStock)
                            }"
                        )
                    }
                }

                for ((prodDocId, deltaChange) in productDeltas) {
                    if (kotlin.math.abs(deltaChange) <= 1e-6) continue
                    val (prodRef, prodSnap) = prodSnapshots[prodDocId] ?: continue
                    if (!prodSnap.exists()) continue
                    val currentStock = (prodSnap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                    val newStock = (currentStock - deltaChange).coerceAtLeast(0.0)
                    tx.update(
                        prodRef,
                        mapOf(
                            "stockQty" to newStock,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val billData = hashMapOf<String, Any>(
                    "billId" to docId,
                    "invoiceNo" to bill.invoiceNo.ifBlank { "TS-001" },
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
                    "paymentStatus" to paymentStatus,
                    "linkedPaymentId" to bill.linkedPaymentId,
                    "stockDeducted" to (docType == "BILL"),
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
                    "documentType" to docType,
                    "helpBill" to bill.isHelpBill,
                    "helpRequestId" to bill.helpRequestId.coerceAtLeast(0L),
                    "quotationNote" to bill.quotationNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tx.set(billRef, billData)
                null
            }.await()
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
            val safeCategory = HelpCategory.fromString(req.category).code
            val safeQuotStatus = when (req.quotationStatus.uppercase(Locale.US)) {
                "PENDING", "APPROVED", "REJECTED", "" -> req.quotationStatus.uppercase(Locale.US)
                else -> ""
            }
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
                        "rejectedReason" to req.rejectedReason,
                        "updatedAt" to req.updatedAt
                    )
                    ref.update(partial).awaitOfflineSafe()
                } else {
                    val data = hashMapOf<String, Any>(
                        "helpId" to req.id,
                        "helpTokenId" to req.helpTokenId,
                        "quotationId" to req.quotationId,
                        "approvedQuotationJson" to req.approvedQuotationJson,
                        "customerName" to req.customerName,
                        "phone" to req.phone,
                        "address" to req.address,
                        "locationUrl" to req.locationUrl,
                        "helpDescription" to req.helpDescription,
                        "category" to safeCategory,
                        "quotationStatus" to safeQuotStatus,
                        "installationDate" to req.installationDate,
                        "installationReminderSent" to req.installationReminderSent,
                        "rejectedReason" to req.rejectedReason,
                        "assignedTechnician" to req.assignedTechnician,
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

    suspend fun pushNotification(
        workspaceId: String,
        category: String,
        title: String,
        message: String,
        targetRole: String = "all",
        createdBy: String = "",
        targetUsername: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getAuth()?.currentUser?.uid
        val db = getFirestore()
        if (workspaceId.isBlank() || uid.isNullOrBlank() || db == null) {
            return@withContext Result.success(Unit)
        }
        val safeTitle = title.trim().take(160)
        if (safeTitle.isBlank()) return@withContext Result.success(Unit)
        val safeCategory = when (category.uppercase(Locale.US)) {
            "PRODUCT", "STOCK", "CUSTOMER", "BILLING", "PAYMENT", "SERVICE_REQUEST", "QUOTATION", "INSTALLATION", "SYSTEM" ->
                category.uppercase(Locale.US)
            else -> "SYSTEM"
        }
        val safeTargetRole = when (targetRole.lowercase(Locale.US)) {
            "admin", "technician", "super_admin", "all" -> targetRole.lowercase(Locale.US)
            else -> "all"
        }
        val notifId = "n_${System.currentTimeMillis()}_${uid.take(6)}_${random.nextInt(1000, 9999)}"
        try {
            val data = hashMapOf<String, Any>(
                "notifId" to notifId,
                "category" to safeCategory,
                "title" to safeTitle,
                "message" to message.trim().take(500),
                "targetRole" to safeTargetRole,
                "targetUsername" to targetUsername.trim(),
                "createdBy" to createdBy.trim().ifBlank { getAuth()?.currentUser?.email.orEmpty() },
                "createdByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspaces").document(workspaceId)
                .collection("notifications").document(notifId)
                .set(data).awaitOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "workspaces/$workspaceId/notifications/$notifId")
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
            companyName = AppIdentity.OFFICIAL_COMPANY_NAME
        )

    private fun parseCompanySettings(doc: DocumentSnapshot): CompanySettings = CompanySettings(
        companyName = AppIdentity.OFFICIAL_COMPANY_NAME,
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
            photoBase64 = doc.getString("photoBase64").orEmpty(),
            email = doc.getString("email").orEmpty(),
            uid = doc.getString("uid").orEmpty(),
            techId = doc.getString("techId").orEmpty(),
            companyId = doc.getString("companyId").orEmpty(),
            loginEnabled = doc.getBoolean("loginEnabled") ?: true
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
            minStockAlert = (doc.get("minStockAlert") as? Number)?.toDouble() ?: 0.0,
            purchasePrice = (doc.get("purchasePrice") as? Number)?.toDouble() ?: 0.0,
            sku = doc.getString("sku").orEmpty(),
            barcode = doc.getString("barcode").orEmpty()
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
        val grandTotal = (doc.get("grandTotal") as? Number)?.toDouble() ?: 0.0
        val paidAmount = (doc.get("paidAmount") as? Number)?.toDouble() ?: 0.0
        val docType = doc.getString("documentType").orEmpty().ifBlank { "BILL" }
        val explicitPayStatus = doc.getString("paymentStatus").orEmpty()
        val computedPayStatus = if (docType.equals("QUOTATION", ignoreCase = true)) {
            "UNPAID"
        } else if (explicitPayStatus.isNotBlank()) {
            explicitPayStatus
        } else {
            BillPaymentStatus.compute(grandTotal, paidAmount).code
        }
        return BillEntity(
            id = id,
            invoiceNo = inv,
            date = doc.getString("date").orEmpty(),
            customerName = doc.getString("customerName").orEmpty(),
            customerDetails = doc.getString("customerDetails").orEmpty(),
            itemsJson = doc.getString("itemsJson").orEmpty().ifBlank { "[]" },
            subtotal = (doc.get("subtotal") as? Number)?.toDouble() ?: 0.0,
            discount = (doc.get("discount") as? Number)?.toDouble() ?: 0.0,
            grandTotal = grandTotal,
            paidAmount = paidAmount,
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
            documentType = docType,
            isHelpBill = doc.getBoolean("helpBill") ?: false,
            helpRequestId = (doc.get("helpRequestId") as? Number)?.toLong() ?: 0L,
            quotationNote = doc.getString("quotationNote").orEmpty(),
            paymentStatus = computedPayStatus,
            linkedPaymentId = doc.getString("linkedPaymentId").orEmpty(),
            stockDeducted = doc.getBoolean("stockDeducted") ?: false
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
            billId = doc.getString("billId").orEmpty(),
            category = HelpCategory.fromString(doc.getString("category").orEmpty()).code,
            quotationStatus = doc.getString("quotationStatus").orEmpty(),
            installationDate = doc.getString("installationDate").orEmpty(),
            installationReminderSent = doc.getBoolean("installationReminderSent") ?: false,
            rejectedReason = doc.getString("rejectedReason").orEmpty(),
            assignedTechnician = doc.getString("assignedTechnician").orEmpty(),
            helpTokenId = doc.getString("helpTokenId").orEmpty(),
            quotationId = doc.getString("quotationId").orEmpty(),
            approvedQuotationJson = doc.getString("approvedQuotationJson").orEmpty()
        )
    }

    suspend fun pushProductComboFile(companyId: String, combo: ProductComboFileEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (companyId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            val docId = combo.comboFileId
            try {
                runCatching {
                    db.collection("companies").document(companyId).set(
                        hashMapOf(
                            "companyId" to companyId,
                            "companyName" to AppIdentity.OFFICIAL_COMPANY_NAME,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).awaitOfflineSafe()
                }
                val data = hashMapOf<String, Any>(
                    "comboFileId" to docId,
                    "companyId" to companyId,
                    "name" to combo.name.trim(),
                    "description" to combo.description.trim(),
                    "itemsJson" to combo.itemsJson,
                    "comboPrice" to combo.comboPrice.coerceAtLeast(0.0),
                    "createdAt" to combo.createdAt,
                    "updatedAt" to combo.updatedAt
                )
                db.collection("companies").document(companyId)
                    .collection("productComboFiles").document(docId)
                    .set(data).awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "companies/$companyId/productComboFiles/$docId")
                Result.failure(e)
            }
        }

    suspend fun deleteProductComboFile(companyId: String, comboFileId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val db = getFirestore() ?: return@withContext Result.success(Unit)
            if (companyId.isBlank() || getAuth()?.currentUser == null) return@withContext Result.success(Unit)
            try {
                db.collection("companies").document(companyId)
                    .collection("productComboFiles").document(comboFileId)
                    .delete().awaitOfflineSafe()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "companies/$companyId/productComboFiles/$comboFileId")
                Result.failure(e)
            }
        }

    private fun parseProductComboFileDoc(doc: DocumentSnapshot): ProductComboFileEntity? {
        val name = doc.getString("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        return ProductComboFileEntity(
            comboFileId = doc.getString("comboFileId").orEmpty().ifBlank { doc.id },
            companyId = doc.getString("companyId").orEmpty(),
            name = name,
            description = doc.getString("description").orEmpty(),
            itemsJson = doc.getString("itemsJson").orEmpty().ifBlank { "[]" },
            comboPrice = (doc.get("comboPrice") as? Number)?.toDouble() ?: 0.0,
            createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (doc.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun parseUpiTransactionDoc(doc: DocumentSnapshot): UpiTransactionEntity? {
        val paymentId = doc.getString("paymentId").orEmpty().ifBlank { doc.id }
        if (paymentId.isBlank()) return null
        val createdMillis = (doc.get("createdAtMillis") as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        val updatedMillis = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: createdMillis
        return UpiTransactionEntity(
            paymentId = paymentId,
            billId = doc.getString("billId").orEmpty(),
            invoiceNo = doc.getString("invoiceNo").orEmpty(),
            orderId = doc.getString("orderId").orEmpty(),
            amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
            status = PaymentGatewayStatus.fromString(doc.getString("status").orEmpty()).code,
            paymentGateway = doc.getString("paymentGateway").orEmpty().ifBlank { "RAZORPAY" },
            gatewaySignature = doc.getString("gatewaySignature").orEmpty(),
            qrCodeUrl = doc.getString("qrCodeUrl").orEmpty(),
            customerEmail = doc.getString("customerEmail").orEmpty(),
            customerPhone = doc.getString("customerPhone").orEmpty(),
            createdAt = createdMillis,
            updatedAt = updatedMillis,
            companyId = doc.getString("companyId").orEmpty(),
            customerId = doc.getString("customerId").orEmpty(),
            customerName = doc.getString("customerName").orEmpty(),
            paymentRef = doc.getString("paymentRef").orEmpty(),
            txId = doc.getString("txId").orEmpty(),
            upiUri = doc.getString("upiUri").orEmpty(),
            receiptNumber = doc.getString("receiptNumber").orEmpty(),
            expiresAtMillis = (doc.get("expiresAtMillis") as? Number)?.toLong() ?: 0L
        )
    }

    suspend fun pushEmployee(employee: EmployeeEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getAuth()?.currentUser?.uid
        val db = getFirestore()
        if (uid.isNullOrBlank() || db == null || employee.companyId.isBlank()) return@withContext Result.success(Unit)
        try {
            val data = hashMapOf<String, Any>(
                "employeeId" to employee.employeeId,
                "employeeCode" to employee.employeeCode,
                "companyId" to employee.companyId,
                "name" to employee.name.trim(),
                "mobile" to employee.mobile.trim(),
                "hiringDate" to employee.hiringDate.trim(),
                "workStartDate" to employee.workStartDate.trim(),
                "workStopDate" to employee.workStopDate.trim(),
                "monthlySalary" to employee.monthlySalary.coerceAtLeast(0.0),
                "address" to employee.address.trim(),
                "notes" to employee.notes.trim(),
                "isActive" to employee.isActive,
                "createdAt" to employee.createdAt,
                "updatedAt" to employee.updatedAt,
                "createdByUid" to employee.createdByUid.ifBlank { uid }
            )
            db.collection("employees").document(employee.employeeId)
                .set(data).awaitOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "employees/${employee.employeeId}")
            Result.failure(e)
        }
    }

    suspend fun deleteEmployee(employeeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = getFirestore() ?: return@withContext Result.success(Unit)
        if (getAuth()?.currentUser == null || employeeId.isBlank()) return@withContext Result.success(Unit)
        try {
            db.collection("employees").document(employeeId).delete().awaitOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "employees/$employeeId")
            Result.failure(e)
        }
    }

    suspend fun pushSalaryPayment(payment: SalaryPaymentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getAuth()?.currentUser?.uid
        val db = getFirestore()
        if (uid.isNullOrBlank() || db == null || payment.employeeId.isBlank()) return@withContext Result.success(Unit)
        try {
            val data = hashMapOf<String, Any>(
                "paymentId" to payment.paymentId,
                "employeeId" to payment.employeeId,
                "companyId" to payment.companyId,
                "amount" to payment.amount.coerceAtLeast(0.0),
                "paymentDate" to payment.paymentDate.trim(),
                "method" to payment.method.trim(),
                "referenceNo" to payment.referenceNo.trim(),
                "notes" to payment.notes.trim(),
                "cycleStart" to payment.cycleStart.trim(),
                "cycleEnd" to payment.cycleEnd.trim(),
                "createdAt" to payment.createdAt,
                "createdByUid" to payment.createdByUid.ifBlank { uid }
            )
            db.collection("employees").document(payment.employeeId)
                .collection("salaryPayments").document(payment.paymentId)
                .set(data).awaitOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "employees/${payment.employeeId}/salaryPayments/${payment.paymentId}")
            Result.failure(e)
        }
    }

    private fun parseEmployeeDoc(doc: DocumentSnapshot): EmployeeEntity? {
        val name = doc.getString("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        return EmployeeEntity(
            employeeId = doc.getString("employeeId").orEmpty().ifBlank { doc.id },
            employeeCode = doc.getString("employeeCode").orEmpty(),
            companyId = doc.getString("companyId").orEmpty(),
            name = name,
            mobile = doc.getString("mobile").orEmpty(),
            hiringDate = doc.getString("hiringDate").orEmpty(),
            workStartDate = doc.getString("workStartDate").orEmpty(),
            workStopDate = doc.getString("workStopDate").orEmpty(),
            monthlySalary = (doc.get("monthlySalary") as? Number)?.toDouble() ?: 0.0,
            address = doc.getString("address").orEmpty(),
            notes = doc.getString("notes").orEmpty(),
            isActive = doc.getBoolean("isActive") ?: true,
            createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (doc.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
            createdByUid = doc.getString("createdByUid").orEmpty()
        )
    }

    private fun parseSalaryPaymentDoc(doc: DocumentSnapshot): SalaryPaymentEntity? {
        val paymentId = doc.getString("paymentId").orEmpty().ifBlank { doc.id }
        val employeeId = doc.getString("employeeId").orEmpty()
        if (paymentId.isBlank() || employeeId.isBlank()) return null
        return SalaryPaymentEntity(
            paymentId = paymentId,
            employeeId = employeeId,
            companyId = doc.getString("companyId").orEmpty(),
            amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
            paymentDate = doc.getString("paymentDate").orEmpty(),
            method = doc.getString("method").orEmpty().ifBlank { "Cash" },
            referenceNo = doc.getString("referenceNo").orEmpty(),
            notes = doc.getString("notes").orEmpty(),
            cycleStart = doc.getString("cycleStart").orEmpty(),
            cycleEnd = doc.getString("cycleEnd").orEmpty(),
            createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
            createdByUid = doc.getString("createdByUid").orEmpty()
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

    private fun parseNotificationDoc(doc: DocumentSnapshot): CloudWorkspaceNotification? {
        val title = doc.getString("title")?.trim().orEmpty()
        if (title.isEmpty()) return null
        val notifId = doc.getString("notifId").orEmpty().ifBlank { doc.id }
        val ts = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: System.currentTimeMillis()
        return CloudWorkspaceNotification(
            id = notifId,
            category = doc.getString("category").orEmpty().ifBlank { "SYSTEM" },
            title = title,
            message = doc.getString("message").orEmpty(),
            targetRole = doc.getString("targetRole").orEmpty().ifBlank { "all" },
            targetUsername = doc.getString("targetUsername").orEmpty(),
            createdBy = doc.getString("createdBy").orEmpty(),
            createdByUid = doc.getString("createdByUid").orEmpty(),
            createdAt = ts
        )
    }
}
