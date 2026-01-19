package org.htwk.pacing.backend.database

import android.content.Context
import androidx.core.content.edit

class ModeStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("app_mode", Context.MODE_PRIVATE)

    fun isDemo(): Boolean =
        prefs.getBoolean("demo", false)

    fun setDemo(value: Boolean) {
        prefs.edit { putBoolean("demo", value) }
    }
}
