package com.bignerdranch.android.photogallery.api

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.bignerdranch.android.photogallery.FlickrFetchr
import com.bignerdranch.android.photogallery.GalleryItem
import retrofit2.HttpException
import java.io.IOException

private const val TAG = "FlickrPagingSource"

class FlickrPagingSource(private val flickrFetchr: FlickrFetchr) : PagingSource<Int, GalleryItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GalleryItem> {
        val position = params.key ?: 1
        return try {
            val items = flickrFetchr.fetchPhotos(position)
            LoadResult.Page(
                data = items,
                prevKey = if (position == 1) null else position - 1,
                nextKey = if (items.isEmpty()) null else position + 1
            )
        } catch (exception: IOException) {
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, GalleryItem>): Int? {
        TODO("Not yet implemented")
    }
}

