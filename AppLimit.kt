package com.plan.ingsoft

import org.json.JSONArray
import org.json.JSONObject

/** Límite diario de una app. Si [bloquear] es true, al llegar se tapa la pantalla. */
data class AppLimit(
    val pkg: String,
    val label: String,
    val minutos: Int,
    val bloquear: Boolean = true
) {
    companion object {
        fun listToJson(list: List<AppLimit>): String {
            val a = JSONArray()
            for (l in list) {
                val o = JSONObject()
                o.put("pkg", l.pkg)
                o.put("label", l.label)
                o.put("min", l.minutos)
                o.put("block", l.bloquear)
                a.put(o)
            }
            return a.toString()
        }

        fun listFromJson(txt: String): List<AppLimit> {
            val a = JSONArray(txt)
            val out = ArrayList<AppLimit>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    AppLimit(
                        o.getString("pkg"), o.optString("label", o.getString("pkg")),
                        o.getInt("min"), o.optBoolean("block", true)
                    )
                )
            }
            return out
        }
    }
}
