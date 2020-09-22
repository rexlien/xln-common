package xln.common.controller

data class ConfigRequest(val directory:String, val key:String, val value: String, var type: String?)