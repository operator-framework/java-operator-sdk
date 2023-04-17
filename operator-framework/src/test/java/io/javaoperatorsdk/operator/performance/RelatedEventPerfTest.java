package io.javaoperatorsdk.operator.performance;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@EnableKubeAPIServer
public class RelatedEventPerfTest {

    KubernetesClient adminClient = new KubernetesClientBuilder().build();
    private MixedOperation<Counter, KubernetesResourceList<Counter>, Resource<Counter>> counterClient;

    String namespace = "josdk-perftest";

    private static final Logger log = LoggerFactory.getLogger(RelatedEventPerfTest.class);
    private Operator operator;
    private AtomicInteger eventCounter = new AtomicInteger();

    private CounterEventSource eventSource = new CounterEventSource(namespace);

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        LocallyRunOperatorExtension.applyCrd(Counter.class, adminClient);

        adminClient.namespaces().resource(namespace()).create();
        counterClient = adminClient.resources(Counter.class);
        operator = new Operator(new KubernetesClientBuilder().build());

        operator.register(new CounterReconciler(eventCounter, eventSource));
        operator.start();
    }

    @AfterEach
    void afterEach() {
        adminClient.namespaces().withName(namespace).delete();
        operator.stop();
    }

    @Test
    public void perfTestSyntheticEvents() throws InterruptedException, IOException {
        int numberOfCounters = 3;
        int numberOfEvents = 10000;
        int millisBetweenEvents = 1;

        log.info("Creating Counters");
        for (int i = 0; i < numberOfCounters; i++) {
            Counter counter = new Counter();
            counter.getMetadata().setName("counter-" + i);
            counter.setSpec(new CounterSpec());
            counter.getSpec().setCount(1);
            counterClient.inNamespace(namespace).resource(counter).create();
        }

        Thread.sleep(1000);

        Instant start = Instant.now();
        eventCounter.set(0);

        for (int i = 0; i < numberOfEvents; i++) {
            eventSource.generateEvent(i % numberOfCounters);
            if (millisBetweenEvents > 0) Thread.sleep(millisBetweenEvents);
            if (i % (numberOfEvents / 10) == 0) log.info("Generated " + i + " events");
        }

        await()
                .atMost(2, MINUTES)
                .untilAsserted(() -> assertThat(eventCounter.get(), equalTo(numberOfEvents)));
        Duration duration = Duration.between(start, Instant.now());
        log.info("Duration: " + duration.toMillis() + "ms");
        writeCsvOutput(numberOfCounters, numberOfEvents, millisBetweenEvents, duration);
    }

    @Test
    @Ignore
    public void perfTestKubernetes() {
        warmup();
        log.info("Warmup finished");

        int numberOfCounters = 10000;
        eventCounter.set(0);

        log.info("Creating Counters");
        for (int i = 0; i < numberOfCounters; i++) {
            Counter counter = new Counter();
            counter.getMetadata().setName("counter-" + i);
            counter.setSpec(new CounterSpec());
            counter.getSpec().setCount(1);
            counterClient.inNamespace(namespace).resource(counter).create();
            if (i % (numberOfCounters / 10) == 0) log.info("Created " + i + " counters");
        }

        log.info("Updating Counters");
        for (int i = 0; i < numberOfCounters; i++) {
            Counter counter = counterClient.inNamespace(namespace).withName("counter-" + i).get();
            counter.getSpec().setCount(counter.getSpec().getCount() + 1);
            counterClient.inNamespace(namespace).resource(counter).update();
            if (i % (numberOfCounters / 10) == 0) log.info("Updated " + i + " counters");
        }

        log.info("Verifying and Deleting Counters");
        for (int i = 0; i < numberOfCounters; i++) {
            var counter = counterClient.inNamespace(namespace).withName("counter-" + i);
            counter.delete();
        }

        Instant instant = Instant.now();
        await()
                .atMost(2, MINUTES)
                .untilAsserted(() -> assertThat(eventCounter.get(), equalTo(numberOfCounters * 2)));
        Duration duration = Duration.between(instant, Instant.now());
        log.info("Duration: " + duration.toMillis() + "ms");
    }

    private void warmup() {
        for (int i = 0; i < 100; i++) {
            Counter counter = new Counter();
            counter.getMetadata().setName("counter-" + i);
            counter.setSpec(new CounterSpec());
            counter.getSpec().setCount(1);
            counterClient.inNamespace(namespace).resource(counter).create();
        }
        for (int i = 0; i < 100; i++) {
            counterClient.inNamespace(namespace).withName("counter-" + i).delete();
        }
    }

    private Namespace namespace() {
        Namespace n = new Namespace();
        n.setMetadata(new ObjectMetaBuilder()
                .withName(namespace)
                .build());
        return n;
    }

    private void writeCsvOutput(int numberOfObjects, int numberOfEvents, int millisBetweenEvents, Duration totalTime) throws IOException {
        File csvFile = new File("target/perf-test-stats.csv");
        var dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        boolean fileExists = csvFile.exists();
        try (var writer = new FileWriter(csvFile, true)) {
            if (!fileExists) {
                writer.write("Date, Nr of Objects, Nr of Events, Ms between Events, Total time ms\n");
            }
            writer.write(String.format(Locale.ROOT, "%s,%d,%d,%d,%d\n",
                    dateFormat.format(Date.from(Instant.now())),
                    numberOfObjects, numberOfEvents, millisBetweenEvents, totalTime.toMillis()));
        }
    }
}
