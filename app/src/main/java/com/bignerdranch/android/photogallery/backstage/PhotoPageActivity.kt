package com.bignerdranch.android.photogallery.backstage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bignerdranch.android.photogallery.R

class PhotoPageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_page)

        val fm = supportFragmentManager
        val currentFragment = fm.findFragmentById(R.id.fragment_container)
        if (currentFragment == null) {
            val fragment = PhotoPageFragment.newInstance(intent.data!!) //activity本身就是intent, 现在它的data必须不为空(page url)
            fm.beginTransaction()
                .add(R.id.fragment_container, fragment) //将fragment加入栈
                .commit()
        }
    }

//    1. **模块**：在Kotlin中，模块是编译在一起的文件的集合。 这通常包括单个 IntelliJ IDEA 模块、Maven 项目、Gradle 源集或使用单次执行 Kotlin 编译器编译在一起的任何文件集中的所有内容。 模块是比包更大的单元，并且没有严格由目录结构定
    //internal防止外部客户端意外使用(webView)
    override fun onBackPressed() { //点击回退访问历史记录
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? PhotoPageFragment
        if (currentFragment != null && currentFragment.webView.canGoBack()) { //检查是否有历史记录
            currentFragment.webView.goBack() //回退, 回到前一个历史网页
        } else {
            super.onBackPressed() //大回退, 直接回到activity
        }
    }

    companion object {
        fun newIntent(context: Context, photoPageUri: Uri): Intent {
            return Intent(context, PhotoPageActivity::class.java).apply {
                data = photoPageUri
            }
        }
    }
}