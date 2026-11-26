package com.example.data.remote

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Zero-API-key Web Mercator raster tile loader with in-memory LRU cache and local disk cache.
 * Uses CartoDB Dark Matter / Voyager basemaps with automatic OpenStreetMap fallback.
 */
class MapTileRepository(context: Context) {

    private val cacheRoot = File(context.cacheDir, "map_tiles").apply { mkdirs() }
    private val memoryCache = LruCache<String, ImageBitmap>(96)
    private val inFlight = ConcurrentHashMap<String, Boolean>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val USER_AGENT = "UpperAirViewer/2.0 (Android; US Sounding Map)"
    }

    private fun cacheKey(darkTheme: Boolean, z: Int, x: Int, y: Int): String {
        val style = if (darkTheme) "dark" else "light"
        return "$style/$z/$x/$y"
    }

    fun getMemoryTile(darkTheme: Boolean, z: Int, x: Int, y: Int): ImageBitmap? {
        return memoryCache.get(cacheKey(darkTheme, z, x, y))
    }

    suspend fun loadTile(darkTheme: Boolean, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = cacheKey(darkTheme, z, x, y)
        memoryCache.get(key)?.let { return it }

        if (inFlight.putIfAbsent(key, true) != null) {
            return null
        }

        return try {
            withContext(Dispatchers.IO) {
                val diskFile = File(cacheRoot, "${key.replace('/', '_')}.png")
                if (diskFile.exists() && diskFile.length() > 100L) {
                    decodeAndCache(key, diskFile.readBytes())?.let { return@withContext it }
                }

                val urls = if (darkTheme) {
                    listOf(
                        "https://a.basemaps.cartocdn.com/dark_all/$z/$x/$y.png",
                        "https://b.basemaps.cartocdn.com/dark_all/$z/$x/$y.png",
                        "https://tile.openstreetmap.org/$z/$x/$y.png"
                    )
                } else {
                    listOf(
                        "https://a.basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png",
                        "https://b.basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png",
                        "https://tile.openstreetmap.org/$z/$x/$y.png"
                    )
                }

                for (url in urls) {
                    val bytes = fetchBytes(url) ?: continue
                    val decoded = decodeAndCache(key, bytes)
                    if (decoded != null) {
                        runCatching { diskFile.writeBytes(bytes) }
                        return@withContext decoded
                    }
                }
                null
            }
        } finally {
            inFlight.remove(key)
        }
    }

    private fun fetchBytes(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeAndCache(key: String, bytes: ByteArray): ImageBitmap? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val imageBitmap = bmp.asImageBitmap()
        memoryCache.put(key, imageBitmap)
        return imageBitmap
    }
}
