package xln.common.controller

import xln.common.dist.BroadcastResult
import xln.common.web.BaseResponse

data class BroadcastResponse(val results: MutableMap<String, BroadcastResult>) : BaseResponse()