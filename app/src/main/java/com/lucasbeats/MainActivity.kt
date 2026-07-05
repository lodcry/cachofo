package com.lucasbeats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lucasbeats.map.MapView
import com.lucasbeats.map.TileCache
import com.lucasbeats.overlay.OverlayManager

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS   = "tork_prefs"
        const val REQ_PERMS   = 100
        const val REQ_OVERLAY = 101
    }

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var mapView: MapView
    private lateinit var tileCache: TileCache
    private val overlay by lazy { OverlayManager(this) }

    private val COLORS = listOf(
        "#4eff9a","#ff6b6b","#ffd93d","#6bcbff",
        "#ff9f43","#a29bfe","#fd79a8","#55efc4"
    )
    private var selectedColor = COLORS[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se já tem perfil salvo, vai direto pra tela principal
        val savedPhone = prefs.getString("phone", null)
        val savedName  = prefs.getString("name", null)
        if (savedPhone != null && savedName != null) {
            buildMainScreen(savedPhone, savedName, prefs.getInt("color", Color.parseColor(COLORS[0])))
        } else {
            buildJoinScreen()
        }
    }

    // ── Tela de cadastro ──────────────────────────────────────────────────────

    private fun buildJoinScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), dp(32))
            setBackgroundColor(Color.parseColor("#0a1a0f"))
        }

        root.addView(TextView(this).apply {
            text = "🌿"; textSize = 48f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Tork"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#4eff9a")); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "GPS · Chat · Offline"; textSize = 12f
            setTextColor(Color.parseColor("#6b9e7e")); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(24))
        })

        // Campo de nome
        val etName = EditText(this).apply {
            hint = "Seu nome"; textSize = 16f
            setTextColor(Color.parseColor("#d8f5e5"))
            setHintTextColor(Color.parseColor("#6b9e7e"))
            setBackgroundColor(Color.parseColor("#102d1a"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(etName, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12)) })

        // Campo de telefone
        val etPhone = EditText(this).apply {
            hint = "Seu número (ex: +5511999999999)"; textSize = 14f
            setTextColor(Color.parseColor("#d8f5e5"))
            setHintTextColor(Color.parseColor("#6b9e7e"))
            setBackgroundColor(Color.parseColor("#102d1a"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        root.addView(etPhone, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(16)) })

        // Seletor de cor
        root.addView(TextView(this).apply {
            text = "Sua cor no mapa"; textSize = 12f
            setTextColor(Color.parseColor("#6b9e7e"))
            setPadding(0, 0, 0, dp(8))
        })
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(20))
        }
        COLORS.forEach { hex ->
            val btn = Button(this).apply {
                stateListAnimator = null
                setBackgroundColor(Color.parseColor(hex))
                if (hex == selectedColor) setPadding(dp(3), dp(3), dp(3), dp(3))
            }
            btn.setOnClickListener {
                selectedColor = hex
                colorRow.children().forEach { (it as? Button)?.setPadding(0, 0, 0, 0) }
                btn.setPadding(dp(3), dp(3), dp(3), dp(3))
            }
            colorRow.addView(btn, lp(dp(36), dp(36)).apply { setMargins(0, 0, dp(8), 0) })
        }
        root.addView(colorRow)

        // Botão entrar
        val btnJoin = Button(this).apply {
            text = "Entrar →"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            setBackgroundColor(Color.parseColor("#1a6b3c"))
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        btnJoin.setOnClickListener {
            val name  = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Preencha nome e telefone", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val color = Color.parseColor(selectedColor)
            prefs.edit().putString("name", name).putString("phone", phone).putInt("color", color).apply()
            buildMainScreen(phone, name, color)
        }
        root.addView(btnJoin, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        requestAllPermissions()
    }

    // ── Tela principal ────────────────────────────────────────────────────────

    private fun buildMainScreen(phone: String, name: String, color: Int) {
        tileCache = TileCache(this)
        mapView   = MapView(this, tileCache)

        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0a1a0f"))
        }

        // Mapa ocupa tudo
        root.addView(mapView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Botão centralizar
        val btnCenter = Button(this).apply {
            text = "📍"; textSize = 16f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#CC0d2518"))
            setTextColor(Color.parseColor("#4eff9a"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        btnCenter.setOnClickListener { mapView.centerOnMe() }
        root.addView(btnCenter, android.widget.FrameLayout.LayoutParams(dp(48), dp(44)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = dp(24); rightMargin = dp(16)
        })

        // Botão overlay
        val btnOverlay = Button(this).apply {
            text = "🫧"; textSize = 16f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#CC0d2518"))
            setTextColor(Color.parseColor("#6bcbff"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        btnOverlay.setOnClickListener { launchOverlay(phone, name, color) }
        root.addView(btnOverlay, android.widget.FrameLayout.LayoutParams(dp(48), dp(44)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = dp(80); rightMargin = dp(16)
        })

        // Botão adicionar contato
        val btnAdd = Button(this).apply {
            text = "+👤"; textSize = 13f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#CC0d2518"))
            setTextColor(Color.parseColor("#ffd93d"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        btnAdd.setOnClickListener { showAddContactDialog() }
        root.addView(btnAdd, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, dp(44)
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; bottomMargin = dp(24); leftMargin = dp(16) })

        setContentView(root)
        startTorkService(phone, name, color)
        requestAllPermissions()
    }

    private fun launchOverlay(phone: String, name: String, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
            return
        }
        val svc = TorkService.instance ?: return
        overlay.show { text -> svc.sendChat(text) }
        svc.onMemberUpdate = { list ->
            mapView.setMembers(list)
            overlay.updateMembers(list)
        }
        svc.onChatMessage = { msg -> overlay.addMessage(msg) }
        Toast.makeText(this, "Overlay ativo — pode minimizar o app", Toast.LENGTH_SHORT).show()
    }

    private fun showAddContactDialog() {
        val et = EditText(this).apply {
            hint = "+5511999999999"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Adicionar contato ao grupo")
            .setView(et)
            .setPositiveButton("Adicionar") { _, _ ->
                val phone = et.text.toString().trim()
                if (phone.isNotEmpty()) {
                    TorkService.instance?.mesh?.addContact(phone)
                    Toast.makeText(this, "Contato adicionado: $phone", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startTorkService(phone: String, name: String, color: Int) {
        val i = Intent(this, TorkService::class.java).apply {
            putExtra("phone", phone)
            putExtra("name",  name)
            putExtra("color", color)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)

        // Liga callbacks quando o serviço subir
        Handler(Looper.getMainLooper()).postDelayed({
            TorkService.instance?.let { svc ->
                svc.onLocationUpdate = { lat, lng, _ -> mapView.setMyLocation(lat, lng) }
                svc.onMemberUpdate   = { list -> mapView.setMembers(list) }
                svc.onChatMessage    = { _ -> /* apenas overlay mostra */ }
            }
        }, 800)
    }

    // ── Permissões ────────────────────────────────────────────────────────────

    private fun requestAllPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        // Permissão de overlay é separada
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_OVERLAY) {
            val phone = prefs.getString("phone", null) ?: return
            val name  = prefs.getString("name", null)  ?: return
            val color = prefs.getInt("color", Color.parseColor(COLORS[0]))
            launchOverlay(phone, name, color)
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun LinearLayout.children() = (0 until childCount).map { getChildAt(it) }
}
