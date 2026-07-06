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
import com.lucasbeats.store.Storage

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS       = "tork_prefs"
        const val REQ_PERMS   = 100
        const val REQ_OVERLAY = 101
    }

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var mapView: MapView
    private lateinit var tileCache: TileCache
    private val overlay by lazy { OverlayManager(this) }

    // As 3 "telas" internas — trocamos a visibilidade delas em vez de usar Fragments,
    // seguindo o padrão 100% manual/Kotlin do projeto (sem XML, sem navigation component).
    private var mapScreen:    View? = null
    private var chatScreen:   View? = null
    private var satsScreen:   View? = null
    private var llChatMsgs:   LinearLayout? = null
    private var chatScroll:   ScrollView? = null
    private var llSatellites: LinearLayout? = null
    private val tabButtons = mutableListOf<Button>()

    private val COLORS = listOf(
        "#4eff9a","#ff6b6b","#ffd93d","#6bcbff",
        "#ff9f43","#a29bfe","#fd79a8","#55efc4"
    )
    private var selectedColor = COLORS[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val etName = EditText(this).apply {
            hint = "Seu nome"; textSize = 16f
            setTextColor(Color.parseColor("#d8f5e5"))
            setHintTextColor(Color.parseColor("#6b9e7e"))
            setBackgroundColor(Color.parseColor("#102d1a"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(etName, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12)) })

        val etPhone = EditText(this).apply {
            hint = "Seu número (ex: +5511999999999)"; textSize = 14f
            setTextColor(Color.parseColor("#d8f5e5"))
            setHintTextColor(Color.parseColor("#6b9e7e"))
            setBackgroundColor(Color.parseColor("#102d1a"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        root.addView(etPhone, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(16)) })

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

    // ── Tela principal — Mapa / Chat / Satélites com nav inferior ─────────────
    private fun buildMainScreen(phone: String, name: String, color: Int) {
        tileCache = TileCache(this)
        mapView   = MapView(this, tileCache)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0a1a0f"))
        }
        val contentArea = FrameLayout(this)

        mapScreen  = buildMapScreen(phone, name, color)
        chatScreen = buildChatScreenView()
        satsScreen = buildSatellitesScreenView()
        mapScreen?.visibility  = View.VISIBLE
        chatScreen?.visibility = View.GONE
        satsScreen?.visibility = View.GONE

        contentArea.addView(mapScreen,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentArea.addView(chatScreen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentArea.addView(satsScreen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        outer.addView(contentArea, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        outer.addView(buildBottomNav(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)))

        setContentView(outer)
        startTorkService(phone, name, color)
        requestAllPermissions()
    }

    private fun buildMapScreen(phone: String, name: String, color: Int): View {
        val container = FrameLayout(this)
        container.addView(mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val btnCenter = Button(this).apply {
            text = "📍"; textSize = 16f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#CC0d2518")); setTextColor(Color.parseColor("#4eff9a"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        btnCenter.setOnClickListener { mapView.centerOnMe() }
        container.addView(btnCenter, FrameLayout.LayoutParams(dp(48), dp(44)).apply {
            gravity = Gravity.BOTTOM or Gravity.END; bottomMargin = dp(16); rightMargin = dp(16)
        })

        val btnOverlay = Button(this).apply {
            text = "🫧"; textSize = 16f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#CC0d2518")); setTextColor(Color.parseColor("#6bcbff"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        btnOverlay.setOnClickListener { launchOverlay(phone, name, color) }
        container.addView(btnOverlay, FrameLayout.LayoutParams(dp(48), dp(44)).apply {
            gravity = Gravity.BOTTOM or Gravity.START; bottomMargin = dp(16); leftMargin = dp(16)
        })

        return container
    }

    private fun buildChatScreenView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0a1a0f"))
        }
        root.addView(TextView(this).apply {
            text = "🌿 Chat do grupo"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#4eff9a"))
            setPadding(dp(16), dp(14), dp(16), dp(10))
        })

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(4)) }
        scroll.addView(ll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        llChatMsgs = ll; chatScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0d2518"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val et = EditText(this).apply {
            hint = "Mensagem para o grupo..."; textSize = 14f
            setTextColor(Color.parseColor("#d8f5e5")); setHintTextColor(Color.parseColor("#6b9e7e"))
            setBackgroundColor(Color.parseColor("#102d1a")); setPadding(dp(12), dp(10), dp(12), dp(10))
            maxLines = 3
        }
        val btnSend = Button(this).apply {
            text = "➤"; textSize = 14f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#1a6b3c")); setTextColor(Color.WHITE)
            setPadding(dp(14), 0, dp(14), 0)
        }
        btnSend.setOnClickListener {
            val txt = et.text.toString().trim()
            if (txt.isNotEmpty()) {
                val svc = TorkService.instance
                if (svc != null) { svc.sendChat(txt); et.setText("") }
                else Toast.makeText(this, "Serviço ainda iniciando, aguarde...", Toast.LENGTH_SHORT).show()
            }
        }
        inputRow.addView(et, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(8), 0) })
        inputRow.addView(btnSend, LinearLayout.LayoutParams(dp(50), dp(44)))
        root.addView(inputRow)

        return root
    }

    private fun buildSatellitesScreenView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0a1a0f"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "👥 Satélites do grupo"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#6bcbff"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btnAdd = Button(this).apply {
            text = "+ Contato"; textSize = 11f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#1a6b3c")); setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
        }
        btnAdd.setOnClickListener { showAddContactDialog() }
        header.addView(btnAdd, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)))
        root.addView(header)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(12)) }
        scroll.addView(ll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        llSatellites = ll
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0d2518"))
        }
        val labels = listOf("🗺 Mapa" to 0, "💬 Chat" to 1, "👥 Satélites" to 2)
        labels.forEach { (label, idx) ->
            val btn = Button(this).apply {
                text = label; textSize = 11f; stateListAnimator = null
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(if (idx == 0) Color.parseColor("#4eff9a") else Color.parseColor("#6b9e7e"))
            }
            btn.setOnClickListener { showTab(idx) }
            tabButtons.add(btn)
            nav.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        return nav
    }

    private fun showTab(idx: Int) {
        mapScreen?.visibility  = if (idx == 0) View.VISIBLE else View.GONE
        chatScreen?.visibility = if (idx == 1) View.VISIBLE else View.GONE
        satsScreen?.visibility = if (idx == 2) View.VISIBLE else View.GONE
        tabButtons.forEachIndexed { i, b ->
            b.setTextColor(if (i == idx) Color.parseColor("#4eff9a") else Color.parseColor("#6b9e7e"))
        }
        if (idx == 2) refreshSatellites()
    }

    private fun appendChatMessage(msg: ChatMessage) {
        val ll = llChatMsgs ?: return
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        row.addView(TextView(this).apply {
            text = msg.name; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(msg.color)
        })
        row.addView(TextView(this).apply {
            text = msg.text; textSize = 13f; setTextColor(Color.parseColor("#d8f5e5"))
        })
        ll.addView(row)
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun loadChatHistoryInitial(list: List<ChatMessage>) {
        llChatMsgs?.removeAllViews()
        list.forEach { appendChatMessage(it) }
    }

    private fun refreshSatellites() {
        val svc = TorkService.instance ?: return
        val ll  = llSatellites ?: return
        ll.removeAllViews()
        val members  = svc.mesh.members
        val contacts = svc.mesh.contacts
        if (contacts.isEmpty()) {
            ll.addView(TextView(this).apply {
                text = "Nenhum contato adicionado ainda.\nToque em \"+ Contato\" para começar."
                textSize = 12f; setTextColor(Color.parseColor("#6b9e7e")); gravity = Gravity.CENTER
                setPadding(0, dp(30), 0, dp(30))
            })
            return
        }
        contacts.forEach { phone ->
            val m      = members[phone]
            val name   = m?.name ?: (Storage.loadContactName(this, phone) ?: phone)
            val online = m != null && (System.currentTimeMillis() - m.lastSeen) < 60_000
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#102d1a"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val dot = View(this).apply {
                setBackgroundColor(if (online) Color.parseColor("#4eff9a") else Color.parseColor("#555555"))
            }
            row.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { setMargins(0, 0, dp(10), 0) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply {
                text = name; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#d8f5e5"))
            })
            col.addView(TextView(this).apply {
                text = when {
                    m == null -> "Sem sinal ainda — número: $phone"
                    online    -> "📍 Online agora"
                    else      -> "Offline há ${(System.currentTimeMillis() - m.lastSeen) / 60000}min"
                }
                textSize = 10f; setTextColor(Color.parseColor("#6b9e7e"))
            })
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val btnRemove = Button(this).apply {
                text = "✕"; textSize = 11f; stateListAnimator = null
                setBackgroundColor(Color.parseColor("#3a1015")); setTextColor(Color.parseColor("#ff6b6b"))
                setPadding(dp(10), 0, dp(10), 0)
            }
            btnRemove.setOnClickListener { svc.mesh.removeContact(phone); refreshSatellites() }
            row.addView(btnRemove, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)))
            ll.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(8)) })
        }
    }

    private fun launchOverlay(phone: String, name: String, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
            return
        }
        val svc = TorkService.instance ?: run {
            Toast.makeText(this, "Serviço ainda iniciando, tente de novo em instantes", Toast.LENGTH_SHORT).show()
            return
        }
        overlay.show { text -> svc.sendChat(text) }
        overlay.loadHistory(svc.chatHistory)
        overlay.updateMembers(svc.mesh.members.values.toList())
        svc.onMemberUpdate = { list -> mapView.setMembers(list); refreshSatellites(); overlay.updateMembers(list) }
        svc.onChatMessage  = { msg -> appendChatMessage(msg); overlay.addMessage(msg) }
        Toast.makeText(this, "Overlay ativo — pode minimizar o app", Toast.LENGTH_SHORT).show()
    }

    private fun showAddContactDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(10)) }
        val etPhone = EditText(this).apply { hint = "+5511999999999"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        val etNick  = EditText(this).apply { hint = "Apelido (opcional)" }
        container.addView(etPhone); container.addView(etNick)
        android.app.AlertDialog.Builder(this)
            .setTitle("Adicionar contato ao grupo")
            .setView(container)
            .setPositiveButton("Adicionar") { _, _ ->
                val phone = etPhone.text.toString().trim()
                val nick  = etNick.text.toString().trim()
                if (phone.isNotEmpty()) {
                    TorkService.instance?.mesh?.addContact(phone, nick.ifEmpty { null })
                    refreshSatellites()
                    Toast.makeText(this, "Contato adicionado: $phone", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startTorkService(phone: String, name: String, color: Int) {
        val i = Intent(this, TorkService::class.java).apply {
            putExtra("phone", phone); putExtra("name", name); putExtra("color", color)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)

        Handler(Looper.getMainLooper()).postDelayed({
            TorkService.instance?.let { svc ->
                svc.onLocationUpdate = { lat, lng, _ -> mapView.setMyLocation(lat, lng) }
                svc.onMemberUpdate   = { list -> mapView.setMembers(list); refreshSatellites() }
                svc.onChatMessage    = { msg -> appendChatMessage(msg) }
                loadChatHistoryInitial(svc.chatHistory)
                mapView.setMembers(svc.mesh.members.values.toList())
                refreshSatellites()
            }
        }, 800)
    }

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

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun LinearLayout.children() = (0 until childCount).map { getChildAt(it) }
}
