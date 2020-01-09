package xln.common.service;

import com.google.protobuf.ByteString;
import etcdserverpb.KVGrpc;
import etcdserverpb.LeaseGrpc;
import etcdserverpb.Rpc;
import etcdserverpb.WatchGrpc;
import io.grpc.*;
import io.grpc.internal.GrpcAttributes;
import io.grpc.internal.SharedResourceHolder;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xln.common.config.CommonConfig;
import xln.common.config.EtcdConfig;
import xln.common.grpc.MultiAddressNameResolver;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(prefix ="xln.etcd-config", name = "hosts")
@Service
@Slf4j
public class EtcdClient {

    private final ManagedChannel managedChannel;
    private final Channel stickyChannel;
    private final CommonConfig commonConfig;


    public static class HeaderClientInterceptor implements ClientInterceptor {

        static final Metadata.Key<String> CUSTOM_HEADER_KEY =
                Metadata.Key.of("xln-sticky-key", Metadata.ASCII_STRING_MARSHALLER);
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    /* put custom header */
                    headers.put(CUSTOM_HEADER_KEY, "sticky0");
                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                        @Override
                        public void onHeaders(Metadata headers) {

                            super.onHeaders(headers);
                        }
                    }, headers);
                }
            };
        }
    }


    public EtcdClient(EtcdConfig etcdConfig, CommonConfig commonConfig) {

        Map<String, Object> serviceConfig = new HashMap<>();
        serviceConfig.put("stickinessMetadataKey", "xln-sticky-key");
        //Attributes attributes = Attributes.newBuilder()
        //        .set(GrpcAttributes.NAME_RESOLVER_SERVICE_CONFIG, serviceConfig).build();

        this.managedChannel = ManagedChannelBuilder.forTarget("multiaddress").nameResolverFactory(
                MultiAddressNameResolver.MultiAddressNameResolverFactory.of(etcdConfig.getHosts())).
                defaultLoadBalancingPolicy("round_robin").defaultServiceConfig(serviceConfig).
                usePlaintext().build();

        HeaderClientInterceptor interceptor = new HeaderClientInterceptor();
        this.stickyChannel = ClientInterceptors.intercept(managedChannel, interceptor);
        this.commonConfig = commonConfig;

        initNode();

    }

    @PreDestroy
    private void destroy() throws InterruptedException{
        this.managedChannel.shutdown().awaitTermination(10, TimeUnit.SECONDS);
    }

    public Channel getChannel() {
        return stickyChannel;
    }


    public class Node {


    }

    private long leaseID = 0;


    public void initNode() {

        try {
            var leaseResponse = LeaseGrpc.newFutureStub(stickyChannel).leaseGrant(Rpc.LeaseGrantRequest.newBuilder().setID(leaseID).setTTL(30).build()).get();
            leaseID = leaseResponse.getID();

            //LeaseGrpc.newStub(stickyChannel).leaseKeepAlive()

        }catch (Exception ex) {
            log.error("", ex);
        }
        var appName = commonConfig.getAppName();
        var host = String.valueOf(new Random().nextInt());
        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostName();
            host = ip + ":" + host;
        }catch (UnknownHostException ex) {

        }

        var futureStub = KVGrpc.newFutureStub(stickyChannel);//.withDeadlineAfter(10, TimeUnit.SECONDS)


        var putRequest = etcdserverpb.Rpc.PutRequest.newBuilder().setKey(ByteString.copyFromUtf8("apps/" + appName + "/" + host)).
                    setValue(ByteString.copyFromUtf8(ip)).setLease(leaseID).build();

        try {
            var response = futureStub.put(putRequest).get();

        }catch (Exception ex) {
            log.error("", ex);
        }



    }




}
