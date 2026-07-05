package com.lucasbeats.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.madout2.tork.Member
import kotlin.math.*

// MapView 100% nativo — Canvas + tiles OSM cacheados
// Coordenadas → pixels via projeção Web Mercator (igual OSM/Leaflet)
@SuppressLint("ViewConstructor")
class MapView(ctx: Context, private val tileCache: TileCache) : View(ctx) {

    private val handler = Handler(Looper.getMainLooper())
    private val tileSize = 256

    // Estado do mapa
    private var zoom = 16
    private var centerLat = 0.0
    private var centerLng = 0.0
    private var initialized = false

    // Tiles carregados em memória (z/x/y → Bitmap)
    private val tiles = LinkedHashMap<String, Bitmap?>(64, 0.75f, true)

    // Membros do grupo pra renderizar
    private var members: List<Member> = emptyList()

    // Minha posição
    private var myLat = 0.0; private var myLng = 0.0

    // Paint
    private val tilePaint   = Paint()
    private val trailPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val memberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(4f,1f,1f,Color.BLACK) }
    private val bgPaint     = Paint().apply { color = Color.parseColor("#0a1a0f") }

    // Touch — arrastar mapa
    private var lastTouchX = 0f; private var lastTouchY = 0f
    private var offsetX = 0f;    private var offsetY = 0f

    // ── Projeção Web Mercator ─────────────────────────────────────────────────

    private fun lngToTileX(lng: Double, z: Int): Double {
        return (lng + 180.0) / 360.0 * (1 shl z)
    }

    private fun latToTileY(lat: Double, z: Int): Double {
        val rad = Math.toRadians(lat)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * (1 shl z)
    }

    // Converte lat/lng pra pixel relativo ao centro do canvas
    private fun latLngToPixel(lat: Double, lng: Double): PointF {
        val cx = lngToTileX(centerLng, zoom) * tileSize
        val cy = latToTileY(centerLat, zoom) * tileSize
        val px = lngToTileX(lng, zoom) * tileSize
        val py = latToTileY(lat, zoom) * tileSize
        return PointF(
            width / 2f + (px - cx).toFloat() + offsetX,
            height / 2f + (py - cy).toFloat() + offsetY
        )
    }

    // ── Tiles ─────────────────────────────────────────────────────────────────

    private fun loadVisibleTiles() {
        val cx = lngToTileX(centerLng, zoom)
        val cy = latToTileY(centerLat, zoom)
        val tilesW = ceil(width.toDouble() / tileSize / 2).toInt() + 2
        val tilesH = ceil(height.toDouble() / tileSize / 2).toInt() + 2
        val maxTile = (1 shl zoom) - 1

        for (dx in -tilesW..tilesW) {
            for (dy in -tilesH..tilesH) {
                val tx = (cx.toInt() + dx).coerceIn(0, maxTile)
                val ty = (cy.toInt() + dy).coerceIn(0, maxTile)
                val key = "$zoom/$tx/$ty"
                if (tiles.containsKey(key)) continue
                tiles[key] = null // placeholder
                tileCache.getTile(zoom, tx, ty) { bmp ->
                    tiles[key] = bmp
                    handler.post { invalidate() }
                }
            }
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────

    fun setMyLocation(lat: Double, lng: Double) {
        myLat = lat; myLng = lng
        if (!initialized) {
            centerLat = lat; centerLng = lng; initialized = true; offsetX = 0f; offsetY = 0f
        }
        loadVisibleTiles()
        invalidate()
    }

    fun setMembers(list: List<Member>) {
        members = list
        invalidate()
    }

    fun centerOnMe() {
        centerLat = myLat; centerLng = myLng; offsetX = 0f; offsetY = 0f
        loadVisibleTiles(); invalidate()
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawTiles(canvas)
        members.forEach { drawTrail(canvas, it) }
        members.forEach { drawMember(canvas, it) }
        if (myLat != 0.0) drawMe(canvas)
    }

    private fun drawTiles(canvas: Canvas) {
        if (!initialized) return
        val cx = lngToTileX(centerLng, zoom) * tileSize
        val cy = latToTileY(centerLat, zoom) * tileSize
        val maxTile = (1 shl zoom) - 1
        val tilesW = ceil(width.toDouble() / tileSize / 2).toInt() + 2
        val tilesH = ceil(height.toDouble() / tileSize / 2).toInt() + 2
        val baseTX = lngToTileX(centerLng, zoom).toInt()
        val baseTY = latToTileY(centerLat, zoom).toInt()

        for (dx in -tilesW..tilesW) {
            for (dy in -tilesH..tilesH) {
                val tx = (baseTX + dx).coerceIn(0, maxTile)
                val ty = (baseTY + dy).coerceIn(0, maxTile)
                val key = "$zoom/$tx/$ty"
                val bmp = tiles[key] ?: continue
                val px = width / 2f + (tx * tileSize - cx).toFloat() + offsetX
                val py = height / 2f + (ty * tileSize - cy).toFloat() + offsetY
                canvas.drawBitmap(bmp, px, py, tilePaint)
            }
        }
    }

    private fun drawTrail(canvas: Canvas, m: Member) {
        if (m.trail.size < 2) return
        val path = Path()
        m.trail.forEachIndexed { i, pt ->
            val p = latLngToPixel(pt.lat, pt.lng)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        trailPaint.color = m.color and 0x66FFFFFF.toInt() or (m.color and 0xFF000000.toInt())
        trailPaint.strokeWidth = 6f; canvas.drawPath(path, trailPaint)
        trailPaint.color = m.color; trailPaint.strokeWidth = 3f; canvas.drawPath(path, trailPaint)
    }

    private fun drawMember(canvas: Canvas, m: Member) {
        if (m.lat == 0.0 && m.lng == 0.0) return
        val p = latLngToPixel(m.lat, m.lng)
        memberPaint.color = m.color
        canvas.drawCircle(p.x, p.y, 18f, memberPaint)
        memberPaint.color = Color.WHITE; memberPaint.style = Paint.Style.STROKE; memberPaint.strokeWidth = 3f
        canvas.drawCircle(p.x, p.y, 18f, memberPaint)
        memberPaint.style = Paint.Style.FILL
        // Nome
        textPaint.textSize = 24f
        canvas.drawText(m.name.take(10), p.x + 22f, p.y + 8f, textPaint)
    }

    private fun drawMe(canvas: Canvas) {
        val p = latLngToPixel(myLat, myLng)
        // Pulso externo
        memberPaint.color = Color.parseColor("#334eff9a")
        canvas.drawCircle(p.x, p.y, 30f, memberPaint)
        memberPaint.color = Color.parseColor("#4eff9a")
        canvas.drawCircle(p.x, p.y, 18f, memberPaint)
        memberPaint.color = Color.WHITE; memberPaint.style = Paint.Style.STROKE; memberPaint.strokeWidth = 4f
        canvas.drawCircle(p.x, p.y, 18f, memberPaint)
        memberPaint.style = Paint.Style.FILL
        textPaint.textSize = 24f
        canvas.drawText("Você", p.x + 22f, p.y + 8f, textPaint)
    }

    // ── Touch — arrastar + pinch zoom ─────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { lastTouchX = event.x; lastTouchY = event.y }
            MotionEvent.ACTION_MOVE -> {
                offsetX += event.x - lastTouchX
                offsetY += event.y - lastTouchY
                lastTouchX = event.x; lastTouchY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> loadVisibleTiles()
        }
        return true
    }
}
