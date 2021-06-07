package com.glimmer.requestdsl.request

import com.glimmer.uutil.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class APIDsl<Response> {
    lateinit var request: suspend () -> Response
    var onStart: (() -> Unit)? = null
    var onResponse: ((Response) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null
    var onFinally: (() -> Unit)? = null

    /*=======================================================================*/
    fun onStart(onStart: (() -> Unit)?) {
        this.onStart = onStart
    }

    fun onRequest(request: suspend () -> Response) {
        this.request = request
    }

    fun onResponse(onResponse: ((Response) -> Unit)?) {
        this.onResponse = onResponse
    }

    fun onError(onError: ((Exception) -> Unit)?) {
        this.onError = onError
    }

    fun onFinally(onFinally: (() -> Unit)?) {
        this.onFinally = onFinally
    }

    /*=======================================================================*/
    fun launch(viewModelScope: CoroutineScope) {
        viewModelScope.launch(context = Dispatchers.Main) {
            onStart?.invoke()
            try {
                val response = withContext(Dispatchers.IO) { request() }
                onResponse?.invoke(response)
            } catch (e: Exception) {
                KLog.e(e, "网络请求出错")
                onError?.invoke(e)
            } finally {
                onFinally?.invoke()
            }
        }
    }

}