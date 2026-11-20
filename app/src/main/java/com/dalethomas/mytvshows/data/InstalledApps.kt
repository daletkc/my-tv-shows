package com.dalethomas.mytvshows.data

import android.content.Context
import android.content.Intent
import com.dalethomas.mytvshows.domain.Service
import com.dalethomas.mytvshows.domain.Services

/** Finds the apps installed on the TV, so the app only offers ones the household actually has. */
object InstalledApps {
    /** System screens and utilities, never a place to watch a show. */
    private val NOT_STREAMING = setOf(
        "com.android.tv.settings", "com.android.settings", "com.android.vending",
        "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher",
        "com.google.android.katniss", "com.google.android.tv.remote.service", "com.android.documentsui",
        "com.android.traceur", "com.google.android.play.games", "com.google.android.youtube.tvmusic",
        "com.rma.speedtesttv", "com.surfshark.vpnclient.android", "com.tailscale.ipn",
    )

    /**
     * Apps that show up on the TV's home screen, recognized services first (in the usual
     * order), then everything else by name. Falls back to phone launcher apps on devices
     * without TV apps (the emulator).
     */
    fun scan(context: Context): List<Service> {
        val pm = context.packageManager
        fun query(category: String) =
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0).map { it.activityInfo }
        val activities = query(Intent.CATEGORY_LEANBACK_LAUNCHER).ifEmpty { query(Intent.CATEGORY_LAUNCHER) }
        return activities
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName && it.packageName !in NOT_STREAMING }
            .map { Services.byPackage(it.packageName) ?: Services.otherApp(it.loadLabel(pm).toString(), it.packageName) }
            .sortedWith(compareBy({ Services.all.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.name.lowercase() }))
    }
}
