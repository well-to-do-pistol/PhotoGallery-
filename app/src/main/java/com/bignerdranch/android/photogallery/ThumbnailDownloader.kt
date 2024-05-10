package com.bignerdranch.android.photogallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.OnLifecycleEvent
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ThumbnailDownloader"
private const val MESSAGE_DOWNLOAD = 0

//后台线程不能执行更新视图, 主线程不能执行耗时任务
class ThumbnailDownloader<in T>(
    private val responseHandler: Handler,
    private val onThumbnailDownloaded: (T, Bitmap) -> Unit //高阶函数, 定义成功下载缩略图后进行的操作(和Holder绑定)
) : HandlerThread(TAG) , Observer<Lifecycle.Event> {

    fun observeLifecycleEvents(lifecycleEvents: LiveData<Lifecycle.Event>) { //传入带着Lifecycle.Event的LiveData
        lifecycleEvents.observeForever(this) //观察LiveData, this为观察者因为ThumbnailDownloader继承了Observer<Lifecycle.Event>(生命周期观察者)
    }

    override fun onChanged(value: Lifecycle.Event) {
        when (value) {
            Lifecycle.Event.ON_CREATE -> {
                start()
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

    private var hasQuit = false
    private lateinit var requestHandler: Handler //Android.os.Handler
    private val requestMap = ConcurrentHashMap<T, String>() //线程安全的HashMap
    private val flickrFetchr = FlickrFetchr() //发起一个网络请求就创建并配置一个Retrofit新实例

    @Suppress("UNCHECKED_CAST")  //告诉Lint不用做类型匹配检查, 可以强制转换
    @SuppressLint("HandlerLeak") //这里创建了内部类Handler, 它天然持有外部类ThumbnailDownloader
    override fun onLooperPrepared() { //HandlerThread.onLooperPrepared()会在(后台)Looper首次检查消息队列之前调用, 所以该函数是创建Handler实现的好地方
        requestHandler = object : Handler() {
            override fun handleMessage(msg: Message) { //handleMessage在消息队列中的下载请求被取出并可以处理时调用
                if (msg.what == MESSAGE_DOWNLOAD) {
                    val target = msg.obj as T
                    Log.i(TAG, "Got a request for URL: ${requestMap[target]}")
                    handleRequest(target)
                }
            }
        }
    } //非静态内部类隐式保有对外部类的引用, 如果添加到主线程(那么保留在内部类的外部类引用就会长期存在, 导致其实例不能被正常回收)
    //只会有一个requestHandler负责处理循环队列的消息
    override fun quit(): Boolean {
        hasQuit = true
        return super.quit()
    }

    //这里是和外界持续不断交流的原因
    //Message变量:1.What:Int;2.obj:随消息发送的对象;3.target:处理消息的Handler
    fun queueThumbnail(target: T, url: String) { //这里的T是holder
        Log.i(TAG, "Got a URL: $url")
        requestMap[target] = url
        requestHandler.obtainMessage(MESSAGE_DOWNLOAD, target) //obtainMessage是智能的, obj是T(PhotoHolder), target是requestHandler
            .sendToTarget()
    }

    private fun handleRequest(target: T) {
        val url = requestMap[target] ?: return
        val bitmap = flickrFetchr.fetchPhoto(url) ?: return //图片url会覆盖基url所以相当于使用了独立的网络请求

        //发布Message的便利函数
        responseHandler.post(Runnable { //在主线程上执行, (因为responseHandler和主线程Looper绑定)
            if (requestMap[target] != url || hasQuit) { //1.确保RecyclerView的视图持有者未被回收或在拿到bitmap后没有更新请求
                //2.确保后台线程没有退出, “hasQuit”检查是一种保障措施，可确保执行 UI 更新的“Runnable”仅在从生命周期(应和关联组件生命周期相同)和资源管理角度(阻止回收)来看是适当且安全的情况下才会执行此操作
                return@Runnable //指定返回来自Runnable lambda
            }

            requestMap.remove(target) //清理target和url
            onThumbnailDownloaded(target, bitmap)
        })
    }
}