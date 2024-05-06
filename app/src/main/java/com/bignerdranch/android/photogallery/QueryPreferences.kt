package com.bignerdranch.android.photogallery

import android.content.Context
import android.preference.PreferenceManager
import androidx.core.content.edit

private const val PREF_SEARCH_QUERY = "searchQuery"

object QueryPreferences {

    fun getStoredQuery(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context) //利用SharedPreferences实例
        return prefs.getString(PREF_SEARCH_QUERY, "")!! //""是默认值
    }
    fun setStoredQuery(context: Context, query: String) {
//        PreferenceManager.getDefaultSharedPreferences(context)
//            .edit() //相当于事务, 可将一组数组操作加入事务队列
//            .putString(PREF_SEARCH_QUERY, query)
//            .apply() //异步执行
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit { putString(PREF_SEARCH_QUERY, query) }

    }
}

//``java
//PreferenceManager.getDefaultSharedPreferences（上下文）
//.edit() // 在 SharedPreferences 上启动编辑操作
//.putString(PREF_SEARCH_QUERY, query) // 将字符串添加到 SharedPreferences 编辑器
//.apply() // 异步提交编辑
//````
//
//1. **编辑事务**：启动对 SharedPreferences 对象的编辑操作。
//2. **putString**：将字符串值添加到 SharedPreferences 编辑器。 这是编辑会话中的修改操作。
//3. **应用**：将更改异步应用到 SharedPreferences 文件。 “apply()”是非阻塞的，并安排将更改写入后台的持久存储。 它不提供成功反馈，并且会默默地处理失败。
//
//### 第二种方法：使用 Android KTX
//
//``kotlin
//PreferenceManager.getDefaultSharedPreferences（上下文）
//.edit { putString(PREF_SEARCH_QUERY, 查询) }
//````
//
//1. **使用 Lambda 编辑函数 (KTX)**：这是 Android KTX 扩展提供的一种简化且更惯用的 Kotlin 方法。 这里的 edit 方法是一个 Kotlin 扩展函数，它接受 lambda，允许您直接在大括号内执行编辑操作。
//
//2. **简洁和安全**：KTX 的 `edit` 函数自动处理事务的创建和提交（可以使用 `apply()` 或 `commit()`，其中 `apply()` 是 默认值）。 它减少了样板文件并确保 SharedPreferences 编辑器在有限的范围内正确使用。