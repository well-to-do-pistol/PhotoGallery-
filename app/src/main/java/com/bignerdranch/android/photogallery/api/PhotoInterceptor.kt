package com.bignerdranch.android.photogallery.api
//response和request都要okhttp3的
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

private const val API_KEY = "1200dc64f7481df99b157144104f9e23"

class PhotoInterceptor : Interceptor { //把共享参数值对单独抽出来放到一个拦截器(需要添加到Retrofit参数配置里)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request() //获取到原始网络请求

        //originalRequest.url()从原始网络请求中取出原始URL, 再使用HttpUrl.Builder添加需要的参数
        val newUrl: HttpUrl = originalRequest.url().newBuilder().addQueryParameter("api_key", API_KEY)
            .addQueryParameter("format", "json")
            .addQueryParameter("nojsoncallback", "1")
            .addQueryParameter("extras", "url_s")
            .addQueryParameter("safesearch", "1")
            .build()

        val newRequest: Request = originalRequest.newBuilder()
            .url(newUrl)
            .build() //构建新请求

        return chain.proceed(newRequest) //将请求返回链中
    }
}