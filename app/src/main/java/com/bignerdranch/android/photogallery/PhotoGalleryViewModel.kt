package com.bignerdranch.android.photogallery

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel

private const val TAG = "PhotoGalleryViewModel"

class PhotoGalleryViewModel : ViewModel() {
    private val flickrFetchr = FlickrFetchr()
    val galleryItemLiveData: LiveData<List<GalleryItem>>

    init {
        galleryItemLiveData = flickrFetchr.fetchPhotos()
    }

    override fun onCleared() {
        super.onCleared()
        flickrFetchr.cancel() // Cancel the network request when ViewModel is cleared
        Log.d(TAG, "ViewModel is about to be destroyed, network call canceled")
    }
}
