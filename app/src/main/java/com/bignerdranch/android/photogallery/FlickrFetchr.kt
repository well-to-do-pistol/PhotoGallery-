package com.bignerdranch.android.photogallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bignerdranch.android.photogallery.api.FlickrApi
import com.bignerdranch.android.photogallery.api.FlickrResponse
import com.bignerdranch.android.photogallery.api.PhotoInterceptor
import com.bignerdranch.android.photogallery.api.PhotoResponse
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

private const val TAG = "FlickrFetchr"

class FlickrFetchr {
    private val flickrApi: FlickrApi
    private var currentCall: Call<FlickrResponse>? = null // 存储当前的网络请求

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor(PhotoInterceptor())
            .build() //先创建一个OkHttpClient, 再把拦截器添加给它(共用常用键值对)

        // 初始化 Retrofit，设置基础 URL 和转换器工厂
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://api.flickr.com/")
            .addConverterFactory(GsonConverterFactory.create()) //添加json解析器
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

    fun fetchPhotosRequest(): Call<FlickrResponse> { //暴露Call对象, 在init的时候已经配置好了
        return flickrApi.fetchPhotos()
    }

    fun fetchPhotos(): LiveData<List<GalleryItem>> {
        currentCall?.cancel() // 如果存在，取消之前的网络请求
        return fetchPhotoMetadata(fetchPhotosRequest())
    }

    fun searchPhotosRequest(query: String): Call<FlickrResponse> { //暴露Call对象, 在init的时候已经配置好了
        return flickrApi.searchPhotos(query)
    }

    fun searchPhotos(query: String): LiveData<List<GalleryItem>> {
        currentCall?.cancel() // 如果存在，取消之前的网络请求
        return fetchPhotoMetadata(searchPhotosRequest(query))
    }

    private fun fetchPhotoMetadata(flickrRequest: Call<FlickrResponse>)
            : LiveData<List<GalleryItem>> {
        val responseLiveData: MutableLiveData<List<GalleryItem>> = MutableLiveData()
        currentCall = flickrRequest // 创建新的网络请求

        currentCall?.enqueue(object : Callback<FlickrResponse> {
            override fun onFailure(call: Call<FlickrResponse>, t: Throwable) {
                if (call.isCanceled) {
                    Log.d(TAG, "Call was cancelled") // 如果请求被取消，则记录取消信息
                } else {
                    Log.e(TAG, "Failed to fetch photos", t) // 如果请求失败，则记录错误信息
                }
            }

            override fun onResponse(call: Call<FlickrResponse>, response: Response<FlickrResponse>) {
                Log.d(TAG, "Response received") // 请求成功，记录日志
                val photoResponse: PhotoResponse? = response.body()?.photos
                var galleryItems: List<GalleryItem> = photoResponse?.galleryItems ?: mutableListOf()
                galleryItems = galleryItems.filterNot { it.url.isBlank() } // 过滤掉 URL 为空的条目
                responseLiveData.postValue(galleryItems) // 更新 LiveData 对象，通知观察者数据已改变
            }
        })

        return responseLiveData // 返回 LiveData，允许观察者订阅数据变化
    }

    fun cancel() {
        currentCall?.cancel() // 取消当前的网络请求
    }
}
