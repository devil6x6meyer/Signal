package de.signallabor.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.log10

class MainActivity : Activity() {
    private val c = 299792.458
    private val targets = listOf("Mond" to "301", "Venus" to "299", "Mars" to "499",
        "Jupiter" to "599", "Saturn" to "699", "Sonne" to "10")
    private val ui = Handler(Looper.getMainLooper())
    private var scanning = false
    private var session = 0
    private lateinit var start: Button
    private lateinit var headline: TextView
    private lateinit var results: LinearLayout
    private lateinit var lastUpdate: TextView

    private fun label(s: String, sz: Float = 16f, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            text = s
            textSize = sz
            setTextColor(color)
            setPadding(0, 7, 0, 7)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navy = Color.rgb(9, 17, 32)
        val cyan = Color.rgb(83, 227, 218)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(22, 25, 22, 30)
        }
        root.addView(label("SIGNALLABOR  •  SPACE SCANNER", 21f, cyan).apply {
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(label("NASA/JPL Horizons • echte astronomische Positionsdaten", 13f,
            Color.LTGRAY))
        headline = label("● SCANNER BEREIT", 18f, cyan)
        root.addView(headline)
        start = Button(this).apply { text = "SCAN STARTEN" }
        root.addView(start)
        lastUpdate = label("Noch kein Scan durchgeführt", 12f, Color.LTGRAY)
        root.addView(lastUpdate)
        root.addView(label("ZIELE  /  ENTFERNUNG  /  LICHTLAUFZEIT", 13f, cyan))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
        targets.forEach { (name, _) -> addRow(name, "Warte auf Scan …", 0.0) }
        root.addView(label("Entfernungen vom Erdmittelpunkt. Lichtlaufzeiten berechnet. " +
            "Kein Radar, keine Funkmessung und kein Empfang aus dem All.", 12f, Color.LTGRAY))
        setContentView(ScrollView(this).apply { addView(root) })
        start.setOnClickListener {
            if (scanning) {
                scanning = false
                session++
                start.text = "SCAN STARTEN"
                headline.text = "● SCAN ANGEHALTEN"
            } else {
                scanning = true
                session++
                start.text = "SCAN STOPPEN"
                doScan(session)
            }
        }
    }

    private fun addRow(name: String, value: String, km: Double) {
        val cyan = Color.rgb(83, 227, 218)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 13)
            setBackgroundColor(Color.rgb(19, 33, 53))
        }
        card.addView(label(name.uppercase(Locale.GERMANY), 16f, cyan).apply {
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(label(value, 15f))
        val track = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = if (km > 0) ((log10(km.coerceAtLeast(1.0)) / 11.0) * 1000)
                .toInt().coerceIn(1, 1000) else 0
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        card.addView(track)
        results.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = 9
        })
    }

    private fun doScan(token: Int) {
        if (!scanning || token != session) return
        headline.text = "◉ SCAN LÄUFT  •  NASA VERBINDUNG"
        Thread {
            val readings = mutableListOf<Triple<String, String, Double>>()
            val now = System.currentTimeMillis()
            val jd = now / 86400000.0 + 2440587.5
            for ((name, id) in targets) {
                if (!scanning || token != session) return@Thread
                try {
                    val km = fetchDistance(id, jd)
                    val value = String.format(Locale.GERMANY,
                        "%,.0f km\nLicht: %,.2f s  •  Hin/Rück: %,.2f s",
                        km, km / c, 2.0 * km / c)
                    readings.add(Triple(name, value, km))
                } catch (e: Exception) {
                    readings.add(Triple(name, "Keine NASA-Daten: " +
                        (e.message ?: "Netzwerkfehler"), 0.0))
                }
                val snapshot = readings.toList()
                ui.post {
                    if (scanning && token == session) {
                        results.removeAllViews()
                        snapshot.forEach { (n, v, d) -> addRow(n, v, d) }
                        targets.drop(snapshot.size).forEach { (n, _) ->
                            addRow(n, "Wird gescannt …", 0.0)
                        }
                        headline.text = "◉ SCAN " + snapshot.size + "/" + targets.size
                    }
                }
            }
            ui.post {
                if (scanning && token == session) {
                    val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss 'UTC'",
                        Locale.GERMANY).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(now))
                    lastUpdate.text = "Datenzeit: " + stamp + "  •  Aktualisierung alle 60 s"
                    headline.text = "● SCAN ABGESCHLOSSEN  •  AUTO-AKTUALISIERUNG"
                    ui.postDelayed({ doScan(token) }, 60000L)
                }
            }
        }.start()
    }

    private fun fetchDistance(id: String, jd: Double): Double {
        fun param(s: String) = URLEncoder.encode("'" + s + "'", "UTF-8")
        val url = "https://ssd.jpl.nasa.gov/api/horizons.api?format=json" +
            "&COMMAND=" + param(id) +
            "&OBJ_DATA=NO&MAKE_EPHEM=YES&EPHEM_TYPE=VECTORS" +
            "&CENTER=" + param("500@399") +
            "&TLIST=" + param(String.format(Locale.US, "%.8f", jd)) +
            "&TIME_TYPE=UT&OUT_UNITS=KM-S&VEC_TABLE=6&CSV_FORMAT=YES"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 20000
        val raw = try {
            if (conn.responseCode != 200) error("HTTP " + conn.responseCode)
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val response = JSONObject(raw).optString("result")
        val row = response.substringAfter("$$" + "SOE", "")
            .substringBefore("$$" + "EOE", "").trim()
            .lineSequence().firstOrNull() ?: error("Kein gültiger Datensatz")
        val fields = row.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (fields.size < 5) error("Datenformat unbekannt")
        val km = fields[fields.size - 2].toDouble()
        if (!km.isFinite() || km <= 0) error("Entfernung ungültig")
        return km
    }

    override fun onDestroy() {
        scanning = false
        session++
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
