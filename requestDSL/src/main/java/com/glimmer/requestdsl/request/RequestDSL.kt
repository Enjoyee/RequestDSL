package com.glimmer.requestdsl.request

import android.content.Context
import com.glimmer.requestdsl.gson.CustomizeGsonConverterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import timber.log.Timber
import java.net.Proxy
import java.util.concurrent.TimeUnit

object RequestDSL {
    private lateinit var mAppContext: Context
    private var mLoggable = true
    private lateinit var mHeaders: HeaderInterceptor
    private lateinit var mOkHttpBuilder: OkHttpClient.Builder
    private lateinit var mRetrofitBuilder: Retrofit.Builder

    /*=======================================================================*/
    fun init(appContext: Context, requestConfig: (RequestConfig.() -> Unit)? = null) {
        init(appContext, "", requestConfig)
    }

    fun init(
        appContext: Context,
        baseUrl: String,
        requestConfig: (RequestConfig.() -> Unit)? = null
    ) {
        if (Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        mAppContext = appContext.applicationContext
        mHeaders = HeaderInterceptor()
        initConfig(requestConfig, baseUrl)
    }

    /*=======================================================================*/
    fun <ApiService> createApiService(apiService: Class<ApiService>): ApiService {
        return mRetrofitBuilder.build().create(apiService)
    }

    fun putHead(key: String, value: String): HeaderInterceptor {
        mHeaders.put(key, value)
        return mHeaders
    }

    /*=======================================================================*/
    fun getDefaultOkHttpBuilder(appContext: Context): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .cache(Cache(appContext.cacheDir, 10 * 1024 * 1024L))
            .addInterceptor(mHeaders)
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
    }

    private fun initConfig(requestConfig: (RequestConfig.() -> Unit)?, baseUrl: String) {
        // dsl
        val dsl = if (requestConfig != null) RequestConfig().also(requestConfig) else null
        // OKHttp Builder
        initOkHttpConfig(dsl)
        // request Header
        initRequestHeader(dsl)
        // Retrofit Builder
        initRetrofitConfig(dsl, baseUrl)
    }

    private fun initOkHttpConfig(config: RequestConfig?) {
        val defaultOkHttpBuilder = getDefaultOkHttpBuilder(mAppContext)
        mOkHttpBuilder = config?.mBuildOkHttp?.invoke(defaultOkHttpBuilder) ?: defaultOkHttpBuilder
        mLoggable = config?.mShowLog?.invoke() ?: true
        if (mLoggable) {
            mOkHttpBuilder.addInterceptor(HttpLoggingInterceptor { msg ->
                Timber.tag("OkHttp").d(msg)
            }.apply {
                setLevel(HttpLoggingInterceptor.Level.BODY)
            })
        }
    }

    private fun initRequestHeader(dsl: RequestConfig?) {
        dsl?.mHeader?.invoke()?.iterator()?.let { iterator ->
            while (iterator.hasNext()) {
                val item = iterator.next()
                putHead(item.key, item.value)
            }
        }
    }

    private fun initRetrofitConfig(config: RequestConfig?, baseUrl: String) {
        // Retrofit Builder
        val retrofitBuilder = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(CustomizeGsonConverterFactory.create())
            .client(mOkHttpBuilder.build())
        mRetrofitBuilder = config?.mBuildRetrofit?.invoke(retrofitBuilder) ?: retrofitBuilder
    }

    /*=======================================================================*/
    fun loggable() = mLoggable
}

class RequestConfig {
    internal var mShowLog: (() -> Boolean) = { true }
    internal var mBuildOkHttp: ((OkHttpClient.Builder) -> OkHttpClient.Builder)? = null
    internal var mHeader: (() -> (Map<String, String>))? = null
    internal var mBuildRetrofit: ((Retrofit.Builder) -> Retrofit.Builder)? = null

    fun showApiLog(showLog: (() -> Boolean)) {
        mShowLog = showLog
    }

    fun okHttp(builder: ((OkHttpClient.Builder) -> OkHttpClient.Builder)?) {
        mBuildOkHttp = builder
    }

    fun putHeader(header: (() -> (Map<String, String>))?) {
        mHeader = header
    }

    fun retrofit(builder: ((Retrofit.Builder) -> Retrofit.Builder)?) {
        mBuildRetrofit = builder
    }

}