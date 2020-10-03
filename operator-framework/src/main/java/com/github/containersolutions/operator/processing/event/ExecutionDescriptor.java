package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.PostExecutionControl;
import com.github.containersolutions.operator.processing.ExecutionScope;

public class ExecutionDescriptor {

    private final ExecutionScope executionScope;
    private final PostExecutionControl postExecutionControl;

    public ExecutionDescriptor(ExecutionScope executionScope, PostExecutionControl postExecutionControl) {
        this.executionScope = executionScope;
        this.postExecutionControl = postExecutionControl;
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
