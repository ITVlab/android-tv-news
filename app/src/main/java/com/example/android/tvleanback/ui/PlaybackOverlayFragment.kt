/*
 * Copyright (c) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.tvleanback.ui

import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS

import com.google.android.exoplayer.ExoPlayer
import com.google.android.exoplayer.util.Util

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.RemoteException
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.ClassPresenterSelector
import android.support.v17.leanback.widget.CursorObjectAdapter
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.ListRowPresenter
import android.support.v17.leanback.widget.OnItemViewClickedListener
import android.support.v17.leanback.widget.PlaybackControlsRow
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter
import android.support.v17.leanback.widget.Presenter
import android.support.v17.leanback.widget.Row
import android.support.v17.leanback.widget.RowPresenter
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.Surface
import android.view.TextureView

import com.bumptech.glide.Glide
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.example.android.tvleanback.R
import com.example.android.tvleanback.Utils
import com.example.android.tvleanback.model.Video
import com.example.android.tvleanback.player.ExtractorRendererBuilder
import com.example.android.tvleanback.player.VideoPlayer
import com.example.android.tvleanback.presenter.CardPresenter

import java.util.ArrayList

/*
 * The PlaybackOverlayFragment class handles the Fragment associated with displaying the UI for the
 * media controls such as play / pause / skip forward / skip backward etc.
 *
 * The UI is updated through events that it receives from its MediaController
 */
class PlaybackOverlayFragment : android.support.v17.leanback.app.PlaybackOverlayFragment(), TextureView.SurfaceTextureListener, VideoPlayer.Listener {

    private var mQueueIndex = -1
    private var mSelectedVideo: Video? = null // Video is the currently playing Video and its metadata.
    private var mRowsAdapter: ArrayObjectAdapter? = null
    private val mQueue = ArrayList<MediaSessionCompat.QueueItem>()
    private var mVideoCursorAdapter: CursorObjectAdapter? = null
    private var mSession: MediaSessionCompat? = null // MediaSession is used to hold the state of our media playback.
    private var mMediaController: MediaController? = null
    private var mGlue: PlaybackControlHelper? = null
    private var mMediaControllerCallback: MediaController.Callback? = null
    private var mPlayer: VideoPlayer? = null
    private var mIsMetadataSet = false
    private var mAudioManager: AudioManager? = null
    private var mHasAudioFocus: Boolean = false
    private var mPauseTransient: Boolean = false
    private val mOnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                abandonAudioFocus()
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (mGlue!!.isMediaPlaying) {
                pause()
                mPauseTransient = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mPlayer!!.mute(true)
            AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                if (mPauseTransient) {
                    play()
                }
                mPlayer!!.mute(false)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        createMediaSession()
    }

    override fun onStop() {
        super.onStop()

        mSession!!.release()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mMediaController != null) {
            mMediaController!!.unregisterCallback(mMediaControllerCallback!!)
        }
        mSession!!.release()
        releasePlayer()
    }

    override fun onStart() {
        super.onStart()

        // Set up UI

        mGlue = PlaybackControlHelper(activity, this, mSelectedVideo)
        val controlsRowPresenter = mGlue!!.createControlsRowAndPresenter()
        val controlsRow = mGlue!!.controlsRow
        mMediaControllerCallback = mGlue!!.createMediaControllerCallback()

        mMediaController = activity.mediaController
        mMediaController!!.registerCallback(mMediaControllerCallback!!)

        val ps = ClassPresenterSelector()
        ps.addClassPresenter(PlaybackControlsRow::class.java, controlsRowPresenter)
        ps.addClassPresenter(ListRow::class.java, ListRowPresenter())
        mRowsAdapter = ArrayObjectAdapter(ps)
        mRowsAdapter!!.add(controlsRow)
        addOtherRows()
        updatePlaybackRow()
        adapter = mRowsAdapter!!

        startPlaying()
    }

    override fun onResume() {
        super.onResume()
        startPlaying()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAudioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Initialize instance variables.
        val textureView = activity.findViewById(R.id.texture_view) as TextureView
        textureView.surfaceTextureListener = this

        backgroundType = BACKGROUND_TYPE

        // Set up listener.
        onItemViewClickedListener = ItemViewClickedListener()
    }

    private fun updateSelectedVideo(video: Video): Boolean {
        val intent = Intent(activity.intent)

        // Reconstruct the video from intent data.
        val builder = Video.Builder()
        builder.videoUrl = intent.getStringExtra(PlaybackOverlayActivity.EXTRA_MEDIA_URL)
        builder.title = intent.getStringExtra(PlaybackOverlayActivity.EXTRA_MEDIA_TITLE)
        builder.bgImageUrl = intent.getStringExtra(PlaybackOverlayActivity.EXTRA_MEDIA_IMG)
        builder.cardImageUrl = intent.getStringExtra(PlaybackOverlayActivity.EXTRA_MEDIA_IMG)
        mSelectedVideo = builder.build()

        val pi = PendingIntent.getActivity(
                activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        mSession!!.setSessionActivity(pi)

        return true
    }

    @TargetApi(VERSION_CODES.N)
    override fun onPause() {
        super.onPause()
        if (mGlue!!.isMediaPlaying) {
            val isVisibleBehind = activity.requestVisibleBehind(true)
            val isInPictureInPictureMode = PlaybackOverlayActivity.supportsPictureInPicture(activity) && activity.isInPictureInPictureMode
            if (!isVisibleBehind && !isInPictureInPictureMode) {
                pause()
            }
        } else {
            activity.requestVisibleBehind(false)
        }
    }

    override fun onPictureInPictureModeChanged(pictureInPictureMode: Boolean) {
        if (pictureInPictureMode) {
            mGlue!!.isFadingEnabled = false
            isFadingEnabled = true
            fadeOut()
        } else {
            mGlue!!.isFadingEnabled = true
        }
    }

    private fun setPosition(position: Long) {
        if (position > mPlayer!!.duration) {
            mPlayer!!.seekTo(mPlayer!!.duration)
        } else if (position < 0) {
            mPlayer!!.seekTo(0L)
        } else {
            mPlayer!!.seekTo(position)
        }
    }

    private fun createMediaSession() {
        if (mSession == null) {
            mSession = MediaSessionCompat(activity, "LeanbackSampleApp")
            mSession!!.setCallback(MediaSessionCallback())
            mSession!!.setFlags(FLAG_HANDLES_MEDIA_BUTTONS or FLAG_HANDLES_TRANSPORT_CONTROLS)
            mSession!!.isActive = true

            // Set the Activity's MediaController used to invoke transport controls / adjust volume.
            try {
                (activity as FragmentActivity).supportMediaController = MediaControllerCompat(activity, mSession!!.sessionToken)
                playbackState = PlaybackState.STATE_NONE
            } catch (e: RemoteException) {
                e.printStackTrace()
            }

        }
    }

    private fun getQueueItem(v: Video): MediaSessionCompat.QueueItem {
        val desc = MediaDescriptionCompat.Builder()
                .setDescription(v.description)
                .setMediaId(v.id.toString() + "")
                .setIconUri(Uri.parse(v.cardImageUrl))
                .setMediaUri(Uri.parse(v.videoUrl))
                .setSubtitle(v.studio)
                .setTitle(v.title)
                .build()
        return MediaSessionCompat.QueueItem(desc, v.id)
    }

    val bufferedPosition: Long
        get() {
            if (mPlayer != null) {
                return mPlayer!!.bufferedPosition
            }
            return 0L
        }

    val currentPosition: Long
        get() {
            if (mPlayer != null) {
                return mPlayer!!.currentPosition
            }
            return 0L
        }

    val duration: Long
        get() {
            if (mPlayer != null) {
                return mPlayer!!.duration
            }
            return ExoPlayer.UNKNOWN_TIME
        }

    private fun getAvailableActions(nextState: Int): Long {
        var actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackState.ACTION_PLAY_FROM_SEARCH or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_PAUSE

        if (nextState == PlaybackState.STATE_PLAYING) {
            actions = actions or PlaybackState.ACTION_PAUSE
        }

        return actions
    }

    private fun play() {
        // Request audio focus whenever we resume playback
        // because the app might have abandoned audio focus due to the AUDIOFOCUS_LOSS.
        requestAudioFocus()

        if (mPlayer == null) {
            playbackState = PlaybackState.STATE_NONE
            return
        }
        if (!mGlue!!.isMediaPlaying) {
            mPlayer!!.playerControl.start()
            playbackState = PlaybackState.STATE_PLAYING
        }
    }

    private fun pause() {
        mPauseTransient = false

        if (mPlayer == null) {
            playbackState = PlaybackState.STATE_NONE
            return
        }
        if (mGlue!!.isMediaPlaying) {
            mPlayer!!.playerControl.pause()
            playbackState = PlaybackState.STATE_PAUSED
        }
    }

    private fun requestAudioFocus() {
        if (mHasAudioFocus) {
            return
        }
        val result = mAudioManager!!.requestAudioFocus(mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mHasAudioFocus = true
        } else {
            pause()
        }
    }

    private fun abandonAudioFocus() {
        mHasAudioFocus = false
        mAudioManager!!.abandonAudioFocus(mOnAudioFocusChangeListener)
    }

    internal fun updatePlaybackRow() {
        mRowsAdapter!!.notifyArrayItemRangeChanged(0, 1)
    }

    /**
     * Creates a ListRow for related videos.
     */
    private fun addOtherRows() {
        mVideoCursorAdapter = CursorObjectAdapter(CardPresenter())

        val args = Bundle()

        val header = HeaderItem(getString(R.string.related_movies))
        mRowsAdapter!!.add(ListRow(header, mVideoCursorAdapter))
    }

    private val rendererBuilder: VideoPlayer.RendererBuilder
        get() {
            val userAgent = Util.getUserAgent(activity, "ExoVideoPlayer")
            val contentUri = Uri.parse(mSelectedVideo!!.videoUrl)
            val contentType = Util.inferContentType(contentUri.lastPathSegment)

            when (contentType) {
                Util.TYPE_OTHER -> {
                    return ExtractorRendererBuilder(activity, userAgent, contentUri)
                }
                else -> {
                    throw IllegalStateException("Unsupported type: " + contentType)
                }
            }
        }

    private fun preparePlayer() {
        if (mPlayer == null) {
            mPlayer = VideoPlayer(rendererBuilder)
            mPlayer!!.addListener(this)
            mPlayer!!.seekTo(0L)
            mPlayer!!.prepare()
        } else {
            mPlayer!!.stop()
            mPlayer!!.seekTo(0L)
            mPlayer!!.setRendererBuilder(rendererBuilder)
            mPlayer!!.prepare()
        }
        mPlayer!!.playWhenReady = true

        requestAudioFocus()
    }

    private fun releasePlayer() {
        if (mPlayer != null) {
            mPlayer!!.release()
            mPlayer = null
        }
        abandonAudioFocus()
    }

    override fun onStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            ExoPlayer.STATE_BUFFERING -> {
            }
            ExoPlayer.STATE_ENDED -> {
                mIsMetadataSet = false
                mMediaController!!.transportControls.skipToNext()
            }
            ExoPlayer.STATE_IDLE -> {
            }
            ExoPlayer.STATE_PREPARING -> mIsMetadataSet = false
            ExoPlayer.STATE_READY ->
                // Duration is set here.
                if (!mIsMetadataSet) {
                    updateMetadata(mSelectedVideo)
                    mIsMetadataSet = true
                }
            else -> {
            }
        }// Do nothing.
        // Do nothing.
        // Do nothing.
    }

    override fun onError(e: Exception) {
        Log.e(TAG, "An error occurred: " + e)
    }

    override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int,
                                    pixelWidthHeightRatio: Float) {
        // Do nothing.
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (mPlayer != null) {
            mPlayer!!.surface = Surface(surfaceTexture)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // Do nothing.
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (mPlayer != null) {
            mPlayer!!.blockingClearSurface()
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Do nothing.
    }

    private var playbackState: Int
        get() {
            val activity = activity

            if (activity != null) {
                val state = activity.mediaController.playbackState
                if (state != null) {
                    return state.state
                } else {
                    return PlaybackState.STATE_NONE
                }
            }
            return PlaybackState.STATE_NONE
        }
        set(state) {
            val currPosition = currentPosition

            val stateBuilder = PlaybackStateCompat.Builder()
                    .setActions(getAvailableActions(state))
            stateBuilder.setState(state, currPosition, 1.0f)
            mSession!!.setPlaybackState(stateBuilder.build())
        }

    private fun updateMetadata(video: Video) {
        val metadataBuilder = MediaMetadataCompat.Builder()

        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, video.id.toString() + "")
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, video.title)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, video.studio)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                video.description)

        val duration = Utils.getDuration(video.videoUrl)
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        // And at minimum the title and artist for legacy support
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.title)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.studio)

        val res = resources
        val cardWidth = res.getDimensionPixelSize(R.dimen.playback_overlay_width)
        val cardHeight = res.getDimensionPixelSize(R.dimen.playback_overlay_height)

        Glide.with(this)
                .load(Uri.parse(video.cardImageUrl))
                .asBitmap()
                .centerCrop()
                .into<>(object : SimpleTarget<Bitmap>(cardWidth, cardHeight) {
                    override fun onResourceReady(bitmap: Bitmap, anim: GlideAnimation<*>) {
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                        mSession!!.setMetadata(metadataBuilder.build())
                    }
                })
    }

    private fun playVideo(video: Video, extras: Bundle) {
        updateSelectedVideo(video)
        preparePlayer()
        playbackState = PlaybackState.STATE_PAUSED
        if (extras.getBoolean(AUTO_PLAY)) {
            play()
        } else {
            pause()
        }
    }

    private fun startPlaying() {
        // Prepare the player and start playing the selected video
        playVideo(mSelectedVideo, mAutoPlayExtras)

        // Start loading videos for the queue
        val args = Bundle()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any,
                                   rowViewHolder: RowPresenter.ViewHolder, row: Row) {

            if (item is Video) {
                val video = item
                val intent = Intent(activity, PlaybackOverlayActivity::class.java)
                activity.startActivity(intent)
            }
        }
    }

    // An event was triggered by MediaController.TransportControls and must be handled here.
    // Here we update the media itself to act on the event that was triggered.
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPlay() {
            play()
        }

        override // This method should play any media item regardless of the Queue.
        fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val args = Bundle()
        }

        override fun onPause() {
            pause()
        }

        override fun onSkipToNext() {
            // Update the media to skip to the next video.
            val bundle = Bundle()
            bundle.putBoolean(AUTO_PLAY, true)

            val nextIndex = ++mQueueIndex
            if (nextIndex < mQueue.size) {
                val item = mQueue[nextIndex]
                val mediaId = item.description.mediaId
                activity.mediaController
                        .transportControls
                        .playFromMediaId(mediaId, bundle)
            } else {
                activity.onBackPressed() // Return to details presenter.
            }
        }

        override fun onSkipToPrevious() {
            // Update the media to skip to the previous video.
            playbackState = PlaybackState.STATE_SKIPPING_TO_PREVIOUS

            val bundle = Bundle()
            bundle.putBoolean(AUTO_PLAY, true)

            val prevIndex = --mQueueIndex
            if (prevIndex >= 0) {
                val item = mQueue[prevIndex]
                val mediaId = item.description.mediaId

                activity.mediaController
                        .transportControls
                        .playFromMediaId(mediaId, bundle)
            } else {
                activity.onBackPressed() // Return to details presenter.
            }
        }

        override fun onFastForward() {
            if (mPlayer!!.duration != ExoPlayer.UNKNOWN_TIME) {
                // Fast forward 10 seconds.
                val prevState = playbackState
                playbackState = PlaybackState.STATE_FAST_FORWARDING
                setPosition(mPlayer!!.currentPosition + 10 * 1000)
                playbackState = prevState
            }
        }

        override fun onRewind() {
            // Rewind 10 seconds.
            val prevState = playbackState
            playbackState = PlaybackState.STATE_REWINDING
            setPosition(mPlayer!!.currentPosition - 10 * 1000)
            playbackState = prevState
        }

        override fun onSeekTo(position: Long) {
            setPosition(position)
        }
    }

    companion object {
        private val TAG = "PlaybackOverlayFragment"
        private val BACKGROUND_TYPE = PlaybackOverlayFragment.BG_LIGHT
        private val AUTO_PLAY = "auto_play"
        private val mAutoPlayExtras = Bundle()

        init {
            mAutoPlayExtras.putBoolean(AUTO_PLAY, true)
        }
    }
}
