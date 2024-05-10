package com.bignerdranch.android.photogallery.backstage

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.bignerdranch.android.photogallery.R

private const val ARG_URI = "photo_page_url"

class PhotoPageFragment : VisibleFragment() {

    private lateinit var uri: Uri
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uri = arguments?.getParcelable(ARG_URI) ?: Uri.EMPTY
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_photo_page, container, false)

        progressBar = view.findViewById(R.id.progress_bar)
        progressBar.max = 100 //设置进度条计数为100

        webView = view.findViewById(R.id.web_view)
        webView.settings.javaScriptEnabled = true //用Settings类启动javaScript
        webView.webChromeClient = object : WebChromeClient() { //用这个实例渲染WebView
            override fun onProgressChanged(webView: WebView, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                (activity as AppCompatActivity).supportActionBar?.subtitle = title //从WebView拿到一个title传给应用栏的子标题
            }
        }
        webView.webViewClient = WebViewClient() //WebViewClient类用来响应WebView上的渲染事件, 这是必须的, 告诉WebView自己载入Url
        webView.loadUrl(uri.toString()) //必须最后载入Url, 等WebView加载完成

        return view
    }

    companion object {
        fun newInstance(uri: Uri): PhotoPageFragment { //通过自己的伴生对象和arguments和putParcelable传递数据(Url)
            return PhotoPageFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_URI, uri)
                }
            }
        }
    }
}