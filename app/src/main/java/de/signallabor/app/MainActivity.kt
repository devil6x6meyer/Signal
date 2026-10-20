package de.signallabor.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private val speedOfLightKmS = 299792.458
    private val targets = listOf("Mond" to "301", "Mars" to "499", "Venus" to "299",
        "Jupiter" to "599", "Saturn" to "699", "Sonne" to "10")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.rgb(11, 18, 33)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(25, 26, 25, 32)
        }
        fun text(value: String, size: Float = 17f) = TextView(this).apply {
            setText(value)
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, 9, 0, 9)
        }
        root.addView(text("SIGNALLABOR • Echte NASA-Daten", 23f))
        root.addView(text("NASA/JPL Horizons: aktuelle geometrische Entfernungen vom Erdmittelpunkt. Keine Funkübertragung.", 15f))
        root.addView(text("Ziel auswählen"))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            targets.map { it.first })
        root.addView(spinner)
        val status = text("Noch keine Daten abgerufen.", 19f)
        val button = Button(this).apply { this.text = "NASA-Daten jetzt abrufen" }
        root.addView(button)
        root.addView(status)
        root.addView(text("Quelle: NASA/JPL Horizons API, Ephemeriden (berechnete Positionen). Hin- und Rücklaufzeit sind aus der Entfernung errechnet, nicht per Radar gemessen.", 14f))
        setContentView(ScrollView(this).apply { addView(root) })
        button.setOnClickListener {
            val (name, id) = targets[spinner.selectedItemPosition]
            button.isEnabled = false
            status.text = "Verbinde mit NASA/JPL Horizons …"
            Thread {
                try {
                    val now = System.currentTimeMillis()
                    val jd = now / 86400000.0 + 2440587.5
                    fun parameter(value: String): String = URLEncoder.encode("'$value'", "UTF-8")
                    val url = "https://ssd.jpl.nasa.gov/api/horizons.api?format=json" +
                        "&COMMAND=" + parameter(id) +
                        "&OBJ_DATA=NO&MAKE_EPHEM=YES&EPHEM_TYPE=VECTORS" +
                        "&CENTER=" + parameter("500@399") +
                        "&TLIST=" + parameter(String.format(Locale.US, "%.8f", jd)) +
                        "&TIME_TYPE=UT&OUT_UNITS=KM-S&VEC_TABLE=6&CSV_FORMAT=YES"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 12000
                    conn.readTimeout = 20000
                    val raw = try {
                        if (conn.responseCode != 200) error("NASA HTTP ${conn.responseCode}")
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } finally { conn.disconnect() }
                    val response = JSONObject(raw).optString("result")
                    val row = response.substringAfter("$$" + "SOE", "")
                        .substringBefore("$$" + "EOE", "").trim().lineSequence().firstOrNull()
                        ?: error("NASA lieferte keine gültige Entfernung.")
                    val fields = row.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (fields.size < 5) error("Unerwartetes NASA-Datenformat.")
                    // JPL VEC_TABLE=6 CSV: JD, calendar, LT (s), RG (km), RR (km/s)
                    val km = fields[fields.size - 2].toDouble()
                    if (!km.isFinite() || km <= 0) error("Ungültiger Entfernungswert.")
                    val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss 'UTC'", Locale.GERMANY).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(now))
                    val output = String.format(Locale.GERMANY,
                        "Ziel: %s\nEntfernung: %,.0f km\nLicht-Hinweg: %,.3f s\nRechnerischer Hin- und Rückweg: %,.3f s\nDatenzeit: %s\nQuelle: NASA/JPL Horizons",
                        name, km, km / speedOfLightKmS, 2 * km / speedOfLightKmS, stamp)
                    runOnUiThread { status.text = output; button.isEnabled = true }
                } catch (e: Exception) {
                    runOnUiThread {
                        status.text = "Keine verifizierten NASA-Daten verfügbar.\n" +
                            (e.message ?: "Netzwerkfehler") +
                            "\nEs werden keine Ersatzwerte erfunden."
                        button.isEnabled = true
                    }
                }
            }.start()
        }
    }
}
