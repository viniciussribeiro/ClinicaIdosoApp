package br.com.carinhosos

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dados do paciente e do responsável já adaptados para exibição. */
data class Patient(
    val id: String,
    val name: String,
    val birthDate: String,
    val room: String,
    val clinicName: String,
    val clinicPhone: String,
    val careTeam: String,
    val relationship: String,
    val guardianName: String,
    val guardianEmail: String,
    val guardianPhone: String,
    val observations: String,
    val photoUrl: String
) {
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

/**
 * Medicamento consolidado para a interface. Várias entradas do Firestore
 * podem originar um único item quando representam horários diferentes.
 */
data class Medicine(
    val id: String,
    val name: String,
    val dose: String,
    val presentation: String,
    val instructions: String,
    val schedules: List<String>,
    val stockQuantity: Int,
    val minimumStock: Int,
    val stockUnit: String,
    val unitsPerDay: Int,
    val suppliedBy: String,
    val stockAvailable: Boolean
) {
    // A estimativa usa consumo inteiro e só existe quando o estoque foi localizado.
    val daysRemaining: Int?
        get() = if (stockAvailable && unitsPerDay > 0) stockQuantity / unitsPerDay else null

    val stockLevel: StockLevel
        get() = when {
            !stockAvailable -> StockLevel.UNKNOWN
            stockQuantity <= minimumStock -> StockLevel.CRITICAL
            stockQuantity <= minimumStock * 2 -> StockLevel.ATTENTION
            else -> StockLevel.GOOD
        }
}

enum class StockLevel { GOOD, ATTENTION, CRITICAL, UNKNOWN }

enum class DoseStatus { ADMINISTERED, SCHEDULED, DELAYED, NOT_ADMINISTERED }

/** Evento registrado pela clínica ou compromisso calculado para o dia atual. */
data class DoseEvent(
    val id: String,
    val medicineId: String,
    val medicineName: String,
    val dose: String,
    val scheduledAt: Date,
    val administeredAt: Date? = null,
    val professional: String? = null,
    val professionalPhotoUrl: String? = null,
    val explicitStatus: DoseStatus? = null,
    val note: String? = null
) {
    // Atraso é uma regra visual do cliente: 30 minutos após o horário previsto.
    val status: DoseStatus
        get() {
            explicitStatus?.let { return it }
            if (administeredAt != null) return DoseStatus.ADMINISTERED
            return if (System.currentTimeMillis() > scheduledAt.time + 30 * 60 * 1000L) {
                DoseStatus.DELAYED
            } else {
                DoseStatus.SCHEDULED
            }
        }
}

/** Estado completo e imutável consumido pelas telas depois de cada atualização. */
data class FamilySnapshot(
    val patient: Patient,
    val medicines: List<Medicine>,
    val events: List<DoseEvent>,
    val updatedAt: Date
)

fun Date.dayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(this)

private val BrazilianPortuguese: Locale = Locale.forLanguageTag("pt-BR")

fun Date.timeLabel(): String = SimpleDateFormat("HH:mm", BrazilianPortuguese).format(this)

fun Date.dateLabel(): String = SimpleDateFormat("dd 'de' MMMM", BrazilianPortuguese).format(this)

fun Date.fullDateTimeLabel(): String =
    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", BrazilianPortuguese).format(this)

fun todayKey(): String = Date().dayKey()
