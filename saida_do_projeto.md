---
### Início do arquivo: ./app/build.gradle
```
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.madout2.overlay'
    compileSdk 34

    defaultConfig {
        applicationId "com.madout2.overlay"
        minSdk 26
        targetSdk 34
        versionCode 4
        versionName "4.0"
    }

    buildTypes {
        debug   { minifyEnabled false; debuggable true }
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    buildFeatures { viewBinding true }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = '17' }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.webkit:webkit:1.8.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation ('io.socket:socket.io-client:2.1.0') {
        exclude group: 'org.json', module: 'json'
    }
}
```
### Fim do arquivo: ./app/build.gradle
---
### Início do arquivo: ./app/src/main/AndroidManifest.xml
```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:label="ChatVivo"
        android:theme="@style/Theme.Overlay"
        android:usesCleartextTraffic="true"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="chatvivo" android:host="overlay" />
            </intent-filter>
        </activity>

        <activity
            android:name=".WebViewActivity"
            android:exported="false"
            android:launchMode="singleTop"
            android:configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize"
            android:windowSoftInputMode="adjustResize" />

        <activity
            android:name=".LogcatActivity"
            android:exported="false"
            android:launchMode="singleTop"
            android:configChanges="orientation|screenSize|keyboardHidden|screenLayout" />

        <service
            android:name=".OverlayService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

    </application>
</manifest>
```
### Fim do arquivo: ./app/src/main/AndroidManifest.xml
---
### Início do arquivo: ./app/src/main/java/com/madout2/overlay/DebugLog.kt
```
package com.madout2.overlay

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.*

object DebugLog {

    private const val MAX = 800
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val entries = ArrayDeque<Entry>()
    private val listeners = mutableListOf<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())

    enum class Tag { WEBVIEW, SOCKET, QRR, MIC, ERRO, SYS }

    data class Entry(val time: String, val tag: Tag, val msg: String)

    @Synchronized
    fun log(tag: Tag, msg: String) {
        android.util.Log.d("ChatVivo/${tag.name}", msg)
        entries.addLast(Entry(fmt.format(Date()), tag, msg))
        if (entries.size > MAX) entries.removeFirst()
        handler.post { listeners.forEach { it() } }
    }

    fun w(tag: Tag, msg: String) = log(tag, "⚠ $msg")
    fun e(tag: Tag, msg: String) = log(tag, "✕ $msg")

    @Synchronized
    fun getAll(): List<Entry> = entries.toList()

    @Synchronized
    fun getFiltered(tag: Tag?): List<Entry> =
        if (tag == null) entries.toList()
        else entries.filter { it.tag == tag }

    @Synchronized
    fun clear() { entries.clear(); handler.post { listeners.forEach { it() } } }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun dump(): String = getAll().joinToString("\n") { "[${it.time}][${it.tag}] ${it.msg}" }
}
```
### Fim do arquivo: ./app/src/main/java/com/madout2/overlay/DebugLog.kt
---
### Início do arquivo: ./app/src/main/java/com/madout2/overlay/LogcatActivity.kt
```
package com.madout2.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class LogcatActivity : AppCompatActivity() {

    private lateinit var llLogs: LinearLayout
    private lateinit var scroll: ScrollView
    private var activeFilter: DebugLog.Tag? = null
    private val handler = Handler(Looper.getMainLooper())
    private val listener: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0a0a0f.toInt())
        }

        // ── Header ────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF111118.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val tvTitle = TextView(this).apply {
            text = "📋 LOGCAT"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF00e87a.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClear = Button(this).apply {
            text = "🗑"; textSize = 12f; stateListAnimator = null
            setBackgroundColor(0xFF1a1a26.toInt()); setTextColor(0xFF5a5a70.toInt())
            setPadding(dp(8), 0, dp(8), 0)
        }
        btnClear.setOnClickListener { DebugLog.clear() }

        val btnCopy = Button(this).apply {
            text = "📋 COPIAR"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            setBackgroundColor(0xFF00e87a.toInt()); setTextColor(0xFF000000.toInt())
            setPadding(dp(10), 0, dp(10), 0)
        }
        btnCopy.setOnClickListener {
            val clip = DebugLog.dump()
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("logcat", clip))
            Toast.makeText(this, "✅ Copiado (${DebugLog.getAll().size} linhas)", Toast.LENGTH_SHORT).show()
        }

        header.addView(tvTitle)
        header.addView(btnClear, LinearLayout.LayoutParams(dp(40), dp(36)).apply { setMargins(0,0,dp(8),0) })
        header.addView(btnCopy, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)))

        // ── Filtros ───────────────────────────────────────────
        val filterScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(0xFF111118.toInt())
            isHorizontalScrollBarEnabled = false
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        val filters = listOf(null to "TODOS") +
            DebugLog.Tag.values().map { it to it.name }

        val chipViews = mutableListOf<Button>()
        filters.forEach { (tag, label) ->
            val chip = Button(this).apply {
                text = label; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                stateListAnimator = null
                setPadding(dp(10), 0, dp(10), 0)
                setOnClickListener {
                    activeFilter = tag
                    chipViews.forEach { b ->
                        b.setBackgroundColor(0xFF1a1a26.toInt())
                        b.setTextColor(0xFF5a5a70.toInt())
                    }
                    setBackgroundColor(0xFF00e87a.toInt())
                    setTextColor(0xFF000000.toInt())
                    refresh()
                }
            }
            if (tag == null) {
                chip.setBackgroundColor(0xFF00e87a.toInt())
                chip.setTextColor(0xFF000000.toInt())
            } else {
                chip.setBackgroundColor(0xFF1a1a26.toInt())
                chip.setTextColor(0xFF5a5a70.toInt())
            }
            chipViews.add(chip)
            filterRow.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)
            ).apply { setMargins(0, 0, dp(6), 0) })
        }
        filterScroll.addView(filterRow)

        // ── Lista de logs ─────────────────────────────────────
        scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0a0a0f.toInt())
        }
        llLogs = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        scroll.addView(llLogs)

        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(filterScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        DebugLog.addListener(listener)
        refresh()
    }

    private fun tagColor(tag: DebugLog.Tag): Int = when (tag) {
        DebugLog.Tag.WEBVIEW -> 0xFF5b8cff.toInt()
        DebugLog.Tag.SOCKET  -> 0xFF00e87a.toInt()
        DebugLog.Tag.QRR     -> 0xFFffb800.toInt()
        DebugLog.Tag.MIC     -> 0xFFff8800.toInt()
        DebugLog.Tag.ERRO    -> 0xFFff3355.toInt()
        DebugLog.Tag.SYS     -> 0xFF9090a0.toInt()
    }

    private fun refresh() {
        val entries = if (activeFilter == null)
            DebugLog.getAll() else DebugLog.getFiltered(activeFilter)
        llLogs.removeAllViews()
        entries.forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
            }
            val tvTime = TextView(this).apply {
                text = e.time; textSize = 9f
                setTextColor(0xFF5a5a70.toInt())
                setPadding(0, 0, dp(6), 0)
                typeface = Typeface.MONOSPACE
            }
            val tvTag = TextView(this).apply {
                text = e.tag.name; textSize = 9f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(tagColor(e.tag))
                setPadding(0, 0, dp(6), 0)
            }
            val tvMsg = TextView(this).apply {
                text = e.msg; textSize = 11f
                setTextColor(0xFFe8e8f0.toInt())
                typeface = Typeface.MONOSPACE
            }
            row.addView(tvTime)
            row.addView(tvTag)
            row.addView(tvMsg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            llLogs.addView(row)
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLog.removeListener(listener)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
```
### Fim do arquivo: ./app/src/main/java/com/madout2/overlay/LogcatActivity.kt
---
### Início do arquivo: ./app/src/main/java/com/madout2/overlay/MainActivity.kt
```
package com.madout2.overlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME  = "madout2_overlay"
        const val KEY_SERVER  = "server_url"
        const val KEY_TOKEN   = "auth_token"
        const val KEY_NICK    = "game_nick"
        const val DEFAULT_URL = "https://chatvivomad.onrender.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(DebugLog.Tag.SYS, "MainActivity — iniciando WebViewActivity direto")
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data

        // Veio do browser via chatvivo://overlay?token=...
        if (data != null && data.scheme == "chatvivo") {
            val token  = data.getQueryParameter("token")
            val server = data.getQueryParameter("server")
            val nick   = data.getQueryParameter("nick")
            if (!token.isNullOrEmpty()) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
                    putString(KEY_TOKEN, token)
                    putString(KEY_SERVER, if (!server.isNullOrEmpty()) server else DEFAULT_URL)
                    if (!nick.isNullOrEmpty()) putString(KEY_NICK, nick)
                }.apply()
                DebugLog.log(DebugLog.Tag.SYS, "Token recebido via intent — salvando")
            }
        }

        // Sempre vai direto pra WebView — o login fica dentro do PWA
        startActivity(Intent(this, WebViewActivity::class.java))
        finish()
    }
}
```
### Fim do arquivo: ./app/src/main/java/com/madout2/overlay/MainActivity.kt
---
### Início do arquivo: ./app/src/main/java/com/madout2/overlay/OverlayService.kt
```
package com.madout2.overlay

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.util.Base64
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.core.app.NotificationCompat
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URISyntaxException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : Service() {

    companion object {
        var isRunning     = false
        const val CHANNEL_ID      = "radinho_overlay"
        const val NOTIF_ID        = 1
        const val MAX_REC_SECONDS = 60
        const val MAX_CHAT_MSGS   = 60
        const val CHUNK_MS        = 2000L
        const val IMAGE_CACHE_MAX = 30
    }

    private val winType: Int by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    private var wm: WindowManager? = null

    private var pillView:   View?        = null
    private var chatView:   View?        = null
    private var historyView: View?       = null
    private var mapContainer: FrameLayout? = null
    private var mapWebView: WebView?     = null

    private var pillP: WindowManager.LayoutParams? = null
    private var chatP: WindowManager.LayoutParams? = null
    private var historyP: WindowManager.LayoutParams? = null
    private var mapP:  WindowManager.LayoutParams? = null

    private var llChat:   LinearLayout? = null
    private var llHistory: LinearLayout? = null
    private var etMsg:    EditText?     = null
    private var tvTalk:   TextView?     = null
    private var tvCount:  TextView?     = null
    private var btnPTT:   Button?       = null
    private var btnMap:   Button?       = null
    private var tvFilter: TextView?     = null

    private var socket: Socket? = null
    private val handler = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var sessionId: String? = null
    private var myUid = ""; private var myName = "você"; private var myRole = "membro"

    private var recorder: MediaRecorder? = null
    private var recordFile: File?        = null
    private var isRecording  = false;  private var recSec = 0
    private var recRunnable: Runnable? = null
    private var uploading    = false;  private var pendingChunks = 0
    private var hbRunnable:  Runnable? = null

    private val audioQueue  = ArrayDeque<JSONObject>()
    private var playingAudio = false
    private var mediaPlayer: MediaPlayer? = null

    private val FILTERS = listOf(0,5,10,20)
    private var filterIdx = 0
    private var chatFontSize = 11f
    private var chatExpanded = false
    private var historyExpanded = false
    private var mapExpanded  = false
    private var mapFloating  = false

    // Dedupe defensivo: mesmo com a correção no servidor (que agora emite uma única vez
    // para a room certa), mantemos um cache simples dos últimos IDs de mensagem recebidos
    // para nunca exibir a mesma mensagem duas vezes na pílula, cobrindo qualquer cenário
    // de reconexão/replay futuro.
    private val seenMsgIds = LinkedHashSet<String>()
    private fun isDuplicate(id: String): Boolean {
        if (id.isEmpty()) return false
        if (seenMsgIds.contains(id)) return true
        seenMsgIds.add(id)
        if (seenMsgIds.size > 200) seenMsgIds.remove(seenMsgIds.first())
        return false
    }

    private val chatColors = mutableMapOf(
        "adm" to 0xFF9c27b0.toInt(), "lider" to 0xFFffd700.toInt(),
        "sublider" to 0xFF4fc3f7.toInt(), "membro" to 0xFFe8e8f0.toInt(), "own" to 0xFF5b8cff.toInt()
    )

    data class ChatMsg(
        val id: String, val sender: String, val text: String, val role: String = "membro",
        val type: String = "text", val mediaUrl: String = "",
        val duration: Int = 0, val cargo: String = "",
        val avatarUrl: String = "", val flagUrl: String = ""
    )
    private val chatMessages = ArrayDeque<ChatMsg>()

    data class SessionInfo(
        val sessionId: String, val startedAt: String, val endedAt: String?,
        val startedBy: String, val audioCount: Int, val active: Boolean, val participantCount: Int
    )
    private var sessions = listOf<SessionInfo>()
    private var loadingSessions = false

    private var clanTfNormal: Typeface? = null
    private var clanTfBold:   Typeface? = null

    private val imageCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap>?) = size > IMAGE_CACHE_MAX
    }

    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private val dm get() = resources.displayMetrics

    override fun onCreate() {
        super.onCreate(); isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotif("Conectando..."))
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        connectSocket()
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onDestroy() {
        isRunning = false
        try { sessionId?.let { socket?.emit("qrr:overlay_status", JSONObject().apply { put("sessionId", it); put("active", false) }) } } catch (_: Exception) {}
        stopRecInternal(false); stopHb()
        handler.removeCallbacksAndMessages(null)
        try { socket?.off(); socket?.io()?.off(); socket?.disconnect() } catch (_: Exception) {}
        socket = null
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null; audioQueue.clear()
        handler.post { closeMap(); rmv(historyView); rmv(chatView); rmv(pillView); historyView=null; chatView = null; pillView = null }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        } catch (_: Exception) {}
        super.onDestroy()
    }
    private fun rmv(v: View?) { try { if (v != null) wm?.removeView(v) } catch (_: Exception) {} }
    override fun onBind(i: Intent?) = null

    private fun decUid(token: String): String {
        return try {
            var p = token.split(".")[1].replace('-','+').replace('_','/')
            while (p.length % 4 != 0) p += "="
            val j = JSONObject(String(Base64.decode(p, Base64.DEFAULT), Charset.forName("UTF-8")))
            j.optString("id","").ifEmpty { j.optString("login","") }
        } catch (_: Exception) { "" }
    }

    private fun decRole(token: String): String {
        return try {
            var p = token.split(".")[1].replace('-','+').replace('_','/')
            while (p.length % 4 != 0) p += "="
            val j = JSONObject(String(Base64.decode(p, Base64.DEFAULT), Charset.forName("UTF-8")))
            j.optString("role","membro")
        } catch (_: Exception) { "membro" }
    }

    private fun downloadFont(url: String) {
        if (url.isEmpty()) return
        Thread {
            try {
                val b = http.newCall(Request.Builder().url(url).build()).execute().body?.bytes() ?: return@Thread
                val f = File(cacheDir, "clan_font.ttf"); f.writeBytes(b)
                val tf = Typeface.createFromFile(f)
                clanTfNormal = tf; clanTfBold = Typeface.create(tf, Typeface.BOLD)
                handler.post { refreshChat() }
            } catch (_: Exception) {}
        }.start()
    }

    private fun connectSocket() {
        val prefs  = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val server = prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_URL) ?: MainActivity.DEFAULT_URL
        val token  = prefs.getString(MainActivity.KEY_TOKEN, null)
        myName     = prefs.getString(MainActivity.KEY_NICK, "você") ?: "você"
        if (token == null) { updateNotif("❌ Sem token"); return }
        myUid  = decUid(token)
        myRole = decRole(token)
        try {
            val opts = IO.Options.builder().setReconnection(true).setReconnectionAttempts(Int.MAX_VALUE)
                .setReconnectionDelay(3000).setAuth(mapOf("token" to token)).build()
            socket = IO.socket(server, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                updateNotif("🟢 Conectado"); socket?.emit("qrr:join")
            }
            socket?.on(Socket.EVENT_CONNECT_ERROR) { updateNotif("🔴 Reconectando...") }
            socket?.on(Socket.EVENT_DISCONNECT)    { updateNotif("🔴 Reconectando...") }

            socket?.on("clan:config") { args ->
                val d = args.getOrNull(0) as? JSONObject ?: return@on
                d.optJSONObject("chatColors")?.let { cc ->
                    listOf("adm","lider","sublider","membro","own").forEach { k ->
                        val h = cc.optString(k,"")
                        if (h.isNotEmpty()) try { chatColors[k] = Color.parseColor(h) } catch (_: Exception) {}
                    }
                }
                val fu = d.optJSONObject("chatFont")?.optString("url","") ?: ""
                if (fu.isNotEmpty()) downloadFont(fu)
                handler.post { refreshChat() }
            }

            socket?.on("qrr:joined") { args ->
                val d    = args.getOrNull(0) as? JSONObject ?: return@on
                val nSid = d.optString("sessionId").takeIf { it.isNotEmpty() }
                val isNew = nSid != sessionId; sessionId = nSid
                if (isNew) {
                    chatMessages.clear(); seenMsgIds.clear(); handler.post { refreshChat() }
                    sessionId?.let { sid ->
                        // Não entra mais na room "chat:qrr" — o "qrr:join" já coloca este
                        // socket na room "qrr:${sid}", que é a única usada pelo servidor
                        // para tudo relacionado a essa sessão (chat, áudio, talking). Entrar
                        // também em "chat:qrr" fazia o servidor mandar cada mensagem duas
                        // vezes (uma por room), causando a duplicação visível na pílula.
                        socket?.emit("chat:history", JSONObject().apply { put("channel","qrr"); put("sessionId",sid); put("limit",MAX_CHAT_MSGS) })
                        socket?.emit("qrr:overlay_status", JSONObject().apply { put("sessionId", sid); put("active", true) })
                    }
                }
                handler.post { updateNotif("🎙 QRR"); showPill(); updatePTT(); btnMap?.visibility = View.VISIBLE }
                startHb()
            }

            socket?.on("qrr:participants") { args ->
                val n = (args.getOrNull(0) as? JSONArray)?.length() ?: 0
                handler.post { tvCount?.text = if (n > 0) "${n}👥" else "" }
            }

            socket?.on("qrr:talking") { args ->
                val d = args.getOrNull(0) as? JSONObject ?: return@on
                val t = d.optBoolean("talking",false); val n = d.optString("name",""); val c = d.optString("cargo","")
                handler.post { if (!playingAudio) tvTalk?.text = if (t) { if (c.isNotEmpty()) "🗣 [$c] $n" else "🗣 $n" } else "" }
            }

            socket?.on("qrr:audio") { args ->
                val d = args.getOrNull(0) as? JSONObject ?: return@on
                val sid = d.optString("senderId","")
                if (sid.isNotEmpty() && sid == myUid) return@on
                audioQueue.addLast(d); handler.post { if (!playingAudio) playNext() }
            }

            socket?.on("qrr:session_ended") {
                sessionId = null; stopHb()
                handler.post { tvTalk?.text=""; tvCount?.text=""; btnMap?.visibility=View.GONE; closeMap() }
                handler.postDelayed({ socket?.emit("qrr:join") }, 2000)
            }

            socket?.on("chat:history") { args ->
                val payload = args.getOrNull(0)
                val arr: JSONArray? = when (payload) {
                    is JSONArray  -> payload
                    is JSONObject -> {
                        val ch  = payload.optString("channel","")
                        val sid = payload.optString("sessionId","")
                        if (ch.isNotEmpty() && ch != "qrr") return@on
                        if (sid.isNotEmpty() && sid != (sessionId ?: "")) return@on
                        payload.optJSONArray("messages")
                    }
                    else -> null
                } ?: return@on
                chatMessages.clear(); seenMsgIds.clear()
                val len = arr.length()
                for (i in 0 until len) {
                    val obj = arr.getJSONObject(i)
                    val mid = obj.optString("_id", "")
                    if (isDuplicate(mid)) continue
                    chatMessages.addLast(parseMsg(obj))
                    if (chatMessages.size > MAX_CHAT_MSGS) chatMessages.removeFirst()
                }
                handler.post { refreshChat() }
            }

            socket?.on("chat:message") { args ->
                val m   = args.getOrNull(0) as? JSONObject ?: return@on
                val ch  = m.optString("channel","")
                val sid = m.optString("sessionId","")
                if (ch != "qrr" && sid != (sessionId ?: "")) return@on
                val mid = m.optString("_id", "")
                if (isDuplicate(mid)) return@on
                chatMessages.addLast(parseMsg(m))
                if (chatMessages.size > MAX_CHAT_MSGS) chatMessages.removeFirst()
                handler.post { refreshChat() }
            }

            socket?.connect()
        } catch (e: URISyntaxException) { DebugLog.e(DebugLog.Tag.SOCKET,"URI: ${e.message}") }
    }

    private fun parseMsg(m: JSONObject): ChatMsg {
        val type = m.optString("type","text")
        return ChatMsg(
            id        = m.optString("_id",""),
            sender    = m.optString("senderName","?"),
            text      = when(type) { "audio"->"🎙 ${m.optInt("duration")}s"; "image"->"🖼"; "video"->"🎬"; else->m.optString("content","") },
            role      = m.optString("senderRole","membro"),
            type      = type,
            mediaUrl  = m.optString("mediaUrl",""),
            duration  = m.optInt("duration",0),
            cargo     = m.optString("senderCargo",""),
            avatarUrl = m.optString("senderAvatarUrl",""),
            flagUrl   = m.optString("senderFlagUrl","")
        )
    }

    private fun sendText(t: String) {
        if (t.isBlank() || socket == null || sessionId == null) return
        socket?.emit("chat:message", JSONObject().apply { put("channel","qrr"); put("sessionId",sessionId); put("content",t.trim()); put("type","text") })
    }

    private fun startHb() {
        stopHb()
        val r = object : Runnable { override fun run() {
            sessionId?.let { socket?.emit("qrr:heartbeat", JSONObject().apply { put("sessionId",it) }) }
            handler.postDelayed(this, 30_000)
        }}
        hbRunnable = r; handler.postDelayed(r, 30_000)
    }
    private fun stopHb() { hbRunnable?.let { handler.removeCallbacks(it) }; hbRunnable = null }

    private fun startRec() {
        if (isRecording || sessionId == null || uploading) return
        try {
            val file = File(cacheDir,"qrr_${System.currentTimeMillis()}.m4a")
            recordFile = file
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000); setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath); prepare(); start()
            }
            isRecording = true; recSec = 0; vibrate(50)
            sessionId?.let { socket?.emit("qrr:talking", JSONObject().apply { put("sessionId",it); put("talking",true) }) }
            updatePTT()
            val r = object : Runnable { override fun run() {
                if (!isRecording) return
                recSec++; handler.post { btnPTT?.text = "⏹ ${recSec}s" }
                if (recSec >= MAX_REC_SECONDS) { stopRecInternal(true); return }
                flushChunk(); handler.postDelayed(this, CHUNK_MS)
            }}
            recRunnable = r; handler.postDelayed(r, CHUNK_MS)
        } catch (e: Exception) { DebugLog.e(DebugLog.Tag.MIC,"startRec: ${e.message}"); isRecording=false; recorder?.release(); recorder=null }
    }

    private fun flushChunk() {
        val file = recordFile ?: return; val sid = sessionId ?: return
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        if (file.exists() && file.length() > 500) { pendingChunks++; setUpl(true); uploadAsync(file,sid,(CHUNK_MS/1000).toInt()) }
        else file.delete()
        if (!isRecording) return
        try {
            val nf = File(cacheDir,"qrr_${System.currentTimeMillis()}.m4a"); recordFile = nf
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioEncodingBitRate(64_000); setAudioSamplingRate(44_100)
                setOutputFile(nf.absolutePath); prepare(); start()
            }
        } catch (e: Exception) { DebugLog.e(DebugLog.Tag.MIC,"flushChunk: ${e.message}"); isRecording = false }
    }

    private fun stopRecInternal(send: Boolean) {
        if (!isRecording) return; isRecording = false
        recRunnable?.let { handler.removeCallbacks(it) }; recRunnable = null
        val dur = recSec; recSec = 0
        val file = recordFile; recordFile = null; val sid = sessionId
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        sessionId?.let { socket?.emit("qrr:talking", JSONObject().apply { put("sessionId",it); put("talking",false) }) }
        vibrate(30); updatePTT()
        if (send && file != null && file.exists() && file.length() > 500 && sid != null) { pendingChunks++; setUpl(true); uploadAsync(file,sid,dur) }
        else file?.delete()
    }

    private fun setUpl(v: Boolean) { uploading = v; handler.post { updatePTT() } }

    private fun uploadAsync(file: File, sid: String, dur: Int) {
        val prefs  = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val server = prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_URL) ?: MainActivity.DEFAULT_URL
        val token  = prefs.getString(MainActivity.KEY_TOKEN, null)
        if (token == null) { finChunk(file); return }
        http.newCall(Request.Builder().url("$server/api/qrr/upload-signature?folder=qrr/audio").header("Authorization","Bearer $token").build())
            .enqueue(object : Callback {
                override fun onFailure(c: Call, e: java.io.IOException) { finChunk(file) }
                override fun onResponse(c: Call, r: Response) {
                    val body = r.body?.string(); if (!r.isSuccessful||body==null) { finChunk(file); return }
                    try {
                        val sig = JSONObject(body)
                        val ub  = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("file",file.name,file.asRequestBody("audio/mp4".toMediaType()))
                            .addFormDataPart("api_key",sig.getString("apiKey"))
                            .addFormDataPart("timestamp",sig.getString("timestamp"))
                            .addFormDataPart("signature",sig.getString("signature"))
                            .addFormDataPart("folder",sig.getString("folder")).build()
                        http.newCall(Request.Builder().url("https://api.cloudinary.com/v1_1/${sig.getString("cloudName")}/auto/upload").post(ub).build())
                            .enqueue(object : Callback {
                                override fun onFailure(c: Call, e: java.io.IOException) { finChunk(file) }
                                override fun onResponse(c: Call, r: Response) {
                                    try {
                                        val d=JSONObject(r.body?.string()?:"{}"); val url=d.optString("secure_url","")
                                        if (url.isNotEmpty()) socket?.emit("qrr:audio", JSONObject().apply {
                                            put("sessionId",sid); put("url",url); put("duration",d.optDouble("duration",dur.toDouble())); put("mimeType","audio/mp4")
                                        })
                                    } catch (_: Exception) {}
                                    finChunk(file)
                                }
                            })
                    } catch (_: Exception) { finChunk(file) }
                }
            })
    }
    private fun finChunk(f: File) { f.delete(); pendingChunks=(pendingChunks-1).coerceAtLeast(0); if(pendingChunks==0) setUpl(false) }

    private fun playNext() {
        if (audioQueue.isEmpty()) { playingAudio=false; handler.post { tvTalk?.text="" }; return }
        val next=audioQueue.removeFirst(); val url=next.optString("url","")
        val name=next.optString("senderName",""); val cargo=next.optString("cargo","")
        if (url.isEmpty()) { playNext(); return }
        playingAudio=true; handler.post { tvTalk?.text=if(cargo.isNotEmpty())"🔊 [$cargo] $name" else "🔊 $name" }
        try {
            mediaPlayer?.release()
            val mp=MediaPlayer(); mp.setDataSource(url)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { it.release(); mediaPlayer=null; handler.post { playNext() } }
            mp.setOnErrorListener { p,_,_ -> p.release(); mediaPlayer=null; handler.post { playNext() }; true }
            mediaPlayer=mp; mp.prepareAsync()
        } catch (_: Exception) { handler.post { playNext() } }
    }

    private fun loadBmp(url: String, cb: (Bitmap?) -> Unit) {
        if (url.isEmpty()) { cb(null); return }
        synchronized(imageCache) { imageCache[url] }?.let { cb(it); return }
        Thread {
            try {
                val bytes=http.newCall(Request.Builder().url(url).build()).execute().body?.bytes()
                if (bytes==null) { handler.post { cb(null) }; return@Thread }
                val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts)
                var ss=1; while(opts.outWidth/ss>dp(24)*2) ss*=2
                val bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=ss })
                if(bmp!=null) synchronized(imageCache){imageCache[url]=bmp}
                handler.post { cb(bmp) }
            } catch (_: Exception) { handler.post { cb(null) } }
        }.start()
    }

    private fun cropCircle(src: Bitmap, size: Int): Bitmap {
        val out=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val cv=Canvas(out); val p=Paint(Paint.ANTI_ALIAS_FLAG); val r=size/2f
        cv.drawCircle(r,r,r,p); p.xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        cv.drawBitmap(Bitmap.createScaledBitmap(src,size,size,true),0f,0f,p); return out
    }

    private fun showPill() {
        if (pillView != null) return
        val pill = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4),dp(3),dp(4),dp(3))
        }

        tvTalk = TextView(this).apply {
            text=""; textSize=9.5f; maxLines=1; setTextColor(0xFFffb800.toInt()); typeface=Typeface.DEFAULT_BOLD
            setShadowLayer(4f,1f,1f,Color.BLACK)
        }
        tvCount = TextView(this).apply {
            text=""; textSize=9f; setTextColor(0xFF5a5a70.toInt()); setShadowLayer(3f,1f,1f,Color.BLACK)
        }

        val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(0,dp(2),0,0) }

        btnPTT = Button(this).apply {
            text="🎙"; textSize=11f; typeface=Typeface.DEFAULT_BOLD; stateListAnimator=null; setPadding(dp(3),0,dp(3),0)
        }
        updatePTT()
        btnPTT?.setOnTouchListener { _,ev ->
            when(ev.action) {
                MotionEvent.ACTION_DOWN   -> { startRec(); true }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> { stopRecInternal(true); true }
                else -> false
            }
        }

        val btnChat = Button(this).apply {
            text="💬"; textSize=12f; stateListAnimator=null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF00e87a.toInt()); setPadding(dp(2),0,dp(2),0)
        }
        btnChat.setOnClickListener { toggleChat() }

        btnMap = Button(this).apply {
            text="🗺"; textSize=12f; stateListAnimator=null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF4fc3f7.toInt()); setPadding(dp(2),0,dp(2),0); visibility=View.GONE
        }
        btnMap?.setOnClickListener { toggleMap() }

        // O botão de logcat/debug (📋) saiu da pílula do overlay — ali agora mora o
        // Histórico de Sessões, que é uma informação útil pra qualquer membro durante o
        // jogo. O logcat de depuração continua existindo, mas só dentro da tela principal
        // do app (WebViewActivity), e só visível pra quem está logado como ADM — não faz
        // sentido expor logs técnicos de debug pra todo mundo no overlay flutuante.
        val btnHistory = Button(this).apply {
            text="📋"; textSize=12f; stateListAnimator=null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFFffb800.toInt()); setPadding(dp(2),0,dp(2),0)
        }
        btnHistory.setOnClickListener { toggleHistory() }

        val btnClose = Button(this).apply {
            text="✕"; textSize=10f; stateListAnimator=null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFFff3355.toInt()); setPadding(dp(2),0,dp(2),0)
        }
        btnClose.setOnClickListener { socket?.emit("qrr:leave"); stopSelf() }

        row.addView(btnPTT,     lp(0,dp(30),1.3f).apply{setMargins(0,0,dp(1),0)})
        row.addView(btnChat,    lp(dp(26),dp(30)).apply{setMargins(0,0,dp(1),0)})
        row.addView(btnMap,     lp(dp(26),dp(30)).apply{setMargins(0,0,dp(1),0)})
        row.addView(btnHistory, lp(dp(26),dp(30)).apply{setMargins(0,0,dp(1),0)})
        row.addView(btnClose,   lp(dp(20),dp(30)))

        pill.addView(tvTalk,  lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        pill.addView(tvCount, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        pill.addView(row,     lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val pp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            winType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.END; x=prefs.getInt("pill_x",dp(12)); y=prefs.getInt("pill_y",dp(80)) }
        pillP = pp

        var ix=0; var iy=0; var tx=0f; var ty=0f; var mv=false
        pill.setOnTouchListener { _,ev ->
            when(ev.action) {
                MotionEvent.ACTION_DOWN  -> { ix=pp.x; iy=pp.y; tx=ev.rawX; ty=ev.rawY; mv=false; true }
                MotionEvent.ACTION_MOVE  -> { val dx=(ev.rawX-tx).toInt(); val dy=(ev.rawY-ty).toInt(); if(dx*dx+dy*dy>25){ mv=true; pp.x=ix-dx; pp.y=iy+dy; try{wm?.updateViewLayout(pill,pp)}catch(_:Exception){} }; true }
                MotionEvent.ACTION_UP    -> { if(mv) prefs.edit().putInt("pill_x",pp.x).putInt("pill_y",pp.y).apply(); true }
                else -> false
            }
        }

        pillView = pill
        try { wm?.addView(pill, pp) } catch (_: Exception) { return }
        buildChatView()
        buildHistoryView()
        refreshChat()
    }

    private fun buildChatView() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xBB0a0a0f.toInt())
            visibility = View.GONE
        }

        val dragH = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; setBackgroundColor(0xFF1a1a26.toInt())
            gravity=Gravity.CENTER; setPadding(dp(6),0,dp(6),0)
        }
        val dragLbl = TextView(this).apply {
            text="QRR CHAT"; textSize=8.5f; setTextColor(0xFF00e87a.toInt()); typeface=Typeface.DEFAULT_BOLD
            gravity=Gravity.CENTER
        }
        val btnFontDown = Button(this).apply { text="A−"; textSize=7f; stateListAnimator=null; setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF5a5a70.toInt()); setPadding(dp(3),0,dp(3),0) }
        btnFontDown.setOnClickListener { chatFontSize=(chatFontSize-0.5f).coerceAtLeast(7f); refreshChat() }
        val btnFontUp = Button(this).apply { text="A+"; textSize=7f; stateListAnimator=null; setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF5a5a70.toInt()); setPadding(dp(3),0,dp(3),0) }
        btnFontUp.setOnClickListener { chatFontSize=(chatFontSize+0.5f).coerceAtMost(18f); refreshChat() }
        val btnFilter2 = Button(this).apply { text="🔄"; textSize=9f; stateListAnimator=null; setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF5a5a70.toInt()); setPadding(dp(3),0,dp(3),0) }
        tvFilter = TextView(this).apply { text=filterLabel(); textSize=8f; setTextColor(0xFF5a5a70.toInt()); setPadding(0,0,dp(3),0) }
        btnFilter2.setOnClickListener { filterIdx=(filterIdx+1)%FILTERS.size; tvFilter?.text=filterLabel(); refreshChat() }
        val btnClose2 = Button(this).apply { text="✕"; textSize=9f; stateListAnimator=null; setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF5a5a70.toInt()); setPadding(dp(3),0,dp(3),0) }
        btnClose2.setOnClickListener { toggleChat() }

        dragH.addView(btnFontDown, lp(dp(24),dp(24)).apply{setMargins(0,0,dp(2),0)})
        dragH.addView(btnFontUp,   lp(dp(24),dp(24)).apply{setMargins(0,0,dp(4),0)})
        dragH.addView(dragLbl,     lp(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        dragH.addView(tvFilter,    lp(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT))
        dragH.addView(btnFilter2,  lp(dp(24),dp(24)).apply{setMargins(0,0,dp(2),0)})
        dragH.addView(btnClose2,   lp(dp(20),dp(24)))

        var dSY=0f; var dPY=0
        dragH.setOnTouchListener { _,ev ->
            val p=chatP?:return@setOnTouchListener false
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> { dSY=ev.rawY; dPY=p.y; true }
                MotionEvent.ACTION_MOVE -> { p.y=(dPY+(ev.rawY-dSY).toInt()).coerceIn(0,dm.heightPixels-200); try{wm?.updateViewLayout(chatView,p)}catch(_:Exception){}; true }
                else -> false
            }
        }

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled=false }
        llChat = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(6),dp(2),dp(6),dp(2)) }
        scroll.addView(llChat, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val inputRow = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF111118.toInt()); setPadding(dp(5),dp(3),dp(5),dp(3))
        }
        etMsg = EditText(this).apply {
            hint="msg..."; textSize=11f
            setTextColor(0xFFe8e8f0.toInt()); setHintTextColor(0xFF5a5a70.toInt())
            setBackgroundColor(0xFF1a1a26.toInt()); setPadding(dp(6),dp(4),dp(6),dp(4))
            maxLines=1; imeOptions=EditorInfo.IME_ACTION_SEND
            inputType=android.text.InputType.TYPE_CLASS_TEXT
        }
        etMsg?.setOnEditorActionListener { v,id,_ ->
            if (id==EditorInfo.IME_ACTION_SEND) { sendText(v.text.toString()); v.setText(""); true } else false
        }
        val btnSend = Button(this).apply {
            text="➤"; textSize=11f; stateListAnimator=null
            setBackgroundColor(0xFFb71c1c.toInt()); setTextColor(0xFFffffff.toInt()); setPadding(dp(6),0,dp(6),0)
        }
        btnSend.setOnClickListener { sendText(etMsg?.text?.toString() ?: ""); etMsg?.setText("") }
        inputRow.addView(etMsg,   lp(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{setMargins(0,0,dp(3),0)})
        inputRow.addView(btnSend, lp(LinearLayout.LayoutParams.WRAP_CONTENT,dp(28)))

        val resH = View(this).apply { setBackgroundColor(0xFF252535.toInt()) }
        var rSY=0f; var rSH=0
        resH.setOnTouchListener { _,ev ->
            val p=chatP?:return@setOnTouchListener false
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> { rSY=ev.rawY; rSH=p.height; true }
                MotionEvent.ACTION_MOVE -> { p.height=(rSH+(ev.rawY-rSY).toInt()).coerceIn(dp(60),dp(500)); try{wm?.updateViewLayout(chatView,p)}catch(_:Exception){}; true }
                MotionEvent.ACTION_UP   -> { saveChatLayout(); true }
                else -> false
            }
        }

        panel.addView(dragH,    lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(26)))
        panel.addView(scroll,   lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(inputRow, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(resH,     lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)))

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val cp = WindowManager.LayoutParams(
            prefs.getInt("chat_w",dp(260)).coerceIn(dp(160),dp(420)),
            prefs.getInt("chat_h",dp(250)).coerceIn(dp(60),dp(500)),
            winType, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.END; x=dp(10); y=prefs.getInt("chat_y",dp(200)) }
        chatP    = cp
        chatView = panel
        try { wm?.addView(panel, cp) } catch (e: Exception) { DebugLog.e(DebugLog.Tag.SYS,"addChat: ${e.message}") }
    }

    // ── HISTÓRICO DE SESSÕES (substitui o antigo botão de logcat na pílula) ──────────
    private fun buildHistoryView() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0a0a0f.toInt())
            visibility = View.GONE
        }

        val dragH = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; setBackgroundColor(0xFF1a1a26.toInt())
            gravity=Gravity.CENTER; setPadding(dp(8),0,dp(8),0)
        }
        val title = TextView(this).apply {
            text="📋 HISTÓRICO DE SESSÕES"; textSize=8.5f; setTextColor(0xFFffb800.toInt()); typeface=Typeface.DEFAULT_BOLD
        }
        val btnReload = Button(this).apply { text="🔄"; textSize=9f; stateListAnimator=null; setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF5a5a70.toInt()); setPadding(dp(3),0,dp(3),0) }
        btnReload.setOnClickListener { loadSessions() }
        val btnCloseH = Button(this).apply { text="✕"; textSize=9f; stateListAnimator=null; setBackgroundColor(Color.TRANSPARENT); setTextColor(0xFF5a5a70.toInt()); setPadding(dp(3),0,dp(3),0) }
        btnCloseH.setOnClickListener { toggleHistory() }

        dragH.addView(title,      lp(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        dragH.addView(btnReload,  lp(dp(24),dp(24)).apply{setMargins(0,0,dp(2),0)})
        dragH.addView(btnCloseH,  lp(dp(20),dp(24)))

        var dSY=0f; var dPY=0
        dragH.setOnTouchListener { _,ev ->
            val p=historyP?:return@setOnTouchListener false
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> { dSY=ev.rawY; dPY=p.y; true }
                MotionEvent.ACTION_MOVE -> { p.y=(dPY+(ev.rawY-dSY).toInt()).coerceIn(0,dm.heightPixels-200); try{wm?.updateViewLayout(historyView,p)}catch(_:Exception){}; true }
                else -> false
            }
        }

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled=false }
        llHistory = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(6),dp(8),dp(6)) }
        scroll.addView(llHistory, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val resH = View(this).apply { setBackgroundColor(0xFF252535.toInt()) }
        var rSY=0f; var rSH=0
        resH.setOnTouchListener { _,ev ->
            val p=historyP?:return@setOnTouchListener false
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> { rSY=ev.rawY; rSH=p.height; true }
                MotionEvent.ACTION_MOVE -> { p.height=(rSH+(ev.rawY-rSY).toInt()).coerceIn(dp(120),dp(560)); try{wm?.updateViewLayout(historyView,p)}catch(_:Exception){}; true }
                MotionEvent.ACTION_UP   -> { saveHistoryLayout(); true }
                else -> false
            }
        }

        panel.addView(dragH,  lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(26)))
        panel.addView(scroll, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(resH,   lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)))

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val hp = WindowManager.LayoutParams(
            prefs.getInt("hist_w",dp(280)).coerceIn(dp(200),dp(420)),
            prefs.getInt("hist_h",dp(320)).coerceIn(dp(120),dp(560)),
            winType, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.END; x=dp(10); y=prefs.getInt("hist_y",dp(200)) }
        historyP    = hp
        historyView = panel
        try { wm?.addView(panel, hp) } catch (e: Exception) { DebugLog.e(DebugLog.Tag.SYS,"addHistory: ${e.message}") }
    }

    private fun saveHistoryLayout() {
        val p=historyP?:return
        getSharedPreferences(MainActivity.PREFS_NAME,Context.MODE_PRIVATE).edit()
            .putInt("hist_w",p.width).putInt("hist_h",p.height).putInt("hist_y",p.y).apply()
    }

    private fun toggleHistory() {
        historyExpanded=!historyExpanded; val panel=historyView?:return; val hp=historyP?:return
        if (historyExpanded) {
            panel.visibility=View.VISIBLE
            if (sessions.isEmpty() && !loadingSessions) loadSessions()
        } else {
            saveHistoryLayout(); panel.visibility=View.GONE
        }
        try { wm?.updateViewLayout(historyView,hp) } catch (_: Exception) {}
    }

    private fun loadSessions() {
        val prefs  = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val server = prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_URL) ?: MainActivity.DEFAULT_URL
        val token  = prefs.getString(MainActivity.KEY_TOKEN, null) ?: return
        loadingSessions = true; refreshHistory()
        http.newCall(Request.Builder().url("$server/api/qrr/sessions?limit=15").header("Authorization","Bearer $token").build())
            .enqueue(object : Callback {
                override fun onFailure(c: Call, e: java.io.IOException) {
                    loadingSessions = false; handler.post { refreshHistory() }
                }
                override fun onResponse(c: Call, r: Response) {
                    loadingSessions = false
                    try {
                        val body = r.body?.string() ?: "{}"
                        val obj  = JSONObject(body)
                        val arr  = obj.optJSONArray("sessions") ?: JSONArray()
                        val list = mutableListOf<SessionInfo>()
                        for (i in 0 until arr.length()) {
                            val s = arr.getJSONObject(i)
                            list.add(SessionInfo(
                                sessionId = s.optString("sessionId",""),
                                startedAt = s.optString("startedAt",""),
                                endedAt   = s.optString("endedAt", null),
                                startedBy = s.optString("startedBy","?"),
                                audioCount = s.optInt("audioCount",0),
                                active     = s.optBoolean("active", false),
                                participantCount = (s.optJSONArray("participants")?.length()) ?: 0,
                            ))
                        }
                        sessions = list
                    } catch (_: Exception) {}
                    handler.post { refreshHistory() }
                }
            })
    }

    private fun fmtDate(iso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(iso.substring(0, 19))
            dateFmt.timeZone = TimeZone.getDefault()
            dateFmt.format(date ?: Date())
        } catch (_: Exception) { iso.take(16) }
    }

    private fun refreshHistory() {
        val ll = llHistory ?: return; ll.removeAllViews()
        if (loadingSessions) {
            ll.addView(TextView(this).apply { text="Carregando..."; textSize=10f; setTextColor(0xFF5a5a70.toInt()); gravity=Gravity.CENTER; setPadding(0,dp(10),0,dp(10)) })
            return
        }
        if (sessions.isEmpty()) {
            ll.addView(TextView(this).apply { text="Nenhuma sessão ainda"; textSize=10f; setTextColor(0xFF5a5a70.toInt()); gravity=Gravity.CENTER; setPadding(0,dp(10),0,dp(10)) })
            return
        }
        sessions.forEach { s ->
            val card = LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setBackgroundColor(0xFF1a1a26.toInt())
                setPadding(dp(8),dp(6),dp(8),dp(6))
            }
            val topRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
            topRow.addView(TextView(this).apply {
                text = fmtDate(s.startedAt); textSize=10f; typeface=Typeface.DEFAULT_BOLD; setTextColor(0xFFe8e8f0.toInt())
            }, lp(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            topRow.addView(TextView(this).apply {
                text = if (s.active) "🟢 ativa" else "⚫ encerrada"
                textSize=9f; setTextColor(if (s.active) 0xFF00e87a.toInt() else 0xFF5a5a70.toInt())
            })
            card.addView(topRow)
            card.addView(TextView(this).apply {
                text = "🎙 ${s.audioCount} áudios · 👥 ${s.participantCount} · por ${s.startedBy}"
                textSize=9f; setTextColor(0xFF5a5a70.toInt()); setPadding(0,dp(2),0,0)
            })
            ll.addView(card, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,0,dp(6)) })
        }
    }

    private fun saveChatLayout() {
        val p=chatP?:return
        getSharedPreferences(MainActivity.PREFS_NAME,Context.MODE_PRIVATE).edit()
            .putInt("chat_w",p.width).putInt("chat_h",p.height).putInt("chat_y",p.y).apply()
    }

    private fun toggleChat() {
        chatExpanded=!chatExpanded; val panel=chatView?:return; val cp=chatP?:return
        if (chatExpanded) {
            panel.visibility=View.VISIBLE
            cp.flags=cp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            saveChatLayout(); panel.visibility=View.GONE
            cp.flags=cp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            etMsg?.let { val imm=getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager; imm.hideSoftInputFromWindow(it.windowToken,0) }
        }
        try { wm?.updateViewLayout(chatView,cp) } catch (_: Exception) {}
    }

    private fun refreshChat() {
        val ll=llChat?:return; ll.removeAllViews()
        val n=FILTERS[filterIdx]; val list=if(n==0) chatMessages.toList() else chatMessages.takeLast(n)
        if (list.isEmpty()) {
            ll.addView(TextView(this).apply { text="Sem mensagens"; textSize=9f; setTextColor(0xFF5a5a70.toInt()); gravity=Gravity.CENTER; setPadding(0,dp(6),0,dp(6)) })
            return
        }
        list.forEach { msg ->
            val col=chatColors[if(msg.sender==myName)"own" else msg.role]?:0xFFe8e8f0.toInt()
            val rowV=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(1),0,dp(1)) }

            val headRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,0,0,dp(1)) }
            val avSize=dp(18)
            val avView=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(col and 0x33FFFFFF.toInt()) }
            if (msg.avatarUrl.isNotEmpty()) loadBmp(msg.avatarUrl) { b -> if(b!=null) avView.setImageBitmap(cropCircle(b,avSize)) }
            headRow.addView(avView, lp(avSize,avSize).apply{setMargins(0,0,dp(3),0)})

            if (msg.flagUrl.isNotEmpty()) {
                val fSize=dp(12); val fv=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP}
                loadBmp(msg.flagUrl){b->if(b!=null)fv.setImageBitmap(cropCircle(b,fSize))}
                headRow.addView(fv, lp(fSize,fSize).apply{setMargins(0,0,dp(3),0)})
            }

            val tfBold=clanTfBold?:Typeface.DEFAULT_BOLD
            headRow.addView(TextView(this).apply {
                text=if(msg.cargo.isNotEmpty())"[${msg.cargo}] ${msg.sender}" else msg.sender
                textSize=(chatFontSize-1.5f).coerceAtLeast(7f); typeface=tfBold; setTextColor(col)
                setShadowLayer(3f,1f,1f,Color.BLACK)
            })
            rowV.addView(headRow)

            val tfNormal=clanTfNormal?:Typeface.DEFAULT
            when (msg.type) {
                "text"  -> rowV.addView(TextView(this).apply { text=msg.text; textSize=chatFontSize; typeface=tfNormal; setTextColor(0xFFccccdd.toInt()); maxLines=4; setShadowLayer(3f,1f,1f,Color.BLACK) })
                "image" -> {
                    val iv=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(0xFF1a1a26.toInt())}
                    rowV.addView(iv, lp(dp(80),dp(55))); if(msg.mediaUrl.isNotEmpty()) loadBmp(msg.mediaUrl){b->if(b!=null)iv.setImageBitmap(b)}
                }
                "audio" -> rowV.addView(TextView(this).apply { text="🎙 ${msg.duration}s"; textSize=chatFontSize; setTextColor(0xFF00e87a.toInt()); setShadowLayer(3f,1f,1f,Color.BLACK) })
                "video" -> rowV.addView(TextView(this).apply { text="🎬"; textSize=chatFontSize; setTextColor(0xFF5b8cff.toInt()); setShadowLayer(3f,1f,1f,Color.BLACK) })
            }
            ll.addView(rowV)
        }
        (llChat?.parent as? ScrollView)?.post { (llChat?.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun toggleMap() { if (mapExpanded) closeMap() else openMap() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openMap() {
        mapExpanded=true
        if (mapContainer!=null) { mapContainer?.visibility=View.VISIBLE; return }
        val sid=sessionId?:return
        val prefs=getSharedPreferences(MainActivity.PREFS_NAME,Context.MODE_PRIVATE)
        val token=prefs.getString(MainActivity.KEY_TOKEN,null)?:return
        val server=prefs.getString(MainActivity.KEY_SERVER,MainActivity.DEFAULT_URL)?:MainActivity.DEFAULT_URL
        val url="$server/map-overlay?token=${Uri.encode(token)}&session=${Uri.encode(sid)}"

        val container=FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }

        val wv=WebView(this)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.apply {
            javaScriptEnabled=true; domStorageEnabled=true; mediaPlaybackRequiresUserGesture=false
            mixedContentMode=WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString="$userAgentString ChatVivoApp/4.0"
        }
        wv.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface fun close() { handler.post { closeMap() } }
            @android.webkit.JavascriptInterface fun log(m:String) { DebugLog.log(DebugLog.Tag.SYS,"[Map] $m") }
        }, "MapBridge")
        wv.webViewClient=object:WebViewClient(){
            override fun onPageFinished(v:WebView,u:String) {
                v.evaluateJavascript("document.body.style.background='transparent'; document.documentElement.style.background='transparent';", null)
            }
        }
        wv.loadUrl(url)

        val btnClose=Button(this).apply { text="✕"; textSize=8f; stateListAnimator=null; setBackgroundColor(0xAAff3355.toInt()); setTextColor(Color.WHITE); setPadding(dp(5),dp(2),dp(5),dp(2)) }
        btnClose.setOnClickListener { closeMap() }

        val btnFloat=Button(this).apply { text="📌"; textSize=8f; stateListAnimator=null; setBackgroundColor(0xAA1a1a26.toInt()); setTextColor(0xFFffd700.toInt()); setPadding(dp(5),dp(2),dp(5),dp(2)) }

        val dragBar=View(this).apply { setBackgroundColor(0x661a1a26.toInt()) }

        container.addView(wv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(dragBar,   FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,dp(18)).apply{gravity=Gravity.TOP})
        container.addView(btnClose,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT).apply{gravity=Gravity.TOP or Gravity.END;topMargin=0;rightMargin=0})
        container.addView(btnFloat,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT).apply{gravity=Gravity.TOP or Gravity.START;topMargin=0;leftMargin=0})

        val sw=dm.widthPixels; val sh=dm.heightPixels
        // O React (MapOverlayPage.jsx) sempre mostra a toolbar de ferramentas (✏️🛣️📍⬡🧹 +
        // cores + espessuras) quando role é adm/lider/sublider — essa toolbar tem ~110-130dp
        // de altura real em telas pequenas mesmo com flex-wrap. O piso mínimo antigo de
        // dp(80) cortava boa parte dela. Agora o mínimo respeita quem pode desenhar:
        // observadores (membro comum) continuam podendo usar uma janela pequena, mas
        // ADM/líder/sub-líder começam com altura suficiente pra toolbar inteira aparecer.
        val canDraw = myRole == "adm" || myRole == "lider" || myRole == "sublider"
        val minMapH = if (canDraw) dp(190) else dp(80)
        val defaultH = if (canDraw) (sh / 2) else (sh / 3)

        mapFloating=prefs.getInt("map_w",-1)!=-1
        val initW=if(mapFloating) prefs.getInt("map_w",dp(220)).coerceIn(dp(140),sw) else WindowManager.LayoutParams.MATCH_PARENT
        val initH=prefs.getInt("map_h",defaultH).coerceIn(minMapH,sh-dp(80))
        val initX=prefs.getInt("map_x",0); val initY=prefs.getInt("map_y",0)

        val mp=WindowManager.LayoutParams(
            initW, initH, winType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSPARENT
        ).apply {
            gravity=if(mapFloating) Gravity.TOP or Gravity.START else Gravity.BOTTOM
            if(mapFloating){x=initX;y=initY} else {x=0;y=0}
        }
        mapP=mp

        btnFloat.setOnClickListener {
            mapFloating=!mapFloating
            mp.width=if(mapFloating) dp(220) else WindowManager.LayoutParams.MATCH_PARENT
            mp.height=if(mapFloating) minMapH.coerceAtLeast(dp(200)) else defaultH
            mp.gravity=if(mapFloating) Gravity.TOP or Gravity.START else Gravity.BOTTOM
            mp.x=if(mapFloating) dp(16) else 0; mp.y=if(mapFloating) dp(100) else 0
            btnFloat.text=if(mapFloating)"⬜" else "📌"
            try{wm?.updateViewLayout(container,mp)}catch(_:Exception){}
            prefs.edit().putInt("map_w",if(mapFloating)mp.width else -1).apply()
        }

        var dSX=0f;var dSY=0f;var dPX=0;var dPY=0;var dSH=0
        dragBar.setOnTouchListener{_,ev->
            when(ev.action){
                MotionEvent.ACTION_DOWN->{dSX=ev.rawX;dSY=ev.rawY;dPX=mp.x;dPY=mp.y;dSH=mp.height;true}
                MotionEvent.ACTION_MOVE->{
                    if(mapFloating){
                        mp.x=(dPX+(ev.rawX-dSX).toInt()).coerceAtLeast(0)
                        mp.y=(dPY+(ev.rawY-dSY).toInt()).coerceAtLeast(0)
                    } else {
                        mp.height=(dSH+(dSY-ev.rawY).toInt()).coerceIn(minMapH,sh-dp(80))
                    }
                    try{wm?.updateViewLayout(container,mp)}catch(_:Exception){};true
                }
                MotionEvent.ACTION_UP->{
                    prefs.edit().putInt("map_x",mp.x).putInt("map_y",mp.y).putInt("map_h",mp.height).apply();true
                }
                else->false
            }
        }

        val resizeCorner=View(this).apply { setBackgroundColor(0x661a1a26.toInt()) }
        var rcSX=0f;var rcSY=0f;var rcW=0;var rcH=0
        resizeCorner.setOnTouchListener{_,ev->
            when(ev.action){
                MotionEvent.ACTION_DOWN->{rcSX=ev.rawX;rcSY=ev.rawY;rcW=mp.width.coerceAtLeast(0);rcH=mp.height;true}
                MotionEvent.ACTION_MOVE->{
                    if(mapFloating){
                        mp.width=((rcW+(ev.rawX-rcSX).toInt())).coerceIn(dp(140),sw)
                        mp.height=((rcH+(ev.rawY-rcSY).toInt())).coerceIn(minMapH,sh-dp(80))
                        try{wm?.updateViewLayout(container,mp)}catch(_:Exception){}
                    }
                    true
                }
                MotionEvent.ACTION_UP->{prefs.edit().putInt("map_w",mp.width).putInt("map_h",mp.height).apply();true}
                else->false
            }
        }
        container.addView(resizeCorner, FrameLayout.LayoutParams(dp(24),dp(24)).apply{gravity=Gravity.BOTTOM or Gravity.END})

        mapContainer=container; mapWebView=wv
        try { wm?.addView(container,mp) } catch (e:Exception) { DebugLog.e(DebugLog.Tag.SYS,"addMap: ${e.message}"); mapContainer=null; mapWebView=null }
    }

    private fun closeMap() {
        mapExpanded=false
        try { if(mapContainer!=null){wm?.removeView(mapContainer);mapContainer=null;mapWebView=null;mapP=null} } catch(_:Exception){}
    }

    private fun updatePTT() {
        btnPTT?.apply {
            when { uploading->{text="📤";setBackgroundColor(0x885b8cff.toInt());setTextColor(Color.WHITE)} isRecording->{text="⏹${recSec}s";setBackgroundColor(0xAAff3355.toInt());setTextColor(Color.WHITE)} else->{text="🎙";setBackgroundColor(0xAA00e87a.toInt());setTextColor(Color.BLACK)} }
        }
    }

    private fun filterLabel(): String = if(FILTERS[filterIdx]==0) "∞" else "${FILTERS[filterIdx]}"
    private fun lp(w:Int,h:Int,wt:Float=0f)=LinearLayout.LayoutParams(w,h,wt)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun vibrate(ms:Long){try{val v=getSystemService(VIBRATOR_SERVICE) as?Vibrator?:return;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE))else @Suppress("DEPRECATION") v.vibrate(ms)}catch(_:Exception){}}
    private fun createNotificationChannel(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){val ch=NotificationChannel(CHANNEL_ID,"ChatVivo QRR",NotificationManager.IMPORTANCE_LOW).apply{description="QRR";setSound(null,null)};getSystemService(NotificationManager::class.java).createNotificationChannel(ch)}}
    private fun buildNotif(t:String):Notification{val pi=PendingIntent.getActivity(this,0,Intent(this,WebViewActivity::class.java),PendingIntent.FLAG_IMMUTABLE);return NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("ChatVivo QRR").setContentText(t).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi).setOngoing(true).setSilent(true).build()}
    private fun updateNotif(t:String){getSystemService(NotificationManager::class.java).notify(NOTIF_ID,buildNotif(t))}
}
```
### Fim do arquivo: ./app/src/main/java/com/madout2/overlay/OverlayService.kt
---
### Início do arquivo: ./app/src/main/java/com/madout2/overlay/WebViewActivity.kt
```
package com.madout2.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Base64

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var urlLoaded = false
    private var btnLog: Button? = null

    companion object {
        const val REQ_MIC     = 100
        const val REQ_OVERLAY = 2001
        const val REQ_NOTIF   = 2002
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun log(tag: String, msg: String) {
            val t = when (tag.uppercase()) {
                "SOCKET"       -> DebugLog.Tag.SOCKET
                "QRR"          -> DebugLog.Tag.QRR
                "MIC"          -> DebugLog.Tag.MIC
                "ERRO","ERROR" -> DebugLog.Tag.ERRO
                "WEBVIEW"      -> DebugLog.Tag.WEBVIEW
                else           -> DebugLog.Tag.SYS
            }
            DebugLog.log(t, "[JS] $msg")
        }

        @JavascriptInterface
        fun error(tag: String, msg: String) {
            val t = when(tag.uppercase()) {
                "SOCKET" -> DebugLog.Tag.SOCKET; "QRR" -> DebugLog.Tag.QRR
                "MIC"    -> DebugLog.Tag.MIC;    else  -> DebugLog.Tag.ERRO
            }
            DebugLog.e(t, "[JS] $msg")
        }

        @JavascriptInterface fun isMicGranted(): Boolean =
            ContextCompat.checkSelfPermission(this@WebViewActivity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        @JavascriptInterface fun isOverlayGranted(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this@WebViewActivity)

        @JavascriptInterface fun isOverlayRunning(): Boolean = OverlayService.isRunning

        @JavascriptInterface
        fun startOverlay(jsToken: String) {
            DebugLog.log(DebugLog.Tag.SYS, "startOverlay via bridge — já rodando: ${OverlayService.isRunning}")
            if (OverlayService.isRunning) return
            val token = jsToken.ifEmpty { null } ?: run { DebugLog.e(DebugLog.Tag.SYS,"token vazio"); return }
            getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit()
                .putString(MainActivity.KEY_TOKEN, token)
                .putString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_URL)
                .apply()
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@WebViewActivity)) {
                    startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                        REQ_OVERLAY
                    )
                } else startOverlayService()
            }
        }

        @JavascriptInterface
        fun stopOverlay() {
            DebugLog.log(DebugLog.Tag.SYS, "stopOverlay via bridge")
            runOnUiThread {
                stopService(Intent(this@WebViewActivity, OverlayService::class.java))
            }
        }
    }

    // Decodifica o "role" do JWT salvo, só pra decidir se mostra o botão de logcat na tela
    // principal. Isso não é validação de segurança (o backend já protege tudo que importa);
    // é só uma escolha de UI para não expor logs técnicos de debug a membros comuns.
    private fun decodeRole(token: String?): String {
        if (token == null) return "membro"
        return try {
            var p = token.split(".")[1].replace('-','+').replace('_','/')
            while (p.length % 4 != 0) p += "="
            val j = JSONObject(String(Base64.getDecoder().decode(p), Charsets.UTF_8))
            j.optString("role", "membro")
        } catch (_: Exception) { "membro" }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs     = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val serverUrl = prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_URL) ?: MainActivity.DEFAULT_URL
        val token     = prefs.getString(MainActivity.KEY_TOKEN, null)
        val isAdm     = decodeRole(token) == "adm"

        val root = android.widget.FrameLayout(this)
        webView = WebView(this)
        root.addView(webView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Botão de logcat/debug só aparece pra ADM — membros comuns não precisam (e não
        // devem) ver logs técnicos internos do app.
        if (isAdm) {
            btnLog = Button(this).apply {
                text = "📋"; textSize = 16f; stateListAnimator = null
                setBackgroundColor(0xCC1a1a26.toInt()); setTextColor(0xFFffb800.toInt())
                setPadding(dp(6), dp(4), dp(6), dp(4)); alpha = 0.85f
            }
            btnLog?.setOnClickListener { startActivity(Intent(this, LogcatActivity::class.java)) }
            root.addView(btnLog, android.widget.FrameLayout.LayoutParams(dp(48), dp(40)).apply {
                gravity = Gravity.TOP or Gravity.END; topMargin = dp(48); rightMargin = dp(8)
            })
        }

        setContentView(root)
        DebugLog.log(DebugLog.Tag.SYS, "WebViewActivity — server=$serverUrl")
        setupWebView(serverUrl, token)
        checkAndRequestPermissions(serverUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(serverUrl: String, token: String?) {
        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            databaseEnabled                  = true
            allowFileAccess                  = true
            allowContentAccess               = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode                 = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode                        = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls              = false
            displayZoomControls              = false
            useWideViewPort                  = true
            loadWithOverviewMode             = true
            userAgentString                  = "$userAgentString ChatVivoApp/4.0"
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidLog")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "intent" || uri.scheme == "chatvivo") { handleOverlayIntent(uri, token); return true }
                val host = uri.host ?: ""; val serverHost = Uri.parse(serverUrl).host ?: ""
                if ((uri.scheme=="http"||uri.scheme=="https") && host != serverHost) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri)); return true
                }
                return false
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (token != null) {
                    val safe = token.replace("\\","\\\\").replace("'","\\'")
                    view.evaluateJavascript("(function(){ localStorage.setItem('cv_token','$safe'); })()", null)
                }
                view.evaluateJavascript("""
                    (function(){
                        window.onerror=function(m,s,l){ AndroidLog&&AndroidLog.error('ERRO',m+' ('+s+':'+l+')'); };
                        window.addEventListener('unhandledrejection',function(e){ AndroidLog&&AndroidLog.error('ERRO','Promise: '+(e.reason?.message||e.reason||e)); });
                        var _e=console.error; console.error=function(){ AndroidLog&&AndroidLog.error('ERRO','[console.error] '+Array.from(arguments).join(' ')); _e.apply(console,arguments); };
                    })();
                """.trimIndent(), null)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    DebugLog.e(DebugLog.Tag.WEBVIEW, "Erro ${error.errorCode}: ${error.description} — ${request.url}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (OverlayService.isRunning) { DebugLog.w(DebugLog.Tag.MIC,"Overlay ativo — nega mic WebView"); request.deny(); return }
                val granted = ContextCompat.checkSelfPermission(this@WebViewActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) { DebugLog.log(DebugLog.Tag.MIC,"Grant direto"); request.grant(request.resources) }
                else { pendingPermissionRequest = request; ActivityCompat.requestPermissions(this@WebViewActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) }
            }
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val tag = if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) DebugLog.Tag.ERRO else DebugLog.Tag.WEBVIEW
                DebugLog.log(tag, "[console] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                return true
            }
            override fun onShowFileChooser(wv: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileUploadCallback = cb
                try { startActivityForResult(p.createIntent(), 3001) } catch (e: Exception) { return false }
                return true
            }
        }

        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(webView, true) }
    }

    private fun checkAndRequestPermissions(serverUrl: String) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this) -> {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), REQ_OVERLAY)
            }
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
            else -> loadWebView(serverUrl)
        }
    }

    private fun loadWebView(url: String) {
        if (urlLoaded) return; urlLoaded = true
        DebugLog.log(DebugLog.Tag.WEBVIEW, "loadUrl → $url")
        webView.loadUrl(url)
    }

    private fun startOverlayService() {
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        DebugLog.log(DebugLog.Tag.SYS, "OverlayService iniciado")
    }

    private fun handleOverlayIntent(uri: Uri, savedToken: String?) {
        val token  = uri.getQueryParameter("token") ?: savedToken ?: return
        val server = uri.getQueryParameter("server") ?: MainActivity.DEFAULT_URL
        val nick   = uri.getQueryParameter("nick") ?: ""
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString(MainActivity.KEY_TOKEN, token); putString(MainActivity.KEY_SERVER, server)
            if (nick.isNotEmpty()) putString(MainActivity.KEY_NICK, nick)
        }.apply()
        if (!OverlayService.isRunning) startOverlayService()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val prefs     = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val serverUrl = prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_URL) ?: MainActivity.DEFAULT_URL
        when (requestCode) {
            REQ_MIC -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) pendingPermissionRequest?.grant(pendingPermissionRequest!!.resources)
                else { pendingPermissionRequest?.deny() }
                pendingPermissionRequest = null
                checkAndRequestPermissions(serverUrl)
            }
            REQ_NOTIF -> checkAndRequestPermissions(serverUrl)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val prefs     = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val serverUrl = prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_URL) ?: MainActivity.DEFAULT_URL
        when (requestCode) {
            REQ_OVERLAY -> checkAndRequestPermissions(serverUrl)
            3001 -> { fileUploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data) ?: arrayOf()); fileUploadCallback = null }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onResume()  { super.onResume();  webView.onResume() }
    override fun onPause()   { super.onPause();   webView.onPause() }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
```
### Fim do arquivo: ./app/src/main/java/com/madout2/overlay/WebViewActivity.kt
---
### Início do arquivo: ./app/src/main/res/drawable/ic_launcher.xml
```
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#0d0f1a" android:pathData="M0,0h108v108h-108z"/>
    <path android:fillColor="#f5c518" android:pathData="M54,30 C40,30 28,42 28,56 C28,70 40,80 54,80 C68,80 80,70 80,56 C80,42 68,30 54,30Z"/>
    <path android:fillColor="#0d0f1a" android:pathData="M48,48 L48,64 L64,56 Z"/>
</vector>
```
### Fim do arquivo: ./app/src/main/res/drawable/ic_launcher.xml
---
### Início do arquivo: ./app/src/main/res/layout/activity_main.xml
```
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="24dp"
    android:background="#0a0a0f">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="CHATVIVO"
        android:textSize="26sp"
        android:textColor="#00e87a"
        android:textStyle="bold"
        android:letterSpacing="0.2"
        android:layout_marginBottom="12dp"/>

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Carregando..."
        android:textSize="13sp"
        android:textColor="#5a5a70"
        android:gravity="center"
        android:lineSpacingMultiplier="1.4"/>

</LinearLayout>
```
### Fim do arquivo: ./app/src/main/res/layout/activity_main.xml
---
### Início do arquivo: ./app/src/main/res/layout/activity_webview.xml
```
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0a0a0f">

    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```
### Fim do arquivo: ./app/src/main/res/layout/activity_webview.xml
---
### Início do arquivo: ./app/src/main/res/values/strings.xml
```
<resources>
    <string name="app_name">ChatVivo</string>
    <string name="channel_name">ChatVivo QRR</string>
    <string name="channel_desc">Mantém QRR ativo em background</string>
</resources>
```
### Fim do arquivo: ./app/src/main/res/values/strings.xml
---
### Início do arquivo: ./app/src/main/res/values/themes.xml
```
<resources>
    <style name="Theme.Overlay" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="android:windowBackground">#0a0a0f</item>
        <item name="android:textColorPrimary">#e8e8f0</item>
        <item name="android:statusBarColor">#0a0a0f</item>
        <item name="android:navigationBarColor">#0a0a0f</item>
    </style>
</resources>
```
### Fim do arquivo: ./app/src/main/res/values/themes.xml
---
### Início do arquivo: ./app/src/main/res/xml/network_security_config.xml
```
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.15.16</domain>
        <domain includeSubdomains="true">192.168.0.0</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```
### Fim do arquivo: ./app/src/main/res/xml/network_security_config.xml
---
### Início do arquivo: ./build.gradle
```
plugins {
    id 'com.android.application' version '8.2.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
```
### Fim do arquivo: ./build.gradle
---
### Início do arquivo: ./codemagic.yaml
```
workflows:
  android-overlay:
    name: MadOut2 Overlay APK
    max_build_duration: 60
    instance_type: mac_mini_m2

    environment:
      java: 17

    scripts:
      - name: Setup
        script: |
          cd $CM_BUILD_DIR
          echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
          # Gera o wrapper usando o gradle instalado no Codemagic
          gradle wrapper --gradle-version=8.2

      - name: Build debug APK
        script: |
          cd $CM_BUILD_DIR
          ./gradlew assembleDebug --stacktrace

    artifacts:
      - app/build/outputs/apk/debug/*.apk

    publishing:
      email:
        recipients:
          - seu@email.com
        notify:
          success: true
          failure: true
```
### Fim do arquivo: ./codemagic.yaml
---
### Início do arquivo: ./gradle/wrapper/gradle-wrapper.properties
```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```
### Fim do arquivo: ./gradle/wrapper/gradle-wrapper.properties
---
### Início do arquivo: ./gradle.properties
```
android.useAndroidX=true
android.enableJetifier=true
org.gradle.jvmargs=-Xmx2048m
```
### Fim do arquivo: ./gradle.properties
---
### Início do arquivo: ./gradlew
```
#!/bin/sh
DIRNAME="$(dirname "$0")"
exec "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" "$@"
```
### Fim do arquivo: ./gradlew
---
### Início do arquivo: ./settings.gradle
```
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MadOut2Overlay"
include ':app'
```
### Fim do arquivo: ./settings.gradle
---
### Início do arquivo: ./saida_do_projeto.md
```
```
### Fim do arquivo: ./saida_do_projeto.md
