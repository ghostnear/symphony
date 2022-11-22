package io.github.zyrouge.symphony.services.radio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Size
import androidx.core.app.NotificationCompat
import io.github.zyrouge.symphony.MainActivity
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.ui.helpers.Assets

class RadioNotification(private val symphony: Symphony) {
    private val session = MediaSessionCompat(
        symphony.applicationContext,
        MEDIA_SESSION_ID
    )
    private var style =
        androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(session.sessionToken)
    private var builder: NotificationCompat.Builder? = null
    private var manager = RadioNotificationManager(symphony)
    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                handleAction(action)
            }
        }
    }
    private var usable = false

    fun start() {
        symphony.applicationContext.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(ACTION_PLAY_PAUSE)
                addAction(ACTION_PREVIOUS)
                addAction(ACTION_NEXT)
                addAction(ACTION_STOP)
            }
        )
        session.isActive = true
        session.setCallback(
            object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    handleAction(ACTION_PLAY_PAUSE)
                }

                override fun onPause() {
                    super.onPause()
                    handleAction(ACTION_PLAY_PAUSE)
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    handleAction(ACTION_PREVIOUS)
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    handleAction(ACTION_NEXT)
                }

                override fun onStop() {
                    super.onStop()
                    handleAction(ACTION_STOP)
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    symphony.radio.seek(pos.toInt())
                }
            }
        )
        manager.prepare()
        builder = NotificationCompat.Builder(
            symphony.applicationContext,
            CHANNEL_ID
        ).run {
            setSmallIcon(R.drawable.material_icon_music_note)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setStyle(style)
            setContentIntent(
                PendingIntent.getActivity(
                    symphony.applicationContext,
                    0,
                    Intent(symphony.applicationContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        }
        usable = true
        update()
        symphony.radio.onUpdate.subscribe {
            when (it) {
                RadioEvents.StartPlaying,
                RadioEvents.PausePlaying,
                RadioEvents.ResumePlaying,
                RadioEvents.SongStaged,
                RadioEvents.SongSeeked,
                RadioEvents.QueueEnded -> update()
                else -> {}
            }
        }
    }

    fun destroy() {
        cancel()
        session.release()
        symphony.applicationContext.unregisterReceiver(receiver)
    }

    private fun update() {
        if (!usable) return
        when {
            symphony.radio.hasPlayer -> {
                symphony.radio.queue.currentPlayingSong?.let { song ->
                    val playbackPosition = symphony.radio.currentPlaybackPosition!!
                    val isPlaying = symphony.radio.isPlaying
                    session.run {
                        setMetadata(
                            MediaMetadataCompat.Builder().run {
                                putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artistName)
                                putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.albumName)
                                putString(
                                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                                    song.getArtworkUri(symphony).toString()
                                )
                                putLong(
                                    MediaMetadataCompat.METADATA_KEY_DURATION,
                                    playbackPosition.total.toLong()
                                )
                                build()
                            }
                        )
                        setPlaybackState(
                            PlaybackStateCompat.Builder().run {
                                setState(
                                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                                    else PlaybackStateCompat.STATE_PAUSED,
                                    playbackPosition.played.toLong(),
                                    1f
                                )
                                setActions(
                                    PlaybackStateCompat.ACTION_PLAY
                                            or PlaybackStateCompat.ACTION_PAUSE
                                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                            or PlaybackStateCompat.ACTION_SEEK_TO
                                )
                                build()
                            }
                        )
                    }
                    builder!!.run {
                        setContentTitle(song.title)
                        setContentText(song.artistName)
                        val iconSize = Size(500, 500)
                        setLargeIcon(
                            symphony.groove.album.getAlbumArtworkUriNullable(song.albumId)
                                ?.let { uri ->
                                    symphony.applicationContext.contentResolver.loadThumbnail(
                                        uri,
                                        iconSize,
                                        null
                                    )
                                } ?: Assets.getPlaceholderBitmap(symphony.applicationContext)
                        )
                        setOngoing(isPlaying)
                        clearActions()
                        addAction(
                            createAction(
                                R.drawable.material_icon_skip_previous,
                                symphony.t.previous,
                                ACTION_PREVIOUS
                            )
                        )
                        addAction(
                            when {
                                isPlaying -> createAction(
                                    R.drawable.material_icon_pause,
                                    symphony.t.play,
                                    ACTION_PLAY_PAUSE
                                )
                                else -> createAction(
                                    R.drawable.material_icon_play,
                                    symphony.t.pause,
                                    ACTION_PLAY_PAUSE
                                )
                            }
                        )
                        addAction(
                            createAction(
                                R.drawable.material_icon_skip_next,
                                symphony.t.next,
                                ACTION_NEXT
                            )
                        )
                        addAction(
                            createAction(
                                R.drawable.material_icon_stop,
                                symphony.t.stop,
                                ACTION_STOP
                            )
                        )
                        manager.notify(build())
                    }
                }
            }
            else -> cancel()
        }
    }

    private fun cancel() {
        session.isActive = false
        manager.cancel()
    }

    private fun createAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            icon, title, createActionIntent(action)
        ).build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            symphony.applicationContext,
            0,
            Intent(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun handleAction(action: String) {
        if (!usable) return
        when (action) {
            ACTION_PLAY_PAUSE -> symphony.radio.shorty.playPause()
            ACTION_PREVIOUS -> symphony.radio.shorty.previous()
            ACTION_NEXT -> symphony.radio.shorty.skip()
            ACTION_STOP -> symphony.radio.stop()
        }
    }

    companion object {
        const val CHANNEL_ID = "${R.string.app_name}_media_notification"
        const val NOTIFICATION_ID = 69421
        const val MEDIA_SESSION_ID = "${R.string.app_name}_media_session"

        const val ACTION_PLAY_PAUSE = "${R.string.app_name}_play_pause"
        const val ACTION_PREVIOUS = "${R.string.app_name}_previous"
        const val ACTION_NEXT = "${R.string.app_name}_next"
        const val ACTION_STOP = "${R.string.app_name}_stop"
    }
}
