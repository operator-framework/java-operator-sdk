/**
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample.v2;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.crd.CRD;
import io.javaoperatorsdk.operator.sample.ServiceSpec;

/** @author <a href="claprun@redhat.com">Christophe Laprun</a> */
@Group("sample.javaoperatorsdk")
@Version("v2")
@CRD
public class CustomService extends CustomResource<ServiceSpec, Void> {}
