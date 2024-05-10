package com.bignerdranch.android.photogallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bignerdranch.android.photogallery.api.FlickrApi
import com.bignerdranch.android.photogallery.api.PhotoDeserializer
import com.bignerdranch.android.photogallery.api.PhotoInterceptor
import com.bignerdranch.android.photogallery.api.PhotoResponse
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val TAG = "FlickrFetchr"

class FlickrFetchr {
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

    @WorkerThread //该注解指定函数只能在后台线程上执行(网络请求)
    fun fetchPhoto(url: String): Bitmap? {
        val response: Response<ResponseBody> = flickrApi.fetchUrlBytes(url).execute()
        //ResponseBody.byteStream得到InputStream
        //BitmapFactory.decodeStream(InputStream)创建Bitmap对象
        //响应流和字节流都应该被关闭, use函数会清理
        val bitmap = response.body()?.byteStream()?.use(BitmapFactory::decodeStream)
        Log.i(TAG, "Decoded bitmap=$bitmap from Response=$response")
        return bitmap
    }

    fun fetchPhotosRequest(): Call<PhotoResponse> { //暴露Call对象, 在init的时候已经配置好了
        return flickrApi.fetchPhotos()
    }

    fun fetchPhotos(): LiveData<List<GalleryItem>> {
        currentCall?.cancel() // 如果存在，取消之前的网络请求
        return fetchPhotoMetadata(fetchPhotosRequest())
    }

    fun searchPhotosRequest(query: String): Call<PhotoResponse> { //暴露Call对象, 在init的时候已经配置好了
        return flickrApi.searchPhotos(query)
    }

    fun searchPhotos(query: String): LiveData<List<GalleryItem>> {
        currentCall?.cancel() // 如果存在，取消之前的网络请求
        return fetchPhotoMetadata(searchPhotosRequest(query))
    }

    private fun fetchPhotoMetadata(flickrRequest: Call<PhotoResponse>)
            : LiveData<List<GalleryItem>> {
        val responseLiveData: MutableLiveData<List<GalleryItem>> = MutableLiveData()
        currentCall = flickrRequest // 创建新的网络请求

        currentCall?.enqueue(object : Callback<PhotoResponse> {
            override fun onFailure(call: Call<PhotoResponse>, t: Throwable) {
                if (call.isCanceled) {
                    Log.d(TAG, "Call was cancelled") // 如果请求被取消，则记录取消信息
                } else {
                    Log.e(TAG, "Failed to fetch photos", t) // 如果请求失败，则记录错误信息
                }
            }

            override fun onResponse(call: Call<PhotoResponse>, response: Response<PhotoResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val galleryItems = response.body()!!.galleryItems.filterNot { it.url.isBlank() }
                    responseLiveData.postValue(galleryItems) // Update LiveData with filtered items
                    Log.d(TAG, "Response received: ${response.body()}") // Log the successful response
                } else {
                    Log.e(TAG, "Error fetching photos: HTTP ${response.code()} - ${response.errorBody()?.string() ?: "Unknown error"}")
                    responseLiveData.postValue(emptyList())
                }
            }
        })

        return responseLiveData // 返回 LiveData，允许观察者订阅数据变化
    }

    fun cancel() {
        currentCall?.cancel() // 取消当前的网络请求
    }
}
