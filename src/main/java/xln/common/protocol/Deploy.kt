package xln.common.protocol

//class Deployment(var project : String, var registry: String, var manifestPath : String, var extraParameters: Map<String, String>)


data class DeployUnit(val name: String, val parameters: Map<String, String>)

data class DeploymentRegisterRequest(val templateId: String, val pipelineId: String, val deployUnits: List<DeployUnit>,
                                     val autoRun : Boolean, val sendChatRun: Boolean, val ttl: Long)

data class DeploymentRegisterResponse(val deploymentIds: List<String>)


data class DeploymentRunRequest(val deploymentId: String)