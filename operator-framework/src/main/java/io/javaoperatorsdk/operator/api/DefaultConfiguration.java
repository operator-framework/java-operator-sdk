/**
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.api;

import java.util.Locale;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class DefaultConfiguration<R extends CustomResource> implements ControllerConfiguration<R> {
    private final ResourceController<R> controller;
    private final Controller annotation;
    
    public DefaultConfiguration(ResourceController<R> controller) {
        this.controller = controller;
        this.annotation = controller.getClass().getAnnotation(Controller.class);
    }
    
    @Override
    public String getName() {
        final var name = annotation.name();
        return Controller.NULL.equals(name) ? controller.getClass().getSimpleName().toLowerCase(Locale.ROOT) : name;
    }
    
    @Override
    public String getCRDName() {
        return annotation.crdName();
    }
    
    @Override
    public String getFinalizer() {
        final String annotationFinalizerName = annotation.finalizerName();
        if (!Controller.NULL.equals(annotationFinalizerName)) {
            return annotationFinalizerName;
        }
        return ControllerUtils.getDefaultFinalizerName(annotation.crdName());
    }
    
    @Override
    public boolean isGenerationAware() {
        return annotation.generationAwareEventProcessing();
    }
    
    @Override
    public Class<R> getCustomResourceClass() {
        return ControllerUtils.getCustomResourceClass(controller);
    }
}
