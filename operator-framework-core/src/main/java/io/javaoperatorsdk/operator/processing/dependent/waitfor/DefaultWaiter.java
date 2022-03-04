package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.Thread.sleep;

public class DefaultWaiter<R,P extends HasMetadata> implements Waiter<R,P> {

    public static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(1);

    private Duration pollingInterval;
    private Duration timeout = Duration.ZERO;


    public DefaultWaiter() {
    }

    public DefaultWaiter(Duration timeout) {
        this.timeout = timeout;
    }

    public DefaultWaiter(Duration pollingInterval, Duration timeout) {
        this.pollingInterval = pollingInterval;
        this.timeout = timeout;
    }

    @Override
    public void waitFor(DependentResource<R, P> resource, P primary, Condition<R, P> condition) {
        waitFor(() -> resource.getResource(primary),primary,condition );
    }

    @Override
    public void waitFor(Supplier<Optional<R>> supplier, P primary, Condition<R,P> condition) {
        var deadline = Instant.now().plus(timeout.toMillis(), ChronoUnit.MILLIS);
        Optional<R> resource = Optional.empty();
        while (Instant.now().isBefore(deadline)) {
             resource = supplier.get();
            if (resource.isEmpty() || !condition.isMet(resource.get(), primary)) {
                var timeLeft = Duration.between(Instant.now(),deadline);
                if (timeLeft.isZero() || timeLeft.isNegative()) {
                    handleTimeout(resource.orElse(null),primary,condition);
                } else {
                    try {
                        sleep(Math.min(pollingInterval.toMillis(),timeLeft.toMillis()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Thread interrupted.",e);
                    }
                }
            } else {
                return;
            }
        }
        handleTimeout(resource.orElse(null), primary, condition);
    }

    private void handleTimeout(R orElse, P primary, Condition<R, P> condition) {
        // todo
    }

}
