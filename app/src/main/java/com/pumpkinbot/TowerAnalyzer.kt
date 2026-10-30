package com.pumpkinbot

import android.graphics.Rect
import java.nio.ByteBuffer

/** يحلل إطاراً واحداً (RGBA) ويستخرج: النار يمين/يسار لكل مستوى + هل البرج ظاهر. */
class TowerAnalyzer {

    class Result(
        val left: FloatArray,
        val right: FloatArray,
        val towerScore: Float,
        val brightness: Float
    ) {
        val inGame: Boolean get() = towerScore > GameConfig.TOWER_MIN
    }

    /** نار: لون برتقالي-أصفر ساطع جداً (يطابق منطق HSV الذي اختُبر على الفيديو). */
    private fun isFire(r: Int, g: Int, b: Int): Boolean {
        if (r < 216 || g > r || g < b) return false
        val d = r - b
        if (d * 100 < r * 55) return false
        return (g - b) * 100 >= d * 40
    }

    /** يقطينة: برتقالي مشبع متوسط السطوع. */
    private fun isPumpkin(r: Int, g: Int, b: Int): Boolean {
        if (r < 110 || r <= g || g <= b) return false
        val d = r - b
        if (d * 100 < r * 67) return false
        val gb = (g - b) * 100
        return gb >= 17 * d && gb <= 73 * d
    }

    private fun fraction(
        buf: ByteBuffer, w: Int, h: Int, stride: Int,
        x0: Float, x1: Float, y0: Float, y1: Float,
        ign: Rect?, fire: Boolean
    ): Float {
        val xa = (x0 * w).toInt(); val xb = (x1 * w).toInt()
        val ya = (y0 * h).toInt(); val yb = (y1 * h).toInt()
        var hit = 0; var total = 0
        for (y in ya until yb) {
            val row = y * stride
            for (x in xa until xb) {
                if (ign != null && ign.contains(x, y)) continue
                val i = row + x * 4
                val r = buf.get(i).toInt() and 0xFF
                val g = buf.get(i + 1).toInt() and 0xFF
                val b = buf.get(i + 2).toInt() and 0xFF
                total++
                if (if (fire) isFire(r, g, b) else isPumpkin(r, g, b)) hit++
            }
        }
        return if (total == 0) 0f else hit.toFloat() / total
    }

    /** ign = مستطيل الفقاعة بإحداثيات الإطار المصغّر: يُستثنى من التحليل كي لا تؤثر الفقاعة على القراءة. */
    fun analyze(buf: ByteBuffer, w: Int, h: Int, stride: Int, ign: Rect?): Result {
        val n = GameConfig.BAND_Y.size
        val left = FloatArray(n); val right = FloatArray(n)
        for (k in 0 until n) {
            val (y0, y1) = GameConfig.BAND_Y[k]
            left[k] = fraction(buf, w, h, stride, GameConfig.LEFT_X0, GameConfig.LEFT_X1, y0, y1, ign, true)
            right[k] = fraction(buf, w, h, stride, GameConfig.RIGHT_X0, GameConfig.RIGHT_X1, y0, y1, ign, true)
        }
        val tower = fraction(
            buf, w, h, stride,
            GameConfig.TOWER_X0, GameConfig.TOWER_X1, GameConfig.TOWER_Y0, GameConfig.TOWER_Y1, ign, false
        )
        // سطوع تقريبي لاكتشاف الشاشة السوداء (FLAG_SECURE)
        var sum = 0; var cnt = 0
        for (yy in 1..8) for (xx in 1..8) {
            val i = (yy * h / 10) * stride + (xx * w / 10) * 4
            sum += (buf.get(i).toInt() and 0xFF) + (buf.get(i + 1).toInt() and 0xFF) + (buf.get(i + 2).toInt() and 0xFF)
            cnt += 3
        }
        return Result(left, right, tower, sum.toFloat() / cnt)
    }

    /**
     * قرار الجهة للضربة التالية:
     * المستوى [1] هو الذي سينزل بجوار اللاعب بعد هذه الضربة، فنقف في الجهة المعاكسة لناره.
     * بدون نار: نبقى في الجهة الحالية. لا نعرف/غير مستقر: null (ننتظر إطاراً).
     */
    fun decide(res: Result, cur: Side): Side? {
        val t = GameConfig.FIRE_THRESHOLD; val p = GameConfig.FIRE_PARTIAL
        val l = res.left[1]; val r = res.right[1]
        if (l > t && r > t) return null
        if ((l > p && l <= t) || (r > p && r <= t)) return null   // نار أثناء الحركة
        if (l > t) return Side.R
        if (r > t) return Side.L
        return cur
    }
}
