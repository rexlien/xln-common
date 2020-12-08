package xln.common.dist

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.TypeRegistry
import com.google.protobuf.util.JsonFormat
import etcdserverpb.Rpc.LeaseKeepAliveRequest
import etcdserverpb.Rpc.LeaseKeepAliveResponse
import io.grpc.*
import io.grpc.reflection.v1alpha.ServerReflectionGrpc
import io.grpc.reflection.v1alpha.ServerReflectionRequest
import io.grpc.reflection.v1alpha.ServerReflectionResponse
import io.grpc.reflection.v1alpha.ServiceResponse
import io.grpc.stub.ClientCalls
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import xln.common.config.ClusterConfig
import xln.common.grpc.DynamicMessageMarshaller
import xln.common.grpc.GrpcFluxStream
import xln.common.grpc.GrpcReflectionUtils
import xln.common.grpc.UnaryCallObserver
import java.io.FileDescriptor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

class ChannelManager {

    private val clusterChannels = ConcurrentHashMap<String, ManagedChannel>()
    private val grpcReflection = xln.common.grpc.GrpcReflection()

    fun openChannel(node : Node) {
        val nodeInfo = node.info?:return
        val storeKey = node.storeKey?:return

        log.debug("${node.storeKey} opened")
        val newChannel = ManagedChannelBuilder.forAddress(nodeInfo.address, nodeInfo.clusterPort).usePlaintext().build()
        clusterChannels[storeKey] = newChannel

        runBlocking {
            grpcReflection.createReflection(newChannel)
        }


    }

    fun closeChannel(node: Node) {
        val key = node.storeKey
        val channel = clusterChannels[key]?:return

        log.debug("${node.storeKey} closed")
        grpcReflection.cleanReflection(channel)
        clusterChannels.remove(key)
        channel.shutdown()


    }

    suspend fun callMethodJson(node: Node, serviceName: String, methodName: String, jsonPayLoad: String) : String? {
        val channel = getChannel(node)?: return null
        return grpcReflection.callMethodJson(channel, serviceName, methodName, jsonPayLoad)

    }

    fun getChannel(node: Node) : ManagedChannel? {
        val key = node.storeKey
        return clusterChannels[key]
    }




}