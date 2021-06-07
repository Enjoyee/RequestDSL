package com.glimmer.requestdsl.request

import okhttp3.Interceptor
import okhttp3.Response

class HeaderInterceptor : Interceptor {
    private var mHeaders = hashMapOf<String, String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        mHeaders.forEach { (t, u) ->
            requestBuilder.addHeader(t, u)
        }
        return chain.proceed(requestBuilder.build())
    }

    fun put(key: String, value: String): HeaderInterceptor {
        mHeaders[key] = value
        return this
    }

    fun put(headers: HashMap<String, String>): HeaderInterceptor {
        this.mHeaders.putAll(headers)
        return this
    }
}