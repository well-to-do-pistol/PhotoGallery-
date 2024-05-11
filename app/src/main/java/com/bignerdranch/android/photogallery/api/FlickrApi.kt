package com.bignerdranch.android.photogallery.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface FlickrApi {

//    @GET("/") //'/'表示一个相对路径URL
//    fun fetchContents(): Call<String> //String表示反序列化响应数据需要的类型

    @GET("services/rest?method=flickr.interestingness.getList")
//    fun fetchPhotos(): Call<PhotoResponse>
    suspend fun fetchPhotos(@Query("page") page: Int): Response<PhotoResponse>

    @GET //无参数的GET和@Url会让Retrofit覆盖基URL
    fun fetchUrlBytes(@Url url: String): Call<ResponseBody>

    @GET("services/rest?method=flickr.photos.search")
//    fun searchPhotos(@Query("text") query: String): Call<PhotoResponse> //Query允许动态拼接
    suspend fun searchPhotos(@Query("text") query: String, @Query("page") page: Int): Response<PhotoResponse>

    @GET("services/rest?method=flickr.interestingness.getList")
    fun workerPhotos(): Call<PhotoResponse>

    @GET("services/rest?method=flickr.photos.search")
    fun searchPhotos(@Query("text") query: String): Call<PhotoResponse> //Query允许动态拼接
}
