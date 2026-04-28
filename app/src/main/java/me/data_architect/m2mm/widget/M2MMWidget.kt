package me.data_architect.m2mm.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import android.util.Log
import android.content.ComponentName
import me.data_architect.m2mm.MainActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import me.data_architect.m2mm.R
import me.data_architect.m2mm.data.GameRepository
import me.data_architect.m2mm.data.M2MMDatabase
import me.data_architect.m2mm.data.GameState
import me.data_architect.m2mm.WidgetRefreshHelper

class M2MMWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("M2MMWidget", "provideGlance triggered for ID: $id")
        val database = M2MMDatabase.getDatabase(context)
        val repository = GameRepository(context, database.dao())
        
        val config = repository.config()
        val gameState = repository.getGameState()
        
        // Calculate 7-day differential (only fetch start point, not full history)
        val weekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val scoreThen = repository.getScoreAtTimestamp(weekAgo)
        val differential = gameState.score - scoreThen
        
        val levelDetails = repository.getLevelDetails()
        val thresholds = config.widget_progression_thresholds
        
        val arrow = when {
            differential >= (thresholds["Au top"] ?: 500) -> "⬆️"
            differential >= (thresholds["Correct"] ?: 250) -> "↗️"
            differential >= (thresholds["Stagnant"] ?: 0) -> "➡️"
            else -> "⬇️"
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .background(androidx.glance.unit.ColorProvider(R.color.widget_background)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Current Level, Score and Arrow (Clickable for manual refresh)
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                        .clickable(actionRunCallback<RefreshSyncAction>()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = levelDetails.currentLevel.name,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.glance.unit.ColorProvider(R.color.widget_text_primary)
                        )
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "${gameState.score}",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = androidx.glance.unit.ColorProvider(R.color.widget_text_primary)
                        )
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = arrow,
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = if (differential >= 0) {
                                androidx.glance.unit.ColorProvider(R.color.widget_score_positive)
                            } else {
                                androidx.glance.unit.ColorProvider(R.color.widget_score_negative)
                            }
                        )
                    )
                }

                val widgetActivities = config.activities
                    .filter { it.show_in_widget }
                    .sortedBy { it.order }

                val legionInsigniaName = levelDetails.currentLevel.legion_insignia ?: "insigne_01_dark_angels.png"
                val insigniaResId = context.resources.getIdentifier(
                    legionInsigniaName.substringBeforeLast("."),
                    "drawable",
                    context.packageName
                )
                val finalInsigniaResId = if (insigniaResId != 0) insigniaResId else R.drawable.ultramarine_icon_pompes

                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Legion Insignia
                    Column(
                        modifier = GlanceModifier.padding(horizontal = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            provider = ImageProvider(finalInsigniaResId),
                            contentDescription = "Légion",
                            modifier = GlanceModifier
                                .size(46.dp)
                                .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                        )
                    }

                    // Activity icons
                    widgetActivities.forEach { activity ->
                        ActivityWidgetIcon(context, activity.id, activity.icon)
                    }
                }
            }
        }
    }

    @Composable
    private fun ActivityWidgetIcon(context: Context, id: String, iconName: String) {
        val cleanName = iconName.substringBeforeLast(".")
        val resId = context.resources.getIdentifier(cleanName, "drawable", context.packageName)
        
        val finalResId = if (resId != 0) resId else R.drawable.ultramarine_icon_pompes

        Column(
            modifier = GlanceModifier.padding(horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(finalResId),
                contentDescription = id,
                modifier = GlanceModifier
                    .size(46.dp)
                    .clickable(
                        actionRunCallback<RecordActivityAction>(
                            actionParametersOf(ActionParameters.Key<String>("activityId") to id)
                        )
                    )
            )
        }
    }
}

