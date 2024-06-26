package com.bignerdranch.android.photogallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.bignerdranch.android.photogallery.api.FlickrApi
import com.bignerdranch.android.photogallery.api.PhotoDeserializer
import com.bignerdranch.android.photogallery.api.PhotoInterceptor
import com.bignerdranch.android.photogallery.api.PhotoResponse
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val TAG = "FlickrFetchr"

class FlickrFetchr (private val coroutineScope: CoroutineScope){ //传入(FlickrFetchr(viewModelScope))来控制协程, 类内的协程随着viewModel死掉而取消


    private val flickrApi: FlickrApi
    private var currentCall: Call<PhotoResponse>? = null // 存储当前的网络请求

    init {
        val gson = GsonBuilder() //创建Gson实例, 登记自定义反序列化器为类型适配器
            .registerTypeAdapter(PhotoResponse::class.java, PhotoDeserializer())
            .create()

        val client = OkHttpClient.Builder()
            .addInterceptor(PhotoInterceptor())
            .build() //先创建一个OkHttpClient, 再把拦截器添加给它(共用常用键值对)

        // 初始化 Retrofit，设置基础 URL 和转换器工厂
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://api.flickr.com/")
            .addConverterFactory(GsonConverterFactory.create(gson)) //添加json解析器
            .client(client)
            .build()

        flickrApi = retrofit.create(FlickrApi::class.java) // 创建 Flickr API 接口实例
    }

    fun fetchPhotosRequest(): Call<PhotoResponse> { //暴露Call对象, 在init的时候已经配置好了 (后台轮巡需要的网络请求(看看有没有拿到第一个数据))
        return flickrApi.workerPhotos()
    }

    @WorkerThread //该注解指定函数只能在后台线程上执行(网络请求)
    fun fetchPhoto(url: String): Bitmap? { //用url拿数据
        val response: Response<ResponseBody> = flickrApi.fetchUrlBytes(url).execute()
        //ResponseBody.byteStream得到InputStream
        //BitmapFactory.decodeStream(InputStream)创建Bitmap对象
        //响应流和字节流都应该被关闭, use函数会清理
        val bitmap = response.body()?.byteStream()?.use(BitmapFactory::decodeStream)
        Log.i(TAG, "Decoded bitmap=$bitmap from Response=$response")
        return bitmap
    }

    fun cancel() {
        currentCall?.cancel() // 取消当前的网络请求
    }




    suspend fun fetchPhotosRequest(page: Int): Response<PhotoResponse> { //暴露Call对象, 在init的时候已经配置好了 (后台轮巡需要的网络请求(看看有没有拿到第一个数据))
        return flickrApi.fetchPhotos(page)
    }

    suspend fun fetchPhotos(page: Int): List<GalleryItem> {
        currentCall?.cancel() // 如果存在，取消之前的网络请求
        return fetchPhotoMetadata(fetchPhotosRequest(page)) //这里是用回原来的网络请求
    }

    suspend fun searchPhotosRequest(query: String, page: Int): Response<PhotoResponse> { //暴露Call对象, 在init的时候已经配置好了
        return flickrApi.searchPhotos(query, page)
    }

    suspend fun searchPhotos(query: String, page: Int): List<GalleryItem> {
        currentCall?.cancel() // 如果存在，取消之前的网络请求
        return fetchPhotoMetadata(searchPhotosRequest(query, page))
    }

    suspend private fun fetchPhotoMetadata(flickrRequest: Response<PhotoResponse>)
            : List<GalleryItem>  {
        return try {
            if (flickrRequest.isSuccessful && flickrRequest.body() != null) {
                flickrRequest.body()!!.galleryItems.filterNot { it.url.isBlank() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch photos", e)
            emptyList()
        }
    }
}
