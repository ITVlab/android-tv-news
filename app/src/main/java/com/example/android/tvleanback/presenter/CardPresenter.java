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

package com.example.android.tvleanback.presenter;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.support.v4.content.ContextCompat;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.example.android.tvleanback.R;
import com.example.android.tvleanback.model.Card;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
    private int mSelectedBackgroundColor = -1;
    private int mDefaultBackgroundColor = -1;
    private Drawable mDefaultCardImage;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mDefaultBackgroundColor =
            ContextCompat.getColor(parent.getContext(), R.color.default_background);
        mSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.selected_background);
        mDefaultCardImage = parent.getResources().getDrawable(R.drawable.android_tv_block, null);

        ImageCardView cardView = new ImageCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        updateCardBackgroundColor(cardView, false);
        return new ViewHolder(cardView);
    }

    private void updateCardBackgroundColor(ImageCardView view, boolean selected) {
        int color = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

        // Both background colors should be set because the view's
        // background is temporarily visible during animations.
        view.setBackgroundColor(color);
        view.findViewById(R.id.info_field).setBackgroundColor(color);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        Card card = (Card) item;

        ImageCardView cardView = (ImageCardView) viewHolder.view;
        cardView.setTitleText(card.getPrimaryText());
        cardView.setContentText(card.getSecondaryText());

        String cardImg = card.getImageUrl();
        if (card.getImageUrl() == null) {
            if (card.getType() == Card.Companion.getTYPE_ARTICLE()) {
                cardImg = "https://i0.wp.com/androidtv.news/wp-content/uploads/2017/06/Facebook1.png?w=329&ssl=1";
            } else if (card.getType() == Card.Companion.getTYPE_APP_ABOUT()) {
                cardImg = "https://i0.wp.com/androidtv.news/wp-content/uploads/2017/06/Facebook1.png?w=329&ssl=1";
            } else if (card.getType() == Card.Companion.getTYPE_APP()) {
                cardImg = "https://i0.wp.com/androidtv.news/wp-content/uploads/2017/06/Facebook1.png?w=329&ssl=1";
            }
        }

        // Set card size from dimension resources.
        Resources res = cardView.getResources();
        int width = res.getDimensionPixelSize(R.dimen.card_width);
        int height = res.getDimensionPixelSize(R.dimen.card_height);
        cardView.setMainImageDimensions(width, height);

        Glide.with(cardView.getContext())
                .load(cardImg)
                .error(mDefaultCardImage)
                .into(cardView.getMainImageView());

    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ImageCardView cardView = (ImageCardView) viewHolder.view;

        // Remove references to images so that the garbage collector can free up memory.
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
    }
}
