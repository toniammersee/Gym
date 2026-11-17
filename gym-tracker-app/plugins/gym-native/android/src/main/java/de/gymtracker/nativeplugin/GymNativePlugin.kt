package de.gymtracker.nativeplugin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResult
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Native Ergänzungen für Gym Tracker:
 *  - Pausen-Timer als laufende Benachrichtigung mit Countdown (auch im Sperrbildschirm)
 *  - Health Connect: Schritte, Gewicht, Schlaf und Ruhepuls lesen, Trainings schreiben
 */
@CapacitorPlugin(name = "GymNative")
class GymNativePlugin : Plugin() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun handleOnDestroy() {
        scope.cancel()
        super.handleOnDestroy()
    }

    /* ---------------- Pausen-Timer ---------------- */

    @PluginMethod
    fun restStart(call: PluginCall) {
        val end = call.getLong("end")
        if (end == null) {
            call.reject("end fehlt")
            return
        }
        val left = end - System.currentTimeMillis()
        val nm = NotificationManagerCompat.from(context)
        if (left <= 500) {
            nm.cancel(REST_ID)
            call.resolve()
            return
        }
        if (!nm.areNotificationsEnabled()) {
            call.resolve(JSObject().put("shown", false))
            return
        }
        ensureRestChannel(call.getString("channel", "Pausen-Timer") ?: "Pausen-Timer")
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val pi = launch?.let {
            PendingIntent.getActivity(
                context, REST_ID, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val b = NotificationCompat.Builder(context, REST_CHANNEL)
            .setSmallIcon(R.drawable.gn_ic_timer)
            .setContentTitle(call.getString("title", "Pause"))
            .setContentText(call.getString("body", ""))
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(left)
        if (pi != null) b.setContentIntent(pi)
        try {
            nm.notify(REST_ID, b.build())
            call.resolve(JSObject().put("shown", true))
        } catch (e: SecurityException) {
            call.resolve(JSObject().put("shown", false))
        }
    }

    @PluginMethod
    fun restStop(call: PluginCall) {
        NotificationManagerCompat.from(context).cancel(REST_ID)
        call.resolve()
    }

    private fun ensureRestChannel(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = mgr.getNotificationChannel(REST_CHANNEL)
        if (ch != null && ch.name == name) return
        val c = NotificationChannel(REST_CHANNEL, name, NotificationManager.IMPORTANCE_LOW)
        c.setSound(null, null)
        c.enableVibration(false)
        c.setShowBadge(false)
        c.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        mgr.createNotificationChannel(c)
    }

    /* ---------------- Health Connect ---------------- */

    private val permMap: Map<String, String> = mapOf(
        "steps" to HealthPermission.getReadPermission(StepsRecord::class),
        "weight" to HealthPermission.getReadPermission(WeightRecord::class),
        "sleep" to HealthPermission.getReadPermission(SleepSessionRecord::class),
        "rhr" to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        "workouts" to HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    private fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, HC_PACKAGE)

    private fun client(): HealthConnectClient? =
        if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    private fun grantedObj(granted: Set<String>): JSObject {
        val o = JSObject()
        permMap.forEach { (k, p) -> o.put(k, granted.contains(p)) }
        return o
    }

    @PluginMethod
    fun hcStatus(call: PluginCall) {
        val status = when (sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> "available"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
            else -> "unavailable"
        }
        val c = client()
        if (c == null) {
            call.resolve(JSObject().put("status", status).put("granted", grantedObj(emptySet())))
            return
        }
        scope.launch {
            try {
                val g = c.permissionController.getGrantedPermissions()
                call.resolve(JSObject().put("status", status).put("granted", grantedObj(g)))
            } catch (e: Exception) {
                call.reject("Health Connect antwortet nicht: " + e.message)
            }
        }
    }

    @PluginMethod
    fun hcRequest(call: PluginCall) {
        if (client() == null) {
            call.reject("Health Connect ist nicht verfügbar", "unavailable")
            return
        }
        val contract = PermissionController.createRequestPermissionResultContract()
        val intent = contract.createIntent(context, permMap.values.toSet())
        startActivityForResult(call, intent, "hcResult")
    }

    @ActivityCallback
    private fun hcResult(call: PluginCall?, result: ActivityResult) {
        if (call == null) return
        val c = client()
        if (c == null) {
            call.resolve(JSObject().put("granted", grantedObj(emptySet())))
            return
        }
        // Nach dem Dialog den tatsächlichen Stand abfragen, das ist zuverlässiger als das Ergebnis-Intent.
        scope.launch {
            try {
                val g = c.permissionController.getGrantedPermissions()
                call.resolve(JSObject().put("granted", grantedObj(g)))
            } catch (e: Exception) {
                val parsed = PermissionController.createRequestPermissionResultContract()
                    .parseResult(result.resultCode, result.data)
                call.resolve(JSObject().put("granted", grantedObj(parsed)))
            }
        }
    }

    @PluginMethod
    fun hcInstall(call: PluginCall) {
        val uri = Uri.parse("market://details?id=$HC_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        val i = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.vending")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(i)
        } catch (e: Exception) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HC_PACKAGE"))
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(web) } catch (_: Exception) {}
        }
        call.resolve()
    }

    @PluginMethod
    fun hcSettings(call: PluginCall) {
        val action = if (Build.VERSION.SDK_INT >= 34) "android.health.connect.action.HEALTH_HOME_SETTINGS"
        else "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            call.resolve()
        } catch (e: Exception) {
            call.reject("Einstellungen nicht gefunden")
        }
    }

    /** Liest Tageswerte der letzten [days] Tage. Ergebnis: {steps:{tag:n}, weight:{tag:kg}, sleep:{tag:std}, rhr:{tag:bpm}} */
    @PluginMethod
    fun hcRead(call: PluginCall) {
        val c = client()
        if (c == null) {
            call.reject("Health Connect ist nicht verfügbar", "unavailable")
            return
        }
        val days = (call.getInt("days") ?: 14).coerceIn(1, 90)
        val zone = ZoneId.systemDefault()
        val startDay = LocalDate.now(zone).minusDays((days - 1).toLong())
        val startInstant = startDay.atStartOfDay(zone).toInstant()
        val now = Instant.now()
        scope.launch {
            val out = JSObject()
            val granted = try { c.permissionController.getGrantedPermissions() } catch (e: Exception) { emptySet() }

            if (granted.contains(permMap["steps"])) {
                val o = JSObject()
                try {
                    val res = c.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(
                                startDay.atStartOfDay(),
                                LocalDate.now(zone).plusDays(1).atStartOfDay()
                            ),
                            timeRangeSlicer = Period.ofDays(1)
                        )
                    )
                    res.forEach { g ->
                        val n = g.result[StepsRecord.COUNT_TOTAL]
                        if (n != null && n > 0) o.put(g.startTime.toLocalDate().toString(), n)
                    }
                } catch (_: Exception) {}
                out.put("steps", o)
            }

            if (granted.contains(permMap["weight"])) {
                val o = JSObject()
                try {
                    readAll(c, WeightRecord::class, startInstant, now).sortedBy { it.time }.forEach { r ->
                        o.put(r.time.atZone(zone).toLocalDate().toString(), Math.round(r.weight.inKilograms * 10) / 10.0)
                    }
                } catch (_: Exception) {}
                out.put("weight", o)
            }

            if (granted.contains(permMap["sleep"])) {
                val o = JSObject()
                try {
                    // Schlaf zählt für den Tag, an dem man aufwacht. Einen Tag früher anfangen, damit die erste Nacht dabei ist.
                    val sums = HashMap<String, Long>()
                    readAll(c, SleepSessionRecord::class, startInstant.minusSeconds(86400), now).forEach { s ->
                        var ms = s.endTime.toEpochMilli() - s.startTime.toEpochMilli()
                        s.stages.filter {
                            it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE ||
                                it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED ||
                                it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED
                        }.forEach { st -> ms -= st.endTime.toEpochMilli() - st.startTime.toEpochMilli() }
                        val day = s.endTime.atZone(zone).toLocalDate()
                        if (!day.isBefore(startDay) && ms > 0) {
                            val k = day.toString()
                            sums[k] = (sums[k] ?: 0L) + ms
                        }
                    }
                    sums.forEach { (k, ms) -> o.put(k, Math.round(ms / 360000.0) / 10.0) }
                } catch (_: Exception) {}
                out.put("sleep", o)
            }

            if (granted.contains(permMap["rhr"])) {
                val o = JSObject()
                try {
                    readAll(c, RestingHeartRateRecord::class, startInstant, now).sortedBy { it.time }.forEach { r ->
                        o.put(r.time.atZone(zone).toLocalDate().toString(), r.beatsPerMinute)
                    }
                } catch (_: Exception) {}
                out.put("rhr", o)
            }

            out.put("granted", grantedObj(granted))
            call.resolve(out)
        }
    }

    private suspend fun <T : Record> readAll(c: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val all = ArrayList<T>()
        var token: String? = null
        var guard = 0
        do {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = token
                )
            )
            all.addAll(resp.records)
            token = resp.pageToken
            guard++
        } while (token != null && guard < 20)
        return all
    }

    /** Schreibt Trainings als Krafttraining-Einheiten. workouts: [{id, start, end, title, notes}] */
    @PluginMethod
    fun hcWriteWorkouts(call: PluginCall) {
        val c = client()
        if (c == null) {
            call.reject("Health Connect ist nicht verfügbar", "unavailable")
            return
        }
        val arr: JSArray = call.getArray("workouts") ?: JSArray()
        val zone = ZoneId.systemDefault()
        val records = ArrayList<ExerciseSessionRecord>()
        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i) ?: continue
            val start = Instant.ofEpochMilli(w.optLong("start"))
            var end = Instant.ofEpochMilli(w.optLong("end"))
            if (!end.isAfter(start)) end = start.plusSeconds(60)
            records.add(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zone.rules.getOffset(start),
                    endTime = end,
                    endZoneOffset = zone.rules.getOffset(end),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = w.optString("title", "Training").ifBlank { "Training" },
                    notes = w.optString("notes", "").ifBlank { null },
                    metadata = Metadata(clientRecordId = "gym-" + w.optString("id"), clientRecordVersion = 1)
                )
            )
        }
        if (records.isEmpty()) {
            call.resolve(JSObject().put("written", 0))
            return
        }
        scope.launch {
            try {
                c.insertRecords(records)
                call.resolve(JSObject().put("written", records.size))
            } catch (e: SecurityException) {
                call.reject("Keine Berechtigung zum Schreiben von Trainings", "denied")
            } catch (e: Exception) {
                call.reject("Schreiben fehlgeschlagen: " + e.message)
            }
        }
    }

    companion object {
        private const val REST_ID = 4711
        private const val REST_CHANNEL = "gym_rest_timer"
        private const val HC_PACKAGE = "com.google.android.apps.healthdata"
    }
}
