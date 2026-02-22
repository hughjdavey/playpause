package com.example.mediaremote

import android.service.notification.NotificationListenerService

/**
 * This service doesn't do anything — it exists purely so that the app can appear
 * in Settings → Notification access, which is the permission gate for reading
 * active MediaSessions from other apps via MediaSessionManager.getActiveSessions().
 *
 * The user grants this once; after that MainActivity can connect to the real
 * playback state of whatever podcast/audiobook app is active.
 */
class MediaListenerService : NotificationListenerService()
