package com.bignerdranch.android.photogallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.viewModelScope
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ThumbnailDownloader"
private const val MESSAGE_DOWNLOAD = 0
private const val MESSAGE_PRELOAD = 1
private const val MESSAGE_SCROLL = 2
private const val MESSAGE_FIRSTLOAD = 3

//后台线程不能执行更新视图, 主线程不能执行耗时任务
class ThumbnailDownloader<in T>(
    private val responseHandler: Handler,
    private val onThumbnailDownloaded: (T, Bitmap) -> Unit //高阶函数, 定义成功下载缩略图后进行的操作(和Holder绑定)
) : HandlerThread(TAG) , Observer<Lifecycle.Event> {
    private var lruCache: LruCache<String, Bitmap>  //lruCache缓存

    private var hasQuit = false
    private lateinit var requestHandler: Handler //Android.os.Handler
    private lateinit var preloadHandler: Handler //预加载的Handler
    private lateinit var firstloadHandler: Handler //预加载的Handler
    private lateinit var scrollHandler: Handler //滚动预加载的Handler
    private val preloadThread = HandlerThread("ThumbnailPreloader") //预加载自己线程HandlerThread
    private val scrollThread = HandlerThread("ThumbnailScroller") //滚动预加载自己线程ThumbnailScroller
    private val firstloadThread = HandlerThread("Thumbnailfirster") //首次预加载自己线程ThumbnailScroller

    private val requestMap = ConcurrentHashMap<T, String>() //线程安全的HashMap
    private val flickrFetchr = fakeFetchr() //发起一个网络请求就创建并配置一个Retrofit新实例
    private val preloadRequestSet = ConcurrentHashMap<String, Boolean>() //并发哈希映射(已经预加载的图片, 避免重复预加载)

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() // Use 1/8th of available memory for cache
        val cacheSize = maxMemory / 8

        lruCache = LruCache(cacheSize)
    }

    fun observeLifecycleEvents(lifecycleEvents: LiveData<Lifecycle.Event>) { //传入带着Lifecycle.Event的LiveData
        lifecycleEvents.observeForever(this) //观察LiveData, this为观察者因为ThumbnailDownloader继承了Observer<Lifecycle.Event>(生命周期观察者)
    }

    override fun onChanged(value: Lifecycle.Event) {
        when (value) {
            Lifecycle.Event.ON_CREATE -> {
                start()
                preloadThread.start() //预加载线程启动
                scrollThread.start() //滚动预加载线程启动
                firstloadThread.start() //首次预加载线程启动
                looper
            }
            Lifecycle.Event.ON_DESTROY -> {
                quit()
                quitSafely() //使用LiveData自动结束扮演观察者角色
            }
            else -> {}
        }
    }

    fun clearQueue() {
        requestHandler?.removeMessages(MESSAGE_DOWNLOAD)
        requestMap.clear()
    }

    @Suppress("UNCHECKED_CAST")  //告诉Lint不用做类型匹配检查, 可以强制转换
    @SuppressLint("HandlerLeak") //这里创建了内部类Handler, 它天然持有外部类ThumbnailDownloader
    override fun onLooperPrepared() { //HandlerThread.onLooperPrepared()会在(后台)Looper首次检查消息队列之前调用, 所以该函数是创建Handler实现的好地方
        requestHandler = Handler(looper) {
            if (it.what == MESSAGE_DOWNLOAD) {
                val target = it.obj as T
                processDownload(target)
            }
            true
        }

        preloadHandler = Handler(preloadThread.looper) {
            if (it.what == MESSAGE_PRELOAD) {
                processPreload(it.obj as String)
            }
            true
        }

        firstloadHandler = Handler(firstloadThread.looper) {
            if (it.what == MESSAGE_FIRSTLOAD) {
                processFirstload(it.obj as String)
            }
            true
        }

        scrollHandler = Handler(scrollThread.looper) {
            if (it.what == MESSAGE_SCROLL) {
                scrollPreload(it.obj as String)
            }
            true
        }
    } //非静态内部类隐式保有对外部类的引用, 如果添加到主线程(那么保留在内部类的外部类引用就会长期存在, 导致其实例不能被正常回收)
    //只会有一个requestHandler负责处理循环队列的消息

    private fun processDownload(target: T) {
        val url = requestMap[target] ?: return
        handleRequest(target, url, false)
    }

    private fun processPreload(url: String) { //意思不用装载图片
        handleRequest(null, url, true)
    }

    private fun processFirstload(url: String) { //意思不用装载图片
        handleRequest(null, url, true)
    }

    private fun scrollPreload(url: String) { //意思不用装载图片
        handleRequest(null, url, true)
    }

    override fun quit(): Boolean {
        hasQuit = true
        preloadThread.quit() //预加载线程退出
        scrollThread.quit() //滚动预加载线程退出
        firstloadThread.quit() //首次预加载线程退出
        return super.quit()
    }

    //这里是和外界持续不断交流的原因
    //Message变量:1.What:Int;2.obj:随消息发送的对象;3.target:处理消息的Handler
    //这里是和外界持续不断交流的原因
    //Message变量:1.What:Int;2.obj:随消息发送的对象;3.target:处理消息的Handler
    fun queueThumbnail(target: T, url: String) { //这里的T是holder
        Log.i(TAG, "Got a URL: $url")
        requestMap[target] = url
        requestHandler.obtainMessage(MESSAGE_DOWNLOAD, target) //obtainMessage是智能的, obj是T(PhotoHolder), target是requestHandler
            .sendToTarget() //这里必须要发信息才能下载图片 1.第一次请求:下载图片
    }

    fun preloadThumbnail(url: String) {
        if (lruCache.get(url) == null && preloadRequestSet.putIfAbsent(url, true) == null) { //如果映射有, 则返回true, 确保只预加载一次, 防止缓存满了之后又预加载20个图片
            Log.d(TAG, "zzz2")
            preloadHandler.obtainMessage(MESSAGE_PRELOAD, url).sendToTarget() //放进自己的预加载线程
        } //第一次请求, 只是下载图片就够了
        else if (preloadRequestSet.putIfAbsent(url, true) != null){
            preloadRequestSet.remove(url)
        }
    }

    fun firstloadThumbnail(url: String) {
        if (lruCache.get(url) == null && preloadRequestSet.putIfAbsent(url, true) == null) { //如果映射有, 则返回true, 确保只预加载一次, 防止缓存满了之后又预加载20个图片
            Log.d(TAG, "zzz1")
            firstloadHandler.obtainMessage(MESSAGE_FIRSTLOAD, url).sendToTarget() //放进自己的预加载线程
        } //第一次请求, 只是下载图片就够了
        else if (preloadRequestSet.putIfAbsent(url, true) != null){
            preloadRequestSet.remove(url)
        }
    }

    fun scrollThumbnail(url: String){
        if (lruCache.get(url) == null && preloadRequestSet.putIfAbsent(url, true) == null) { //如果映射有, 则返回true, 确保只预加载一次, 防止缓存满了之后又预加载20个图片
            Log.d(TAG, "zzz3")
            scrollHandler.obtainMessage(MESSAGE_SCROLL, url).sendToTarget() //放进自己的预加载线程
        } //第一次请求, 只是下载图片就够了
        else if (preloadRequestSet.putIfAbsent(url, true) != null){
            preloadRequestSet.remove(url)
        }
    }

    private fun handleRequest(target: T?, url: String, isPreload: Boolean) {
        val bitmap = try {
            lruCache[url] ?: flickrFetchr.fetchPhoto(url)?.also {
                lruCache.put(url, it)
            } //从缓存拿图片, 没有就下载并放到缓存
        }catch (e: Exception) {
            Log.e(TAG, "Failed to fetch image from $url with error: ${e.message}")
            null // Handle error by returning null
        }


        if (!isPreload && target != null && requestMap[target] == url && !hasQuit) {
            responseHandler.post { requestMap.remove(target) //清理target和url (这是是确保holder和url匹配的东西, 既然正常加载了就去除)
                onThumbnailDownloaded(target, bitmap!!) }
        } //满足条件发送装载图片请求(发送到主线程Looper)
    } //2.第二次请求:装载图片
}