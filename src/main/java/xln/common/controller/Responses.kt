package xln.common.controller

import xln.common.dist.BroadcastResult
import xln.common.web.BaseResponse

enum class  BroadcastCode(val code: Int) {

    OK(0),
    LEADER_NOT_FOUND(1)

}

data class BroadcastResponse(val code : BroadcastCode, val results: MutableMap<String, BroadcastResult>?) : BaseResponse()