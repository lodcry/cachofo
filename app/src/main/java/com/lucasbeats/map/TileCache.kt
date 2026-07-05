package com.lucasbeats.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// Cache de tiles OSM em disco — baixa quando há internet, serve do disco quando offline
class TileCache(ctx: Context) {

    private val cacheDir = File(ctx.cacheDir, "osm_tiles").also { it.mkdirs() }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Carrega tile — primeiro tenta disco, depois rede, depois null (offline sem cache)
    fun getTile(z: Int, x: Int, y: Int, callback: (Bitmap?) -> Unit) {
        val file = tileFile(z, x, y)
        if (file.exists()) {
            callback(BitmapFactory.decodeFile(file.absolutePath))
            return
        }
        // Tenta baixar em thread de IO
        Thread {
            val bmp = download(z, x, y)
            callback(bmp)
        }.start()
    }

    private fun download(z: Int, x: Int, y: Int): Bitmap? {
        // Alterna entre subdomínios a, b, c como o Leaflet faz
        val sub = listOf("a","b","c").random()
        val url = "https://$sub.tile.openstreetmap.org/$z/$x/$y.png"
        return try {
            val resp = http.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "Tork/1.0 Android GPS App")
                    .build()
            ).execute()
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            // Salva no disco pra uso offline futuro
            val file = tileFile(z, x, y)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { it.write(bytes) }
            bmp
        } catch (_: Exception) { null }
    }

    private fun tileFile(z: Int, x: Int, y: Int): File =
        File(cacheDir, "$z/$x/$y.png")

    // Limpa tiles com mais de 7 dias
    fun pruneOld() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        cacheDir.walkBottomUp().forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) f.delete()
        }
    }
}
