package xln.common.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import xln.common.annotation.XLNCacheable;
import xln.common.service.CacheService;

import java.util.Collection;
import java.util.stream.Collectors;

public class CustomCacheResolver implements CacheResolver {

    private CacheService cacheService;

    public CustomCacheResolver(CacheService cacheService){
        this.cacheService = cacheService;
    }

    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        XLNCacheable xlnCacheable = context.getMethod().getAnnotation(XLNCacheable.class);
        String managerID = xlnCacheable.cacheManagerID();
        CacheManager cacheManager = cacheService.getCacheManager(managerID);

        if(cacheManager == null)
            return null;

        Collection<Cache> caches = getCaches(cacheManager, context);
        return caches;
    }

    private Collection<Cache> getCaches(CacheManager cacheManager, CacheOperationInvocationContext<?> context) {
        return context.getOperation().getCacheNames().stream()
                .map(cacheName -> cacheManager.getCache(cacheName))
                .filter(cache -> cache != null)
                .collect(Collectors.toList());
    }
}