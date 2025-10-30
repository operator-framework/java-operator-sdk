/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.baseapi.createupdateeventfilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class ComparableResourceVersionsDisabledIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CreateUpdateEventFilterTestReconciler())
          .withConfigurationService(overrider -> overrider.withComparableResourceVersions(false))
          .build();

  @Test
  void updateEventReceivedAfterCreateOrUpdate() {
    CreateUpdateEventFilterTestCustomResource resource =
        CreateUpdateInformerEventSourceEventFilterIT.prepareTestResource();
    var createdResource = operator.create(resource);

    CreateUpdateInformerEventSourceEventFilterIT.assertData(operator, createdResource, 1, 2);

    CreateUpdateEventFilterTestCustomResource actualCreatedResource =
        operator.get(
            CreateUpdateEventFilterTestCustomResource.class, resource.getMetadata().getName());
    actualCreatedResource.getSpec().setValue("2");
    operator.replace(actualCreatedResource);

    CreateUpdateInformerEventSourceEventFilterIT.assertData(operator, actualCreatedResource, 2, 4);
  }
}
