package com.bignerdranch.android.photogallery

import android.net.Uri
import com.google.gson.annotations.SerializedName

//- “对象名称”是指 JSON 对象中的键。 例如，上面 JSON 中的 `"title"`、`"id"` 和 `"url_s"`。
//- “属性名称”指的是模型类中的字段/属性（本例中为“GalleryItem”）。 在“GalleryItem”数据类中，有“title”、“id”和“url”属性。
data class GalleryItem(
    var title: String = "",
    var id: String = "",
    @SerializedName("url_s") var url: String = "", //指定序列化名字
    @SerializedName("owner") var owner: String = "" //图片网页拥有者id
) {
    val photoPageUri: Uri //图片网页url
    get() {
        return Uri.parse("https://www.flickr.com/photos/")
            .buildUpon()
            .appendPath(owner)
            .appendPath(id)
            .build()
    }
}