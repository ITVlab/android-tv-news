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
import android.os.Looper
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
import android.support.v7.app.AlertDialog
import android.support.v7.view.ContextThemeWrapper
import android.util.DisplayMetrics
import android.util.Log
import android.view.View

import com.bumptech.glide.Glide
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.example.android.tvleanback.R
import com.example.android.tvleanback.model.Card
import com.example.android.tvleanback.presenter.CardPresenter
import com.example.android.tvleanback.presenter.IconHeaderItemPresenter
import com.example.android.tvleanback.recommendation.UpdateRecommendationsService
import org.json.JSONArray
import org.json.JSONObject
import org.mcsoxford.rss.RSSReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

import java.util.HashMap
import java.util.Timer
import java.util.TimerTask
import java.util.function.Consumer

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
        Thread(Runnable {
            val rssReader = RSSReader()
            val feed = "https://androidtv.news/feed/"
            val rssFeed = rssReader.load(feed)
            rssFeed.items.forEach(Consumer {
                addCard(listRowAdapter, Card(type = Card.TYPE_ARTICLE,
                        imageUrl = it.thumbnails[0].url.toString(),
                        bgImageUrl = it.thumbnails[0].url.toString(),
                        primaryText = it.title,
                        secondaryText = it.pubDate.toString(),
                        extra = it.content))
            })
        }).start()

        val header = HeaderItem(0, getString(R.string.header_articles))
        mCategoryRowAdapter!!.add(ListRow(header, listRowAdapter))
    }

    private fun setupRowVideos() {
        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        // Pull YT feed and parse
        // Ex: https://developers.google.com/apis-explorer/#p/youtube/v3/youtube.playlistItems.list?part=snippet&playlistId=UU1R_fgIcP7DJoPgR0nAUQ1w&_h=4&
        Thread(Runnable({
            val yt_api_key = getString(R.string.yt_api_key)
            val yt_playlist = getString(R.string.yt_uploads_pl)
            val url = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&playlistId=$yt_playlist&key=$yt_api_key"
            val response = downloadUrl(url)
            val items = JSONObject(response).getJSONArray("items")
            for (video in items) {
                addCard(listRowAdapter, Card(type = Card.TYPE_VIDEO,
                        primaryText = video.getJSONObject("snippet").getString("title"),
                        bgImageUrl = video.getJSONObject("snippet").getJSONObject("thumbnails").getJSONObject("maxres").getString("url"),
                        imageUrl = video.getJSONObject("snippet").getJSONObject("thumbnails").getJSONObject("maxres").getString("url"),
                        secondaryText = "New Video",
                        extra = video.getJSONObject("snippet").getJSONObject("resourceId").getString("videoId")))
            }
        })).start()

        val header = HeaderItem(1, getString(R.string.header_videos))
        mCategoryRowAdapter!!.add(ListRow(header, listRowAdapter))
    }

    private fun setupRowPodcasts() {
        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        // Pull upload feed and parse
        listRowAdapter.add(Card(type = Card.TYPE_PODCAST,
                primaryText = "Pre Google I/O", secondaryText = "Ep. 1 - 17 May",
                imageUrl = "https://i1.sndcdn.com/artworks-000222955659-qjewpu-t500x500.jpg",
                extra = "https://soundcloud.com/androidtv-news/android-tv-news-podcast-1-pre-google-io"
        ))

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

    private fun addCard(adapter: ArrayObjectAdapter, card: Card) {
        Handler(Looper.getMainLooper()).post({ adapter.add(card) })
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

    @Throws(IOException::class)
    private fun downloadUrl(myurl: String): String {
        var `is`: InputStream? = null
        // Only display the first 1000 characters of the retrieved
        // web page content.
        val len = 1000
        try {
            val url = URL(myurl)
            val conn = url.openConnection() as HttpURLConnection
            conn.setReadTimeout(10000 /* milliseconds */)
            //set back to 15000, 10000
            conn.setConnectTimeout(15000 /* milliseconds */)
            conn.setRequestMethod("GET")
            conn.setDoInput(true)
            // Starts the query
            conn.connect()
            val response = conn.getResponseCode()
            `is` = conn.getInputStream()

            val byteArrayOutputStream = ByteArrayOutputStream()

            var i = `is`!!.read()
            while (i != -1) {
                byteArrayOutputStream.write(i)
                i = `is`!!.read()
            }
            Log.d(TAG, "Download finished; parse")
            return byteArrayOutputStream.toString("UTF-8")
        } finally {
            if (`is` != null) {
                `is`!!.close()
            }
        }
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
            when (card.type) {
                Card.TYPE_ARTICLE -> {
                    // Open the browser
                    val articleIntent = Intent(activity, ArticleViewActivity::class.java)
                    articleIntent.putExtra(ArticleViewActivity.EXTRA_CONTENT, card.extra)
                    startActivity(articleIntent)
                }
                Card.TYPE_VIDEO -> {
                    // Open YT
                    startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://youtube.com/watch?v=${card.extra}")))
                }
                Card.TYPE_PODCAST -> {
                    // Open podcast
                }
                Card.TYPE_APP -> {
                    // Open Google Play
                    startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${card.extra}")))
                }
                Card.TYPE_APP_ABOUT -> {
                    AlertDialog.Builder(ContextThemeWrapper(activity, R.style.Base_Theme_AppCompat_Dialog))
                            .setTitle("ITV Lab")
                            .setMessage("A description")
                            .show()
                }
            }
            // TODO Add clicker
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
        val TAG = MainFragment::class.java.simpleName
    }
}

operator fun JSONArray.iterator(): Iterator<JSONObject>
        = (0 until length()).asSequence().map { get(it) as JSONObject }.iterator()

