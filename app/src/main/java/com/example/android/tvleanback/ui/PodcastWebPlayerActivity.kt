package com.example.android.tvleanback.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.WebView
import com.example.android.tvleanback.R

/**
 * Created by Nick on 5/30/2017.
 */

class PodcastWebPlayerActivity : AppCompatActivity() {
    companion object {
        val EXTRA_CONTENT = "content"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar.hide()
        if (intent != null && intent.hasExtra(EXTRA_CONTENT)) {
            val content = intent.getStringExtra(EXTRA_CONTENT)
            setContentView(R.layout.activity_podcast)
            (findViewById(R.id.content) as WebView).loadData(content, "text/html; charset=utf-8", "UTF-8")
        } else {
            // No content to show
            finish()
        }
    }
}