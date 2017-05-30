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

package com.example.android.tvleanback.model

import android.media.MediaDescription
import android.os.Parcel
import android.os.Parcelable

/**
 * Video is an immutable object that holds the various metadata associated with a single video.
 */
class Video : Parcelable {
    val id: Long
    val category: String
    val title: String
    val description: String
    val bgImageUrl: String
    val cardImageUrl: String
    val videoUrl: String
    val studio: String

    private constructor(
            id: Long = -1,
            category: String = "",
            title: String = "",
            desc: String = "",
            videoUrl: String = "",
            bgImageUrl: String = "",
            cardImageUrl: String = "",
            studio: String = "") {
        this.id = id
        this.category = category
        this.title = title
        this.description = desc
        this.videoUrl = videoUrl
        this.bgImageUrl = bgImageUrl
        this.cardImageUrl = cardImageUrl
        this.studio = studio
    }

    protected constructor(`in`: Parcel) {
        id = `in`.readLong()
        category = `in`.readString()
        title = `in`.readString()
        description = `in`.readString()
        bgImageUrl = `in`.readString()
        cardImageUrl = `in`.readString()
        videoUrl = `in`.readString()
        studio = `in`.readString()
    }

    override fun equals(m: Any?): Boolean {
        return m is Video && id == m.id
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(category)
        dest.writeString(title)
        dest.writeString(description)
        dest.writeString(bgImageUrl)
        dest.writeString(cardImageUrl)
        dest.writeString(videoUrl)
        dest.writeString(studio)
    }

    override fun toString(): String {
        var s = "Video{"
        s += "id=" + id
        s += ", category='$category'"
        s += ", title='$title'"
        s += ", videoUrl='$videoUrl'"
        s += ", bgImageUrl='$bgImageUrl'"
        s += ", cardImageUrl='$cardImageUrl'"
        s += ", studio='$cardImageUrl'"
        s += "}"
        return s
    }

    // Builder for Video object.
    class Builder {
        var id: Long = 0
        var category: String = ""
        var title: String = ""
        var desc: String = ""
        var bgImageUrl: String = ""
        var cardImageUrl: String = ""
        var videoUrl: String = ""
        var studio: String = ""

        fun buildFromMediaDesc(desc: MediaDescription): Video {
            return Video(
                    java.lang.Long.parseLong(desc.mediaId),
                    "", // Category - not provided by MediaDescription.
                    desc.title.toString(),
                    desc.description.toString(),
                    "", // Media URI - not provided by MediaDescription.
                    "", // Background Image URI - not provided by MediaDescription.
                    desc.iconUri.toString(),
                    desc.subtitle.toString()
            )
        }

        fun build(): Video {
            return Video(
                    id,
                    category,
                    title,
                    desc,
                    videoUrl,
                    bgImageUrl,
                    cardImageUrl,
                    studio
            )
        }
    }

    companion object {

        val CREATOR: Parcelable.Creator<Video> = object : Parcelable.Creator<Video> {
            override fun createFromParcel(`in`: Parcel): Video {
                return Video(`in`)
            }

            override fun newArray(size: Int): Array<Video?> {
                return arrayOfNulls(size)
            }
        }
    }
}
