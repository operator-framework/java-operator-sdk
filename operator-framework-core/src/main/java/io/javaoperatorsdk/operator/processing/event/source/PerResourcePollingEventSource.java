package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public class PerResourcePollingEventSource<T> extends AbstractEventSource implements ResourceEventAware {

    private final Timer timer = new Timer();
    private Function<T,Set<ResourceID>> supplierToPoll;
    private final Map<ResourceID,T> cache = new ConcurrentHashMap<>();

    @Override
    public void start() throws OperatorException {
    }

    @Override
    public void stop() throws OperatorException {
    }
    
}
