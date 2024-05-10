package com.bignerdranch.android.photogallery.backstage

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bignerdranch.android.photogallery.FlickrFetchr
import com.bignerdranch.android.photogallery.GalleryItem
import com.bignerdranch.android.photogallery.NOTIFICATION_CHANNEL_ID
import com.bignerdranch.android.photogallery.PhotoGalleryActivity
import com.bignerdranch.android.photogallery.QueryPreferences
import com.bignerdranch.android.photogallery.R

private const val TAG = "PollWorker"

class PollWorker(val context: Context, workerParams: WorkerParameters)
    : Worker(context, workerParams) {


    @SuppressLint("MissingPermission")
    override fun doWork(): Result { //不能安排它做任何耗时任务
        val query = QueryPreferences.getStoredQuery(context)
        val lastResultId = QueryPreferences.getLastResultId(context)
        val items: List<GalleryItem> = if (query.isEmpty()) {
            FlickrFetchr().fetchPhotosRequest() //没有最后搜索字符串, 就获取一般最新图片 (先拿到Call)
                .execute()
                .body()
//                ?.photos
                ?.galleryItems
        } else {
            FlickrFetchr().searchPhotosRequest(query)//有最后搜索字符串, 就获取匹配字符串的最新图片 (先拿到Call)
                .execute()
                .body()
//                ?.photos
                ?.galleryItems
        } ?: emptyList()

        if (items.isEmpty()) { //什么都拿不到(请求失败)就返回
            return Result.success()
        }

        val resultId = items.first().id //有数据的话, 拿到图片集里第一个图片的Id
        if (resultId == lastResultId) {
            Log.i(TAG, "Got an old result: $resultId")
        } else {
            Log.i(TAG, "Got a new result: $resultId")
            QueryPreferences.setLastResultId(context, resultId) //如果是最新就保存

            val intent = PhotoGalleryActivity.newIntent(context) //拿到Intent
            val pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE) //最后一个参数必须设置为不可变, 否则不安全(可能让别人修改你的意图)

            val resources = context.resources
            val notification = NotificationCompat //NotificationCompat会自动检查, 低版本则会忽略通知频道
                .Builder(context, NOTIFICATION_CHANNEL_ID) //用通知频道ID设置通知频道
                .setTicker(resources.getString(R.string.new_pictures_title)) //发送给辅助服务, 如视力障碍
                .setSmallIcon(android.R.drawable.ic_menu_report_image) //定制通知外观
                .setContentTitle(resources.getString(R.string.new_pictures_title))
                .setContentText(resources.getString(R.string.new_pictures_text))
                .setContentIntent(pendingIntent) //加入点击事件
                .setAutoCancel(true) //一点击通知就会自动删除
                .build()

            showBackgroundNotification(0, notification)
        }

        return Result.success()
    }

    private fun showBackgroundNotification( //发送通知栏的notify将会转到配置文件定义的receiver进行
        requestCode: Int, //id的int如果相同则会覆盖相同id的通知, 不同则会展示新通知
        notification: Notification
    ) {
        val intent = Intent(ACTION_SHOW_NOTIFICATION).apply {
            putExtra(REQUEST_CODE, requestCode)
            putExtra(NOTIFICATION, notification) //这里拿到了通知栏要显示的通知
        } //在通知Intent里添加数据

        //这里的context是fragment, 将会被配置文件receiver拿到来启动NotificationManagerCompat发送(notify)通知
        context.sendOrderedBroadcast(intent, PERM_PRIVATE) //sendOrderedBroadcast保证broadcast一次一个投递到receiver
    }//发送广播时进行权限常量的限制

    companion object {
        const val ACTION_SHOW_NOTIFICATION =
            "com.bignerdranch.android.photogallery.SHOW_NOTIFICATION" //action常量
        const val PERM_PRIVATE = "com.bignerdranch.android.photogallery.PRIVATE" //权限常量, (已在manifest登记并在receiver进行限制)
        const val REQUEST_CODE = "REQUEST_CODE"
        const val NOTIFICATION = "NOTIFICATION"
    }
}

//**1. FlickrFetchr 职责：**
//- **FlickrFetchr** 主要设计用于与 Flickr API 交互以获取照片。 它处理网络请求并解析响应以提供可由应用程序中的 UI 组件观察的“LiveData”对象。 它的方法是为异步执行而定制的，因为 UI 的更新（例如在 RecyclerView 中显示照片）必须发生在主线程上并且是反应性的（即，当数据更改时 UI 会更新）。
//
//**2. PollWorker职责：**
//- **PollWorker** 另一方面，设计为作为后台任务运行，定期检查新内容。 这需要对网络请求进行不同的处理：
//- **后台执行**：`PollWorker` 在后台执行网络调用，不需要直接更新 UI。 相反，它可能需要存储获取的数据或通知系统新数据。
//- **同步请求**：因为“WorkManager”管理自己的线程，所以工作线程中的网络请求通常是同步的。 这与 UI 相关组件中使用的异步方法不同，其中使用“LiveData”和回调来处理响应。

// @@@ PhotoGallery应用中，需要在后台线程上发送broadcast（使用PollWorker.doWork()函数），在主线程上接收intent（在主线程上的onStart(...)函数中使用PhotoGalleryFragment登记的动态receiver）