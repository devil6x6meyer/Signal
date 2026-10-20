package de.signallabor.app
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import android.text.InputType
import kotlin.math.*
class MainActivity : Activity() {
 private val c = 299792458.0
 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(28,32,28,24);setBackgroundColor(Color.rgb(11,18,33)) }
 fun label(t:String,size:Float=17f)=TextView(this).apply{text=t;textSize=size;setTextColor(Color.WHITE);setPadding(0,8,0,8)}
 root.addView(label("SIGNALLABOR • Simulation",25f))
 root.addView(label("Funk-Echo im Weltraum: Offline-Simulation, kein echter Sender.",14f))
 root.addView(label("Entfernung (km)"))
 val distance=EditText(this).apply{setText("384400");inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setTextColor(Color.WHITE)}
 root.addView(distance)
 root.addView(label("Frequenz (MHz)"))
 val freq=EditText(this).apply{setText("1420");inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setTextColor(Color.WHITE)}
 root.addView(freq)
 root.addView(label("Reflektivität (0–100 %)"))
 val reflect=SeekBar(this).apply{max=100;progress=12};root.addView(reflect)
 val result=label("",19f)
 val btn=Button(this).apply{text="Simulation starten"};root.addView(btn);root.addView(result)
 root.addView(label("Der Echopegel ist ein vereinfachter Modellwert. Kein Echo beweist keine Universumsgrenze.",13f))
 btn.setOnClickListener {
 val km=distance.text.toString().toDoubleOrNull();val mhz=freq.text.toString().toDoubleOrNull()
 if(km==null||km<=0||!km.isFinite()||mhz==null||mhz<=0||!mhz.isFinite()){result.text="Bitte positive gültige Werte eingeben.";return@setOnClickListener}
 val oneWay=km*1000/c;val echo=2*oneWay;val wavelength=c/(mhz*1e6)
 val relative=(reflect.progress/100.0)*(384400.0/km).pow(4)
 val db=if(relative>0)10*log10(relative) else Double.NEGATIVE_INFINITY
 result.text="Hinweg: %.3f s\nEcho: %.3f s\nWellenlänge: %.3f m\nModell-Echopegel: %.1f dB\nReflexion: %d %%".format(oneWay,echo,wavelength,db,reflect.progress)
 }
 setContentView(ScrollView(this).apply{addView(root)});btn.performClick()
 }
}