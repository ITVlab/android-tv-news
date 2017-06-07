package com.example.android.tvleanback.ui

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.WebView
import android.widget.TextView
import com.example.android.tvleanback.R

/**
 * Created by Nick on 5/29/2017.
 */

class ArticleViewActivity : AppCompatActivity() {
    companion object {
        val EXTRA_CONTENT = "content"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent != null && intent.hasExtra(EXTRA_CONTENT)) {
            val content = intent.getStringExtra(EXTRA_CONTENT)
            setContentView(R.layout.activity_article)
            (findViewById(R.id.content) as WebView).loadData(content, "text/html; charset=utf-8", "UTF-8")
        } else {
            // No content to show
            finish()
        }
    }
}
