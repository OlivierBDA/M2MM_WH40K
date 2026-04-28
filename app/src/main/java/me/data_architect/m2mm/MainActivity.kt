package me.data_architect.m2mm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import me.data_architect.m2mm.data.*
import me.data_architect.m2mm.ui.theme.M2MMTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    
    private val mainViewModel: MainViewModel by viewModels {
        val database = M2MMDatabase.getDatabase(applicationContext)
        val repository = GameRepository(applicationContext, database.dao())
        MainViewModelFactory(repository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val database = M2MMDatabase.getDatabase(applicationContext)
        val repository = GameRepository(applicationContext, database.dao())
        MainViewModelFactory(repository)
    }

    private val statsViewModel: StatsViewModel by viewModels {
        val database = M2MMDatabase.getDatabase(applicationContext)
        val repository = GameRepository(applicationContext, database.dao())
        MainViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            M2MMTheme {
                val uiState by mainViewModel.uiState.collectAsState()
                val configBySettings by settingsViewModel.configState.collectAsState()
                val pointFeedback by mainViewModel.pointFeedback.collectAsState()
                val scoreHistory by statsViewModel.historyState.collectAsState()
                
                var currentScreen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("main") }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "main" -> {
                            when (val state = uiState) {
                                is MainUiState.Loading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                                is MainUiState.Success -> {
                                    MainScreen(
                                        levelDetails = state.levelDetails,
                                        lastActivityDates = state.lastActivityDates,
                                        activities = configBySettings.activities,
                                        statusLevels = configBySettings.status_levels,
                                        onActivityClick = { mainViewModel.recordActivity(it) },
                                        onSettingsClick = { currentScreen = "settings" },
                                        onStatsClick = { 
                                            statsViewModel.refreshHistory()
                                            currentScreen = "stats" 
                                        }
                                    )
                                }
                            }
                        }
                        "settings" -> {
                            SettingsScreen(
                                config = configBySettings,
                                onBack = { 
                                    settingsViewModel.saveConfig()
                                    mainViewModel.refreshData()
                                    currentScreen = "main" 
                                },
                                onThresholdChange = { actId, label, value ->
                                    settingsViewModel.updateThreshold(actId, label, value)
                                },
                                onPointsChange = { actId, value ->
                                    settingsViewModel.updatePoints(actId, value)
                                },
                                onWidgetVisibilityChange = { actId, visible ->
                                    settingsViewModel.updateWidgetVisibility(actId, visible)
                                },
                                onDailyDecayChange = { value ->
                                    settingsViewModel.updateDailyDecay(value)
                                }
                            )
                        }
                        "stats" -> {
                            StatsScreen(
                                scoreHistory = scoreHistory,
                                onBack = { currentScreen = "main" }
                            )
                        }
                    }

                    // Points Feedback Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = pointFeedback != null,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        pointFeedback?.let { points ->
                            Text(
                                text = if (points > 0) "+$points" else "$points",
                                color = if (points > 0) Color(0xFF2E7D32) else Color.Red,
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .shadow(8.dp, shape = MaterialTheme.shapes.medium)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshData()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    levelDetails: LevelDetails,
    lastActivityDates: Map<String, Long>,
    activities: List<ActivityConfig>,
    statusLevels: Map<String, List<StatusLevel>>,
    onActivityClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val gothicFont = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.gothic))
                        val legionName = levelDetails.currentLevel.legion_name ?: "Adeptus Astartes"
                        Text(
                            text = legionName, 
                            fontSize = 28.sp, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = gothicFont
                        )
                        Text(
                            text = "${levelDetails.currentLevel.name} - ${levelDetails.score} / ${levelDetails.nextLevelThreshold}", 
                            fontSize = 18.sp,
                            fontFamily = gothicFont
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onStatsClick) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                            contentDescription = "Stats"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_preferences),
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Background Image
            val context = LocalContext.current
            val imageResId = context.resources.getIdentifier(
                levelDetails.currentLevel.image.substringBeforeLast("."),
                "drawable",
                context.packageName
            )
            
            Image(
                painter = painterResource(id = if (imageResId != 0) imageResId else R.drawable.ultramarine_niveau1),
                contentDescription = "Background Character",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, // Fill the screen
                alpha = 0.6f // Subtle background
            )

            // Content Overlay
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    activities.filter { it.align == "left" }.sortedBy { it.order }.forEach { activity ->
                        ActivityOverlayButton(activity, lastActivityDates, statusLevels, onActivityClick)
                    }
                }

                Spacer(modifier = Modifier.weight(1.5f)) // More space for center character

                // Right Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    activities.filter { it.align == "right" }.sortedBy { it.order }.forEach { activity ->
                        ActivityOverlayButton(activity, lastActivityDates, statusLevels, onActivityClick)
                    }
                }
            }

            // Legion Insignias Overlay (Dynamic Grid)
            if (levelDetails.achievedLegions.isNotEmpty()) {
                val chunks = levelDetails.achievedLegions.chunked(6) // Max 6 insignias par ligne
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // On inverse pour que le premier bloc soit dessiné en bas (dernier du column)
                    chunks.reversed().forEach { rowInsignias ->
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            rowInsignias.forEach { legion ->
                                val insigniaResId = context.resources.getIdentifier(
                                    legion.insignia.substringBeforeLast("."),
                                    "drawable",
                                    context.packageName
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (insigniaResId != 0) {
                                        Image(
                                            painter = painterResource(id = insigniaResId),
                                            contentDescription = "Insigne ${legion.name}",
                                            modifier = Modifier.size(50.dp)
                                        )
                                    }
                                }
                            }
                            // Espaces vides pour conserver l'alignement sur 6 colonnes même si la ligne est incomplète
                            repeat(6 - rowInsignias.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityOverlayButton(
    activity: ActivityConfig,
    lastActivityDates: Map<String, Long>,
    statusLevels: Map<String, List<StatusLevel>>,
    onActivityClick: (String) -> Unit
) {
    val lastDate = lastActivityDates[activity.id]
    val daysSince = if (lastDate != null) {
        ((System.currentTimeMillis() - lastDate) / (1000 * 60 * 60 * 24)).toInt()
    } else {
        999
    }
    
    ActivityButton(
        activity = activity,
        daysSince = daysSince,
        statusPalette = statusLevels[activity.type] ?: emptyList(),
        onActivityClick = onActivityClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: GameConfig,
    onBack: () -> Unit,
    onThresholdChange: (String, String, Int) -> Unit,
    onPointsChange: (String, Int) -> Unit,
    onWidgetVisibilityChange: (String, Boolean) -> Unit,
    onDailyDecayChange: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = android.R.drawable.ic_menu_revert), contentDescription = "Back")
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
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Général",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Malus journalier", fontWeight = FontWeight.SemiBold)
                            Text("Points perdus chaque jour", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(
                            value = config.daily_decay_points.toString(),
                            onValueChange = { newVal ->
                                newVal.toIntOrNull()?.let { onDailyDecayChange(it) }
                            },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Activités",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            items(config.activities.size) { index ->
                val activity = config.activities[index]
                ActivitySettingsCard(
                    activity = activity,
                    onThresholdChange = { label, value -> onThresholdChange(activity.id, label, value) },
                    onPointsChange = { value -> onPointsChange(activity.id, value) },
                    onWidgetVisibilityChange = { visible -> onWidgetVisibilityChange(activity.id, visible) }
                )
            }
        }
    }
}

@Composable
fun ActivitySettingsCard(
    activity: ActivityConfig,
    onThresholdChange: (String, Int) -> Unit,
    onPointsChange: (Int) -> Unit,
    onWidgetVisibilityChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val iconResId = context.resources.getIdentifier(
        activity.icon.substringBeforeLast("."),
        "drawable",
        context.packageName
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = if (iconResId != 0) iconResId else R.drawable.ultramarine_icon_pompes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = activity.name, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (activity.type == "good") "Bonus: +${activity.points}" else "Malus: ${activity.points}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activity.type == "good") Color(0xFF2E7D32) else Color.Red
                    )
                }
                Icon(
                    painter = painterResource(id = if (expanded) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float),
                    contentDescription = if (expanded) "Réduire" else "Étendre"
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Points Editing
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Score à appliquer :",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = activity.points.toString(),
                        onValueChange = { newVal ->
                            newVal.toIntOrNull()?.let { onPointsChange(it) }
                        },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        prefix = { Text(if (activity.type == "good") "+" else "") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }

                // Widget Visibility
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Afficher sur le widget :",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = activity.show_in_widget,
                        onCheckedChange = { onWidgetVisibilityChange(it) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Seuils de régularité (jours) :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                
                activity.day_thresholds.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "$label :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = value.toString(),
                            onValueChange = { newVal ->
                                newVal.toIntOrNull()?.let { onThresholdChange(label, it) }
                            },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityGrid(
    activities: List<ActivityConfig>,
    lastActivityDates: Map<String, Long>,
    statusLevels: Map<String, List<StatusLevel>>,
    onActivityClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        items(activities.sortedBy { it.order }) { activity ->
            val lastDate = lastActivityDates[activity.id]
            val daysSince = if (lastDate != null) {
                ((System.currentTimeMillis() - lastDate) / (1000 * 60 * 60 * 24)).toInt()
            } else {
                999
            }
            ActivityButton(activity, daysSince, statusLevels[activity.type] ?: emptyList(), onActivityClick)
        }
    }
}

@Composable
fun ActivityButton(
    activity: ActivityConfig,
    daysSince: Int,
    statusPalette: List<StatusLevel>,
    onActivityClick: (String) -> Unit
) {
    val context = LocalContext.current
    val iconResId = context.resources.getIdentifier(
        activity.icon.substringBeforeLast("."),
        "drawable",
        context.packageName
    )

    val status = determineStatus(daysSince, activity, statusPalette)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clickable { onActivityClick(activity.id) }
        ) {
            Image(
                painter = painterResource(id = if (iconResId != 0) iconResId else R.drawable.ultramarine_icon_pompes),
                contentDescription = activity.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = "${if (daysSince >= 999) "-" else daysSince}j ${status?.emoji ?: ""}",
            color = if (status != null) Color(android.graphics.Color.parseColor(status.color)) else Color.Gray,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun determineStatus(daysSince: Int, activity: ActivityConfig, statusPalette: List<StatusLevel>): StatusLevel? {
    if (statusPalette.isEmpty()) return null
    
    if (activity.type == "good") {
        for (level in statusPalette) {
            val threshold = activity.day_thresholds[level.label]
            if (threshold != null && daysSince <= threshold) return level
        }
        return statusPalette.last()
    } else if (activity.type == "bad") {
        var bestLevel = statusPalette.first()
        for (level in statusPalette) {
            val threshold = activity.day_thresholds[level.label]
            if (threshold != null && daysSince >= threshold) {
                bestLevel = level
            }
        }
        return bestLevel
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    scoreHistory: List<ScoreHistory>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = android.R.drawable.ic_menu_revert), contentDescription = "Back")
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
        ) {
            Text(
                text = "Évolution du score (30 jours)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                if (scoreHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Pas encore de données", color = Color.Gray)
                    }
                } else {
                    ScoreChart(
                        history = scoreHistory,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Note : Le graphique commence à enregistrer les données après la mise à jour.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ScoreChart(
    history: List<ScoreHistory>,
    modifier: Modifier = Modifier
) {
    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
    
    // Group by day and take the last score of the day
    val dailyData = history
        .filter { it.timestamp >= thirtyDaysAgo }
        .groupBy { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.DAY_OF_YEAR) + (cal.get(Calendar.YEAR) * 365)
        }
        .mapValues { it.value.last().score }
        .toSortedMap()

    val maxScore = maxOf((dailyData.values.maxOrNull() ?: 100).toFloat() * 1.2f, 100f)
    
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val spacing = width / 29f 

        // Dessin des lignes de niveaux et légions
        val maxScoreValue = maxScore.toInt()
        for (scoreLevel in 0..maxScoreValue step 500) {
            if (scoreLevel == 0) continue // Ignore la ligne du bas
            
            val y = height - (scoreLevel.toFloat() / maxScore * height)
            val isLegionLevel = scoreLevel % 3500 == 0
            
            // Ligne fine bleu pour niveau, ligne épaisse bleu foncé profond pour légion
            val lineColor = if (isLegionLevel) Color(0xFF1565C0) else Color(0xFF64B5F6)
            val strokeWidth = if (isLegionLevel) 2.dp.toPx() else 1.dp.toPx()
            
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(width, y),
                strokeWidth = strokeWidth
            )
        }

        val path = Path()
        val points = mutableListOf<androidx.compose.ui.geometry.Offset>()

        var firstPoint = true
        for (i in 0 until 30) {
            val dayTimestamp = thirtyDaysAgo + (i.toLong() * 24 * 60 * 60 * 1000)
            val cal = Calendar.getInstance().apply { timeInMillis = dayTimestamp }
            val dayKey = cal.get(Calendar.DAY_OF_YEAR) + (cal.get(Calendar.YEAR) * 365)
            
            // Find the latest score available UP TO this day
            val latestScoreAtDay = history
                .filter { it.timestamp <= (dayTimestamp + 24L * 60 * 60 * 1000 - 1) }
                .lastOrNull()?.score?.toFloat()
            
            if (latestScoreAtDay != null) {
                val x = i * spacing
                val y = height - (latestScoreAtDay / maxScore * height)
                
                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
                points.add(androidx.compose.ui.geometry.Offset(x, y))
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        
        points.forEach { point ->
            drawCircle(
                color = primaryColor,
                radius = 4.dp.toPx(),
                center = point
            )
        }
    }
}