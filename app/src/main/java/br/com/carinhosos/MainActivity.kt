package br.com.carinhosos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.Calendar
import java.util.Date

private val Ivory = Color(0xFFFAF7F0)
private val Forest = Color(0xFF3E6F63)
private val Mint = Color(0xFF58B79B)
private val MintPale = Color(0xFFE4F4EE)
private val Ink = Color(0xFF352E2A)
private val Muted = Color(0xFF81776F)
private val Border = Color(0xFFE2DDD5)
private val Amber = Color(0xFFE5A12C)
private val AmberPale = Color(0xFFFFF3D8)
private val Danger = Color(0xFFD94B4B)
private val DangerPale = Color(0xFFFFE8E8)
private val BluePale = Color(0xFFEAF1F5)
private val Blue = Color(0xFF537A8D)

private val CarinhososColors = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    secondary = Forest,
    background = Ivory,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    error = Danger
)

private enum class AppSection { HOME, MEDICINES, HISTORY, PROFILE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = CarinhososColors) {
                CarinhososFamilyApp()
            }
        }
    }
}

@Composable
private fun CarinhososFamilyApp() {
    val context = LocalContext.current
    val repository = remember { FirebaseFamilyRepository(context.applicationContext) }
    var authenticatedEmail by remember { mutableStateOf(repository.currentUserEmail()) }
    var snapshot by remember { mutableStateOf<FamilySnapshot?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(authenticatedEmail != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var section by remember { mutableStateOf(AppSection.HOME) }
    var selectedMedicineId by remember { mutableStateOf<String?>(null) }

    // Alterar refreshRequest reinicia a leitura sem acoplar as telas ao Firebase.
    LaunchedEffect(authenticatedEmail, refreshRequest) {
        val email = authenticatedEmail ?: return@LaunchedEffect
        isLoadingData = true
        errorMessage = null
        repository.loadFamilySnapshot(email) { result ->
            result.fold(
                onSuccess = {
                    snapshot = it
                    isLoadingData = false
                },
                onFailure = {
                    snapshot = null
                    isLoadingData = false
                    errorMessage = it.message ?: "Não foi possível carregar os dados da clínica."
                }
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ivory) {
        if (authenticatedEmail == null) {
            LoginScreen(
                isLoading = isAuthenticating,
                errorMessage = errorMessage,
                onLogin = { email, password ->
                    isAuthenticating = true
                    errorMessage = null
                    repository.signIn(email, password) { result ->
                        isAuthenticating = false
                        result.fold(
                            onSuccess = {
                                authenticatedEmail = it
                                snapshot = null
                                refreshRequest++
                            },
                            onFailure = {
                                errorMessage = "E-mail ou senha incorretos, ou não foi possível conectar."
                            }
                        )
                    }
                }
            )
        } else if (snapshot == null) {
            DataStateScreen(
                isLoading = isLoadingData,
                message = errorMessage,
                onRetry = { refreshRequest++ },
                onLogout = {
                    repository.signOut()
                    authenticatedEmail = null
                    errorMessage = null
                }
            )
        } else {
            val loadedSnapshot = snapshot ?: return@Surface
            val selectedMedicine = loadedSnapshot.medicines.firstOrNull { it.id == selectedMedicineId }
            if (selectedMedicine != null) {
                MedicineDetailScreen(
                    medicine = selectedMedicine,
                    events = loadedSnapshot.events.filter { it.medicineId == selectedMedicine.id },
                    onBack = { selectedMedicineId = null }
                )
            } else {
                FamilyScaffold(
                    active = section,
                    onNavigate = { section = it }
                ) { padding ->
                    when (section) {
                        AppSection.HOME -> HomeScreen(
                            snapshot = loadedSnapshot,
                            modifier = Modifier.padding(padding),
                            onRefresh = { refreshRequest++ },
                            onOpenMedicine = { selectedMedicineId = it },
                            onSeeMedicines = { section = AppSection.MEDICINES }
                        )
                        AppSection.MEDICINES -> MedicinesScreen(
                            snapshot = loadedSnapshot,
                            modifier = Modifier.padding(padding),
                            onOpenMedicine = { selectedMedicineId = it }
                        )
                        AppSection.HISTORY -> HistoryScreen(
                            snapshot = loadedSnapshot,
                            modifier = Modifier.padding(padding)
                        )
                        AppSection.PROFILE -> ProfileScreen(
                            snapshot = loadedSnapshot,
                            modifier = Modifier.padding(padding),
                            onLogout = {
                                repository.signOut()
                                authenticatedEmail = null
                                snapshot = null
                                errorMessage = null
                                section = AppSection.HOME
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ivory, Color(0xFFF1ECE2))))
            .statusBarsPadding()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark(size = 76)
        Spacer(Modifier.height(14.dp))
        Text("Carinhosos", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Cuidado perto, mesmo de longe", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("Área da família", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Acompanhe os cuidados de quem você ama.",
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 3.dp, bottom = 20.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail") },
                    placeholder = { Text("seu@email.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    isError = errorMessage != null,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(13.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha") },
                    placeholder = { Text("••••••••") },
                    singleLine = true,
                    isError = errorMessage != null,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp)
                )
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        color = Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(19.dp))
                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Entrar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        Text(
            "Seus dados são protegidos e o acesso é pessoal.",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 22.dp)
        )
    }
}

@Composable
private fun DataStateScreen(
    isLoading: Boolean,
    message: String?,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark(size = 64)
        Spacer(Modifier.height(20.dp))
        if (isLoading) {
            CircularProgressIndicator(color = Forest)
            Text("Carregando dados da clínica…", color = Muted, modifier = Modifier.padding(top = 16.dp))
        } else {
            Text("Não foi possível abrir o acompanhamento", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(
                message ?: "Tente novamente.",
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
            )
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
                Text("Tentar novamente")
            }
            TextButton(onClick = onLogout) { Text("Sair da conta", color = Danger) }
        }
    }
}

@Composable
private fun FamilyScaffold(
    active: AppSection,
    onNavigate: (AppSection) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Ivory,
        bottomBar = {
            Column {
                HorizontalDivider(color = Border)
                NavigationBar(
                    containerColor = Color.White,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    FamilyNavItem("⌂", "Início", AppSection.HOME, active, onNavigate)
                    FamilyNavItem("✚", "Remédios", AppSection.MEDICINES, active, onNavigate)
                    FamilyNavItem("◷", "Histórico", AppSection.HISTORY, active, onNavigate)
                    FamilyNavItem("○", "Perfil", AppSection.PROFILE, active, onNavigate)
                }
            }
        },
        content = content
    )
}

@Composable
private fun RowScope.FamilyNavItem(
    symbol: String,
    label: String,
    section: AppSection,
    active: AppSection,
    onNavigate: (AppSection) -> Unit
) {
    NavigationBarItem(
        selected = section == active,
        onClick = { onNavigate(section) },
        icon = { Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold) },
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Forest,
            selectedTextColor = Forest,
            indicatorColor = MintPale,
            unselectedIconColor = Muted,
            unselectedTextColor = Muted
        )
    )
}

@Composable
private fun HomeScreen(
    snapshot: FamilySnapshot,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onOpenMedicine: (String) -> Unit,
    onSeeMedicines: () -> Unit
) {
    val todayEvents = snapshot.events.filter { it.scheduledAt.dayKey() == todayKey() }
        .sortedBy { it.scheduledAt }
    val administered = todayEvents.count { it.status == DoseStatus.ADMINISTERED }
    val lowestStock = snapshot.medicines
        .filter { it.stockAvailable }
        .minByOrNull { it.daysRemaining ?: Int.MAX_VALUE }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), color = Muted, fontSize = 13.sp)
                    Text(
                        "Olá, ${snapshot.patient.guardianName.substringBefore(" ")}",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                RemoteAvatar(
                    photoUrl = null,
                    fallback = snapshot.patient.guardianName,
                    size = 48,
                    contentDescription = "Responsável ${snapshot.patient.guardianName}"
                )
            }
        }
        item { PatientHeroCard(snapshot.patient) }
        item { DayProgressCard(administered, todayEvents.size) }
        item {
            SectionTitle(
                title = "Medicações de hoje",
                action = "Ver remédios",
                onAction = onSeeMedicines
            )
        }
        if (todayEvents.isEmpty()) {
            item { EmptyCard("Nenhuma medicação programada para hoje.") }
        } else {
            items(todayEvents, key = { it.id }) { event ->
                DoseTimelineCard(event = event, onClick = { onOpenMedicine(event.medicineId) })
            }
        }
        if (lowestStock != null) {
            item {
                SectionTitle("Estoque na clínica")
            }
            item {
                StockSummaryCard(lowestStock, onClick = { onOpenMedicine(lowestStock.id) })
            }
        }
        item {
            UpdateCard(snapshot.updatedAt, onRefresh)
        }
    }
}

@Composable
private fun PatientHeroCard(patient: Patient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Forest)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PatientAvatar(patient, 66, light = true)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text("Vínculo: ${patient.relationship}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                Text(
                    patient.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Quarto ${patient.room}  •  ${patient.clinicName}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            StatusChip("Na clínica", MintPale, Forest)
        }
    }
}

@Composable
private fun DayProgressCard(done: Int, total: Int) {
    val progress = if (total == 0) 0f else done.toFloat() / total
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cuidados de hoje", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("$done de $total doses confirmadas pela clínica", color = Muted, fontSize = 12.sp)
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    color = Forest,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(13.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                color = Mint,
                trackColor = MintPale
            )
        }
    }
}

@Composable
private fun DoseTimelineCard(event: DoseEvent, onClick: () -> Unit) {
    val style = statusStyle(event.status)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(style.pale),
                contentAlignment = Alignment.Center
            ) {
                Text(event.scheduledAt.timeLabel(), color = style.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(event.medicineName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(event.dose, color = Muted, fontSize = 12.sp)
                if (event.administeredAt != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (!event.professionalPhotoUrl.isNullOrBlank()) {
                            RemoteAvatar(
                                photoUrl = event.professionalPhotoUrl,
                                fallback = event.professional.orEmpty(),
                                size = 20
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            "Confirmado às ${event.administeredAt.timeLabel()} por ${event.professional.orEmpty()}",
                            color = Forest,
                            fontSize = 11.sp
                        )
                    }
                } else if (!event.note.isNullOrBlank()) {
                    Text(event.note, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            StatusChip(style.label, style.pale, style.color)
        }
    }
}

@Composable
private fun StockSummaryCard(medicine: Medicine, onClick: () -> Unit) {
    val style = stockStyle(medicine.stockLevel)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = style.pale),
        border = BorderStroke(1.dp, style.color.copy(alpha = 0.25f))
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Text("▣", color = style.color, fontSize = 21.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (medicine.stockLevel == StockLevel.CRITICAL) "Reposição necessária" else "Menor estoque disponível",
                    color = style.color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("${medicine.name} ${medicine.dose}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${medicine.stockQuantity} ${medicine.stockUnit}" +
                        (medicine.daysRemaining?.let { "  •  cerca de $it dias" } ?: ""),
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            Text("›", color = style.color, fontSize = 27.sp)
        }
    }
}

@Composable
private fun UpdateCard(updatedAt: Date, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Última atualização", color = Muted, fontSize = 11.sp)
            Text(updatedAt.fullDateTimeLabel(), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
            Text("Atualizar", color = Forest)
        }
    }
}

@Composable
private fun MedicinesScreen(
    snapshot: FamilySnapshot,
    modifier: Modifier = Modifier,
    onOpenMedicine: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            PageHeader("Remédios", "Tratamento e estoque individual na clínica")
        }
        item {
            InfoBanner(
                "Somente a equipe da clínica altera prescrições e quantidades. Aqui você acompanha tudo com segurança."
            )
        }
        items(snapshot.medicines, key = { it.id }) { medicine ->
            MedicineCard(medicine = medicine, onClick = { onOpenMedicine(medicine.id) })
        }
    }
}

@Composable
private fun MedicineCard(medicine: Medicine, onClick: () -> Unit) {
    val style = stockStyle(medicine.stockLevel)
    val maxReference = (medicine.minimumStock * 3).coerceAtLeast(1)
    val stockProgress = if (medicine.stockAvailable) {
        (medicine.stockQuantity.toFloat() / maxReference).coerceIn(0f, 1f)
    } else 0f
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(47.dp).clip(RoundedCornerShape(14.dp)).background(MintPale),
                    contentAlignment = Alignment.Center
                ) { Text("Rx", color = Forest, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(medicine.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(medicine.dose, medicine.presentation).filter { it.isNotBlank() }.joinToString("  •  "),
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Text(
                        medicine.schedules.joinToString("  •  ") { "$it diariamente" },
                        color = Forest,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                Text("›", color = Muted, fontSize = 26.sp)
            }
            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estoque na clínica", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                StatusChip(
                    when (medicine.stockLevel) {
                        StockLevel.GOOD -> "Estoque bom"
                        StockLevel.ATTENTION -> "Atenção"
                        StockLevel.CRITICAL -> "Repor agora"
                        StockLevel.UNKNOWN -> "Não informado"
                    },
                    style.pale,
                    style.color
                )
            }
            LinearProgressIndicator(
                progress = { stockProgress },
                modifier = Modifier.fillMaxWidth().height(7.dp).padding(top = 4.dp).clip(CircleShape),
                color = style.color,
                trackColor = style.pale
            )
            Text(
                if (medicine.stockAvailable) {
                    "${medicine.stockQuantity} ${medicine.stockUnit} disponíveis" +
                        (medicine.daysRemaining?.let { "  •  previsão de $it dias" } ?: "")
                } else {
                    "Este medicamento não possui correspondência na coleção de estoque."
                },
                color = Ink,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun HistoryScreen(snapshot: FamilySnapshot, modifier: Modifier = Modifier) {
    val history = snapshot.events
        .filter { it.scheduledAt.time <= System.currentTimeMillis() || it.status != DoseStatus.SCHEDULED }
        .sortedByDescending { it.scheduledAt }
    val administered = history.count { it.status == DoseStatus.ADMINISTERED }
    val adherence = if (history.isEmpty()) 0 else administered * 100 / history.size

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader("Histórico", "Registros enviados pela equipe da clínica") }
        item { HistorySummaryCard(adherence, administered, history.size) }
        var previousDay = ""
        history.forEach { event ->
            val day = event.scheduledAt.dayKey()
            if (day != previousDay) {
                item(key = "header-$day") {
                    Text(
                        if (day == todayKey()) "Hoje" else event.scheduledAt.dateLabel().replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
                previousDay = day
            }
            item(key = event.id) { HistoryEventCard(event) }
        }
    }
}

@Composable
private fun HistorySummaryCard(adherence: Int, administered: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Forest)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(66.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$adherence%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
            Spacer(Modifier.width(15.dp))
            Column {
                Text("Acompanhamento recente", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "$administered de $total registros administrados",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text("Ocorrências ficam visíveis para sua segurança.", color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HistoryEventCard(event: DoseEvent) {
    val style = statusStyle(event.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(style.pale),
                    contentAlignment = Alignment.Center
                ) { Text(if (event.status == DoseStatus.ADMINISTERED) "✓" else "!", color = style.color, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("${event.medicineName} ${event.dose}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Programado para ${event.scheduledAt.timeLabel()}", color = Muted, fontSize = 11.sp)
                }
                StatusChip(style.label, style.pale, style.color)
            }
            if (event.administeredAt != null || !event.note.isNullOrBlank()) {
                HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.note == null && !event.professionalPhotoUrl.isNullOrBlank()) {
                        RemoteAvatar(
                            photoUrl = event.professionalPhotoUrl,
                            fallback = event.professional.orEmpty(),
                            size = 28
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        event.note ?: "Administrado às ${event.administeredAt?.timeLabel()} por ${event.professional}",
                        color = if (event.note != null) Danger else Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    snapshot: FamilySnapshot,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(true) }
    var stockAlerts by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { PageHeader("Perfil", "Seu vínculo e preferências de acompanhamento") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    PatientAvatar(snapshot.patient, 61)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(snapshot.patient.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Vínculo: ${snapshot.patient.relationship}  •  Quarto ${snapshot.patient.room}", color = Muted, fontSize = 12.sp)
                        StatusChip("Vínculo confirmado", MintPale, Forest, Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        item {
            Text("Clínica", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 5.dp))
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Border)
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text(snapshot.patient.clinicName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(snapshot.patient.careTeam, color = Muted, fontSize = 12.sp)
                    if (snapshot.patient.clinicPhone.isNotBlank()) {
                        Text(snapshot.patient.clinicPhone, color = Forest, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${snapshot.patient.clinicPhone.filter { it.isDigit() }}")))
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Ligar para a clínica", color = Forest) }
                    } else {
                        Text("Contato não informado pelo back-end", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
        }
        item { Text("Alertas", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 5.dp)) }
        item {
            SettingsCard(
                notifications = notifications,
                stockAlerts = stockAlerts,
                onNotificationsChange = { notifications = it },
                onStockAlertsChange = { stockAlerts = it }
            )
        }
        item {
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Sair da conta", color = Danger, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    notifications: Boolean,
    stockAlerts: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onStockAlertsChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(horizontal = 17.dp, vertical = 8.dp)) {
            SettingRow(
                title = "Atualizações de doses",
                subtitle = "Avise quando a clínica registrar uma dose",
                checked = notifications,
                onCheckedChange = onNotificationsChange
            )
            HorizontalDivider(color = Border)
            SettingRow(
                title = "Estoque baixo",
                subtitle = "Avise quando for necessário repor",
                checked = stockAlerts,
                onCheckedChange = onStockAlertsChange
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MedicineDetailScreen(medicine: Medicine, events: List<DoseEvent>, onBack: () -> Unit) {
    val style = stockStyle(medicine.stockLevel)
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("‹  Voltar", color = Forest, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(MintPale),
                    contentAlignment = Alignment.Center
                ) { Text("Rx", color = Forest, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(medicine.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(medicine.dose, medicine.presentation).filter { it.isNotBlank() }.joinToString("  •  "),
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = style.pale),
                border = BorderStroke(1.dp, style.color.copy(alpha = 0.25f))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Estoque na clínica", color = style.color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (medicine.stockAvailable) "${medicine.stockQuantity} ${medicine.stockUnit}" else "Não informado",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        StatusChip(
                            when (medicine.stockLevel) {
                                StockLevel.GOOD -> "Estoque bom"
                                StockLevel.ATTENTION -> "Atenção"
                                StockLevel.CRITICAL -> "Reposição necessária"
                                StockLevel.UNKNOWN -> "Sem correspondência"
                            }, Color.White.copy(alpha = 0.7f), style.color
                        )
                    }
                    Text(
                        medicine.daysRemaining?.let { "Quantidade estimada para $it dias de tratamento." }
                            ?: "O back-end não informou estoque utilizável para este medicamento.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text("Origem do estoque: ${medicine.suppliedBy}", color = Muted, fontSize = 11.sp)
                }
            }
        }
        item { DetailInfoCard("Como administrar", medicine.instructions) }
        item { DetailInfoCard("Horários", medicine.schedules.joinToString("  •  ") { "$it todos os dias" }) }
        item { SectionTitle("Registros recentes") }
        items(events.sortedByDescending { it.scheduledAt }.take(5), key = { it.id }) { event ->
            HistoryEventCard(event)
        }
        item {
            InfoBanner("Em caso de dúvida sobre a prescrição, fale diretamente com a equipe da clínica.")
        }
    }
}

@Composable
private fun DetailInfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 11.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BrandMark(size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(Mint),
        contentAlignment = Alignment.Center
    ) {
        Text("♡", color = Color.White, fontSize = (size * 0.55).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PatientAvatar(patient: Patient, size: Int, light: Boolean = false) {
    RemoteAvatar(
        photoUrl = patient.photoUrl,
        fallback = patient.initials,
        size = size,
        light = light,
        contentDescription = "Foto de ${patient.name}"
    )
}

@Composable
private fun RemoteAvatar(
    photoUrl: String?,
    fallback: String,
    size: Int,
    light: Boolean = false,
    contentDescription: String? = null
) {
    val initials = fallback.split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape)
            .background(if (light) Color.White.copy(alpha = 0.16f) else MintPale),
        contentAlignment = Alignment.Center
    ) {
        // As iniciais ficam sob a imagem e aparecem automaticamente se a URL
        // estiver vazia ou se o carregamento remoto falhar.
        Text(
            initials,
            color = if (light) Color.White else Forest,
            fontSize = (size * 0.34).sp,
            fontWeight = FontWeight.Bold
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(action, color = Forest, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MintPale).padding(15.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("i", color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 11.dp))
        Text(text, color = Forest, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Text(text, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text,
        color = foreground,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(background).padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

private data class VisualStatus(val label: String, val pale: Color, val color: Color)

private fun statusStyle(status: DoseStatus): VisualStatus = when (status) {
    DoseStatus.ADMINISTERED -> VisualStatus("Administrado", MintPale, Forest)
    DoseStatus.SCHEDULED -> VisualStatus("Programado", BluePale, Blue)
    DoseStatus.DELAYED -> VisualStatus("Atrasado", AmberPale, Amber)
    DoseStatus.NOT_ADMINISTERED -> VisualStatus("Não administrado", DangerPale, Danger)
}

private fun stockStyle(level: StockLevel): VisualStatus = when (level) {
    StockLevel.GOOD -> VisualStatus("Estoque bom", MintPale, Forest)
    StockLevel.ATTENTION -> VisualStatus("Atenção", AmberPale, Amber)
    StockLevel.CRITICAL -> VisualStatus("Repor agora", DangerPale, Danger)
    StockLevel.UNKNOWN -> VisualStatus("Não informado", BluePale, Muted)
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}
