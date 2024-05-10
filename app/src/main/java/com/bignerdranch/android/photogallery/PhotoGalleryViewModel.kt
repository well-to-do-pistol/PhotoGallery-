package com.bignerdranch.android.photogallery

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap


private const val TAG = "PhotoGalleryViewModel"

class PhotoGalleryViewModel(private val app: Application) : AndroidViewModel(app) { // AndroidViewModel让viewmodel能访问应用上下文, 因为它没上下文活得久, 所以引用上下文是安全的
    private val flickrFetchr = FlickrFetchr()
    val galleryItemLiveData: LiveData<List<GalleryItem>>

    private val mutableSearchTerm = MutableLiveData<String>()
    val searchTerm: String
        get() = mutableSearchTerm.value ?: ""

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {//fetchPhotos和searchPhotos返回的json数据格式都是一样的
        mutableSearchTerm.value = QueryPreferences.getStoredQuery(app) //用存储的值

//        galleryItemLiveData = Transformations.switchMap(mutableSearchTerm) { searchTerm ->
//            flickrFetchr.searchPhotos(searchTerm)
//        }
        //mutableSearchTerm变化时, 就会用它的值从新搜索图片, galleryItemLiveData代表List<GalleryItem>
        galleryItemLiveData = mutableSearchTerm.switchMap { searchTerm -> //此块使用 `switchMap` 函数将 `mutableSearchTerm` 转换为另一个 LiveData `galleryItemLiveData`
//            if (searchTerm.isBlank()) {
//                flickrFetchr.fetchPhotos()
//            } else {
//                flickrFetchr.searchPhotos(searchTerm) //每当“mutableSearchTerm”更改时，都会执行“switchMap”代码块，调用“flickrFetchr.searchPhotos(searchTerm)”以根据更新的搜索词获取新数据
//            }
            _isLoading.value = true //网络请求正在进行
            val liveData = if (searchTerm.isBlank()) {
                flickrFetchr.fetchPhotos() // Assuming this is your existing method
            } else {
                flickrFetchr.searchPhotos(searchTerm) // Assuming this is your existing method
            }

            //该方法允许您设置没有生命周期的观察者。 它将接收所有事件，当不再需要时您需要手动将其删除。 在您的情况下，它用于在数据获取完成时更新“_isLoading”，无论生命周期状态如何，因为此观察是在 ViewModel 中手动管理的
            liveData.observeForever { _ ->
                _isLoading.postValue(false)
            } //首先是LiveData, 它一变_isLoading就变, 然后isLoading才变才传到fragment
//            - `postValue` 用于从后台线程设置 `LiveData` 对象的值。 它将任务发布到主线程以设置给定值。 因此，如果您从后台线程更新 LiveData，则应该使用“postValue”。
//            - `setValue` (`value`) 在主线程上使用。 由于 LiveData 尊重线程限制，因此确保在主线程上完成更新可以避免潜在的问题。
            liveData
        }
    }//当您使用“switchMap”等转换时，您将创建一个新的“LiveData”实例，该实例对原始“LiveData”源中的更改做出反应。

    //接收String更改当前搜索词, 触发mutableSearchTerm的改变
    fun fetchPhotos(query: String = "") {
        QueryPreferences.setStoredQuery(app, query) //设置存储的值
        mutableSearchTerm.value = query
    }

    override fun onCleared() {
        super.onCleared()
        flickrFetchr.cancel() // Cancel the network request when ViewModel is cleared
        Log.d(TAG, "ViewModel is about to be destroyed, network call canceled")
    }
}
