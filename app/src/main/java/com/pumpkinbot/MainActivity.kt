package com.pumpkinbot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_CAPTURE = 42
    private val h = Handler(Looper.getMainLooper())
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvPerms: TextView

    private val tick = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 500) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(text: String, color: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor(color))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BotState.load(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "🎃 بوت اليقطين"; textSize = 26f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
        })
        tvPerms = TextView(this).apply { textSize = 14f; setPadding(0, dp(12), 0, 0) }
        root.addView(tvPerms)

        root.addView(button("1) منح إذن العرض فوق التطبيقات", "#455A64") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button("2) تفعيل خدمة إمكانية الوصول", "#455A64") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "اختر «بوت اليقطين» ثم فعّله", Toast.LENGTH_LONG).show()
        })
        root.addView(button("3) تشغيل الفقاعة والتقاط الشاشة", "#2E7D32") { requestCapture() })
        root.addView(button("إيقاف نهائي", "#C62828") {
            startService(Intent(this, BotService::class.java).setAction(BotService.ACTION_STOP))
        })

        root.addView(TextView(this).apply { text = "السرعة / الدقة"; setPadding(0, dp(16), 0, 0); setTypeface(null, Typeface.BOLD) })
        val rg = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = arrayOf("آمن (أدق)", "متوازن", "سريع")
        for (i in names.indices) rg.addView(RadioButton(this).apply {
            text = names[i]; id = 100 + i; isChecked = (i == BotState.speed)
        })
        rg.setOnCheckedChangeListener { _, id -> BotState.speed = id - 100; BotState.save(this) }
        root.addView(rg)

        root.addView(Switch(this).apply {
            text = "اللعب التلقائي المستمر (لمس وسط الشاشة بعد نهاية الجولة)"
            isChecked = BotState.autoReplay
            setOnCheckedChangeListener { _, c -> BotState.autoReplay = c; BotState.save(this@MainActivity) }
            setPadding(0, dp(12), 0, 0)
        })

        tvStatus = TextView(this).apply { textSize = 18f; setPadding(0, dp(16), 0, 0); setTypeface(null, Typeface.BOLD) }
        tvStats = TextView(this).apply { textSize = 14f; setPadding(0, dp(6), 0, 0) }
        tvLog = TextView(this).apply {
            textSize = 11f; typeface = Typeface.MONOSPACE; setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.parseColor("#EEEEEE")); layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        root.addView(tvStatus); root.addView(tvStats)
        root.addView(TextView(this).apply { text = "سجل الأخطاء والأحداث"; setPadding(0, dp(12), 0, dp(4)); setTypeface(null, Typeface.BOLD) })
        root.addView(tvLog)
        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun requestCapture() {
        if (!Settings.canDrawOverlays(this)) { toast("امنح إذن العرض فوق التطبيقات أولاً"); return }
        if (BotAccessibilityService.instance == null) { toast("فعّل خدمة إمكانية الوصول أولاً"); return }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, BotService::class.java).setAction(BotService.ACTION_START)
                    .putExtra("code", resultCode).putExtra("data", data)
                startForegroundService(i)
                toast("افتح اللعبة ثم اضغط الفقاعة ▶ عند بدء الجولة")
                moveTaskToBack(true)
            } else toast("لم تتم الموافقة على التقاط الشاشة")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val ac = BotAccessibilityService.instance != null
        tvPerms.text = "العرض فوق التطبيقات: ${if (ov) "✅" else "❌"}    إمكانية الوصول: ${if (ac) "✅" else "❌"}"
        tvStatus.text = "الحالة: ${BotState.status}"
        val ok = BotState.tapOk.get(); val fail = BotState.tapFail.get()
        val rate = if (ok + fail > 0) ok * 100 / (ok + fail) else 0
        tvStats.text = "اللمسات: ${BotState.taps.get()}   نجاح اللمس: $rate%   فاشلة: $fail\n" +
            "الجولات: ${BotState.rounds.get()}   آخر جولة: ${BotState.lastRoundTaps} ضربة   أعلى جولة: ${BotState.bestTaps} ضربة\n" +
            "متوسط زمن القرار: ${"%.1f".format(BotState.avgDecisionMs)} مللي ثانية"
        tvLog.text = BotState.logText()
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
}
