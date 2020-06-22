package xln.common.expression

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import xln.common.utils.CollectionUtils
import xln.common.utils.HttpUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap


/**
 * General data provider that handle scheme http path, the path url may contains _resPath parameter to specify an attribute in json response as source
 */
open class GeneralDataProvider : Context.DataProvider {


    private val httpMonoCache = ConcurrentHashMap<String, Mono<ResponseEntity<String>>>()
    private val resolveFunc = mutableMapOf<String, ResolveFunc>()
    private var placeHolderMap = mutableMapOf<String, String>()

    private object Mapper {
        val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    }

    private fun callHttp(url: String, key: String): Mono<ResponseEntity<String>> {

        var cacheMono = httpMonoCache.get(key)
        if (cacheMono != null) {
            return cacheMono
        }

        cacheMono = HttpUtils.httpGetMonoEntity<String>(url, String::class.java).cache()
        httpMonoCache[key] = cacheMono
        return cacheMono

    }

    constructor() {

    }

    constructor(placeHolderMap: MutableMap<String, String>) {
        this.placeHolderMap = placeHolderMap
    }

    @FunctionalInterface
    interface ResolveFunc {
        fun method(context: Context?, scheme: String?, host: String?, path: String?, params: MultiValueMap<String, *>): CompletableFuture<Any?>
    }


    override fun resolveURL(context: Context, scheme: String, host: String, path: String, params: MultiValueMap<String, *>): CompletableFuture<Any?>? {
        val func = resolveFunc[scheme]
        if (func != null) {
            return func.method(context, scheme, host, path, params)
        }

        val responseMono: Mono<ResponseEntity<String>>
        if (scheme == "http" || scheme == "https") {

            val url = UriComponentsBuilder.newInstance().scheme(scheme).host(host).path(path).queryParams(params as MultiValueMap<String, String>).build().toString()
            var paramsString = ""
            params.forEach {

                if(it.key.startsWith("_")) {
                    paramsString += "&${it.key}:${it.value.first()}"
                }

            }

            responseMono = callHttp(url, "$host$path$paramsString")

            return GlobalScope.future {


                var response: ResponseEntity<String>?
                try {
                    response = responseMono!!.awaitFirstOrNull()
                } catch (ex: WebClientResponseException) {
                    response = null
                }
                if (response != null && response.hasBody()) {

                    val body: Map<String, Any> = Mapper.mapper.readValue(response.body)
                    var responsePath = params.getFirst("_resPath") as String?
                    if (responsePath == null) {
                        responsePath = ""
                    }
                    return@future CollectionUtils.pathGet(responsePath, body)
                }



                null

            }
        }


        return null;
    }

    override fun getPathReplacement(placeholder: String): String {
        val res = placeHolderMap[placeholder]
        return res ?: super.getPathReplacement(placeholder)
    }


    fun registerResolver(scheme: String, func: ResolveFunc) {
        resolveFunc[scheme] = func
    }

    fun cleanCache() {
        httpMonoCache.clear()
    }
}