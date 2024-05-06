package com.bignerdranch.android.photogallery

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bignerdranch.android.photogallery.api.FlickrApi
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

private const val TAG = "PhotoGalleryFragment"

class PhotoGalleryFragment : Fragment() {

    private lateinit var photoGalleryViewModel: PhotoGalleryViewModel
    private lateinit var photoRecyclerView: RecyclerView
    private lateinit var thumbnailDownloader: ThumbnailDownloader<PhotoHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true //配置改变不会销毁, 最好不要保留
        setHasOptionsMenu(true) //让fragment接收菜单回调函数

        photoGalleryViewModel =
            ViewModelProvider(this).get(PhotoGalleryViewModel::class.java)

        //直接将观察者添加给fragment的lifestyle, 让它接受fragment的生命周期回调函数
        val responseHandler = Handler() //Handler自动与线程关联(Looper绑定), 现在是在fragment的onCreate上所以和主线程Looper绑定
        thumbnailDownloader =
            ThumbnailDownloader(responseHandler) { photoHolder, bitmap ->
//                Inside the lambda, a `BitmapDrawable` is created from the bitmap, and this drawable is set on the photo holder using the `bindDrawable` method.
                val drawable = BitmapDrawable(resources, bitmap)
//                这是指与应用程序或活动上下文关联的“Resources”实例。 Android 中的“Resources”类提供对应用程序原始资源文件的访问； 这包括本地化的字符串、图形、布局文件定义等等。
//                - 在这种情况下，“资源”主要用于获取有关设备屏幕密度的信息以及渲染图像时很重要的其他配置详细信息。 当您使用“Resources”对象创建“BitmapDrawable”时，除非另有指定，否则可绘制对象会根据当前屏幕的密度自动缩放。
                photoHolder.bindDrawable(drawable)
            }
        lifecycle.addObserver(thumbnailDownloader.fragmentLifecycleObserver)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewLifecycleOwner.lifecycle.addObserver(
            thumbnailDownloader.viewLifecycleObserver
        )

        val view = inflater.inflate(R.layout.fragment_photo_gallery, container, false)

        photoRecyclerView = view.findViewById(R.id.photo_recycler_view)
        photoRecyclerView.layoutManager = GridLayoutManager(context, 3)

        return view
    }

    //观察ViewModel的LiveData
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { //还要在UI相关部件(RecyclerView adapter)响应数据变化, 确保UI已初始化完成
        super.onViewCreated(view, savedInstanceState)
        photoGalleryViewModel.galleryItemLiveData.observe(
            //LifecycleOwner 负责根据组件（在本例中为片段）的生命周期状态观察 LiveData 的变化
            //如果您要将“this”（片段本身）作为“LifecycleOwner”传递：
            //LiveData 将根据片段实例的生命周期观察变化。 如果片段的视图被破坏，
            // 但片段实例仍在内存中（就像添加到返回堆栈时一样），这可能会导致问题。
            // 由于片段实例的生命周期比其视图更长，因此即使片段的视图不活动，您最终也可能会看到观察者处于活动状态，
            // 这可能会导致内存泄漏和不必要的更新。
            viewLifecycleOwner,
            Observer { galleryItems ->
                photoRecyclerView.adapter = PhotoAdapter(galleryItems)
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewLifecycleOwner.lifecycle.removeObserver(
            thumbnailDownloader.viewLifecycleObserver
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(
            thumbnailDownloader.fragmentLifecycleObserver
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_photo_gallery, menu)

        val searchItem: MenuItem = menu.findItem(R.id.menu_item_search)
        val searchView = searchItem.actionView as SearchView

        searchView.apply {

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(queryText: String): Boolean { //搜索提交时执行
                    Log.d(TAG, "QueryTextSubmit: $queryText")
                    photoGalleryViewModel.fetchPhotos(queryText) //下载图片
                    return true
                }

                override fun onQueryTextChange(queryText: String): Boolean { //文字更改时执行(可以提供相关搜索建议)
                    Log.d(TAG, "QueryTextChange: $queryText")
                    return false //按时系统执行默认操作
                }
            })

            setOnSearchClickListener {//点击搜索时触发
                searchView.setQuery(photoGalleryViewModel.searchTerm, false)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_clear -> {
                photoGalleryViewModel.fetchPhotos("")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private class PhotoHolder(private val itemImageView: ImageView)
        : RecyclerView.ViewHolder(itemImageView) {
        val bindDrawable: (Drawable) -> Unit  = itemImageView::setImageDrawable
    }

    //为拿到layoutInflater把adapter变成内部类, 还方便后面访问父activity的属性和函数
    private inner class PhotoAdapter(private val galleryItems: List<GalleryItem>)
        : RecyclerView.Adapter<PhotoHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PhotoHolder {
            val view = layoutInflater.inflate(
                R.layout.list_item_gallery,
                parent,
                false
            ) as ImageView
            return PhotoHolder(view)
        }

        override fun getItemCount(): Int = galleryItems.size

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            val galleryItem = galleryItems[position]
            val placeholder: Drawable = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bill_up_close
            ) ?: ColorDrawable()
            holder.bindDrawable(placeholder)
            thumbnailDownloader.queueThumbnail(holder, galleryItem.url)
        }
    }

    companion object {
        fun newInstance() = PhotoGalleryFragment()
    }
}