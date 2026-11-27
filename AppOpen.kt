package com.honestreward.app

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAdLoadCallback

/**
 * "App open" ad shown when the app is opened. Switched on/off from the admin panel (setting app_open_enabled),
 * and only works when ADMOB_APP_OPEN_UNIT is set. Never on the very first launch, at most once every 4 hours,
 * once per app process, and only if the ad is ready within a few seconds of opening.
 */
object AppOpen {
    private const val GAP_MS = 4 * 60 * 60 * 1000L
    private const val MAX_WAIT_MS = 6000L
    private val startedAt = System.currentTimeMillis()
    var tried = false

    fun maybeShow(act: Activity) {
        if (tried) return
        tried = true
        val unit = BuildConfig.ADMOB_APP_OPEN_UNIT
        if (unit.isBlank()) return
        val p = act.getSharedPreferences("hr", 0)
        val now = System.currentTimeMillis()
        if (p.getLong("first_open", 0L) == 0L) { p.edit().putLong("first_open", now).apply(); return }
        if (now - p.getLong("app_open_last", 0L) < GAP_MS) return
        AppOpenAd.load(act, unit, AdRequest.Builder().build(), object : AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                if (act.isFinishing || act.isDestroyed || System.currentTimeMillis() - startedAt > MAX_WAIT_MS + 4000L) return
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {}
                    override fun onAdFailedToShowFullScreenContent(e: AdError) {}
                }
                p.edit().putLong("app_open_last", System.currentTimeMillis()).apply()
                ad.show(act)
            }
            override fun onAdFailedToLoad(e: LoadAdError) {}
        })
    }
}
