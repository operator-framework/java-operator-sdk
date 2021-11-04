package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import java.util.function.Supplier;

public class PollingEventSource<T> extends AbstractEventSource {

    private Timer timer = new Timer();
    private Supplier<Map<CustomResourceID,T>> supplierToPoll;
    private CacheManager cacheManager;
    private Cache<CustomResourceID,T> cache;
    private long period;

    public PollingEventSource(Supplier<Map<CustomResourceID,T>> supplier,
                              CachingProvider cachingProvider,
                              long period) {
        this.supplierToPoll = supplier;
        cacheManager = cachingProvider.getCacheManager();
        this.period = period;
        // todo 
        MutableConfiguration<CustomResourceID,T> config
                = new MutableConfiguration<>();
        cache = cacheManager.createCache("pollingCache",config);
    }

    @Override
    public void start() throws OperatorException {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                getStateAndFillCache();
            }
        }, period, period);
    }

    private void getStateAndFillCache() {
        var values = supplierToPoll.get();
        values.forEach((k,v) -> cache.put(k,v));
    }

    @Override
    public void stop() throws OperatorException {
        timer.cancel();
        cacheManager.close();
    }

    public Optional<T> getState(CustomResourceID customResourceID) {
        return Optional.ofNullable(cache.get(customResourceID));
    }

    public Optional<T> getStateFromSupplier(CustomResourceID customResourceID) {
        getStateAndFillCache();
        return getState(customResourceID);
    }

}
