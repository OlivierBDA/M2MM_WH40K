package me.data_architect.m2mm.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.data_architect.m2mm.R
import me.data_architect.m2mm.data.GameContext
import me.data_architect.m2mm.data.GameRepository
import me.data_architect.m2mm.data.CloudLlmService
import me.data_architect.m2mm.data.LocalLlmService
import me.data_architect.m2mm.data.LlmService
import me.data_architect.m2mm.data.M2MMDatabase

class CoachWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("CoachWorker", "Starting CoachWorker.doWork()")
            val database = M2MMDatabase.getDatabase(context)
            val repository = GameRepository(context, database.dao())
            val config = repository.config()
            
            val llmService: LlmService = if (config.use_local_llm) {
                android.util.Log.d("CoachWorker", "Using Local LLM (AICore)")
                LocalLlmService(context)
            } else {
                val apiKey = config.llm_api_key
                if (apiKey.isNullOrEmpty()) {
                    android.util.Log.e("CoachWorker", "API Key is missing for Cloud LLM!")
                    return@withContext Result.failure()
                }
                android.util.Log.d("CoachWorker", "Using Cloud LLM")
                CloudLlmService(apiKey)
            }
            
            val levelDetails = repository.getLevelDetails()
            val currentScore = levelDetails.score
            
            val sevenDaysAgoTimestamp = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val score7DaysAgo = repository.getScoreAtTimestamp(sevenDaysAgoTimestamp)
            
            val recentLogs = repository.getRecentActivityLogs()
            val recentActivitiesDone = recentLogs.mapNotNull { log ->
                config.activities.find { it.id == log.activityId }?.name
            }.distinct()
            
            val gameContext = GameContext(
                primarchName = levelDetails.currentLevel.primarch?.name ?: "Inconnu",
                legionName = levelDetails.currentLevel.legion_name ?: "Inconnue",
                currentScore = currentScore,
                currentLevel = levelDetails.currentLevel.name,
                pointsToNextLevel = levelDetails.nextLevelThreshold - currentScore,
                score7DaysAgo = score7DaysAgo,
                recentActivitiesDone = recentActivitiesDone,
                recentActivitiesMissed = emptyList(), // Not explicitly tracked in DB right now
                allAvailableActivities = config.activities.map { it.name }
            )
            
            android.util.Log.d("CoachWorker", "GameContext created: $gameContext")
            
            val llmResult = llmService.generateEncouragementDynamic(gameContext)
            if (llmResult.isFailure) {
                android.util.Log.e("CoachWorker", "LLM API Call failed: ${llmResult.exceptionOrNull()?.message}")
            }
            
            val responseString = llmResult.getOrNull()?.response
            
            val messageParts = responseString?.split("🎯 Réponse finale :\n")
            val message = if (messageParts != null && messageParts.size > 1) {
                messageParts[1]
            } else {
                responseString ?: "Continue tes efforts, Frère !"
            }
            android.util.Log.d("CoachWorker", "Message to display: $message")
            
            // Retrieve portrait
            val portraitName = levelDetails.currentLevel.primarch?.portrait_square?.substringBeforeLast(".")
            val portraitResId = if (portraitName != null) {
                context.resources.getIdentifier(portraitName, "drawable", context.packageName)
            } else 0
            val largeIcon = if (portraitResId != 0) {
                BitmapFactory.decodeResource(context.resources, portraitResId)
            } else {
                null
            }

            android.util.Log.d("CoachWorker", "Sending notification...")
            sendNotification(
                title = gameContext.primarchName,
                message = message,
                largeIcon = largeIcon
            )
            android.util.Log.d("CoachWorker", "Worker finished successfully.")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("CoachWorker", "Exception in doWork", e)
            Result.failure()
        }
    }

    private fun sendNotification(title: String, message: String, largeIcon: Bitmap?) {
        val channelId = "coach_channel"
        val notificationId = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Coach Primarque"
            val descriptionText = "Notifications d'encouragement quotidien"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: replace with app icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
            // Optional: Also use BigPictureStyle if you want a huge portrait
            // builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(largeIcon).bigLargeIcon(null as Bitmap?))
        }

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(notificationId, builder.build())
            }
        }
    }

    companion object {
        fun scheduleDailyCoachNotification(context: Context, timeString: String) {
            try {
                val parts = timeString.split(":")
                val hour = parts[0].toIntOrNull() ?: 22
                val minute = parts[1].toIntOrNull() ?: 0

                val calendar = java.util.Calendar.getInstance()
                val nowMillis = calendar.timeInMillis
                
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                
                if (calendar.timeInMillis <= nowMillis) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                
                val initialDelay = calendar.timeInMillis - nowMillis

                val workRequest = androidx.work.PeriodicWorkRequestBuilder<CoachWorker>(
                    24, java.util.concurrent.TimeUnit.HOURS
                )
                .setInitialDelay(initialDelay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("daily_coach_notification")
                .build()

                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "DailyCoachNotification",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            } catch (e: Exception) {
                android.util.Log.e("CoachWorker", "Erreur lors de la planification", e)
            }
        }
    }
}
