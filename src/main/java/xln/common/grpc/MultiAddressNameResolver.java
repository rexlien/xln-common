package xln.common.grpc;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.*;

@Slf4j
public class MultiAddressNameResolver extends NameResolver {

    private final String authority;
    private List<SocketAddress> addresses = new ArrayList<>();


    public MultiAddressNameResolver(String authority, Collection<String> addresses)  {
        this.authority = authority;
        for(String s : addresses) {
            s = s.trim();
            if(s.length() == 0) {
                continue;
            }
            var hostPort = s.split(":");
            this.addresses.add(new InetSocketAddress(hostPort[0], Integer.parseInt(hostPort[1])));
        }
    }

    public MultiAddressNameResolver(String authority, String addresses) {
        this(authority, Arrays.asList(addresses.split(",")));
    }


    @Override
    public String getServiceAuthority() {
        return authority;
    }

    @Override
    public void start(Listener listener) {
        doResolve(listener);
        //super.start(listener);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void refresh() {
        log.info("start refresh");
    }

    private void checkReachable() {

    }

    private void doResolve(Listener listener) {
        List<EquivalentAddressGroup> addressGroups = new LinkedList<>();

        addressGroups.add(new EquivalentAddressGroup(addresses));
        listener.onAddresses(addressGroups, Attributes.EMPTY);
    }

    public static class MultiAddressNameResolverFactory extends NameResolver.Factory {

        private String addresses;

        public MultiAddressNameResolverFactory(String addresses) {
            this.addresses = addresses;
        }

        @Override
        public NameResolver newNameResolver(URI targetUri, Args args) {


            var resolver = new MultiAddressNameResolver("xln", addresses);
            return resolver;


        }

        @Override
        public String getDefaultScheme() {
            return "multiaddress";
        }

        public static MultiAddressNameResolverFactory of(String addresses) {
            return new MultiAddressNameResolverFactory(addresses);
        }
    }

}
