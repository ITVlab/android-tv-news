/*
 * Copyright (c) 2015 The Android Open Source Project
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

package com.example.android.tvleanback.presenter

import android.graphics.drawable.Drawable
import android.support.v17.leanback.widget.ImageCardView
import android.support.v17.leanback.widget.Presenter
import android.support.v4.content.ContextCompat
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.example.android.tvleanback.R
import com.example.android.tvleanback.model.Card

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
class CardPresenter : Presenter() {
    private var mSelectedBackgroundColor = -1
    private var mDefaultBackgroundColor = -1
    private var mDefaultCardImage: Drawable? = null

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        mDefaultBackgroundColor = ContextCompat.getColor(parent.context, R.color.default_background)
        mSelectedBackgroundColor = ContextCompat.getColor(parent.context, R.color.selected_background)
        mDefaultCardImage = parent.resources.getDrawable(R.drawable.android_tv_block, null)

        val cardView = object : ImageCardView(parent.context) {
            override fun setSelected(selected: Boolean) {
                updateCardBackgroundColor(this, selected)
                super.setSelected(selected)
            }
        }

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        updateCardBackgroundColor(cardView, false)
        return Presenter.ViewHolder(cardView)
    }

    private fun updateCardBackgroundColor(view: ImageCardView, selected: Boolean) {
        val color = if (selected) mSelectedBackgroundColor else mDefaultBackgroundColor

        // Both background colors should be set because the view's
        // background is temporarily visible during animations.
        view.setBackgroundColor(color)
        view.findViewById(R.id.info_field).setBackgroundColor(color)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val card = item as Card

        val cardView = viewHolder.view as ImageCardView
        cardView.titleText = card.primaryText
        cardView.contentText = card.secondaryText

        var cardImg: String? = card.imageUrl
        if (card.imageUrl == null) {
            if (card.type == Card.TYPE_ARTICLE) {
                cardImg = "https://i0.wp.com/androidtv.news/wp-content/uploads/2017/06/Facebook1.png?w=329&ssl=1"
            } else if (card.type == Card.TYPE_APP_ABOUT) {
                cardImg = "https://i0.wp.com/androidtv.news/wp-content/uploads/2017/06/Facebook1.png?w=329&ssl=1"
            } else if (card.type == Card.TYPE_APP) {
                cardImg = "https://i0.wp.com/androidtv.news/wp-content/uploads/2017/06/Facebook1.png?w=329&ssl=1"
            }
        }

        // Set card size from dimension resources.
        val res = cardView.resources
        val width = res.getDimensionPixelSize(R.dimen.card_width)
        val height = res.getDimensionPixelSize(R.dimen.card_height)
        cardView.setMainImageDimensions(width, height)

        Glide.with(cardView.context)
                .load(cardImg)
                .error(mDefaultCardImage)
                .into(cardView.mainImageView)

    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val cardView = viewHolder.view as ImageCardView

        // Remove references to images so that the garbage collector can free up memory.
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}
