package com.lucasbeats.overlay

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import android.widget.*
import com.lucasbeats.ChatMessage
import com.lucasbeats.Member

// Overlay flutuante — pílula com chat + lista de membros
// Sem WebView, 100% nativo
class OverlayManager(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val winType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private var pillView:  View?         = null
    private var chatPanel: View?         = null
    private var membPanel: View?         = null
    private var llChat:    LinearLayout? = null
    private var llMembers: LinearLayout? = null
    private var pillP:     WindowManager.LayoutParams? = null
    private var chatP:     WindowManager.LayoutParams? = null
    private var membP:     WindowManager.LayoutParams? = null
    private var chatOpen   = false
    private var membOpen   = false

    private val messages = ArrayDeque<ChatMessage>()
    private val MAX_MSGS = 50

    fun show(onSendChat: (String) -> Unit) {
        if (pillView != null) return
        buildPill(onSendChat)
        buildChatPanel(onSendChat)
        buildMembPanel()
    }

    fun dismiss() {
        rmv(membPanel); rmv(chatPanel); rmv(pillView)
        membPanel = null; chatPanel = null; pillView = null
    }

    fun addMessage(msg: ChatMessage) {
        messages.addLast(msg)
        if (messages.size > MAX_MSGS) messages.removeFirst()
        refreshChat()
    }

    fun updateMembers(list: List<Member>) {
        refreshMembers(list)
    }

    // ── Pílula ────────────────────────────────────────────────────────────────

    private fun buildPill(onSend: (String) -> Unit) {
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC0d2518"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val btnChat = Button(ctx).apply {
            text = "💬"; textSize = 13f; stateListAnimator = null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.parseColor("#4eff9a"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        btnChat.setOnClickListener { toggleChat(onSend) }

        val btnMemb = Button(ctx).apply {
            text = "👥"; textSize = 13f; stateListAnimator = null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.parseColor("#6bcbff"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        btnMemb.setOnClickListener { toggleMemb() }

        val btnClose = Button(ctx).apply {
            text = "✕"; textSize = 11f; stateListAnimator = null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.parseColor("#ff4444"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        btnClose.setOnClickListener { dismiss() }

        pill.addView(btnChat,  lp(dp(36), dp(32)))
        pill.addView(btnMemb,  lp(dp(36), dp(32)))
        pill.addView(btnClose, lp(dp(28), dp(32)))

        val pp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            winType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(80) }
        pillP = pp

        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var mv = false
        pill.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { ix = pp.x; iy = pp.y; tx = ev.rawX; ty = ev.rawY; mv = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - tx).toInt(); val dy = (ev.rawY - ty).toInt()
                    if (dx * dx + dy * dy > 25) { mv = true; pp.x = ix - dx; pp.y = iy + dy; try { wm.updateViewLayout(pill, pp) } catch (_: Exception) {} }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        pillView = pill
        try { wm.addView(pill, pp) } catch (_: Exception) {}
    }

    // ── Chat Panel ────────────────────────────────────────────────────────────

    private fun buildChatPanel(onSend: (String) -> Unit) {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE071a10"))
            visibility = View.GONE
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0d2518"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = "🌿 Chat"; textSize = 9f; setTextColor(Color.parseColor("#4eff9a"))
            typeface = Typeface.DEFAULT_BOLD
        }, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btnCloseChat = Button(ctx).apply {
            text = "✕"; textSize = 9f; stateListAnimator = null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.parseColor("#6b9e7e"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        btnCloseChat.setOnClickListener { toggleChat(onSend) }
        header.addView(btnCloseChat, lp(dp(24), dp(24)))

        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        llChat = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        scroll.addView(llChat, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val inputRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0d2518"))
            setPadding(dp(6), dp(4), dp(6), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        val et = android.widget.EditText(ctx).apply {
            hint = "Mensagem..."; textSize = 11f
            setTextColor(Color.parseColor("#d8f5e5"))
            setHintTextColor(Color.parseColor("#6b9e7e"))
            setBackgroundColor(Color.parseColor("#102d1a"))
            setPadding(dp(8), dp(6), dp(8), dp(6)); maxLines = 1
        }
        val btnSend = Button(ctx).apply {
            text = "➤"; textSize = 11f; stateListAnimator = null
            setBackgroundColor(Color.parseColor("#1a6b3c"))
            setTextColor(Color.WHITE); setPadding(dp(8), 0, dp(8), 0)
        }
        btnSend.setOnClickListener {
            val txt = et.text.toString().trim()
            if (txt.isNotEmpty()) { onSend(txt); et.setText("") }
        }
        inputRow.addView(et,      lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(4), 0) })
        inputRow.addView(btnSend, lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)))

        panel.addView(header, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))
        panel.addView(scroll, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(inputRow, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val cp = WindowManager.LayoutParams(
            dp(280), dp(280), winType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(10); y = dp(130) }
        chatP = cp; chatPanel = panel
        try { wm.addView(panel, cp) } catch (_: Exception) {}
    }

    // ── Membros Panel ─────────────────────────────────────────────────────────

    private fun buildMembPanel() {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE071a10"))
            visibility = View.GONE
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0d2518"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = "👥 Grupo"; textSize = 9f; setTextColor(Color.parseColor("#6bcbff"))
            typeface = Typeface.DEFAULT_BOLD
        }, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btnCloseMemb = Button(ctx).apply {
            text = "✕"; textSize = 9f; stateListAnimator = null
            setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.parseColor("#6b9e7e"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        btnCloseMemb.setOnClickListener { toggleMemb() }
        header.addView(btnCloseMemb, lp(dp(24), dp(24)))

        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        llMembers = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        scroll.addView(llMembers, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        panel.addView(header, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))
        panel.addView(scroll, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val mp = WindowManager.LayoutParams(
            dp(220), dp(200), winType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(10); y = dp(130) }
        membP = mp; membPanel = panel
        try { wm.addView(panel, mp) } catch (_: Exception) {}
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private fun refreshChat() {
        val ll = llChat ?: return; ll.removeAllViews()
        messages.forEach { msg ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            row.addView(TextView(ctx).apply {
                text = msg.name; textSize = 8f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(msg.color)
            })
            row.addView(TextView(ctx).apply {
                text = msg.text; textSize = 10f
                setTextColor(Color.parseColor("#d8f5e5"))
            })
            ll.addView(row)
        }
        (llChat?.parent as? ScrollView)?.post {
            (llChat?.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun refreshMembers(list: List<Member>) {
        val ll = llMembers ?: return; ll.removeAllViews()
        list.forEach { m ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val dot = View(ctx).apply { setBackgroundColor(m.color) }
            row.addView(dot, lp(dp(10), dp(10)).apply { setMargins(0, 0, dp(8), 0) })
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(ctx).apply {
                text = m.name; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#d8f5e5"))
            })
            val ago = (System.currentTimeMillis() - m.lastSeen) / 1000
            col.addView(TextView(ctx).apply {
                text = if (m.lat != 0.0) "📍 ${ago}s atrás" else "Sem GPS"
                textSize = 8f; setTextColor(Color.parseColor("#6b9e7e"))
            })
            row.addView(col, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            ll.addView(row)
        }
    }

    // ── Toggle ────────────────────────────────────────────────────────────────

    private fun toggleChat(onSend: (String) -> Unit) {
        chatOpen = !chatOpen
        val panel = chatPanel ?: return; val cp = chatP ?: return
        panel.visibility = if (chatOpen) View.VISIBLE else View.GONE
        if (chatOpen) {
            cp.flags = cp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            refreshChat()
        } else {
            cp.flags = cp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try { wm.updateViewLayout(panel, cp) } catch (_: Exception) {}
    }

    private fun toggleMemb() {
        membOpen = !membOpen
        val panel = membPanel ?: return
        panel.visibility = if (membOpen) View.VISIBLE else View.GONE
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private fun rmv(v: View?) { try { if (v != null) wm.removeView(v) } catch (_: Exception) {} }
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
