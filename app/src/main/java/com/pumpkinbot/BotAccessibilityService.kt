package com.pumpkinbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class BotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: BotAccessibilityService? = null
    }

    @Volatile private var inFlight = false
    @Volatile private var flightAt = 0L

    override fun onServiceConnected() {
        instance = this
        BotState.log("خدمة إمكانية الوصول متصلة")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** لمسة قصيرة. ترجع false إذا كانت لمسة سابقة ما زالت قيد التنفيذ. */
    fun tap(x: Float, y: Float): Boolean {
        val now = SystemClock.uptimeMillis()
        if (inFlight && now - flightAt < 250) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()
        inFlight = true; flightAt = now
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                inFlight = false; BotState.tapOk.incrementAndGet()
            }
            override fun onCancelled(g: GestureDescription?) {
                inFlight = false; BotState.tapFail.incrementAndGet()
            }
        }, null)
        if (!ok) { inFlight = false; BotState.tapFail.incrementAndGet() }
        return ok
    }
}
