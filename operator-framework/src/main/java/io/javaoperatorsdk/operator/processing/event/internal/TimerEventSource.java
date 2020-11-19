package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

import java.util.Timer;

public class TimerEventSource extends AbstractEventSource {

    private final Timer timer = new Timer();


    public void schedule(CustomResource customResource ){

    };


}
