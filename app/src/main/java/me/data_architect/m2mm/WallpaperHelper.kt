package me.data_architect.m2mm

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperHelper {
    private const val TAG = "WallpaperHelper"

    suspend fun updateWallpaper(context: Context, imageName: String) {
        withContext(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val cleanImageName = imageName.substringBeforeLast(".")
                val resId = context.resources.getIdentifier(cleanImageName, "drawable", context.packageName)
                
                if (resId != 0) {
                    val originalBitmap = BitmapFactory.decodeResource(context.resources, resId)
                    if (originalBitmap != null) {
                        // Get screen dimensions
                        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        val metrics = DisplayMetrics()
                        windowManager.defaultDisplay.getRealMetrics(metrics)
                        val screenWidth = metrics.widthPixels
                        val screenHeight = metrics.heightPixels

                        // Process and scale bitmap
                        // Astuce : On ajoute +2 pixels à la largeur du canvas pour empêcher le launcher
                        // (ex: Pixel Launcher) d'appliquer un zoom forcé en détectant la taille exacte.
                        val canvasWidth = screenWidth + 2
                        val scaledBitmap = getScaledBitmap(originalBitmap, screenWidth, canvasWidth, screenHeight)
                        
                        // Set wallpaper and suggest dimensions to disable system parallax zoom
                        wallpaperManager.suggestDesiredDimensions(canvasWidth, screenHeight)
                        wallpaperManager.setBitmap(scaledBitmap)
                        
                        Log.d(TAG, "Wallpaper updated and scaled to image:${screenWidth} canvas:${canvasWidth}x${screenHeight}: $cleanImageName")
                        
                        // Recycle bitmaps to free memory
                        if (scaledBitmap != originalBitmap) {
                            scaledBitmap.recycle()
                        }
                        originalBitmap.recycle()
                    } else {
                        Log.e(TAG, "Failed to decode bitmap for: $cleanImageName")
                    }
                } else {
                    Log.e(TAG, "Resource not found: $cleanImageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting wallpaper: ${e.message}", e)
            }
        }
    }

    private fun getScaledBitmap(source: Bitmap, imageDrawWidth: Int, canvasWidth: Int, canvasHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        // On calcule l'échelle pour correspondre exactement à la largeur de l'écran (imageDrawWidth)
        val scale = imageDrawWidth.toFloat() / sourceWidth.toFloat()
        val scaledWidth = imageDrawWidth
        val scaledHeight = (sourceHeight * scale).toInt()

        // 1. Redimensionner l'image source à la bonne largeur
        val scaledSource = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

        // 2. Créer l'image finale aux dimensions exactes du canvas avec fond blanc
        val finalBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(finalBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // 3. Dessiner l'image redimensionnée, centrée verticalement et horizontalement
        val xOffset = (canvasWidth - scaledWidth) / 2f
        val yOffset = (canvasHeight - scaledHeight) / 2f
        canvas.drawBitmap(scaledSource, xOffset, yOffset, null)
        
        // Libérer la mémoire
        if (scaledSource != source) {
            scaledSource.recycle()
        }

        return finalBitmap
    }
}
