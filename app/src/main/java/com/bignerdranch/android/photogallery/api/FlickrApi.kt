package com.bignerdranch.android.photogallery.api

import retrofit2.Call
import retrofit2.http.GET

interface FlickrApi {

//    @GET("/") //'/'表示一个相对路径URL
//    fun fetchContents(): Call<String> //String表示反序列化响应数据需要的类型

    @GET( //GET注解里包含了部分路径和其他信息
        "services/rest/?method=flickr.interestingness.getList" +
                "&api_key=1200dc64f7481df99b157144104f9e23" +
                "&format=json" +
                "&nojsoncallback=1" +          //不要传封闭方法名和括号, 方便数据解析
                "&extras=url_s"                //小图片也要
    )
    fun fetchPhotos(): Call<FlickrResponse>
}
