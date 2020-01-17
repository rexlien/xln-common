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
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import xln.common.config.CommonConfig;
import xln.common.config.EtcdConfig;
import xln.common.etcd.KVManager;
import xln.common.etcd.LeaseManager;
import xln.common.etcd.WatchManager;
import xln.common.grpc.MultiAddressNameResolver;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(prefix ="xln.etcd-config", name = "hosts")
@Component
@Slf4j
public class EtcdClient {

    private final ManagedChannel managedChannel;
    private final Channel stickyChannel;
    private final CommonConfig commonConfig;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final long timeOut;

    private final LeaseManager leaseManager;
    private final KVManager kvManager;
    private final WatchManager watchManager;


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


    public EtcdClient(EtcdConfig etcdConfig, CommonConfig commonConfig) throws Exception {

        Map<String, Object> serviceConfig = new HashMap<>();
        serviceConfig.put("stickinessMetadataKey", "xln-sticky-key");

        this.managedChannel = ManagedChannelBuilder.forTarget("multiaddress").nameResolverFactory(
                MultiAddressNameResolver.MultiAddressNameResolverFactory.of(etcdConfig.getHosts())).
                defaultLoadBalancingPolicy("round_robin").defaultServiceConfig(serviceConfig).
                usePlaintext().build();

        HeaderClientInterceptor interceptor = new HeaderClientInterceptor();
        this.stickyChannel = ClientInterceptors.intercept(managedChannel, interceptor);
        this.commonConfig = commonConfig;

        this.timeOut = 15000;

        leaseManager = new LeaseManager(this);
        kvManager = new KVManager(this, leaseManager);
        watchManager = new WatchManager(this);

    }

    @PreDestroy
    private void destroy() throws InterruptedException{
        log.info("destroy");
        this.leaseManager.shutdown();
        this.managedChannel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public LeaseManager getLeaseManager() {
        return leaseManager;
    }

    public KVManager getKvManager() {
        return kvManager;
    }

    public WatchManager getWatchManager() {return watchManager;}

    public long getTimeoutMillis() {
        return timeOut;
    }


    public Channel getChannel() {
        return stickyChannel;
    }






}
