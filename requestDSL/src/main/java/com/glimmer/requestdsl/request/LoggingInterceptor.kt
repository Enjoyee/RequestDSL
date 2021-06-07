package com.glimmer.requestdsl.request

import android.text.TextUtils
import com.glimmer.requestdsl.utils.OkLog
import okhttp3.*
import okhttp3.internal.http.StatusLine
import okio.Buffer
import okio.GzipSource
import okio.source
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

class LoggingInterceptor : Interceptor {

    companion object {
        private val UTF8 = StandardCharsets.UTF_8
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!RequestDSL.loggable()) {
            return chain.proceed(request)
        }
        val requestBody = request.body
        val hasRequestBody = requestBody != null
        val connection = chain.connection()
        val protocol = connection?.protocol() ?: Protocol.HTTP_1_1
        OkLog.start("Request ↓↓↓")
        OkLog.log("Method-->" + request.method)
        OkLog.log("URL-->" + request.url)
        OkLog.log("Protocol-->$protocol")
        if (hasRequestBody) {
            if (requestBody!!.contentType() != null) {
                OkLog.log("Content-Type-->" + requestBody.contentType())
            }
            if (requestBody.contentLength() != -1L) {
                OkLog.log("Content-Length-->" + requestBody.contentLength())
            }
            if ("POST" == request.method) {
                val sb = StringBuilder()
                if (request.body is FormBody) {
                    val body = request.body as FormBody?
                    if (body != null) {
                        for (i in 0 until body.size) {
                            sb.append(body.encodedName(i)).append("=").append(body.encodedValue(i)).append(",")
                        }
                        OkLog.log("Params-->{$sb}")
                    }
                }
            }
        }
        val requestHeaders = request.headers
        run {
            var i = 0
            val count = requestHeaders.size
            while (i < count) {
                val name = requestHeaders.name(i)
                if (!"Content-Type".equals(name, ignoreCase = true) && !"Content-Length".equals(name, ignoreCase = true)) {
                    OkLog.log("Headers-->" + name + ": " + requestHeaders.value(i))
                }
                i++
            }
        }
        if (!hasRequestBody) {
            OkLog.end("Request ↑↑↑")
        } else {
            var buffer = Buffer()
            requestBody!!.writeTo(buffer)
            var charset = UTF8
            val contentType = requestBody.contentType()
            if (contentType != null) {
                charset = contentType.charset(UTF8)
            }
            if (requestBody.contentLength() != 0L && requestBody.contentLength() < 32 * 1024 && bodyIsText(contentType)) {
                if (bodyEncodedGzip(request.headers)) {
                    buffer = decodeGzip(buffer)
                }
                if (charset != null) {
                    OkLog.log("Params-->" + buffer.readString(charset))
                }
            }
            OkLog.log("Content-Length-->" + requestBody.contentLength())
            OkLog.end("Request ↑↑↑ ")
        }
        val startNs = System.nanoTime()
        val response = chain.proceed(request)
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val responseBody = response.body!!
        val contentLength = responseBody.contentLength()
        OkLog.start("Response ↓↓↓")
        OkLog.log("Code-->" + response.code)
        OkLog.log("Message-->" + response.message)
        OkLog.log("URL-->" + response.request.url)
        OkLog.log("CostTime-->" + tookMs + "ms")
        OkLog.log("BodySize-->" + contentLength + "byte")
        OkLog.log("Method-->" + response.request.method)
        val responseHeaders = response.headers
        var i = 0
        val count = responseHeaders.size
        while (i < count) {
            OkLog.log(responseHeaders.name(i) + ": " + responseHeaders.value(i))
            i++
        }
        if (!hasBody(response)) {
            OkLog.end("Response ↑↑↑")
        } else {
            val source = responseBody.source()
            source.request(Long.MAX_VALUE)
            var buffer = source.buffer
            var charset = UTF8
            val contentType = responseBody.contentType()
            if (contentType != null) {
                OkLog.log("ContentType-->$contentType")
                charset = try {
                    contentType.charset(UTF8)
                } catch (e: UnsupportedCharsetException) {
                    OkLog.end("Response ↑↑↑")
                    return response
                }
            }
            if (contentLength != 0L && contentLength < 32 * 1024 && bodyIsText(contentType)) {
                if (bodyEncodedGzip(response.headers)) {
                    buffer = decodeGzip(buffer)
                }
                OkLog.log("________________________________________________________")
                OkLog.log("*******************ResponseBody*************************")
                if (charset != null) {
                    OkLog.log(buffer.clone().readString(charset))
                }
            }
            OkLog.log("ResponseBody-->" + buffer.size + "byte")
            OkLog.end("Response ↑↑↑")
        }
        return response
    }

    private fun bodyEncodedGzip(headers: Headers): Boolean {
        val contentEncoding = headers["Content-Encoding"]
        return contentEncoding != null && contentEncoding.equals("gzip", ignoreCase = true)
    }

    private fun bodyIsText(contentType: MediaType?): Boolean {
        return contentType != null && ("text" == contentType.type || "json" == contentType.subtype || contentType.subtype.contains("form"))
    }

    @Throws(IOException::class)
    private fun decodeGzip(buffer: Buffer): Buffer {
        val gzipSource = GzipSource(buffer.clone().inputStream().source())
        val count = buffer.size
        val resultBuffer = Buffer()
        gzipSource.read(resultBuffer, count)
        gzipSource.close()
        return resultBuffer
    }

    private fun hasBody(response: Response): Boolean {
        if (response.request.method == "HEAD") {
            return false
        }
        val responseCode = response.code
        return if ((responseCode < StatusLine.HTTP_CONTINUE || responseCode >= 200)
            && responseCode != HttpURLConnection.HTTP_NO_CONTENT && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED
        ) {
            true
        } else contentLength(response.headers) != -1L || "chunked".equals(response.header("Transfer-Encoding"), ignoreCase = true)
    }

    private fun contentLength(headers: Headers): Long {
        var length = headers["Content-Length"]
        if (TextUtils.isEmpty(length)) {
            return -1
        }
        length = length!!.trim { it <= ' ' }
        return try {
            length.toLong()
        } catch (e: NumberFormatException) {
            -1
        }
    }

}