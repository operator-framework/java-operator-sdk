package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.PostExecutionControl;
import com.github.containersolutions.operator.processing.ExecutionScope;

import java.time.LocalDateTime;

public class ExecutionDescriptor {

    private final ExecutionScope executionScope;
    private final PostExecutionControl postExecutionControl;
    private final LocalDateTime executionFinishedAt;

    public ExecutionDescriptor(ExecutionScope executionScope, PostExecutionControl postExecutionControl, LocalDateTime executionFinishedAt) {
        this.executionScope = executionScope;
        this.postExecutionControl = postExecutionControl;

        this.executionFinishedAt = executionFinishedAt;
    }

    public ExecutionScope getExecutionScope() {
        return executionScope;
    }

    public PostExecutionControl getPostExecutionControl() {
        return postExecutionControl;
    }

    public String getCustomResourceUid() {
        return executionScope.getCustomResourceUid();
    }
}
