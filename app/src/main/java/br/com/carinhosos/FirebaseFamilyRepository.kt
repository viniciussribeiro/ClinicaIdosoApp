package br.com.carinhosos

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adaptador somente leitura para o back-end existente do projeto extensao-unip.
 * Não há chamadas set, add, update ou delete neste repositório.
 */
class FirebaseFamilyRepository(context: Context) {
    // Uma instância nomeada evita depender de google-services.json e isola esta
    // integração caso outro FirebaseApp seja adicionado ao aplicativo no futuro.
    private val app: FirebaseApp = firebaseApp(context.applicationContext)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(app)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(app)

    fun currentUserEmail(): String? = auth.currentUser?.email

    fun signIn(email: String, password: String, onResult: (Result<String>) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val authenticatedEmail = result.user?.email
                if (authenticatedEmail.isNullOrBlank()) {
                    onResult(Result.failure(IllegalStateException("Conta sem e-mail.")))
                } else {
                    onResult(Result.success(authenticatedEmail))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun signOut() = auth.signOut()

    /**
     * Carrega tudo o que a interface familiar precisa em uma única fotografia
     * imutável. O encadeamento é paciente -> estoque -> profissionais, sempre
     * por consultas GET; o aplicativo não possui operações de escrita.
     */
    fun loadFamilySnapshot(email: String, onResult: (Result<FamilySnapshot>) -> Unit) {
        loadPatient(email) { patientResult ->
            patientResult.fold(
                onSuccess = { patientDocument ->
                    firestore.collection(STOCK_COLLECTION).get()
                        .addOnSuccessListener { stocks ->
                            firestore.collection(USERS_COLLECTION).get()
                                .addOnSuccessListener { professionals ->
                                    runCatching {
                                        mapSnapshot(
                                            patientDocument,
                                            stocks.documents,
                                            professionals.documents
                                        )
                                    }.also(onResult)
                                }
                                .addOnFailureListener {
                                    // A foto do profissional é complementar; os dados clínicos
                                    // continuam disponíveis caso a coleção users falhe.
                                    runCatching {
                                        mapSnapshot(patientDocument, stocks.documents, emptyList())
                                    }.also(onResult)
                                }
                        }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    private fun loadPatient(email: String, onResult: (Result<DocumentSnapshot>) -> Unit) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        // A conta legada de demonstração ainda não possui guardianEmail no
        // Firestore. Contas regulares seguem o vínculo persistido pelo back-end.
        val demonstrationPatientId = DEMONSTRATION_ASSIGNMENTS[normalizedEmail]
        if (demonstrationPatientId != null) {
            firestore.collection(ELDERLY_COLLECTION).document(demonstrationPatientId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) onResult(Result.success(document))
                    else onResult(Result.failure(NoSuchElementException("Paciente demonstrativo não encontrado.")))
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
            return
        }

        firestore.collection(ELDERLY_COLLECTION)
            .whereEqualTo("guardianEmail", normalizedEmail)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val document = result.documents.firstOrNull()
                if (document != null) onResult(Result.success(document))
                else onResult(Result.failure(NoSuchElementException("Nenhum paciente está vinculado a esta conta.")))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun mapSnapshot(
        patientDocument: DocumentSnapshot,
        stockDocuments: List<DocumentSnapshot>,
        professionalDocuments: List<DocumentSnapshot>
    ): FamilySnapshot {
        val patient = patientDocument.toPatient()
        val rawMedications = patientDocument.rawMedications()
        val stockItems = stockDocuments.mapNotNull { it.toStockItem() }
        val professionals = professionalDocuments.mapNotNull { it.toProfessional() }
        val medicineBuild = buildMedicines(patient.id, rawMedications, stockItems)
        val events = buildEvents(
            patientDocument,
            rawMedications,
            medicineBuild.rawIndexToMedicine,
            professionals
        )

        return FamilySnapshot(
            patient = patient,
            medicines = medicineBuild.medicines,
            events = events.sortedBy { it.scheduledAt },
            updatedAt = Date()
        )
    }

    private fun DocumentSnapshot.toPatient(): Patient = Patient(
        id = id,
        name = string("name", "Paciente"),
        birthDate = formatBackendDate(string("birthDate")),
        room = string("room", "Não informado"),
        clinicName = "Clínica Carinhosos",
        clinicPhone = "",
        careTeam = "Equipe clínica",
        relationship = string("guardianRelationship", "Responsável"),
        guardianName = string("guardianName", "Responsável"),
        guardianEmail = string("guardianEmail"),
        guardianPhone = string("guardianPhone"),
        observations = string("observations"),
        photoUrl = string("photo")
    )

    private fun DocumentSnapshot.rawMedications(): List<RawMedication> =
        (get("medications") as? List<*>)
            .orEmpty()
            .mapIndexedNotNull { index, value ->
                val map = value as? Map<*, *> ?: return@mapIndexedNotNull null
                val rawName = map.string("name")
                if (rawName.isBlank()) return@mapIndexedNotNull null
                val parsedName = parseMedicationName(rawName)
                RawMedication(
                    index = index,
                    name = parsedName.first,
                    dose = parsedName.second,
                    time = map.string("time", "00:00"),
                    startDate = map.string("startDate"),
                    recurrence = map.string("recurrence", "Daily"),
                    intervalHours = (map["intervalHours"] as? Number)?.toInt()
                )
            }

    private fun DocumentSnapshot.toStockItem(): StockItem? {
        val name = string("name")
        if (name.isBlank()) return null
        return StockItem(
            name = name,
            quantity = number("quantity"),
            minimum = number("minQuantity"),
            unit = string("unit", "unidades")
        )
    }

    private fun DocumentSnapshot.toProfessional(): ProfessionalProfile? {
        val name = string("name")
        if (name.isBlank()) return null
        return ProfessionalProfile(name = name, photoUrl = string("photo"))
    }

    private fun buildMedicines(
        patientId: String,
        rawMedications: List<RawMedication>,
        stocks: List<StockItem>
    ): MedicineBuild {
        val rawIndexToMedicine = mutableMapOf<Int, Medicine>()
        // Um mesmo medicamento pode aparecer mais de uma vez no array para
        // representar horários diferentes. A tela apresenta um cartão agrupado.
        val medicines = rawMedications.groupBy { normalizeName(it.name) }.map { (key, group) ->
            val stock = stocks.firstOrNull {
                val stockKey = normalizeName(it.name)
                stockKey == key || stockKey in key || key in stockKey
            }
            val schedules = group.flatMap(::schedulesFor).distinct().sorted()
            val recurrence = recurrenceDescription(group)
            val medicine = Medicine(
                id = "$patientId-$key",
                name = group.first().name,
                dose = group.map { it.dose }.firstOrNull { it.isNotBlank() }.orEmpty(),
                presentation = stock?.unit ?: "Medicamento",
                instructions = recurrence,
                schedules = schedules,
                stockQuantity = stock?.quantity ?: 0,
                minimumStock = stock?.minimum ?: 0,
                stockUnit = stock?.unit ?: "unidades",
                unitsPerDay = dailyUnits(group),
                suppliedBy = "Não informado pelo back-end",
                stockAvailable = stock != null
            )
            group.forEach { rawIndexToMedicine[it.index] = medicine }
            medicine
        }.sortedBy { it.name.lowercase(Locale.ROOT) }

        return MedicineBuild(medicines, rawIndexToMedicine)
    }

    private fun buildEvents(
        patientDocument: DocumentSnapshot,
        rawMedications: List<RawMedication>,
        rawIndexToMedicine: Map<Int, Medicine>,
        professionals: List<ProfessionalProfile>
    ): List<DoseEvent> {
        // Os logs guardam o índice original do array medications; por isso o
        // mapeamento por índice deve ser preservado mesmo após o agrupamento.
        val logMaps = (patientDocument.get("logs") as? List<*>).orEmpty()
        val administered = logMaps.mapIndexedNotNull { logIndex, value ->
            val map = value as? Map<*, *> ?: return@mapIndexedNotNull null
            val medicationIndex = (map["medicationIndex"] as? Number)?.toInt()
                ?: return@mapIndexedNotNull null
            val raw = rawMedications.firstOrNull { it.index == medicationIndex }
                ?: return@mapIndexedNotNull null
            val medicine = rawIndexToMedicine[medicationIndex] ?: return@mapIndexedNotNull null
            val date = map.string("date")
            val scheduledTime = map.string("scheduledTime", raw.time)
            val scheduledAt = parseLocalDateTime(date, scheduledTime) ?: return@mapIndexedNotNull null
            val timestamp = map.string("timestamp")
            val professionalName = map.string("appliedBy", "Equipe clínica")
            DoseEvent(
                id = "log-$logIndex-$timestamp",
                medicineId = medicine.id,
                medicineName = medicine.name,
                dose = medicine.dose,
                scheduledAt = scheduledAt,
                administeredAt = parseTimestamp(timestamp),
                professional = professionalName,
                professionalPhotoUrl = professionals.firstOrNull {
                    normalizeName(it.name) == normalizeName(professionalName)
                }?.photoUrl,
                explicitStatus = DoseStatus.ADMINISTERED
            )
        }

        val today = todayKey()
        // Eventos ainda não aplicados não existem no Firestore. Eles são
        // derivados localmente da prescrição para compor a agenda do dia.
        val scheduledToday = rawMedications.flatMap { raw ->
            if (!isScheduledOn(raw, today)) return@flatMap emptyList()
            val medicine = rawIndexToMedicine[raw.index] ?: return@flatMap emptyList()
            schedulesFor(raw).mapNotNull { time ->
                val scheduledAt = parseLocalDateTime(today, time) ?: return@mapNotNull null
                val wasAdministered = administered.any {
                    it.medicineId == medicine.id &&
                        it.scheduledAt.dayKey() == today &&
                        it.scheduledAt.timeLabel() == time
                }
                if (wasAdministered) null else DoseEvent(
                    id = "scheduled-${raw.index}-$today-$time",
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    dose = medicine.dose,
                    scheduledAt = scheduledAt
                )
            }
        }

        return administered + scheduledToday
    }

    private fun isScheduledOn(raw: RawMedication, date: String): Boolean = when (raw.recurrence.lowercase(Locale.ROOT)) {
        "once" -> raw.startDate == date
        else -> raw.startDate.isBlank() || raw.startDate <= date
    }

    private fun schedulesFor(raw: RawMedication): List<String> {
        if (!raw.recurrence.equals("Interval", ignoreCase = true)) return listOf(raw.time)
        val interval = raw.intervalHours?.takeIf { it in 1..23 } ?: return listOf(raw.time)
        val parts = raw.time.split(":")
        val startMinutes = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
            (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        // A recorrência Interval representa uma janela cíclica de 24 horas.
        val count = (24 / interval).coerceAtLeast(1)
        return (0 until count).map { offset ->
            val minutes = (startMinutes + offset * interval * 60) % (24 * 60)
            "%02d:%02d".format(Locale.US, minutes / 60, minutes % 60)
        }
    }

    private fun dailyUnits(group: List<RawMedication>): Int = group.sumOf { raw ->
        when (raw.recurrence.lowercase(Locale.ROOT)) {
            "once" -> 0
            "interval" -> schedulesFor(raw).size
            else -> 1
        }
    }.coerceAtLeast(1)

    private fun recurrenceDescription(group: List<RawMedication>): String {
        val interval = group.firstOrNull { it.recurrence.equals("Interval", ignoreCase = true) }
        if (interval != null) {
            return interval.intervalHours?.let { "Administrar a cada $it horas." }
                ?: "Administrar nos horários indicados."
        }
        if (group.all { it.recurrence.equals("Once", ignoreCase = true) }) {
            return "Dose única conforme a data e o horário prescritos."
        }
        return "Administrar diariamente nos horários indicados."
    }

    private fun DocumentSnapshot.string(field: String, fallback: String = ""): String =
        getString(field)?.trim().takeUnless { it.isNullOrBlank() } ?: fallback

    private fun DocumentSnapshot.number(field: String): Int = getLong(field)?.toInt() ?: 0

    private fun Map<*, *>.string(field: String, fallback: String = ""): String =
        (this[field] as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: fallback

    private fun firebaseApp(context: Context): FirebaseApp {
        // Reutilizar a instância é importante em recomposições e recriações
        // da Activity, pois o SDK não permite nomes duplicados de FirebaseApp.
        FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP_NAME }?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        return FirebaseApp.initializeApp(context, options, FIREBASE_APP_NAME)
    }

    private data class RawMedication(
        val index: Int,
        val name: String,
        val dose: String,
        val time: String,
        val startDate: String,
        val recurrence: String,
        val intervalHours: Int?
    )

    private data class StockItem(val name: String, val quantity: Int, val minimum: Int, val unit: String)
    private data class ProfessionalProfile(val name: String, val photoUrl: String)
    private data class MedicineBuild(
        val medicines: List<Medicine>,
        val rawIndexToMedicine: Map<Int, Medicine>
    )

    companion object {
        private const val FIREBASE_APP_NAME = "carinhosos-backend"
        private const val ELDERLY_COLLECTION = "elderly"
        private const val STOCK_COLLECTION = "stock"
        private const val USERS_COLLECTION = "users"

        // Compatibilidade necessária porque o back-end demonstrativo não persiste
        // guardianEmail=user@mail.com. Nenhum documento é alterado pelo aplicativo.
        private val DEMONSTRATION_ASSIGNMENTS = mapOf(
            "user@mail.com" to "82xgVb2t0oenSyfYaydO"
        )

        private val DosePattern = Regex("(?i)\\b(\\d+(?:[.,]\\d+)?\\s*(?:mcg|mg|ml|g))\\b")

        // O back-end atual armazena alguns valores como "Nome 10mg". Separar a
        // dose aqui mantém o modelo de apresentação consistente sem alterar a fonte.
        private fun parseMedicationName(value: String): Pair<String, String> {
            val dose = DosePattern.find(value)?.value.orEmpty().replace(" ", "")
            val name = value.replace(DosePattern, "").trim().ifBlank { value.trim() }
            return name to dose
        }

        // Remove acentos, espaços e pontuação para conciliar medications com
        // stock e profissionais, cujos nomes podem ter formatações diferentes.
        private fun normalizeName(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")

        private fun formatBackendDate(value: String): String {
            val parsed = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
            }.getOrNull() ?: return value
            val year = SimpleDateFormat("yyyy", Locale.US).format(parsed).toIntOrNull() ?: return value
            return if (year >= 1900) {
                SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(parsed)
            } else value
        }

        private fun parseLocalDateTime(date: String, time: String): Date? = runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
                .parse("$date $time")
        }.getOrNull()

        private fun parseTimestamp(value: String): Date? {
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm"
            )
            patterns.forEach { pattern ->
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return null
        }
    }
}
