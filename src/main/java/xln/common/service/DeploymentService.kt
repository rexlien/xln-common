package xln.common.service

import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import xln.common.config.DeploymentConfig
import xln.common.etcd.KVManager
import xln.common.proto.deploypb.DeployOuterClass
import xln.common.protocol.DeployUnit
import xln.common.protocol.SpinnakerRequest
import xln.common.utils.HttpUtils
import xln.common.utils.ProtoUtils

private val log = KotlinLogging.logger {}

data class Deployment(val templateId: String, val pipelineId: String, val longevity: Long, val deployUnits: List<DeployUnit>)

@Service
@ConditionalOnProperty(prefix = "xln.deployment-config", name = ["enable"], havingValue = "true")
class DeploymentService(private val etcdClient: EtcdClient, private val deploymentConfig: DeploymentConfig) {

    suspend fun registerDeployment(deploy: Deployment) : List<String> {
        //val timestamp = Instant.now().toEpochMilli()

        val ret = mutableListOf<String>()
        deploy.deployUnits.forEach {

            val versionKey = "xln-deploy-version:${deploy.templateId}"
            val version = etcdClient.kvManager.inc(versionKey).awaitSingle()


            val key = "xln-deploy:${deploy.templateId}:$version"

            val builder = DeployOuterClass.Deploy.newBuilder().setTemplateKey(deploy.templateId)
                    .setPipelineId(deploy.pipelineId).putAllParameters(it.parameters)

            //val value = request.
            val response = etcdClient.kvManager.transactPut(KVManager.TransactPut(
                    KVManager.PutOptions().withKey(key).withTtlSecs(deploy.longevity).withValue(builder.build().toByteString()))).awaitSingle()

            if (response.succeeded) {
                ret.add(key)
            } else {
                log.error("register deployment failed")
            }
        }
        return ret
    }

    suspend fun runDeployment(deploymentId: String) {

        val deploymentStr = etcdClient.kvManager.get(deploymentId).awaitSingle()
        val deployment = DeployOuterClass.Deploy.parseFrom(deploymentStr)//ProtoUtils.fromJson(deploymentStr.toStringUtf8(), DeployOuterClass.Deploy::class.java)//gson.fromJson(deploymentStr.toStringUtf8(), Deployment::class.java)

        val pipelineUrl = "${deploymentConfig.spinnakerConfig.url}/webhooks/webhook/${deployment.pipelineId}"
        val response = HttpUtils.httpCallMonoResponseEntity<Any>(pipelineUrl, null,
                HttpMethod.POST, Any::class.java, null, SpinnakerRequest(deployment.parametersMap)).awaitSingle()
        log.info(response.toString())
    }

    //suspend fun runDeployment
}