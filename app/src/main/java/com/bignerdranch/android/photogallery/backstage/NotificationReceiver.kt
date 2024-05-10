package com.bignerdranch.android.photogallery.backstage

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

private const val TAG = "NotificationReceiver"
//standalone receiver(应用进程已消亡也可被激活); dynamic receiver(与activity或fragment生命周期绑定)
class NotificationReceiver : BroadcastReceiver() { //在配置文件登记的普通receiver, 由PollWorker发送通知

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) { //onReceive是在主线程调用的(所以并不能按顺序执行(同步并发))
        Log.i(TAG, "received result: $resultCode") //定义在配置文件, 现在的操作只是打印一个日志
        //通过resultCode来决定取消或发送通知
        if (resultCode != Activity.RESULT_OK) {
            // A foreground activity canceled the broadcast
            return
        } //选择了在这里发送通知栏的通知(notify), 并且在配置文件设置了优先级为-999(用户能定义的最低优先级)来确保NotificationReceiver在[动态登记receiver]之后接受目标broadcast, -1000及以下是系统保留值

        val requestCode = intent.getIntExtra(PollWorker.REQUEST_CODE, 0)
        val notification: Notification =
            intent.getParcelableExtra(PollWorker.NOTIFICATION)!!

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(requestCode, notification)
    }
}