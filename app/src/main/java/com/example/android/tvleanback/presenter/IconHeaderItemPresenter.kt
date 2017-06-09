/*
 * Copyright (c) 2016 The Android Open Source Project
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

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.Presenter
import android.support.v17.leanback.widget.RowHeaderPresenter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import com.example.android.tvleanback.R

class IconHeaderItemPresenter : RowHeaderPresenter() {

    private var mUnselectedAlpha: Float = 0.toFloat()

    override fun onCreateViewHolder(viewGroup: ViewGroup): RowHeaderPresenter.ViewHolder {
        mUnselectedAlpha = viewGroup.resources
                .getFraction(R.fraction.lb_browse_header_unselect_alpha, 1, 1)
        val inflater = viewGroup.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val view = inflater.inflate(R.layout.icon_header_item, null)
        view.alpha = mUnselectedAlpha // Initialize icons to be at half-opacity.

        return RowHeaderPresenter.ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val headerItem = (item as ListRow).headerItem
        val rootView = viewHolder.view
        rootView.isFocusable = true

        val iconView = rootView.findViewById(R.id.header_icon) as ImageView
        val icon = rootView.resources.getDrawable(R.drawable.android_header, null)
        //        iconView.setImageDrawable(icon);

        val label = rootView.findViewById(R.id.header_label) as TextView
        label.text = headerItem.name
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        // no op
    }

    // TODO: This is a temporary fix. Remove me when leanback onCreateViewHolder no longer sets the
    // mUnselectAlpha, and also assumes the xml inflation will return a RowHeaderView.
    override fun onSelectLevelChanged(holder: RowHeaderPresenter.ViewHolder) {
        holder.view.alpha = mUnselectedAlpha + holder.selectLevel * (1.0f - mUnselectedAlpha)
    }
}
