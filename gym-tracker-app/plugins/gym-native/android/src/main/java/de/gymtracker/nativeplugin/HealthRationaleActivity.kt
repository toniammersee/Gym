package de.gymtracker.nativeplugin

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Erklärt in Health Connect, wofür Gym Tracker die Gesundheitsdaten nutzt. */
class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val de = Locale.getDefault().language == "de"
        val pad = (20 * resources.displayMetrics.density).toInt()
        val tv = TextView(this).apply {
            textSize = 15f
            text = if (de) TEXT_DE else TEXT_EN
        }
        val ok = Button(this).apply {
            text = "OK"
            setOnClickListener { finish() }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(tv)
            addView(ok)
        }
        setTitle(if (de) "Gym Tracker und Health Connect" else "Gym Tracker and Health Connect")
        setContentView(ScrollView(this).apply {
            addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    companion object {
        private const val TEXT_DE =
            "Gym Tracker liest aus Health Connect deine Schritte, dein Körpergewicht, deinen Schlaf und deinen Ruhepuls. " +
                "Damit füllt die App die Bereiche Erholung, Schritte und Körpergewicht aus, ohne dass du alles abtippen musst.\n\n" +
                "Wenn du es erlaubst, trägt die App deine abgeschlossenen Trainings als Krafttraining in Health Connect ein.\n\n" +
                "Alle Daten bleiben auf deinem Handy. Gym Tracker schickt nichts an einen Server und gibt nichts weiter. " +
                "Du kannst die Berechtigungen jederzeit in Health Connect zurücknehmen."
        private const val TEXT_EN =
            "Gym Tracker reads your steps, body weight, sleep and resting heart rate from Health Connect. " +
                "The app uses them to fill in recovery, steps and body weight, so you don't have to type everything.\n\n" +
                "If you allow it, the app adds your finished workouts to Health Connect as strength training.\n\n" +
                "All data stays on your phone. Gym Tracker sends nothing to a server and shares nothing. " +
                "You can revoke the permissions in Health Connect at any time."
    }
}
