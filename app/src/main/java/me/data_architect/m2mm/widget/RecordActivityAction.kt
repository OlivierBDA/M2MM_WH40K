package me.data_architect.m2mm.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import me.data_architect.m2mm.data.GameRepository
import me.data_architect.m2mm.data.M2MMDatabase

class RecordActivityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val activityId = parameters[ActionParameters.Key<String>("activityId")] ?: return
        Log.d("M2MMWidget", "Action: RecordActivity triggered for $activityId")
        
        try {
            val database = M2MMDatabase.getDatabase(context)
            val repository = GameRepository(context, database.dao())
            
            val points = repository.recordActivity(activityId)
            Log.d("M2MMWidget", "Activity $activityId recorded: $points points")
            
            // Trigger refresh for ALL instances (most robust way)
            Log.d("M2MMWidget", "Requesting widget updateAll...")
            M2MMWidget().updateAll(context)
            Log.d("M2MMWidget", "updateAll request sent")
        } catch (e: Exception) {
            Log.e("M2MMWidget", "Error in RecordActivityAction: ${e.message}", e)
        }
    }
}
