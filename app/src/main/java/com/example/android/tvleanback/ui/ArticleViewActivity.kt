package com.example.android.tvleanback.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.webkit.WebView
import com.example.android.tvleanback.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView


/**
 * Created by Nick on 5/29/2017.
 */

class ArticleViewActivity : AppCompatActivity() {
    companion object {
        val TAG = ArticleViewActivity::class.java.simpleName
        val EXTRA_CONTENT = "content"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (supportActionBar != null) {
            supportActionBar!!.hide()
        }

        Log.d(TAG, "Opening article.")

        if (intent != null && intent.hasExtra(EXTRA_CONTENT)) {
            val header = "<html><body style='background-color:#555; color: #ddd'><style>.youtube-player { width: 50% !important; height: inherit !important; text-align:center;}</style>"
            val footer = "<br><br><br><br><br></body></head>"
            var content = header + intent.getStringExtra(EXTRA_CONTENT) + footer
            // Replace images to use new width <img width="xxx -> <img width="100%
            val img_regex = Regex("<img width=\"\\d+\" height=\"\\d+\"")
            content = content.replace(img_regex, "<img width='100%'")
            val link_regex = Regex("href=\".+\"")
            content = content.replace(link_regex, "")
            Log.d(TAG, "Has content: " + content.substring(0, 80))
            setContentView(R.layout.activity_article)
            val wv = findViewById(R.id.content) as WebView
            wv.settings.javaScriptEnabled = true
            wv.loadDataWithBaseURL("http://androidtv.news", content, "text/html", "UTF-8", null)

            val adView = findViewById(R.id.adView) as AdView
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        } else {
            // No content to show
            Log.d(TAG, "No content or intent")
            finish()
        }
    }
}
