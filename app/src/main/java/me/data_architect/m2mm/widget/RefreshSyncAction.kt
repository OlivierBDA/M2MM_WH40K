package me.data_architect.m2mm.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class RefreshSyncAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d("M2MMWidget", "Action: Manual Refresh triggered")
        try {
            Log.d("M2MMWidget", "Requesting widget updateAll for sync...")
            M2MMWidget().updateAll(context)
            Log.d("M2MMWidget", "Sync updateAll request sent")
        } catch (e: Exception) {
            Log.e("M2MMWidget", "Error in RefreshSyncAction: ${e.message}", e)
        }
    }
}
