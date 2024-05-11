package com.bignerdranch.android.photogallery

import android.content.Intent
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
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.bignerdranch.android.photogallery.api.FlickrApi
import com.bignerdranch.android.photogallery.backstage.PhotoPageActivity
import com.bignerdranch.android.photogallery.backstage.PollWorker
import com.bignerdranch.android.photogallery.backstage.VisibleFragment
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.min

private const val TAG = "PhotoGalleryFragment"
private const val POLL_WORK = "POLL_WORK"

class PhotoGalleryFragment : VisibleFragment() {

    private lateinit var photoGalleryViewModel: PhotoGalleryViewModel
    private lateinit var photoRecyclerView: RecyclerView
    private lateinit var thumbnailDownloader: ThumbnailDownloader<PhotoHolder>
    private val lifecycleEvents = MutableLiveData<Lifecycle.Event>()
    private lateinit var progressBar: ProgressBar

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
        thumbnailDownloader.observeLifecycleEvents(lifecycleEvents)
        lifecycleEvents.value = Lifecycle.Event.ON_CREATE
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_photo_gallery, container, false)
        progressBar = view.findViewById(R.id.photo_gallery_progress_bar)
        photoRecyclerView = view.findViewById(R.id.photo_recycler_view)
        photoRecyclerView.layoutManager = GridLayoutManager(context, 3)

        return view
    }

    //观察ViewModel的LiveData
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { //还要在UI相关部件(RecyclerView adapter)响应数据变化, 确保UI已初始化完成
        super.onViewCreated(view, savedInstanceState)
        // Define the CoroutineExceptionHandler
        val errorHandler = CoroutineExceptionHandler { _, exception ->
            Log.e(TAG, "Error in submitData process: $exception")
        }
        photoGalleryViewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        })

        photoGalleryViewModel.galleryItemLiveData.observe(viewLifecycleOwner,
            Observer { pagingData ->
                firstpreload(pagingData) //首次加载18张图片

//                photoRecyclerView.adapter = PhotoAdapter(pagingData)
                photoRecyclerView.post {
                    Log.d(TAG, "Is RecyclerView visible: ${photoRecyclerView.isShown}")
                }

                val adapter = PhotoAdapter()
                photoRecyclerView.adapter = adapter //定义photoRecyclerView的adapter

                // This will log the type of galleryItemLiveData
                Log.d(TAG, "Type of galleryItemLiveData: ${pagingData::class.java}")

                // This will log the instance of galleryItemLiveData
                Log.d(TAG, "Instance of galleryItemLiveData: $pagingData")

                viewLifecycleOwner.lifecycleScope.launch(errorHandler) {
                    try {
                        if(pagingData == null){Log.i(TAG, "submit success")}
                        Log.d(TAG, "Submitting new paging data to adapter.$pagingData")
                        adapter.submitData(pagingData)
                        Log.i(TAG, "submit success")
                        photoRecyclerView.adapter = adapter
                    } catch (e: Exception) {
                        Log.e(TAG, "Error submitting data to adapter: $e")
                    }
                }

                photoRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() { //观察滚动行为
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val totalItemCount = layoutManager.itemCount
                        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                        // Check if we've reached the threshold to preload more images
                        if (totalItemCount <= lastVisibleItem + PRELOAD_THRESHOLD) {
                            Log.i(TAG, "gsize: ${pagingData.size}")
                            preloadImages(pagingData.subList(lastVisibleItem + 1, min(lastVisibleItem + 1 + PRELOAD_AMOUNT, galleryItems.size)))
                        }
                    }
                })

                preloadOne(galleryItems) //预加载一次

            })

        val layoutManager = GridLayoutManager(context, 3) // Default to 3, will adjust dynamically
        photoRecyclerView.layoutManager = layoutManager
        photoRecyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val columnWidth = resources.getDimension(R.dimen.column_width).toInt()
                val screenWidth = photoRecyclerView.width
                val numberOfColumns = screenWidth / columnWidth
                layoutManager.spanCount = numberOfColumns
                photoRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun preloadImages(galleryItems: List<GalleryItem>) { //预加载函数
        galleryItems.forEach { thumbnailDownloader.scrollThumbnail(it.url) }
    }

    private fun preloadOne(galleryItems: List<GalleryItem>) { //预加载函数
        galleryItems.takeLast(50).forEach { thumbnailDownloader.preloadThumbnail(it.url) }
    }

    private fun firstpreload(galleryItems: List<GalleryItem>) { //预加载函数
        galleryItems.subList(4,18).forEach { thumbnailDownloader.firstloadThumbnail(it.url) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        thumbnailDownloader.clearQueue() //直接调用函数清理队列(无论下载图片还是装载图片)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleEvents.value = Lifecycle.Event.ON_DESTROY
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


                    searchView.clearFocus() // Hide soft keyboard
                    searchView.onActionViewCollapsed() // Collapse the SearchView

                    // Clear RecyclerView immediately
                    photoRecyclerView.adapter = PhotoAdapter(emptyList())  // Setting an empty adapter
                    progressBar.visibility = View.VISIBLE // Show loading indicator

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

        val toggleItem = menu.findItem(R.id.menu_item_toggle_polling)
        val isPolling = QueryPreferences.isPolling(requireContext()) //在共享数据里拿isPolling
        val toggleItemTitle = if (isPolling) {
            R.string.stop_polling
        } else {
            R.string.start_polling
        }
        toggleItem.setTitle(toggleItemTitle) //根据isPolling对错来显示对应菜单项的标题(false则显示启动(证明服务没在运行需要你点击启动))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_clear -> {
                photoGalleryViewModel.fetchPhotos("") //设置为空, 共享数据存储的最后一次搜索也变空
                true
            }
            R.id.menu_item_toggle_polling -> { //点击启停轮巡服务
                val isPolling = QueryPreferences.isPolling(requireContext())
                if (isPolling) { //true证明正在启用, 则(点击动作)要删除轮巡后台任务
                    WorkManager.getInstance().cancelUniqueWork(POLL_WORK)
                    QueryPreferences.setPolling(requireContext(), false) //设置共享数据的isPolling指示已停
                } else { //false证明未启用, 则(点击动作)要启用轮巡后台任务
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED) //限制为不计流量才启用
                        .build()
                    val periodicRequest = PeriodicWorkRequest //这里和OneTime相对
                        .Builder(PollWorker::class.java, 15, TimeUnit.MINUTES) //设置时间为15分钟(最小间隔), 应用完全关闭都会执行
                        .setConstraints(constraints)
                        .build()
                    WorkManager.getInstance().enqueueUniquePeriodicWork(
                        POLL_WORK,
                        ExistingPeriodicWorkPolicy.KEEP, //KEEP的意思是(对待已经安排好的具名工作任务)保留当前任务, 不更改它; REPLACE是替换
                        periodicRequest //需要一个网络请求
                    )
                    QueryPreferences.setPolling(requireContext(), true) //设置共享数据的isPolling指示已启动
                }
                activity?.invalidateOptionsMenu() //通过调用“invalidateOptionsMenu()”，您可以触发(Fragment关联的)“Activity”刷新其菜单选项，这使您可以根据应用程序的状态动态更新这些提示。 这可确保用户界面保持直观并响应应用程序的功能
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private inner class PhotoHolder(private val itemImageView: ImageView)
        : RecyclerView.ViewHolder(itemImageView), View.OnClickListener { //inner关键字能让类能访问外部类的属性和函数, 这里是调用Fragment.startActivity(Intent)

        private lateinit var galleryItem: GalleryItem

        init {
            itemView.setOnClickListener(this) //继承, 重写, setOnClickListener三步棋, 喝水一样简单
        }

        val bindDrawable: (Drawable) -> Unit  =
            itemImageView::setImageDrawable

        fun bindGalleryItem(item: GalleryItem) {
            galleryItem = item
        }

        override fun onClick(view: View) {
//            val intent = Intent(Intent.ACTION_VIEW, galleryItem.photoPageUri) //使用隐式Intent启动浏览器访问图片url
            val intent = PhotoPageActivity
                .newIntent(requireContext(), galleryItem.photoPageUri) //创建Intent返回自己(传递了Url)
            startActivity(intent)

//                    CustomTabsIntent.Builder() //使用Chrome Custom Tab显示网页
//                .setToolbarColor(ContextCompat.getColor(
//                    requireContext(), R.color.purple_500))
//                .setShowTitle(true)
//                .build()
//                .launchUrl(requireContext(), galleryItem.photoPageUri)
        }//- **上下文和导航**：“Intent”是使用“requireContext()”方法创建的，该方法提供片段宿主活动的上下文。 此上下文是从片段启动另一个活动所必需的。
//        - **Activity Stack**：当调用 `startActivity(intent)` 时，`PhotoPageActivity` 被放置在堆栈中当前 Activity 的顶部（大概是托管 `PhotoGalleryFragment` 的 Activity）。 这不会替换现有的活动，而是在其之上添加一个新层。

    }

    //为拿到layoutInflater把adapter变成内部类, 还方便后面访问父activity的属性和函数
    private inner class PhotoAdapter(private val galleryItems: List<GalleryItem>)
        : PagingDataAdapter<GalleryItem, PhotoHolder>(GalleryItemComparator) {

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
            holder.bindGalleryItem(galleryItem) //绑定galleryItem给Holder
            val placeholder: Drawable = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bill_up_close
            ) ?: ColorDrawable()
            holder.bindDrawable(placeholder)
            thumbnailDownloader.queueThumbnail(holder, galleryItem.url)
        }
    }

    companion object {
        const val PRELOAD_THRESHOLD = 100 // Start preloading 20 items before reaching the last visible item
        const val PRELOAD_AMOUNT = 25 // Number of items to preload
        fun newInstance() = PhotoGalleryFragment()
        private val GalleryItemComparator = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean =
                oldItem == newItem
        }
    }
}