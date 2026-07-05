package com.lucasbeats.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

// Wrapper simples sobre LocationManager — sem dependência de Play Services
class GpsManager(private val ctx: Context) {

    fun interface Listener { fun onLocation(lat: Double, lng: Double, acc: Float) }

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: Listener? = null
    private var lastLat = 0.0; private var lastLng = 0.0

    private val locListener = LocationListener { loc -> onRaw(loc) }

    @SuppressLint("MissingPermission")
    fun start(l: Listener) {
        listener = l
        // Tenta GPS primeiro, cai em network se não disponível
        val hasGps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val prov   = if (hasGps) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        lm.requestLocationUpdates(prov, 3000L, 2f, locListener, Looper.getMainLooper())
        // Última localização conhecida como ponto de partida imediato
        lm.getLastKnownLocation(prov)?.let { onRaw(it) }
    }

    fun stop() {
        try { lm.removeUpdates(locListener) } catch (_: Exception) {}
        listener = null
    }

    private fun onRaw(loc: Location) {
        // Filtro anti-teleporte: ignora saltos > 500m em < 10s
        if (lastLat != 0.0) {
            val res = FloatArray(1)
            Location.distanceBetween(lastLat, lastLng, loc.latitude, loc.longitude, res)
            if (res[0] > 500f) return
        }
        lastLat = loc.latitude; lastLng = loc.longitude
        listener?.onLocation(loc.latitude, loc.longitude, loc.accuracy)
    }
}
