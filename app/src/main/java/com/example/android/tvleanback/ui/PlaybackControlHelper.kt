package com.example.android.tvleanback.ui

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.support.v17.leanback.app.PlaybackControlGlue
import android.support.v17.leanback.widget.Action
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.ControlButtonPresenterSelector
import android.support.v17.leanback.widget.OnActionClickedListener
import android.support.v17.leanback.widget.PlaybackControlsRow
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter
import android.support.v17.leanback.widget.SparseArrayObjectAdapter
import android.util.Log
import android.view.View

import com.example.android.tvleanback.BuildConfig
import com.example.android.tvleanback.model.Video

internal class PlaybackControlHelper(context: Context, private val mFragment: PlaybackOverlayFragment, private var mVideo: Video?) : PlaybackControlGlue(context, mFragment, PlaybackControlHelper.SEEK_SPEEDS) {
    var mMediaArt: Drawable? = null
    private val mMediaController: MediaController?
    private val mTransportControls: MediaController.TransportControls
    private val mRepeatAction: PlaybackControlsRow.RepeatAction? = null
    private val mThumbsUpAction: PlaybackControlsRow.ThumbsUpAction? = null
    private val mThumbsDownAction: PlaybackControlsRow.ThumbsDownAction? = null
    private var mFastForwardAction: PlaybackControlsRow.FastForwardAction? = null
    private var mRewindAction: PlaybackControlsRow.RewindAction? = null
    private val mPipAction: PlaybackControlsRow.PictureInPictureAction
    private val mHandler = Handler()
    private var mUpdateProgressRunnable: Runnable? = null

    init {
        mMediaController = mFragment.activity.mediaController
        if (mMediaController == null) {
            throw NullPointerException("Media controller never set")
        }
        mTransportControls = mMediaController.transportControls

        /*        mThumbsUpAction = new PlaybackControlsRow.ThumbsUpAction(context);
        mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsUpAction.OUTLINE);
        mThumbsDownAction = new PlaybackControlsRow.ThumbsDownAction(context);
        mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsDownAction.OUTLINE);
        mRepeatAction = new PlaybackControlsRow.RepeatAction(context);*/
        mPipAction = PlaybackControlsRow.PictureInPictureAction(context)
    }

    fun createMediaControllerCallback(): MediaController.Callback {
        return MediaControllerCallback()
    }

    override fun createControlsRowAndPresenter(): PlaybackControlsRowPresenter {
        val presenter = super.createControlsRowAndPresenter()

        val adapter = ArrayObjectAdapter(ControlButtonPresenterSelector())
        controlsRow.secondaryActionsAdapter = adapter

        mFastForwardAction = primaryActionsAdapter
                .lookup(PlaybackControlGlue.ACTION_FAST_FORWARD) as PlaybackControlsRow.FastForwardAction

        mRewindAction = primaryActionsAdapter
                .lookup(PlaybackControlGlue.ACTION_REWIND) as PlaybackControlsRow.RewindAction

        //        adapter.add(mThumbsDownAction);
        //        adapter.add(mRepeatAction);
        //        adapter.add(mThumbsUpAction);
        if (PlaybackOverlayActivity.supportsPictureInPicture(context)) {
            adapter.add(mPipAction)
        }

        presenter.onActionClickedListener = OnActionClickedListener { action -> dispatchAction(action) }

        return presenter
    }

    override fun enableProgressUpdating(enable: Boolean) {
        mHandler.removeCallbacks(mUpdateProgressRunnable)
        if (enable) {
            mHandler.post(mUpdateProgressRunnable)
        }
    }

    override fun getUpdatePeriod(): Int {
        val view = mFragment.view
        val totalTime = controlsRow.totalTime
        if (view == null || totalTime <= 0 || view.width == 0) {
            return DEFAULT_UPDATE_PERIOD
        }
        return Math.max(UPDATE_PERIOD, totalTime / view.width)
    }

    override fun updateProgress() {
        if (mUpdateProgressRunnable == null) {
            mUpdateProgressRunnable = Runnable {
                val totalTime = controlsRow.totalTime
                val currentTime = currentPosition
                controlsRow.currentTime = currentTime

                val progress = mFragment.bufferedPosition.toInt()
                controlsRow.bufferedProgress = progress

                if (totalTime > 0 && totalTime <= currentTime) {
                    stopProgressAnimation()
                } else {
                    updateProgress()
                }
            }
        }

        mHandler.postDelayed(mUpdateProgressRunnable, updatePeriod.toLong())
    }

    override fun hasValidMedia(): Boolean {
        return mVideo != null
    }

    override fun isMediaPlaying(): Boolean {
        if (mMediaController!!.playbackState == null) {
            return false
        }
        val state = mMediaController.playbackState!!.state
        return state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING
                || state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_FAST_FORWARDING
                || state == PlaybackState.STATE_REWINDING
                || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
                || state == PlaybackState.STATE_SKIPPING_TO_NEXT
    }

    override fun getMediaTitle(): CharSequence {
        return mVideo!!.title
    }

    override fun getMediaSubtitle(): CharSequence {
        return mVideo!!.description
    }

    override fun getMediaDuration(): Int {
        return mFragment.duration.toInt()
    }

    override fun getMediaArt(): Drawable? {
        return mMediaArt
    }

    override fun getSupportedActions(): Long {
        return (PlaybackControlGlue.ACTION_PLAY_PAUSE or PlaybackControlGlue.ACTION_FAST_FORWARD or PlaybackControlGlue.ACTION_REWIND).toLong() /* | ACTION_SKIP_TO_PREVIOUS |
                ACTION_SKIP_TO_NEXT*/
    }

    override fun getCurrentSpeedId(): Int {
        return if (isMediaPlaying) PlaybackControlGlue.PLAYBACK_SPEED_NORMAL else PlaybackControlGlue.PLAYBACK_SPEED_PAUSED
    }

    override fun getCurrentPosition(): Int {
        return mFragment.currentPosition.toInt()
    }

    override fun startPlayback(speed: Int) {
        if (currentSpeedId == speed) {
            return
        }
        mTransportControls.play()
    }

    override fun pausePlayback() {
        mTransportControls.pause()
    }

    override fun skipToNext() {
        mTransportControls.skipToNext()
    }

    override fun skipToPrevious() {
        mTransportControls.skipToPrevious()
    }

    override fun onRowChanged(row: PlaybackControlsRow?) {
        // Do nothing.
    }

    override fun onMetadataChanged() {
        val metadata = mFragment.activity.mediaController.metadata
        if (metadata != null) {
            mVideo = Video.Builder().buildFromMediaDesc(metadata.description)
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).toInt()
            controlsRow.totalTime = duration
            mMediaArt = BitmapDrawable(mFragment.resources,
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ART))
        }
        super.onMetadataChanged()
    }

    @TargetApi(VERSION_CODES.N)
    fun dispatchAction(action: Action) {
        if (action is PlaybackControlsRow.MultiAction) {
            val multiAction = action
            multiAction.nextIndex()
            notifyActionChanged(multiAction)
        }

        if (action === mFastForwardAction) {
            mTransportControls.fastForward()
        } else if (action === mRewindAction) {
            mTransportControls.rewind()
        } else if (action.id == mPipAction.id) {
            (context as Activity).enterPictureInPictureMode()
        } else if (action.toString() == "Play") {
            mTransportControls.play()
        } else if (action.toString() == "Pause") {
            mTransportControls.pause()
        } else {
            Log.d(TAG, action.toString())
            super.onActionClicked(action)
        }
    }

    private fun notifyActionChanged(action: PlaybackControlsRow.MultiAction) {
        var index: Int
        index = primaryActionsAdapter.indexOf(action)
        if (index >= 0) {
            primaryActionsAdapter.notifyArrayItemRangeChanged(index, 1)
        } else {
            index = secondaryActionsAdapter.indexOf(action)
            if (index >= 0) {
                secondaryActionsAdapter.notifyArrayItemRangeChanged(index, 1)
            }
        }
    }

    private fun stopProgressAnimation() {
        if (mHandler != null && mUpdateProgressRunnable != null) {
            mHandler.removeCallbacks(mUpdateProgressRunnable)
            mUpdateProgressRunnable = null
        }
    }

    private val primaryActionsAdapter: SparseArrayObjectAdapter
        get() = controlsRow.primaryActionsAdapter as SparseArrayObjectAdapter

    private val secondaryActionsAdapter: ArrayObjectAdapter
        get() = controlsRow.secondaryActionsAdapter as ArrayObjectAdapter

    private inner class MediaControllerCallback : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState) {
            // Update your UI to reflect the new state. Do not change media playback here.

            val nextState = state.state
            if (nextState != PlaybackState.STATE_NONE) {
                updateProgress()
            }
            onStateChanged()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            this@PlaybackControlHelper.onMetadataChanged() // Update metadata on controls.
        }
    }

    companion object {
        private val TAG = "PlaybackControlHelper"
        private val DEBUG = BuildConfig.DEBUG

        private val SEEK_SPEEDS = intArrayOf(2) // A single seek speed for fast-forward / rewind.
        private val DEFAULT_UPDATE_PERIOD = 500
        private val UPDATE_PERIOD = 16
    }
}
