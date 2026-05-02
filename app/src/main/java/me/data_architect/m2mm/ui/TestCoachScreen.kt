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
import me.data_architect.m2mm.data.LlmRepository
import me.data_architect.m2mm.data.GameContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestCoachScreen(
    apiKey: String,
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
                "Outils de développement pour tester les différentes étapes de l'intégration du Coach Primarque.",
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

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Étape 1 : Notification de base", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val request = OneTimeWorkRequestBuilder<CoachWorker>().build()
                            WorkManager.getInstance(context).enqueue(request)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = permissionGranted
                    ) {
                        Text("Déclencher la notification immédiatement")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val request = OneTimeWorkRequestBuilder<CoachWorker>()
                                .setInitialDelay(1, TimeUnit.MINUTES)
                                .build()
                            WorkManager.getInstance(context).enqueue(request)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = permissionGranted
                    ) {
                        Text("Planifier dans 1 minute")
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Étape 3 : Appel LLM Dynamique", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (apiKey.isBlank()) {
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
                                    val repository = LlmRepository(apiKey)
                                    val dummyContext = GameContext(
                                        primarchName = "Lion El'Jonson",
                                        legionName = "Dark Angels",
                                        currentScore = 1250,
                                        currentLevel = "Frère de bataille",
                                        pointsToNextLevel = 250,
                                        score7DaysAgo = 1100,
                                        recentActivitiesDone = listOf("Pompes", "Lecture"),
                                        recentActivitiesMissed = listOf("Malus de points"),
                                        allAvailableActivities = allActivities
                                    )
                                    val result = repository.generateEncouragementDynamic(dummyContext)
                                    isLlmLoading = false
                                    if (result.isSuccess) {
                                        val res = result.getOrThrow()
                                        llmPrompt = res.prompt
                                        llmResponse = res.response
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
