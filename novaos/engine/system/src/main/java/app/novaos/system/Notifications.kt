package app.novaos.system

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.novaos.model.NotificationActionEntry
import app.novaos.model.NotificationEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Nova Notification Integration über die offizielle NotificationListenerService-API.
 * Funktioniert erst, wenn der Benutzer den Benachrichtigungszugriff in den Android-Einstellungen erlaubt.
 */
class NovaNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationHub.attach(this)
        refreshAll()
        MediaHub.onListenerConnected(applicationContext)
    }

    override fun onListenerDisconnected() {
        NotificationHub.detach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refreshAll()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refreshAll()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refreshAll()

    private fun refreshAll() {
        val list = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        NotificationHub.publish(applicationContext, list)
    }
}

object NotificationHub {
    private var service: NovaNotificationService? = null
    private val raw = HashMap<String, StatusBarNotification>()

    private val _entries = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val entries: StateFlow<List<NotificationEntry>> = _entries

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /** Anzahl Benachrichtigungen je Paket (für App-Badges). */
    private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())
    val badges: StateFlow<Map<String, Int>> = _badges

    internal fun attach(s: NovaNotificationService) { service = s; _connected.value = true }
    internal fun detach() { service = null; _connected.value = false }

    fun isAccessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(context, NovaNotificationService::class.java).flattenToString()
        return flat.split(':').any { it == me }
    }

    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, NovaNotificationService::class.java).flattenToString())

    internal fun publish(context: Context, list: List<StatusBarNotification>) {
        val pm = context.packageManager
        val labels = HashMap<String, String>()
        fun label(pkg: String) = labels.getOrPut(pkg) {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString() }.getOrDefault(pkg)
        }
        synchronized(raw) {
            raw.clear()
            list.forEach { raw[it.key] = it }
        }
        val entries = list
            .filter { sbn ->
                val n = sbn.notification
                // Gruppen-Zusammenfassungen ausblenden, wenn Einzelbenachrichtigungen vorhanden sind.
                !(n.flags and Notification.FLAG_GROUP_SUMMARY != 0 && list.any { it !== sbn && it.groupKey == sbn.groupKey })
            }
            .filter { it.notification.category != Notification.CATEGORY_TRANSPORT || it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null }
            .map { sbn ->
                val n = sbn.notification
                val ex = n.extras
                val title = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                    ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG)
                    ?: ex.getCharSequence(Notification.EXTRA_TITLE)
                val text = messagingText(ex)
                    ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: ex.getCharSequence(Notification.EXTRA_TEXT)
                NotificationEntry(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = label(sbn.packageName),
                    title = title?.toString().orEmpty(),
                    text = text?.toString().orEmpty(),
                    postedAtMs = sbn.postTime,
                    priority = legacyPriority(n),
                    isOngoing = sbn.isOngoing,
                    isClearable = sbn.isClearable,
                    groupKey = sbn.groupKey,
                    category = n.category,
                    isMessage = n.category == Notification.CATEGORY_MESSAGE || ex.containsKey(Notification.EXTRA_MESSAGES),
                    actions = n.actions.orEmpty().mapIndexed { i, a ->
                        NotificationActionEntry(i, a.title?.toString().orEmpty(), a.remoteInputs?.any { it.allowFreeFormInput } == true)
                    }.filter { it.title.isNotBlank() },
                )
            }
        _entries.value = entries
        _badges.value = entries.filter { !it.isOngoing }.groupingBy { it.packageName }.eachCount()
    }

    @Suppress("DEPRECATION")
    private fun legacyPriority(n: Notification): Int = n.priority

    private fun messagingText(ex: Bundle): CharSequence? {
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = msgs.lastOrNull() as? Bundle ?: return null
        return last.getCharSequence("text")
    }

    fun smallIcon(key: String): Icon? = synchronized(raw) { raw[key]?.notification?.smallIcon }
    fun largeIcon(key: String): Icon? = synchronized(raw) { raw[key]?.notification?.getLargeIcon() }
    fun accentColor(key: String): Int? = synchronized(raw) { raw[key]?.notification?.color?.takeIf { it != 0 } }

    /** Öffnet die Benachrichtigung (contentIntent) und entfernt sie, wenn sie autoCancel ist. */
    fun open(key: String): Boolean {
        val sbn = synchronized(raw) { raw[key] } ?: return false
        val pi = sbn.notification.contentIntent ?: return false
        return try {
            pi.send()
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) dismiss(key)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
    }

    fun dismissAll() {
        runCatching { service?.cancelAllNotifications() }
    }

    /** Führt eine Schnellaktion aus; mit [reply] wird eine Direktantwort (RemoteInput) gesendet. */
    fun performAction(context: Context, key: String, index: Int, reply: String? = null): Boolean {
        val sbn = synchronized(raw) { raw[key] } ?: return false
        val action = sbn.notification.actions?.getOrNull(index) ?: return false
        return try {
            val inputs = action.remoteInputs
            if (reply != null && inputs != null && inputs.isNotEmpty()) {
                val intent = Intent()
                val results = Bundle()
                inputs.forEach { results.putCharSequence(it.resultKey, reply) }
                RemoteInput.addResultsToIntent(inputs, intent, results)
                action.actionIntent.send(context, 0, intent)
            } else {
                action.actionIntent.send()
            }
            true
        } catch (e: Exception) {
            Log.w("NovaOS", "Aktion fehlgeschlagen", e)
            false
        }
    }
}

data class MediaState(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
    val speed: Float,
    val queue: List<String>,
) {
    fun currentPosition(nowElapsed: Long): Long =
        if (!playing) positionMs else (positionMs + ((nowElapsed - updatedAtMs) * speed).toLong()).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
}

/** Mediensteuerung über MediaSessionManager (erfordert Benachrichtigungszugriff, Android-Vorgabe). */
object MediaHub {
    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = update()
        override fun onSessionDestroyed() { controller = null; _state.value = null }
    }

    fun onListenerConnected(context: Context) {
        runCatching {
            val m = context.getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(context, NovaNotificationService::class.java)
            manager = m
            m.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            pick(m.getActiveSessions(component))
        }.onFailure { Log.w("NovaOS", "MediaSession nicht verfügbar", it) }
    }

    /** Neu einlesen (z. B. wenn der Homescreen sichtbar wird). */
    fun refresh(context: Context) {
        val m = manager ?: return onListenerConnected(context)
        runCatching { pick(m.getActiveSessions(ComponentName(context, NovaNotificationService::class.java))) }
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        update()
    }

    private fun update() {
        val c = controller ?: run { _state.value = null; return }
        val md = c.metadata
        val ps = c.playbackState
        if (md == null && ps == null) { _state.value = null; return }
        _state.value = MediaState(
            packageName = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
            positionMs = ps?.position ?: 0,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            updatedAtMs = ps?.lastPositionUpdateTime ?: 0,
            speed = ps?.playbackSpeed ?: 1f,
            queue = c.queue.orEmpty().take(20).map { it.description.title?.toString().orEmpty() },
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }
    fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        c.queue?.getOrNull(index)?.let { c.transportControls.skipToQueueItem(it.queueId) }
    }

    fun openPlayer(context: Context) {
        val c = controller ?: return
        val pi = c.sessionActivity
        if (pi != null) runCatching { pi.send() } else {
            context.packageManager.getLaunchIntentForPackage(c.packageName)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}
