    }

    /** نقطة لمس بعيدة عن الفقاعة. */
    private fun safeTap(side: Side): Boolean {
        val svc = BotAccessibilityService.instance ?: run {
            BotState.status = "خدمة إمكانية الوصول غير مفعّلة"; return false
        }
        val x = (if (side == Side.L) GameConfig.TAP_LEFT_X else GameConfig.TAP_RIGHT_X) * screenW
        var y = GameConfig.TAP_Y * screenH
        val br = bubbleRect
        if (br != null) {
            val m = 14 * density
            var guard = 0
            while (x >= br.left - m && x <= br.right + m && y >= br.top - m && y <= br.bottom + m && guard++ < 30)
                y -= 0.03f * screenH
        }
        return svc.tap(x, y)
    }

    private fun centerTap() {
        BotAccessibilityService.instance?.tap(0.5f * screenW, 0.5f * screenH)
    }

    private fun loop(cw: Int, ch: Int) {
        var cur = Side.L
        var pend: Side? = null; var pendN = 0
        var lastTap = 0L; var tapsRound = 0
        var offSince = 0L; var ended = false; var lastReplay = 0L
        var black = 0
        while (alive) {
            try {
                val img = reader?.acquireLatestImage()
                if (img == null) { Thread.sleep(2); continue }
                try {
                    if (BotState.mode != Mode.RUNNING) { pend = null; pendN = 0; Thread.sleep(25); continue }
                    val t0 = System.nanoTime()
                    val now = SystemClock.uptimeMillis()
                    val pl = img.planes[0]
                    val res = analyzer.analyze(pl.buffer, cw, ch, pl.rowStride, scaledIgnore())

                    if (res.brightness < 2f) {
                        if (++black == 60) {
                            BotState.log("الإطار الملتقط أسود: قد تمنع اللعبة التقاط الشاشة")
                            BotState.status = "الشاشة الملتقطة سوداء"
                        }
                        continue
                    }
                    black = 0

                    if (!res.inGame) {
                        if (offSince == 0L) offSince = now
                        if (!ended && tapsRound >= GameConfig.MIN_TAPS_ROUND && now - offSince > GameConfig.ROUND_END_MS) {
                            ended = true
                            BotState.finishRound(tapsRound)
                            BotState.log("انتهت الجولة: $tapsRound ضربة")
                            tapsRound = 0
                        }
                        BotState.status = if (ended) "انتهت الجولة" else "بانتظار ظهور البرج"
                        if (ended && BotState.autoReplay && now - lastReplay > 2500) {
                            lastReplay = now; centerTap()
                        }
                        pend = null; pendN = 0
                        continue
                    }
                    offSince = 0L
                    if (ended) { ended = false; cur = Side.L; BotState.log("جولة جديدة") }

                    val want = analyzer.decide(res, cur)
                    if (want == null) { pend = null; pendN = 0; continue }
                    if (want == pend) pendN++ else { pend = want; pendN = 1 }

                    val sp = BotState.speed.coerceIn(0, 2)
                    if (pendN >= GameConfig.CONFIRM_FRAMES[sp] && now - lastTap >= GameConfig.COOLDOWN_MS[sp]) {
                        if (safeTap(want)) {
                            lastTap = now; cur = want; tapsRound++
                            BotState.taps.incrementAndGet()
                            BotState.noteDecision((System.nanoTime() - t0) / 1e6)
                            BotState.status = "يلعب — ${if (want == Side.L) "يسار" else "يمين"}"
                            pend = null; pendN = 0
                        }
                    }
                } finally {
                    img.close()
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                BotState.log("خطأ في الحلقة: ${e.javaClass.simpleName}: ${e.message}")
                try { Thread.sleep(100) } catch (_: InterruptedException) { break }
            }
        }
    }

    // ───────────────────────── الإيقاف ─────────────────────────
    private fun shutdown() {
        if (!alive && projection == null && bubble == null) { stopSelf(); return }
        alive = false
        BotState.mode = Mode.STOPPED
        BotState.status = "متوقف"
        worker?.interrupt()
        try { worker?.join(300) } catch (_: Exception) {}
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        try { vd?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        vd = null; reader = null; projection = null
        BotState.save(this)
        BotState.log("تم الإيقاف")
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (alive) shutdown()
        super.onDestroy()
    }
}
