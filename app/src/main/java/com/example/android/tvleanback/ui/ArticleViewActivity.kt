package com.example.android.tvleanback.ui

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
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

        actionBar.hide()
        if (intent != null && intent.hasExtra(EXTRA_CONTENT)) {
            val content = intent.getStringExtra(EXTRA_CONTENT)
            setContentView(R.layout.activity_article)
            (findViewById(R.id.content) as TextView).text = content
        } else {
            // No content to show
            finish()
        }
    }
}
