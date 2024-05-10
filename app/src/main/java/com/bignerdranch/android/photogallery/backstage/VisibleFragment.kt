package com.bignerdranch.android.photogallery.backstage

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment

private const val TAG = "VisibleFragment"
abstract class VisibleFragment : Fragment() { //隐藏前台通知的通知型fragment

    private val onShowNotification = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // If we receive this, we're visible, so cancel
            // the notification
            Log.i(TAG, "canceling notification")
            resultCode = Activity.RESULT_CANCELED //通知SHOW_NOTIFICATION的发送者要取消通知信息, 也会发送给接受链的所有broadcast receiver
        } //用结果码Activity.RESULT_CANCELED来取消通知(截胡者)
    }

    override fun onStart() { //如果是在onCreate和onDestroy登记撤销, 因为它们中getActivity()会返回不同的值, 应改用requireActivity().getApplicationContext()函数
        super.onStart()
        val filter = IntentFilter(PollWorker.ACTION_SHOW_NOTIFICATION) //和manifest配置文件里声明的IntentFilter一样也要添加action常量
        requireActivity().registerReceiver( //动态登记receiver,  - 使用“requireActivity()”而不是“getActivity()”来确保片段当前已附加到活动。 如果片段未附加，则“requireActivity()”将抛出“IllegalStateException”，从而避免潜在的空指针异常并确保片段生命周期内更安全的使用。
            onShowNotification, //用一个变量接受创建的receiver实例,  - `onShowNotification` 是对您可能之前创建的 `BroadcastReceiver` 实例的引用（可能在 `onCreate()` 中或在片段的初始化阶段）。 该接收器将在发送广播时处理该广播。
            filter, //- 这会将之前创建的“IntentFilter”传递给“registerReceiver”方法，指示“onShowNotification”应该只处理与此过滤器匹配的 Intent
            PollWorker.PERM_PRIVATE, //添加限制常量
            null //- 最后一个“null”参数用于将调用接收器的“Handler”。 如果为“null”，则默认为主应用程序线程，这适用于大多数与 UI 相关的更新
        )
    }

    override fun onStop() {
        super.onStop()
        requireActivity().unregisterReceiver(onShowNotification) //移除登记
    }
}