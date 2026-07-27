package br.com.carinhosos

import android.Manifest
import android.app.AlarmManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Ivory = Color(0xFFFAF7F0)
private val Forest = Color(0xFF3E6F63)
private val Mint = Color(0xFF58B79B)
private val MintPale = Color(0xFFE4F4EE)
private val Ink = Color(0xFF352E2A)
private val Muted = Color(0xFF81776F)
private val Border = Color(0xFFE2DDD5)
private val Amber = Color(0xFFF1AF3D)
private val AmberPale = Color(0xFFFFF3D8)
private val Danger = Color(0xFFE24B4B)
private val DangerPale = Color(0xFFFFE8E8)

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

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val stockItemId: String = "",
    val name: String,
    val dose: String,
    val time: String,
    val startDate: String = todayDisplay(),
    val frequency: String = "Diário",
    val notificationEnabled: Boolean = true,
    val lastAppliedDate: String = ""
)

data class DoseRecord(
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val medicationName: String,
    val dose: String,
    val scheduledTime: String,
    val appliedAt: String
)

data class Elder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cpf: String,
    val birthDate: String,
    val room: String,
    val notes: String,
    val guardianName: String,
    val guardianCpf: String,
    val relationship: String,
    val phone: String,
    val email: String,
    val photoUri: String = "",
    val medications: List<Medication>,
    val doseHistory: List<DoseRecord> = emptyList()
)

data class StockItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int,
    val minimum: Int
)

private enum class Screen { LOGIN, DASHBOARD, REGISTER, DETAIL, STOCK, NOTIFICATIONS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = CarinhososColors) {
                CarinhososApp(applicationContext)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val repository = LocalRepository(applicationContext)
        NotificationScheduler.sync(
            applicationContext,
            repository.loadElders(),
            repository.notificationsEnabled()
        )
        repository.close()
    }
}

@Composable
private fun CarinhososApp(context: Context) {
    val repository = remember { LocalRepository(context) }
    var screen by remember {
        mutableStateOf(if (repository.isLoggedIn()) Screen.DASHBOARD else Screen.LOGIN)
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var elders by remember { mutableStateOf(repository.loadElders()) }
    var stock by remember { mutableStateOf(repository.loadStock()) }
    var alertsEnabled by remember { mutableStateOf(repository.notificationsEnabled()) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) NotificationScheduler.sync(context, elders, alertsEnabled)
    }

    LaunchedEffect(Unit) {
        NotificationScheduler.ensureChannel(context)
        NotificationScheduler.sync(context, elders, alertsEnabled)
    }

    fun persistElders(value: List<Elder>) {
        elders = value
        repository.saveElders(value)
        NotificationScheduler.sync(context, value, alertsEnabled)
    }

    fun persistStock(value: List<StockItem>) {
        stock = value
        repository.saveStock(value)
    }

    fun logout() {
        repository.setLoggedIn(false)
        screen = Screen.LOGIN
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ivory) {
        when (screen) {
            Screen.LOGIN -> LoginScreen(
                onLogin = { email, password ->
                    if (email.trim().lowercase() == "user@mail.com" &&
                        sha256(password) == "e172c5654dbc12d78ce1850a4f7956ba6e5a3d2ac40f0925fc6d691ebb54f6bf"
                    ) {
                        repository.setLoggedIn(true)
                        screen = Screen.DASHBOARD
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            NotificationScheduler.sync(context, elders, alertsEnabled)
                        }
                        true
                    } else false
                }
            )

            Screen.DASHBOARD -> AppScaffold(
                active = Screen.DASHBOARD,
                onDashboard = { screen = Screen.DASHBOARD },
                onRegister = { screen = Screen.REGISTER },
                onStock = { screen = Screen.STOCK },
                onNotifications = { screen = Screen.NOTIFICATIONS }
            ) { padding ->
                DashboardScreen(
                    elders = elders,
                    modifier = Modifier.padding(padding),
                    onNew = { screen = Screen.REGISTER },
                    onSelect = {
                        selectedId = it
                        screen = Screen.DETAIL
                    }
                )
            }

            Screen.REGISTER -> AppScaffold(
                active = Screen.REGISTER,
                onDashboard = { screen = Screen.DASHBOARD },
                onRegister = { screen = Screen.REGISTER },
                onStock = { screen = Screen.STOCK },
                onNotifications = { screen = Screen.NOTIFICATIONS }
            ) { padding ->
                RegisterScreen(
                    context = context,
                    stock = stock,
                    modifier = Modifier.padding(padding),
                    onCancel = { screen = Screen.DASHBOARD },
                    onSave = { elder ->
                        persistElders(elders + elder)
                        screen = Screen.DASHBOARD
                    }
                )
            }

            Screen.DETAIL -> {
                val elder = elders.firstOrNull { it.id == selectedId }
                if (elder == null) {
                    screen = Screen.DASHBOARD
                } else {
                    DetailScreen(
                        context = context,
                        elder = elder,
                        stock = stock,
                        onBack = { screen = Screen.DASHBOARD },
                        onApply = { medicationId ->
                            val medication = elder.medications.firstOrNull { it.id == medicationId }
                            val stockItem = medication?.let { findStockItem(stock, it) }
                            when {
                                medication == null -> "Medicamento não encontrado na ficha."
                                !medication.isActiveToday() ->
                                    "A medicação começa em ${medication.startDate}."
                                medication.isCompletedForCurrentCycle() ->
                                    "Esta dose já foi registrada."
                                stockItem == null ->
                                    "Medicamento não encontrado no estoque. Cadastre-o antes de aplicar."
                                stockItem.quantity <= 0 ->
                                    "Estoque zerado para ${stockItem.name}. Reponha antes de aplicar."
                                else -> {
                                    val record = DoseRecord(
                                        medicationId = medication.id,
                                        medicationName = medication.name,
                                        dose = medication.dose,
                                        scheduledTime = medication.time,
                                        appliedAt = currentDateTime()
                                    )
                                    val updated = elders.map { current ->
                                        if (current.id != elder.id) current else current.copy(
                                            medications = current.medications.map {
                                                if (it.id == medicationId) {
                                                    it.copy(lastAppliedDate = todayKey())
                                                } else it
                                            },
                                            doseHistory = current.doseHistory + record
                                        )
                                    }
                                    persistStock(stock.map {
                                        if (it.id == stockItem.id) {
                                            it.copy(quantity = (it.quantity - 1).coerceAtLeast(0))
                                        } else it
                                    })
                                    persistElders(updated)
                                    "Dose registrada e 1 unidade retirada do estoque."
                                }
                            }
                        },
                        onAddMedication = { medication ->
                            persistElders(elders.map {
                                if (it.id == elder.id) it.copy(medications = it.medications + medication) else it
                            })
                        },
                        onEditMedication = { medication ->
                            persistElders(elders.map { current ->
                                if (current.id == elder.id) current.copy(
                                    medications = current.medications.map {
                                        if (it.id == medication.id) medication else it
                                    }
                                ) else current
                            })
                        },
                        onDeleteMedication = { medicationId ->
                            persistElders(elders.map { current ->
                                if (current.id == elder.id) current.copy(
                                    medications = current.medications.filterNot { it.id == medicationId }
                                ) else current
                            })
                        },
                        onChangePhoto = { uri ->
                            persistElders(elders.map {
                                if (it.id == elder.id) it.copy(photoUri = uri) else it
                            })
                        }
                    )
                }
            }

            Screen.STOCK -> AppScaffold(
                active = Screen.STOCK,
                onDashboard = { screen = Screen.DASHBOARD },
                onRegister = { screen = Screen.REGISTER },
                onStock = { screen = Screen.STOCK },
                onNotifications = { screen = Screen.NOTIFICATIONS }
            ) { padding ->
                StockScreen(
                    stock = stock,
                    modifier = Modifier.padding(padding),
                    onAdd = { persistStock(stock + it) },
                    onChange = { id, delta ->
                        persistStock(stock.map {
                            if (it.id == id) it.copy(quantity = (it.quantity + delta).coerceAtLeast(0)) else it
                        })
                    },
                    onDelete = { id -> persistStock(stock.filterNot { it.id == id }) }
                )
            }

            Screen.NOTIFICATIONS -> AppScaffold(
                active = Screen.NOTIFICATIONS,
                onDashboard = { screen = Screen.DASHBOARD },
                onRegister = { screen = Screen.REGISTER },
                onStock = { screen = Screen.STOCK },
                onNotifications = { screen = Screen.NOTIFICATIONS }
            ) { padding ->
                NotificationsScreen(
                    elders = elders,
                    enabled = alertsEnabled,
                    modifier = Modifier.padding(padding),
                    onToggle = { enabled ->
                        alertsEnabled = enabled
                        repository.setNotificationsEnabled(enabled)
                        if (enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        NotificationScheduler.sync(context, elders, enabled)
                    },
                    onOpenElder = { id ->
                        selectedId = id
                        screen = Screen.DETAIL
                    },
                    onLogout = ::logout
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(onLogin: (String, String) -> Boolean) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Ivory, Color(0xFFF2EDE3))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Mint),
            contentAlignment = Alignment.Center
        ) {
            Text("♡", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("Carinhosos", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Cuidado com amor, como em família", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(36.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("Área do Funcionário", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Entre com suas credenciais para acessar", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(22.dp))
                FormField(
                    value = email,
                    onValueChange = { email = it; error = false },
                    label = "E-mail",
                    placeholder = "seu@email.com",
                    keyboardType = KeyboardType.Email
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha") },
                    placeholder = { Text("••••••••") },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp)
                )
                if (error) {
                    Text(
                        "E-mail ou senha incorretos.",
                        color = Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { error = !onLogin(email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint)
                ) {
                    Text("Entrar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Ambiente seguro para o acompanhamento dos residentes",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AppScaffold(
    active: Screen,
    onDashboard: () -> Unit,
    onRegister: () -> Unit,
    onStock: () -> Unit,
    onNotifications: () -> Unit,
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
                    NavigationBarItem(
                        selected = active == Screen.DASHBOARD,
                        onClick = onDashboard,
                        icon = { Text("♙", fontSize = 22.sp) },
                        label = { Text("Idosos") },
                        colors = navColors()
                    )
                    NavigationBarItem(
                        selected = active == Screen.REGISTER,
                        onClick = onRegister,
                        icon = { Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                        label = { Text("Cadastrar") },
                        colors = navColors()
                    )
                    NavigationBarItem(
                        selected = active == Screen.STOCK,
                        onClick = onStock,
                        icon = { Text("▣", fontSize = 21.sp) },
                        label = { Text("Estoque") },
                        colors = navColors()
                    )
                    NavigationBarItem(
                        selected = active == Screen.NOTIFICATIONS,
                        onClick = onNotifications,
                        icon = { Text("●", fontSize = 18.sp) },
                        label = { Text("Alertas") },
                        colors = navColors()
                    )
                }
            }
        },
        content = content
    )
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Forest,
    selectedTextColor = Forest,
    indicatorColor = MintPale,
    unselectedIconColor = Muted,
    unselectedTextColor = Muted
)

@Composable
private fun DashboardScreen(
    elders: List<Elder>,
    modifier: Modifier = Modifier,
    onNew: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = elders.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val pendingPeople = elders.count { elder ->
        elder.medications.any { it.isDueToday() }
    }
    val upToDate = elders.size - pendingPeople

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Idosos", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Gerencie os cuidados e medicamentos", color = Muted, fontSize = 13.sp)
                }
                Button(
                    onClick = onNew,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint)
                ) {
                    Text("+  Novo Idoso", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SummaryCard("${elders.size}", "Total", MintPale, Forest, Modifier.weight(1f))
                SummaryCard("$pendingPeople", "Pendentes", AmberPale, Amber, Modifier.weight(1f))
                SummaryCard("$upToDate", "Em dia", MintPale, Mint, Modifier.weight(1f))
            }
        }
        item {
            FormField(
                value = query,
                onValueChange = { query = it },
                label = "",
                placeholder = "Buscar idoso por nome..."
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (query.isBlank()) "Nenhum idoso cadastrado" else "Nenhum resultado",
                    subtitle = if (query.isBlank()) {
                        "Cadastre o primeiro residente para começar."
                    } else "Tente buscar por outro nome."
                )
            }
        } else {
            items(filtered, key = { it.id }) { elder ->
                ElderCard(elder = elder, onClick = { onSelect(elder.id) })
            }
        }
    }
}

@Composable
private fun SummaryCard(
    value: String,
    label: String,
    iconBackground: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ElderCard(elder: Elder, onClick: () -> Unit) {
    val activeMedications = elder.medications.filter {
        it.frequency == "Diário" && it.isActiveToday() ||
            it.frequency == "Dose única" && it.lastAppliedDate.isBlank()
    }
    val pending = activeMedications.count { it.isDueToday() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(elder = elder, size = 62)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    elder.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${elder.ageLabel()} • Quarto ${elder.room}", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${activeMedications.size} medicamento(s) hoje",
                    color = Forest,
                    fontSize = 12.sp
                )
            }
            StatusPill(
                text = if (pending == 0) "Em dia" else "$pending pendente${if (pending > 1) "s" else ""}",
                ok = pending == 0
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    Text(
        text,
        color = if (ok) Forest else Danger,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (ok) MintPale else DangerPale)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun RegisterScreen(
    context: Context,
    stock: List<StockItem>,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: (Elder) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var guardianName by remember { mutableStateOf("") }
    var guardianCpf by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf("") }
    var medStockId by remember { mutableStateOf("") }
    var medName by remember { mutableStateOf("") }
    var medDose by remember { mutableStateOf("") }
    var medTime by remember { mutableStateOf("") }
    var medStartDate by remember { mutableStateOf(todayDisplay()) }
    var medNotification by remember { mutableStateOf(true) }
    var medDaily by remember { mutableStateOf(true) }
    var medError by remember { mutableStateOf("") }
    var medications by remember { mutableStateOf(emptyList<Medication>()) }
    var showError by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            photoUri = uri.toString()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "Cadastrar Novo Idoso",
                subtitle = "Preencha as informações do novo residente"
            )
        }
        item {
            PhotoPickerCard(
                photoUri = photoUri,
                initials = if (name.isBlank()) "♡" else name
                    .trim()
                    .split(Regex("\\s+"))
                    .take(2)
                    .joinToString("") { it.first().uppercase() },
                onPick = { photoPicker.launch(arrayOf("image/*")) }
            )
        }
        item {
            FormSection("Informações Pessoais") {
                FormField(name, { name = it; showError = false }, "Nome Completo *", "Nome do idoso")
                FormField(cpf, { cpf = it }, "CPF *", "000.000.000-00", KeyboardType.Number)
                FormField(birthDate, { birthDate = it }, "Nascimento *", "DD/MM/AAAA", KeyboardType.Number)
                FormField(room, { room = it }, "Quarto *", "Ex: 101")
                FormField(
                    notes,
                    { notes = it },
                    "Observações",
                    "Saúde, alimentação e preferências...",
                    singleLine = false
                )
            }
        }
        item {
            FormSection("Responsável") {
                FormField(guardianName, { guardianName = it }, "Nome do Responsável *", "Nome completo")
                FormField(guardianCpf, { guardianCpf = it }, "CPF *", "000.000.000-00", KeyboardType.Number)
                FormField(relationship, { relationship = it }, "Parentesco *", "Ex: Filho")
                FormField(phone, { phone = it }, "Telefone *", "(00) 00000-0000", KeyboardType.Phone)
                FormField(email, { email = it }, "E-mail", "email@exemplo.com", KeyboardType.Email)
            }
        }
        item {
            FormSection("Medicamentos") {
                StockMedicationPicker(
                    stock = stock,
                    selectedId = medStockId,
                    onSelect = { item ->
                        medStockId = item.id
                        medName = item.name
                        medError = ""
                    }
                )
                FormField(medName, { medName = it }, "Medicamento", "Nome do medicamento")
                FormField(medDose, { medDose = it }, "Dosagem", "Ex: 50 mg")
                FormField(medTime, { medTime = it }, "Horário", "HH:MM", KeyboardType.Text)
                FormField(
                    medStartDate,
                    { medStartDate = it },
                    "Data de início",
                    "DD/MM/AAAA",
                    KeyboardType.Number
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notificação", fontWeight = FontWeight.SemiBold)
                        Text("Avisar diariamente neste horário", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = medNotification, onCheckedChange = { medNotification = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Uso diário", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (medDaily) "Renova todos os dias" else "Dose única",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(checked = medDaily, onCheckedChange = { medDaily = it })
                }
                if (medError.isNotBlank()) {
                    Text(medError, color = Danger, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        val validation = validateMedication(
                            name = medName,
                            dose = medDose,
                            time = medTime,
                            startDate = medStartDate,
                            stockItemId = medStockId,
                            stock = stock
                        )
                        if (validation == null) {
                            medications = medications + Medication(
                                stockItemId = medStockId,
                                name = medName.trim(),
                                dose = medDose.trim(),
                                time = medTime.trim(),
                                startDate = medStartDate.trim(),
                                frequency = if (medDaily) "Diário" else "Dose única",
                                notificationEnabled = medNotification
                            )
                            medStockId = ""
                            medName = ""
                            medDose = ""
                            medTime = ""
                            medStartDate = todayDisplay()
                            medNotification = true
                            medDaily = true
                            medError = ""
                        } else {
                            medError = validation
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text("+ Adicionar medicamento", color = Forest)
                }
                medications.forEach { medication ->
                    MedicationPreview(medication) {
                        medications = medications.filterNot { it.id == medication.id }
                    }
                }
            }
        }
        if (showError) {
            item {
                Text(
                    "Preencha os campos obrigatórios: nome, CPF, nascimento, quarto, responsável, parentesco e telefone.",
                    color = Danger,
                    fontSize = 13.sp
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancelar", color = Muted)
                }
                Button(
                    onClick = {
                        val valid = listOf(
                            name, cpf, birthDate, room, guardianName, guardianCpf, relationship, phone
                        ).all { it.isNotBlank() }
                        val medicationDraftStarted =
                            medStockId.isNotBlank() || medName.isNotBlank() ||
                                medDose.isNotBlank() || medTime.isNotBlank()
                        if (!valid) {
                            showError = true
                        } else if (medicationDraftStarted) {
                            medError = validateMedication(
                                name = medName,
                                dose = medDose,
                                time = medTime,
                                startDate = medStartDate,
                                stockItemId = medStockId,
                                stock = stock
                            ) ?: "Clique em “Adicionar medicamento” antes de finalizar o cadastro."
                        } else {
                            onSave(
                                Elder(
                                    name = name.trim(),
                                    cpf = cpf.trim(),
                                    birthDate = birthDate.trim(),
                                    room = room.trim(),
                                    notes = notes.trim(),
                                    guardianName = guardianName.trim(),
                                    guardianCpf = guardianCpf.trim(),
                                    relationship = relationship.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    photoUri = photoUri,
                                    medications = medications
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1.35f),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cadastrar Idoso", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MedicationPreview(medication: Medication, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MintPale)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                medicationDisplay(medication.name, medication.dose),
                fontWeight = FontWeight.SemiBold,
                color = Forest
            )
            Text(
                "Início: ${medication.startDate} • ${medication.time} • ${medication.frequency}",
                color = Muted,
                fontSize = 12.sp
            )
            Text(
                if (medication.notificationEnabled) "Notificação ativa" else "Notificação desativada",
                color = if (medication.notificationEnabled) Forest else Muted,
                fontSize = 11.sp
            )
        }
        TextButton(onClick = onRemove) { Text("Remover", color = Danger) }
    }
}

@Composable
private fun DetailScreen(
    context: Context,
    elder: Elder,
    stock: List<StockItem>,
    onBack: () -> Unit,
    onApply: (String) -> String,
    onAddMedication: (Medication) -> Unit,
    onEditMedication: (Medication) -> Unit,
    onDeleteMedication: (String) -> Unit,
    onChangePhoto: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMedication by remember { mutableStateOf<Medication?>(null) }
    var deletingMedication by remember { mutableStateOf<Medication?>(null) }
    var actionMessage by remember { mutableStateOf("") }
    val applied = elder.medications.count { it.appliedToday() }
    val pending = elder.medications.count { it.isDueToday() }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onChangePhoto(uri.toString())
        }
    }

    if (showAddDialog) {
        MedicationDialog(
            initial = null,
            stock = stock,
            onDismiss = { showAddDialog = false },
            onConfirm = {
                onAddMedication(it)
                showAddDialog = false
            }
        )
    }
    editingMedication?.let { medication ->
        MedicationDialog(
            initial = medication,
            stock = stock,
            onDismiss = { editingMedication = null },
            onConfirm = {
                onEditMedication(it)
                editingMedication = null
            }
        )
    }
    deletingMedication?.let { medication ->
        AlertDialog(
            onDismissRequest = { deletingMedication = null },
            title = { Text("Remover medicamento?") },
            text = { Text("${medication.name} será removido da ficha de ${elder.name}.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMedication(medication.id)
                        deletingMedication = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMedication = null }) {
                    Text("Cancelar", color = Muted)
                }
            },
            containerColor = Color.White
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("← Voltar", color = Forest) }
                Spacer(Modifier.weight(1f))
                Text("Carinhosos", color = Forest, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        if (actionMessage.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (actionMessage.startsWith("Dose registrada")) {
                            MintPale
                        } else {
                            DangerPale
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        actionMessage,
                        modifier = Modifier.padding(13.dp),
                        color = if (actionMessage.startsWith("Dose registrada")) Forest else Danger,
                        fontSize = 13.sp
                    )
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProfileAvatar(elder = elder, size = 84)
                    TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) {
                        Text(
                            if (elder.photoUri.isBlank()) "Adicionar foto" else "Alterar foto",
                            color = Forest
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(elder.name, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("${elder.ageLabel()} • Quarto ${elder.room}", color = Muted)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailMetric("$applied", "Aplicados hoje", MintPale, Forest, Modifier.weight(1f))
                        DetailMetric("$pending", "Pendentes hoje", AmberPale, Amber, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            FormSection("Informações") {
                InfoRow("Nascimento", elder.birthDate)
                InfoRow("CPF", elder.cpf)
                InfoRow("Responsável", elder.guardianName)
                InfoRow("Parentesco", elder.relationship)
                InfoRow("Telefone", elder.phone)
                if (elder.email.isNotBlank()) InfoRow("E-mail", elder.email)
                if (elder.notes.isNotBlank()) InfoRow("Observações", elder.notes)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Medicamentos de Hoje", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(formatToday(), color = Muted, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text("+ Adicionar", color = Forest)
                }
            }
        }
        if (elder.medications.isEmpty()) {
            item { EmptyState("Nenhum medicamento", "Adicione a prescrição deste residente.") }
        } else {
            items(elder.medications.sortedBy { it.time }, key = { it.id }) { medication ->
                MedicationCard(
                    medication = medication,
                    onApply = { actionMessage = onApply(medication.id) },
                    onEdit = { editingMedication = medication },
                    onDelete = { deletingMedication = medication }
                )
            }
        }
        item {
            Text("Histórico de aplicações", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (elder.doseHistory.isEmpty()) {
            item {
                EmptyState(
                    "Nenhuma dose registrada",
                    "As aplicações aparecerão aqui com data e horário."
                )
            }
        } else {
            items(elder.doseHistory.asReversed().take(50), key = { it.id }) { record ->
                DoseHistoryCard(record)
            }
        }
    }
}

@Composable
private fun DoseHistoryCard(record: DoseRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MintPale),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Forest, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    medicationDisplay(record.medicationName, record.dose),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Previsto: ${record.scheduledTime}",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            Text(
                record.appliedAt,
                color = Forest,
                fontSize = 12.sp,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun DetailMetric(
    value: String,
    label: String,
    background: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(104.dp))
        Text(value, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MedicationCard(
    medication: Medication,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val active = medication.isActiveToday()
    val applied = medication.isCompletedForCurrentCycle()
    Card(
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (applied) Color(0xFFB8DECF) else Border)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (applied) MintPale else AmberPale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (applied) "✓" else "●", color = if (applied) Mint else Amber, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        medicationDisplay(medication.name, medication.dose),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Início: ${medication.startDate} • ${medication.time}",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Text(
                        if (medication.notificationEnabled) "Lembrete ativo" else "Lembrete desativado",
                        color = if (medication.notificationEnabled) Forest else Muted,
                        fontSize = 11.sp
                    )
                }
                StatusPill(
                    when {
                        !active -> "Agendado"
                        applied && medication.frequency == "Dose única" -> "Concluído"
                        applied -> "Aplicado"
                        else -> "Pendente"
                    },
                    applied || !active
                )
            }
            if (active && !applied) {
                Spacer(Modifier.height(13.dp))
                Button(
                    onClick = onApply,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text("Marcar como Aplicado", fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) { Text("Editar", color = Forest) }
                TextButton(onClick = onDelete) { Text("Remover", color = Danger) }
            }
        }
    }
}

@Composable
private fun MedicationDialog(
    initial: Medication?,
    stock: List<StockItem>,
    onDismiss: () -> Unit,
    onConfirm: (Medication) -> Unit
) {
    var stockItemId by remember(initial?.id, stock) {
        mutableStateOf(
            initial?.stockItemId
                ?.takeIf { id -> stock.any { it.id == id } }
                ?: initial?.let { findStockItem(stock, it)?.id }
                .orEmpty()
        )
    }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var dose by remember(initial?.id) { mutableStateOf(initial?.dose.orEmpty()) }
    var time by remember(initial?.id) { mutableStateOf(initial?.time.orEmpty()) }
    var startDate by remember(initial?.id) {
        mutableStateOf(initial?.startDate ?: todayDisplay())
    }
    var notificationEnabled by remember(initial?.id) {
        mutableStateOf(initial?.notificationEnabled ?: true)
    }
    var daily by remember(initial?.id) {
        mutableStateOf(initial?.frequency != "Dose única")
    }
    var validationError by remember(initial?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Adicionar Medicamento" else "Editar Medicamento",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StockMedicationPicker(
                    stock = stock,
                    selectedId = stockItemId,
                    onSelect = { item ->
                        stockItemId = item.id
                        name = item.name
                        validationError = ""
                    }
                )
                FormField(name, { name = it }, "Medicamento", "Nome")
                FormField(dose, { dose = it }, "Dosagem", "Ex: 50 mg")
                FormField(time, { time = it }, "Horário", "HH:MM", KeyboardType.Text)
                FormField(
                    startDate,
                    { startDate = it },
                    "Data de início",
                    "DD/MM/AAAA",
                    KeyboardType.Number
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notificação diária", fontWeight = FontWeight.SemiBold)
                        Text("Usar nome do idoso e medicamento", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = { notificationEnabled = it }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Uso diário", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (daily) "Renova no dia seguinte" else "Aplicação única",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(checked = daily, onCheckedChange = { daily = it })
                }
                if (validationError.isNotBlank()) {
                    Text(validationError, color = Danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validation = validateMedication(
                        name = name,
                        dose = dose,
                        time = time,
                        startDate = startDate,
                        stockItemId = stockItemId,
                        stock = stock
                    )
                    if (validation == null) {
                        onConfirm(
                            Medication(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                stockItemId = stockItemId,
                                name = name.trim(),
                                dose = dose.trim(),
                                time = time.trim(),
                                startDate = startDate.trim(),
                                frequency = if (daily) "Diário" else "Dose única",
                                notificationEnabled = notificationEnabled,
                                lastAppliedDate = initial?.lastAppliedDate.orEmpty()
                            )
                        )
                    } else {
                        validationError = validation
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint)
            ) { Text(if (initial == null) "Adicionar" else "Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun NotificationsScreen(
    elders: List<Elder>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onOpenElder: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactAlarmAllowed =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    val reminders = elders.flatMap { elder ->
        elder.medications
            .filter {
                it.notificationEnabled &&
                    (it.frequency == "Diário" || it.lastAppliedDate.isBlank())
            }
            .map { elder to it }
    }.sortedWith(compareBy({ it.second.time }, { it.first.name }))

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageHeader(
                    title = "Notificações",
                    subtitle = "Configure os lembretes de medicamentos"
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onLogout) { Text("Sair", color = Danger) }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (enabled) MintPale else Color(0xFFF0EDE8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("●", color = if (enabled) Mint else Muted, fontSize = 19.sp)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Lembretes de medicamentos", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            if (enabled) "Notificações gerais ativadas" else "Todos os lembretes estão pausados",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }
            }
        }
        if (!exactAlarmAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberPale)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Precisão dos horários", fontWeight = FontWeight.Bold)
                        Text(
                            "Autorize alarmes exatos para que os lembretes cheguem no horário definido, mesmo com economia de bateria.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(9.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Permitir horários exatos", color = Forest)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Alertas programados (${reminders.size})",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (reminders.isEmpty()) {
            item {
                EmptyState(
                    "Nenhum alerta configurado",
                    "Abra a ficha de um idoso e ative a notificação do medicamento."
                )
            }
        } else {
            items(reminders, key = { "${it.first.id}:${it.second.id}" }) { (elder, medication) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenElder(elder.id) },
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatar(elder = elder, size = 48)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(elder.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                medicationDisplay(medication.name, medication.dose),
                                color = Forest,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                "Desde ${medication.startDate} • ${medication.frequency}",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            medication.time,
                            color = if (enabled) Forest else Muted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmberPale),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(
                    "Para receber os avisos, mantenha as notificações permitidas nas configurações do Android. O alerta apresenta o nome do idoso, o medicamento e o horário.",
                    modifier = Modifier.padding(14.dp),
                    color = Ink,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun StockScreen(
    stock: List<StockItem>,
    modifier: Modifier = Modifier,
    onAdd: (StockItem) -> Unit,
    onChange: (String, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<StockItem?>(null) }
    val filtered = stock.filter { it.name.contains(query.trim(), ignoreCase = true) }

    if (showDialog) {
        StockDialog(
            onDismiss = { showDialog = false },
            onConfirm = {
                onAdd(it)
                showDialog = false
            }
        )
    }
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remover medicamento?") },
            text = { Text("${item.name} será removido do estoque.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(item.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar", color = Muted) }
            },
            containerColor = Color.White
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PageHeader("Estoque de Medicamentos", "Gerencie quantidades e alertas")
                }
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Mint),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                ) { Text("+ Adicionar") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("${stock.size}", "Tipos", MintPale, Forest, Modifier.weight(1f))
                SummaryCard(
                    "${stock.count { it.quantity <= it.minimum }}",
                    "Estoque baixo",
                    AmberPale,
                    Amber,
                    Modifier.weight(1f)
                )
            }
        }
        item {
            FormField(query, { query = it }, "", "Buscar medicamento...")
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    if (query.isBlank()) "Estoque vazio" else "Nenhum resultado",
                    if (query.isBlank()) "Adicione medicamentos para controlar as quantidades." else "Tente outro nome."
                )
            }
        } else {
            items(filtered.sortedBy { it.name }, key = { it.id }) { item ->
                StockCard(
                    item = item,
                    onMinus = { onChange(item.id, -1) },
                    onPlus = { onChange(item.id, 1) },
                    onDelete = { deleteTarget = item }
                )
            }
        }
    }
}

@Composable
private fun StockCard(
    item: StockItem,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onDelete: () -> Unit
) {
    val low = item.quantity <= item.minimum
    Card(
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (low) Color(0xFFF0D294) else Border)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (low) AmberPale else MintPale),
                    contentAlignment = Alignment.Center
                ) { Text("✚", color = if (low) Amber else Forest, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Mínimo: ${item.minimum} unidades", color = Muted, fontSize = 12.sp)
                }
                StatusPill(if (low) "Baixo" else "Normal", !low)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantidade", color = Muted, modifier = Modifier.weight(1f))
                QuantityButton("−", onMinus)
                Text(
                    "${item.quantity}",
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                QuantityButton("+", onPlus)
                Spacer(Modifier.width(5.dp))
                TextButton(onClick = onDelete) { Text("Excluir", color = Danger, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun QuantityButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape
    ) { Text(text, color = Forest, fontSize = 20.sp) }
}

@Composable
private fun StockDialog(onDismiss: () -> Unit, onConfirm: (StockItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar ao Estoque", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(name, { name = it }, "Medicamento", "Nome")
                FormField(quantity, { quantity = it.filter(Char::isDigit) }, "Quantidade", "0", KeyboardType.Number)
                FormField(minimum, { minimum = it.filter(Char::isDigit) }, "Estoque mínimo", "0", KeyboardType.Number)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            StockItem(
                                name = name.trim(),
                                quantity = quantity.toIntOrNull() ?: 0,
                                minimum = minimum.toIntOrNull() ?: 0
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint)
            ) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PhotoPickerCard(
    photoUri: String,
    initials: String,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfilePhoto(photoUri = photoUri, initials = initials, size = 76)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Foto do Idoso", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Escolha uma imagem da galeria para identificação.",
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPick, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (photoUri.isBlank()) "Selecionar imagem" else "Trocar imagem",
                        color = Forest
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(elder: Elder, size: Int) {
    ProfilePhoto(photoUri = elder.photoUri, initials = elder.initials(), size = size)
}

@Composable
private fun ProfilePhoto(photoUri: String, initials: String, size: Int) {
    val context = LocalContext.current
    val bitmap = remember(photoUri) {
        if (photoUri.isBlank()) null else runCatching {
            context.contentResolver.openInputStream(Uri.parse(photoUri)).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(MintPale, Color(0xFFBFE0D5))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto do idoso",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                initials,
                color = Forest,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 3).sp
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("♡", color = Mint, fontSize = 34.sp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = if (label.isBlank()) null else ({ Text(label) }),
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun StockMedicationPicker(
    stock: List<StockItem>,
    selectedId: String,
    onSelect: (StockItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = stock.firstOrNull { it.id == selectedId }
    Column {
        Text("Medicamento do estoque *", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = stock.isNotEmpty()
            ) {
                Text(
                    selected?.let { "${it.name} (${it.quantity} disponíveis)" }
                        ?: if (stock.isEmpty()) "Estoque vazio" else "Selecionar do estoque",
                    color = if (selected == null) Muted else Forest,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Text("⌄", color = Muted)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.88f)
            ) {
                stock.sortedBy { it.name }.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${item.quantity} unidades em estoque",
                                    color = if (item.quantity > 0) Muted else Danger,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

internal class LocalRepository(private val context: Context) :
    SQLiteOpenHelper(context, "carinhosos.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE elders (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE stock (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadElders(): List<Elder> {
        val result = mutableListOf<Elder>()
        readableDatabase.query("elders", arrayOf("payload"), null, null, null, null, "rowid").use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { JSONObject(cursor.getString(0)).toElder() }.getOrNull()?.let(result::add)
            }
        }
        if (result.isNotEmpty()) return result
        if (setting("elders_initialized") == "true") return emptyList()

        val legacy = context.getSharedPreferences("carinhosos_data", Context.MODE_PRIVATE)
            .getString("elders", null)
        val initial = if (legacy != null) runCatching {
            val array = JSONArray(legacy)
            List(array.length()) { index -> array.getJSONObject(index).toElder() }
        }.getOrElse { seedElders() } else seedElders()
        saveElders(initial)
        return initial
    }

    fun saveElders(elders: List<Elder>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("elders", null, null)
            elders.forEach { elder ->
                writableDatabase.insertOrThrow(
                    "elders",
                    null,
                    ContentValues().apply {
                        put("id", elder.id)
                        put("payload", elder.toJson().toString())
                    }
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        setSetting("elders_initialized", "true")
    }

    fun loadStock(): List<StockItem> {
        val result = mutableListOf<StockItem>()
        readableDatabase.query("stock", arrayOf("payload"), null, null, null, null, "rowid").use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { JSONObject(cursor.getString(0)).toStockItem() }.getOrNull()?.let(result::add)
            }
        }
        if (result.isNotEmpty()) return result
        if (setting("stock_initialized") == "true") return emptyList()

        val legacy = context.getSharedPreferences("carinhosos_data", Context.MODE_PRIVATE)
            .getString("stock", null)
        val initial = if (legacy != null) runCatching {
            val array = JSONArray(legacy)
            List(array.length()) { index -> array.getJSONObject(index).toStockItem() }
        }.getOrElse { seedStock() } else seedStock()
        saveStock(initial)
        return initial
    }

    fun saveStock(stock: List<StockItem>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("stock", null, null)
            stock.forEach { item ->
                val payload = JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("quantity", item.quantity)
                    .put("minimum", item.minimum)
                writableDatabase.insertOrThrow(
                    "stock",
                    null,
                    ContentValues().apply {
                        put("id", item.id)
                        put("payload", payload.toString())
                    }
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        setSetting("stock_initialized", "true")
    }

    fun isLoggedIn(): Boolean = setting("logged_in") == "true"

    fun setLoggedIn(value: Boolean) = setSetting("logged_in", value.toString())

    fun notificationsEnabled(): Boolean = setting("notifications_enabled") != "false"

    fun setNotificationsEnabled(value: Boolean) =
        setSetting("notifications_enabled", value.toString())

    private fun setting(key: String): String? {
        readableDatabase.query(
            "settings",
            arrayOf("value"),
            "key = ?",
            arrayOf(key),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun setSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "settings",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}

private fun Elder.toJson(): JSONObject {
    val medicationArray = JSONArray()
    medications.forEach {
        medicationArray.put(
            JSONObject()
                .put("id", it.id)
                .put("stockItemId", it.stockItemId)
                .put("name", it.name)
                .put("dose", it.dose)
                .put("time", it.time)
                .put("startDate", it.startDate)
                .put("frequency", it.frequency)
                .put("notificationEnabled", it.notificationEnabled)
                .put("lastAppliedDate", it.lastAppliedDate)
        )
    }
    val historyArray = JSONArray()
    doseHistory.forEach {
        historyArray.put(
            JSONObject()
                .put("id", it.id)
                .put("medicationId", it.medicationId)
                .put("medicationName", it.medicationName)
                .put("dose", it.dose)
                .put("scheduledTime", it.scheduledTime)
                .put("appliedAt", it.appliedAt)
        )
    }
    return JSONObject()
        .put("id", id)
        .put("name", name)
        .put("cpf", cpf)
        .put("birthDate", birthDate)
        .put("room", room)
        .put("notes", notes)
        .put("guardianName", guardianName)
        .put("guardianCpf", guardianCpf)
        .put("relationship", relationship)
        .put("phone", phone)
        .put("email", email)
        .put("photoUri", photoUri)
        .put("medications", medicationArray)
        .put("doseHistory", historyArray)
}

private fun JSONObject.toElder(): Elder {
    val medicationArray = optJSONArray("medications") ?: JSONArray()
    val historyArray = optJSONArray("doseHistory") ?: JSONArray()
    return Elder(
        id = optString("id", UUID.randomUUID().toString()),
        name = optString("name"),
        cpf = optString("cpf"),
        birthDate = optString("birthDate"),
        room = optString("room"),
        notes = optString("notes"),
        guardianName = optString("guardianName"),
        guardianCpf = optString("guardianCpf"),
        relationship = optString("relationship"),
        phone = optString("phone"),
        email = optString("email"),
        photoUri = optString("photoUri"),
        medications = List(medicationArray.length()) { index ->
            val item = medicationArray.getJSONObject(index)
            Medication(
                id = item.optString("id", UUID.randomUUID().toString()),
                stockItemId = item.optString("stockItemId"),
                name = item.optString("name"),
                dose = item.optString("dose"),
                time = item.optString("time"),
                startDate = item.optString("startDate", todayDisplay()),
                frequency = item.optString("frequency", "Diário"),
                notificationEnabled = item.optBoolean("notificationEnabled", true),
                lastAppliedDate = item.optString("lastAppliedDate")
            )
        },
        doseHistory = List(historyArray.length()) { index ->
            val item = historyArray.getJSONObject(index)
            DoseRecord(
                id = item.optString("id", UUID.randomUUID().toString()),
                medicationId = item.optString("medicationId"),
                medicationName = item.optString("medicationName"),
                dose = item.optString("dose"),
                scheduledTime = item.optString("scheduledTime"),
                appliedAt = item.optString("appliedAt")
            )
        }
    )
}

private fun JSONObject.toStockItem() = StockItem(
    id = optString("id", UUID.randomUUID().toString()),
    name = optString("name"),
    quantity = optInt("quantity"),
    minimum = optInt("minimum")
)

private fun seedElders() = listOf(
    Elder(
        name = "Carlos Afonso",
        cpf = "123.456.789-10",
        birthDate = "21/03/1970",
        room = "102",
        notes = "Vegano",
        guardianName = "José Afonso",
        guardianCpf = "000.111.222-33",
        relationship = "Filho",
        phone = "(13) 99123-4512",
        email = "",
        medications = listOf(Medication(name = "Loratadina", dose = "50 mg", time = "23:00"))
    ),
    Elder(
        name = "Carlos Asteca",
        cpf = "321.654.987-00",
        birthDate = "14/09/1965",
        room = "103",
        notes = "Atenção à pressão arterial.",
        guardianName = "Ana Asteca",
        guardianCpf = "111.222.333-44",
        relationship = "Filha",
        phone = "(13) 99888-7711",
        email = "ana@example.com",
        medications = listOf(
            Medication(name = "Losartana", dose = "50 mg", time = "08:00"),
            Medication(name = "Metformina", dose = "500 mg", time = "08:30"),
            Medication(name = "Omeprazol", dose = "20 mg", time = "12:00"),
            Medication(name = "Sinvastatina", dose = "20 mg", time = "20:00")
        )
    ),
    Elder(
        name = "Maria Oliveira",
        cpf = "987.654.321-00",
        birthDate = "02/11/1948",
        room = "104",
        notes = "Prefere alimentação com pouco sal.",
        guardianName = "Paulo Oliveira",
        guardianCpf = "222.333.444-55",
        relationship = "Filho",
        phone = "(13) 99777-6622",
        email = "",
        medications = listOf(
            Medication(name = "AAS", dose = "100 mg", time = "09:00"),
            Medication(name = "Cálcio", dose = "500 mg", time = "18:00")
        )
    )
)

private fun seedStock() = listOf(
    StockItem(name = "Loratadina 50 mg", quantity = 18, minimum = 10),
    StockItem(name = "Losartana 50 mg", quantity = 8, minimum = 10),
    StockItem(name = "Metformina 500 mg", quantity = 24, minimum = 12)
)

private fun Medication.appliedToday() = lastAppliedDate == todayKey()

private fun Medication.isCompletedForCurrentCycle(): Boolean =
    if (frequency == "Dose única") lastAppliedDate.isNotBlank() else appliedToday()

private fun Medication.isDueToday(): Boolean =
    isActiveToday() && !isCompletedForCurrentCycle()

private fun Medication.isActiveToday(): Boolean {
    val parsed = listOf("dd/MM/yyyy", "yyyy-MM-dd").firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(startDate)
        }.getOrNull()
    } ?: return true
    val today = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 23)
        set(java.util.Calendar.MINUTE, 59)
        set(java.util.Calendar.SECOND, 59)
    }
    return parsed.time <= today.timeInMillis
}

private fun findStockItem(stock: List<StockItem>, medication: Medication): StockItem? {
    if (medication.stockItemId.isNotBlank()) {
        stock.firstOrNull { it.id == medication.stockItemId }?.let { return it }
    }
    val medicationName = medication.name.trim().lowercase()
    return stock.firstOrNull { item ->
        val stockName = item.name.trim().lowercase()
        stockName.contains(medicationName) || medicationName.contains(stockName)
    }
}

internal fun medicationDisplay(name: String, dose: String): String =
    if (dose.isBlank() || name.contains(dose, ignoreCase = true)) {
        name.trim()
    } else {
        "$name $dose".trim()
    }

private fun validateMedication(
    name: String,
    dose: String,
    time: String,
    startDate: String,
    stockItemId: String,
    stock: List<StockItem>
): String? {
    val errors = mutableListOf<String>()
    if (stock.isEmpty()) {
        errors += "O estoque está vazio. Cadastre o medicamento no estoque primeiro."
    } else if (stockItemId.isBlank() || stock.none { it.id == stockItemId }) {
        errors += "Selecione um medicamento do estoque."
    }
    if (name.isBlank()) errors += "Informe o nome do medicamento."
    if (dose.isBlank()) errors += "Informe a dosagem."
    if (startDate.isBlank()) {
        errors += "Informe a data de início."
    } else if (!isValidDate(startDate)) {
        errors += "Data inválida. Use o formato DD/MM/AAAA."
    }
    if (time.isBlank()) {
        errors += "Informe o horário."
    } else if (!isValidTime(time)) {
        errors += "Horário inválido. Use o formato HH:MM, de 00:00 a 23:59."
    }
    return errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun isValidDate(value: String): Boolean =
    runCatching {
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }.parse(value)
    }.getOrNull() != null

private fun isValidTime(value: String): Boolean {
    val match = Regex("""^(\d{2}):(\d{2})$""").matchEntire(value) ?: return false
    val hour = match.groupValues[1].toIntOrNull() ?: return false
    val minute = match.groupValues[2].toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59
}

private fun Elder.initials(): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }

private fun Elder.ageLabel(): String {
    val parser = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }
    val birth = runCatching { parser.parse(birthDate) }.getOrNull() ?: return "Idade não informada"
    val now = java.util.Calendar.getInstance()
    val born = java.util.Calendar.getInstance().apply { time = birth }
    var age = now.get(java.util.Calendar.YEAR) - born.get(java.util.Calendar.YEAR)
    if (now.get(java.util.Calendar.DAY_OF_YEAR) < born.get(java.util.Calendar.DAY_OF_YEAR)) age--
    return "$age anos"
}

private fun todayKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun todayDisplay(): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())

private fun currentDateTime(): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())

private fun formatToday(): String =
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date())

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
