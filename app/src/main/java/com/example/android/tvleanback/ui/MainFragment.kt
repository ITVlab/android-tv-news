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
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.app.BrowseFragment
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.CursorObjectAdapter
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.ListRowPresenter
import android.support.v17.leanback.widget.OnItemViewClickedListener
import android.support.v17.leanback.widget.OnItemViewSelectedListener
import android.support.v17.leanback.widget.Presenter
import android.support.v17.leanback.widget.PresenterSelector
import android.support.v17.leanback.widget.Row
import android.support.v17.leanback.widget.RowPresenter
import android.support.v4.content.ContextCompat
import android.util.DisplayMetrics
import android.view.View

import com.bumptech.glide.Glide
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.example.android.tvleanback.R
import com.example.android.tvleanback.model.Card
import com.example.android.tvleanback.presenter.CardPresenter
import com.example.android.tvleanback.presenter.IconHeaderItemPresenter
import com.example.android.tvleanback.recommendation.UpdateRecommendationsService

import java.util.HashMap
import java.util.Timer
import java.util.TimerTask

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
class MainFragment : BrowseFragment() {
    private val mHandler = Handler()
    private var mCategoryRowAdapter: ArrayObjectAdapter? = null
    private var mDefaultBackground: Drawable? = null
    private var mMetrics: DisplayMetrics? = null
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundURI: Uri? = null
    private var mBackgroundManager: BackgroundManager? = null

    // Maps a Loader Id to its CursorObjectAdapter.
    private var mVideoCursorAdapters: Map<Int, CursorObjectAdapter>? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // Create a list to contain all the CursorObjectAdapters.
        // Each adapter is used to render a specific row of videos in the MainFragment.
        mVideoCursorAdapters = HashMap<Int, CursorObjectAdapter>()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState)

        // Prepare the manager that maintains the same background image between activities.
        prepareBackgroundManager()

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = ArrayObjectAdapter(ListRowPresenter())

        setupUIElements()
        setupEventListeners()
        prepareEntranceTransition()

        adapter = mCategoryRowAdapter

        updateRecommendations()
    }

    override fun onDestroy() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer!!.cancel()
            mBackgroundTimer = null
        }
        mBackgroundManager = null
        super.onDestroy()
    }

    override fun onStop() {
        mBackgroundManager!!.release()
        super.onStop()
    }

    private fun prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(activity)
        mBackgroundManager!!.attach(activity.window)
        mDefaultBackground = resources.getDrawable(R.drawable.default_background, null)
        mMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(mMetrics)
    }

    private fun setupUIElements() {
        badgeDrawable = activity.resources.getDrawable(R.drawable.videos_by_google_banner, null)
        title = getString(R.string.browse_title) // Badge, when set, takes precedent over title
        headersState = BrowseFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // Set fastLane (or headers) background color
        brandColor = ContextCompat.getColor(activity, R.color.fastlane_background)

        // Set search icon color.
        searchAffordanceColor = ContextCompat.getColor(activity, R.color.search_opaque)

        setHeaderPresenterSelector(object : PresenterSelector() {
            override fun getPresenter(o: Any): Presenter {
                return IconHeaderItemPresenter()
            }
        })

        // Setup the several rows.
        setupRowArticles()
        setupRowVideos()
        setupRowPodcasts()
        setupRowApps()
    }

    private fun setupRowArticles() {
        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        // Pull RSS feed and parse

        val header = HeaderItem(0, getString(R.string.header_articles))
        mCategoryRowAdapter!!.add(ListRow(header, listRowAdapter))
    }

    private fun setupRowVideos() {
        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        // Pull YT feed and parse

        val header = HeaderItem(1, getString(R.string.header_videos))
        mCategoryRowAdapter!!.add(ListRow(header, listRowAdapter))
    }

    private fun setupRowPodcasts() {
        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        // Pull RSS feed and parse

        val header = HeaderItem(1, getString(R.string.header_podcasts))
        mCategoryRowAdapter!!.add(ListRow(header, listRowAdapter))
    }

    private fun setupRowApps() {
        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        // Pull all of the apps.
        val cardArray = arrayOf(
                Card(type = Card.TYPE_APP_ABOUT, primaryText = "About ITV Lab"),
                Card(type = Card.TYPE_APP, primaryText = "NeoDash", secondaryText = "Custom Screensaver", extra = "news.androidtv.neodash"),
                Card(type = Card.TYPE_APP, primaryText = "Tv App Repo", secondaryText = "Shortcut Generator", extra = "news.androidtv.tvapprepo.playstore"),
                Card(type = Card.TYPE_APP, primaryText = "Enterprise Wi-Fi", secondaryText = "WPA2-Enterprise", extra = "com.felkertech.ussenterprise"),
                Card(type = Card.TYPE_APP, primaryText = "Launch on Boot", secondaryText = "Auto Launch", extra = "news.androidtv.launchonboot"),
                Card(type = Card.TYPE_APP, primaryText = "Sample Banner Pack", secondaryText = "Custom Banners", extra = "news.androidtv.bannerpack"),
                Card(type = Card.TYPE_APP, primaryText = "SubChannel", secondaryText = "Subreddits in Live Channels", extra = "news.androidtv.subchannel"),
                Card(type = Card.TYPE_APP, primaryText = "Family Calendar", secondaryText = "Bigscreen Calendar", extra = "news.androidtv.familycalendar")
        )
        for (c in cardArray) {
            listRowAdapter.add(c)
        }

        val header = HeaderItem(1, getString(R.string.header_itvlab))
        mCategoryRowAdapter!!.add(ListRow(header, listRowAdapter))
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            val intent = Intent(activity, SearchActivity::class.java)
            startActivity(intent)
        }

        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private fun updateBackground(uri: String) {
        val width = mMetrics!!.widthPixels
        val height = mMetrics!!.heightPixels
        Glide.with(this)
                .load(uri)
                .asBitmap()
                .centerCrop()
                .error(mDefaultBackground)
                .into(object : SimpleTarget<Bitmap>(width, height) {
                    override fun onResourceReady(resource: Bitmap, glideAnimation: GlideAnimation<in Bitmap>) {
                        mBackgroundManager!!.setBitmap(resource)
                    }
                })
        mBackgroundTimer!!.cancel()
    }

    private fun startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer!!.cancel()
        }
        mBackgroundTimer = Timer()
        mBackgroundTimer!!.schedule(UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY.toLong())
    }

    private fun updateRecommendations() {
        val recommendationIntent = Intent(activity, UpdateRecommendationsService::class.java)
        activity.startService(recommendationIntent)
    }

    private inner class UpdateBackgroundTask : TimerTask() {

        override fun run() {
            mHandler.post {
                if (mBackgroundURI != null) {
                    updateBackground(mBackgroundURI!!.toString())
                }
            }
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any,
                                   rowViewHolder: RowPresenter.ViewHolder, row: Row) {

            val card = item as Card
            if (card.type == Card.TYPE_ARTICLE) {

            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(itemViewHolder: Presenter.ViewHolder, item: Any,
                                    rowViewHolder: RowPresenter.ViewHolder, row: Row) {
            val card = item as Card
            if (card.bgImageUrl != null) {
                mBackgroundURI = Uri.parse(card.bgImageUrl)
                startBackgroundTimer()
            }

        }
    }

    companion object {
        private val BACKGROUND_UPDATE_DELAY = 300
    }
}
