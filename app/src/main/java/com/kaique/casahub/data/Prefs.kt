package com.kaique.casahub.data

import android.content.Context

/** Guarda token, modo demo, favoritos e o último local escolhido (privado ao app). */
class Prefs(context: Context) : Settings {
    private val sp = context.getSharedPreferences("casahub", Context.MODE_PRIVATE)

    override var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    override var demo: Boolean
        get() = sp.getBoolean("demo", false)
        set(v) = sp.edit().putBoolean("demo", v).apply()

    override var locationId: String?
        get() = sp.getString("location", null)
        set(v) = sp.edit().putString("location", v).apply()

    override var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("favorites", v).apply()

    override var autoRefresh: Boolean
        get() = sp.getBoolean("autoRefresh", true)
        set(v) = sp.edit().putBoolean("autoRefresh", v).apply()

    override fun clearSession() {
        sp.edit().remove("token").remove("demo").remove("location").apply()
    }
}
