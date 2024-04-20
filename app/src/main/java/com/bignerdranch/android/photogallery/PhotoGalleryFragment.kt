package com.bignerdranch.android.photogallery

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        photoGalleryViewModel =
            ViewModelProvider(this).get(PhotoGalleryViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

    private class PhotoHolder(itemTextView: TextView)
        : RecyclerView.ViewHolder(itemTextView) {

        val bindTitle: (CharSequence) -> Unit = itemTextView::setText
        //- **CharSequence**：这是一个表示字符序列的接口。 在 Java 和 Kotlin 中，“String”是“CharSequence”的常见类型。 因此，您可以将“String”传递给需要“CharSequence”的函数。
        //- **bindTitle**：这是一个 lambda 函数，它接受 `CharSequence` 并返回 `Unit`。 lambda 函数被分配给 `itemTextView::setText`，它是 Kotlin 中的方法引用。 这意味着“bindTitle”将使用给定的任何“CharSequence”调用“itemTextView”上的“setText”方法。

    }

    private class PhotoAdapter(private val galleryItems: List<GalleryItem>)
        : RecyclerView.Adapter<PhotoHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PhotoHolder {
            val textView = TextView(parent.context)
            return PhotoHolder(textView)
        }

        override fun getItemCount(): Int = galleryItems.size

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            val galleryItem = galleryItems[position]
            holder.bindTitle(galleryItem.title)
        }
    }

    companion object {
        fun newInstance() = PhotoGalleryFragment()
    }
}