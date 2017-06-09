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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.os.BuildCompat
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent

import com.example.android.tvleanback.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd

/**
 * PlaybackOverlayActivity for video playback that loads PlaybackOverlayFragment and handles the
 * MediaSession object used to maintain the state of the media playback.
 */
class PlaybackOverlayActivity : LeanbackActivity() {
    private var gamepadTriggerPressed = false

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Change the intent returned by {@link #getIntent()}.
        // Note that getIntent() only returns the initial intent that created the activity
        // but we need the latest intent that contains the information of the latest video
        // that the user is selected.
        setIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onVisibleBehindCanceled() {
        mediaController.transportControls.pause()
        super.onVisibleBehindCanceled()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            mediaController.transportControls.skipToNext()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            mediaController.transportControls.skipToPrevious()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            mediaController.transportControls.rewind()
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            mediaController.transportControls.fastForward()
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // This method will handle gamepad events
        if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON && !gamepadTriggerPressed) {
            mediaController.transportControls.rewind()
            gamepadTriggerPressed = true
        } else if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON && !gamepadTriggerPressed) {
            mediaController.transportControls.fastForward()
            gamepadTriggerPressed = true
        } else if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF && event.getAxisValue(MotionEvent.AXIS_RTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF) {
            gamepadTriggerPressed = false
        }
        return super.onGenericMotionEvent(event)
    }

    companion object {
        private val GAMEPAD_TRIGGER_INTENSITY_ON = 0.5f
        // Off-condition slightly smaller for button debouncing
        private val GAMEPAD_TRIGGER_INTENSITY_OFF = 0.45f

        fun supportsPictureInPicture(context: Context): Boolean {
            return BuildCompat.isAtLeastN() && context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }

        val EXTRA_MEDIA_URL = "content"
        val EXTRA_MEDIA_TITLE = "title"
        val EXTRA_MEDIA_IMG = "bitmap"
    }
}
