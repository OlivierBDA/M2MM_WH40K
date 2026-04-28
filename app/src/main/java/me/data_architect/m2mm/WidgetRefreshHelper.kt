package me.data_architect.m2mm

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.delay
import me.data_architect.m2mm.widget.M2MMWidget

object WidgetRefreshHelper {
    private const val TAG = "WidgetRefreshHelper"

    /**
     * Refreshes all instances of the M2MMWidget.
     * Uses a direct M2MMWidget() instance to avoid receiver-related initialisation issues.
     */
    suspend fun refreshAllWidgets(context: Context) {
        try {
            // Safety delay to ensure Room database transaction is fully committed
            delay(150) // Increased slightly for safety
            
            Log.d(TAG, "Triggering updateAll for M2MMWidget")
            M2MMWidget().updateAll(context)
            Log.d(TAG, "updateAll completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing widgets: ${e.message}", e)
        }
    }
}
