package me.data_architect.m2mm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import me.data_architect.m2mm.worker.CoachWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import me.data_architect.m2mm.data.CloudLlmService
import me.data_architect.m2mm.data.LocalLlmService
import me.data_architect.m2mm.data.LlmService
import me.data_architect.m2mm.data.GameContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.BitmapFactory
import android.content.Context
import androidx.core.app.NotificationCompat
import me.data_architect.m2mm.data.M2MMDatabase
import me.data_architect.m2mm.data.GameRepository
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestCoachScreen(
    apiKey: String,
    useLocalLlm: Boolean,
    allActivities: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var llmPrompt by remember { mutableStateOf<String?>(null) }
    var llmResponse by remember { mutableStateOf<String?>(null) }
    var isLlmLoading by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
    }

    val database = remember { M2MMDatabase.getDatabase(context) }
    val repository = remember { GameRepository(context, database.dao()) }
    var coachHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        coachHistory = repository.getCoachHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test Coach Primarque", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = android.R.drawable.ic_menu_revert), contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Outil de test de l'IA (Prompt, Historique & Notification).",
                style = MaterialTheme.typography.bodyMedium
            )

            if (!permissionGranted) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Autoriser les notifications")
                }
            }
            
            if (coachHistory.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Historique (7 dernières phrases) :", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        coachHistory.forEach { phrase ->
                            Text("- $phrase", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tester l'encouragement", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!useLocalLlm && apiKey.isBlank()) {
                        Text(
                            "⚠️ Aucune clé API configurée.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Button(
                            onClick = {
                                isLlmLoading = true
                                llmPrompt = null
                                llmResponse = null
                                coroutineScope.launch {
                                    val service: LlmService = if (useLocalLlm) {
                                        LocalLlmService(context)
                                    } else {
                                        CloudLlmService(apiKey)
                                    }
                                    val levelDetails = repository.getLevelDetails()
                                    val currentScore = levelDetails.score
                                    val config = repository.config()
                                    val sevenDaysAgoTimestamp = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                                    val score7DaysAgo = repository.getScoreAtTimestamp(sevenDaysAgoTimestamp)
                                    val recentLogs = repository.getRecentActivityLogs()
                                    val lastActivityDates = repository.getLastActivityDates()
                                    val now = System.currentTimeMillis()
                                    
                                    val activitiesDetails = config.activities.map { activity ->
                                        val count = recentLogs.count { it.activityId == activity.id }
                                        val lastTimestamp = lastActivityDates[activity.id]
                                        val daysSince = if (lastTimestamp != null) {
                                            ((now - lastTimestamp) / (24L * 60 * 60 * 1000)).toInt()
                                        } else {
                                            999
                                        }
                                        val statusPalette = config.status_levels[activity.type] ?: emptyList()
                                        val status = me.data_architect.m2mm.data.determineStatus(daysSince, activity, statusPalette)
                                        val statusLabel = status?.label ?: "Inconnu"
                                        "${activity.name} x$count ($statusLabel)"
                                    }
                                    
                                    val currentHistory = repository.getCoachHistory()

                                    val gameContext = GameContext(
                                        primarchName = levelDetails.currentLevel.primarch?.name ?: "Inconnu",
                                        legionName = levelDetails.currentLevel.legion_name ?: "Inconnue",
                                        currentScore = currentScore,
                                        currentLevel = levelDetails.currentLevel.name,
                                        pointsToNextLevel = levelDetails.nextLevelThreshold - currentScore,
                                        score7DaysAgo = score7DaysAgo,
                                        activitiesDetails = activitiesDetails,
                                        coachHistory = currentHistory
                                    )
                                    val result = service.generateEncouragementDynamic(gameContext)
                                    isLlmLoading = false
                                    if (result.isSuccess) {
                                        val res = result.getOrThrow()
                                        llmPrompt = res.prompt
                                        llmResponse = res.response
                                        
                                        // Save history and trigger notification
                                        val messageParts = res.response.split("🎯 Réponse finale :\n")
                                        val message = if (messageParts.size > 1) messageParts[1] else res.response
                                        
                                        repository.addCoachHistory(message)
                                        coachHistory = repository.getCoachHistory() // update UI state
                                        
                                        if (permissionGranted) {
                                            sendTestNotification(context, gameContext.primarchName, message, levelDetails.currentLevel.primarch?.portrait_square)
                                        }
                                    } else {
                                        llmResponse = result.exceptionOrNull()?.message ?: "Erreur inconnue"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLlmLoading
                        ) {
                            if (isLlmLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Tester le prompt avec contexte")
                            }
                        }
                    }

                    if (llmPrompt != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Prompt envoyé (format Markdown) :", fontWeight = FontWeight.SemiBold)
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = llmPrompt!!,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (llmResponse != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Réponse du modèle :", fontWeight = FontWeight.SemiBold)
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = llmResponse!!,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sendTestNotification(context: Context, title: String, message: String, portraitName: String?) {
    val channelId = "coach_channel"
    val notificationId = System.currentTimeMillis().toInt()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Coach Primarque (Test)"
        val descriptionText = "Notifications d'encouragement de test"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    val portraitResId = if (portraitName != null) {
        val cleanName = portraitName.substringBeforeLast(".")
        context.resources.getIdentifier(cleanName, "drawable", context.packageName)
    } else 0
    
    val largeIcon = if (portraitResId != 0) {
        BitmapFactory.decodeResource(context.resources, portraitResId)
    } else null

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: replace with app icon
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    if (largeIcon != null) {
        builder.setLargeIcon(largeIcon)
    }

    try {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    } catch (e: SecurityException) {
        // Permission not granted
    }
}
