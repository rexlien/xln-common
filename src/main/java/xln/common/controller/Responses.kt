package xln.common.controller

import org.springframework.http.HttpStatus
import xln.common.dist.BroadcastResult
import xln.common.web.BaseResponse

enum class  BroadcastCode(val code: Int) {
    OK(0),
    LEADER_NOT_FOUND(1)
}

data class BroadcastResponse(val code : BroadcastCode, val results: MutableMap<String, BroadcastResult>?) : BaseResponse()


object ConfigureResult {

    val OK = BaseResponse.BaseResult.SUCCEEDED

    val ERROR_CONFIGURE = BaseResponse.BaseResult(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Configure Error")

}