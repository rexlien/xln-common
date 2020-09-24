package xln.common.grpc

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.TypeRegistry
import com.google.protobuf.util.JsonFormat
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.reflection.v1alpha.ServerReflectionGrpc
import io.grpc.reflection.v1alpha.ServerReflectionRequest
import io.grpc.reflection.v1alpha.ServerReflectionResponse
import io.grpc.reflection.v1alpha.ServiceResponse
import io.grpc.stub.ClientCalls
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object GrpcReflectionUtils {

    fun genFileDescriptor(protos : Map<String, DescriptorProtos.FileDescriptorProto> ) : Map<String, Descriptors.FileDescriptor> {
        val fileDescriptor = mutableMapOf<String, Descriptors.FileDescriptor>()
        protos.forEach {

            buildProto(it.value, protos, fileDescriptor)
        }

        return fileDescriptor
    }

    fun buildProto(proto : DescriptorProtos.FileDescriptorProto, protos : Map<String, DescriptorProtos.FileDescriptorProto>, cache : MutableMap<String, Descriptors.FileDescriptor>) : Descriptors.FileDescriptor  {
        val cached = cache[proto.name]
        if(cached != null) {
            return cached
        }
        val dependencies = mutableListOf<Descriptors.FileDescriptor>()
        proto.dependencyList.forEach {
            dependencies.add(buildProto(protos[it]!!, protos, cache))
        }
        val newDescriptor = Descriptors.FileDescriptor.buildFrom(proto, dependencies.toTypedArray())
        cache[proto.name] = newDescriptor
        return newDescriptor
    }

    fun getMethod(serviceName: String, methodName: String, descriptors: Map<String, Descriptors.FileDescriptor>) : Descriptors.MethodDescriptor? {

        var method: Descriptors.MethodDescriptor? = null
        descriptors.forEach {
            val serviceDescriptor = it.value.findServiceByName(serviceName)
            method = serviceDescriptor?.findMethodByName(methodName)
            return@forEach
        }
        return method
    }

    fun getMethodType(method: Descriptors.MethodDescriptor) : MethodDescriptor.MethodType {
        if(method.isClientStreaming && method.isServerStreaming) {
            return MethodDescriptor.MethodType.BIDI_STREAMING
        } else if(method.isServerStreaming) {
            return MethodDescriptor.MethodType.SERVER_STREAMING
        } else if(method.isClientStreaming) {
            return MethodDescriptor.MethodType.CLIENT_STREAMING
        }
        else {
            return MethodDescriptor.MethodType.UNARY
        }
    }

    fun getTypes(fds: Map<String, Descriptors.FileDescriptor>) : List<Descriptors.Descriptor> {

        val types = mutableListOf<Descriptors.Descriptor>()
        fds.forEach { t, u ->
            u.messageTypes.forEach {
                types.add(it)
            }

        }
        return types
    }

    fun json2DynamicMessage(typeRegistry: TypeRegistry, messageDescriptor : Descriptors.Descriptor, json: String) : DynamicMessage {
        val parser = JsonFormat.parser().usingTypeRegistry(typeRegistry)
        val dynamicMessageBuilder = DynamicMessage.newBuilder(messageDescriptor)

        parser.merge(json, dynamicMessageBuilder)
        return dynamicMessageBuilder.build()

    }

    fun dynamicMessage2Json(dynamicMessage: DynamicMessage, typeRegistry: TypeRegistry) : String {
        return JsonFormat.printer().usingTypeRegistry(typeRegistry).includingDefaultValueFields().print(dynamicMessage)
    }
}

class GrpcReflection {

    data class Reflection(
            var fileProtos: Map<String, DescriptorProtos.FileDescriptorProto>,
            var fds: Map<String, Descriptors.FileDescriptor>,
            var types: List<Descriptors.Descriptor>,
            var typeRegistry: TypeRegistry)


    private val channelReflections = ConcurrentHashMap<ManagedChannel, Reflection>()

    suspend fun createReflection(channel : ManagedChannel) : Reflection {

        val cached = channelReflections[channel]
        if(cached != null) {
            return cached
        }

        val reflectionStream = object: GrpcFluxStream<ServerReflectionRequest, ServerReflectionResponse>(channel, "reflectionStream", false) {

        }

        val resultFuture = CompletableFuture< MutableMap<String, DescriptorProtos.FileDescriptorProto>>()
        val serviceCount = AtomicInteger(0)
        val result =  mutableMapOf<String, DescriptorProtos.FileDescriptorProto>()
        //val dependencyCache = mutableMapOf<String, FileDescriptor>()

        reflectionStream.initStreamSink {
            ServerReflectionGrpc.newStub(channel).serverReflectionInfo(reflectionStream)

        }.subscribe { it ->
            if(it.hasListServicesResponse()) {

                val serviceList = mutableListOf<ServiceResponse>()
                it.listServicesResponse.serviceList.forEach {
                    serviceList.add(it)
                    serviceCount.incrementAndGet()

                }
                serviceList.forEach {
                    runBlocking {
                        reflectionStream.streamSource.awaitSingle().onNext(ServerReflectionRequest.newBuilder().setFileContainingSymbol(it.name).build())
                    }
                }

            } else if(it.hasFileDescriptorResponse()) {


                it.fileDescriptorResponse.fileDescriptorProtoList.forEach {

                    val descriptor = DescriptorProtos.FileDescriptorProto.parseFrom(it)

                    //Descriptors.FileDescriptor.buildFrom(descriptor, descriptor.dependencyList)
                    result[descriptor.name] = descriptor

                }


                val remaining = serviceCount.decrementAndGet()
                if(remaining ==  0) {
                    resultFuture.complete(result)
                }
            }
        }

        reflectionStream.streamSource.awaitSingle().onNext(ServerReflectionRequest.newBuilder().setListServices("").build())

        val protos = resultFuture.await()
        val fds = GrpcReflectionUtils.genFileDescriptor(protos)
        val types = GrpcReflectionUtils.getTypes(fds)
        val typeRegistry =  TypeRegistry.newBuilder().add(types).build()
        val reflection = Reflection(protos, fds, types, typeRegistry)

        channelReflections[channel] = reflection

        return reflection

    }

    fun cleanReflection(channel: ManagedChannel) {
        channelReflections.remove(channel)//channelReflections[channel]?: return

    }


    suspend fun callMethodJson(channel: ManagedChannel, serviceName: String, methodName: String, jsonPayLoad: String) : String? {
        val reflection = channelReflections[channel] ?: return null

        val method = GrpcReflectionUtils.getMethod(serviceName, methodName, reflection.fds) ?: return null

        val publisher = callMethod(channel, method, jsonPayLoad)
        val response = publisher.awaitFirstOrNull()
        if(response != null) {
            return GrpcReflectionUtils.dynamicMessage2Json(response, reflection.typeRegistry)
        }

        return null

    }

    fun callMethod(channel: ManagedChannel, method: Descriptors.MethodDescriptor, jsonPayLoad: String ) : Mono<DynamicMessage> {

        val reflection = channelReflections[channel] ?: return Mono.empty()
        val inputMessage = GrpcReflectionUtils.json2DynamicMessage(reflection.typeRegistry, method.inputType, jsonPayLoad)
        val methodType = GrpcReflectionUtils.getMethodType(method)
        val dyMethod = MethodDescriptor.newBuilder<DynamicMessage, DynamicMessage>().setType(methodType).setRequestMarshaller(DynamicMessageMarshaller(method.inputType))
                .setResponseMarshaller(DynamicMessageMarshaller(method.outputType)).setFullMethodName(MethodDescriptor.generateFullMethodName(method.service.fullName, method.name)).build()
        val clientCall = channel.newCall(dyMethod, CallOptions.DEFAULT)

        val callObserver = UnaryCallObserver<DynamicMessage>()

        ClientCalls.asyncUnaryCall(clientCall, inputMessage, callObserver)

        return callObserver.publisher


    }






}