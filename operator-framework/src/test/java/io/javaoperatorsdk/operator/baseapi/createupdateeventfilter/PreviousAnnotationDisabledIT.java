package io.javaoperatorsdk.operator.baseapi.createupdateeventfilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class PreviousAnnotationDisabledIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CreateUpdateEventFilterTestReconciler())
          .withConfigurationService(
              overrider -> overrider.withPreviousAnnotationForDependentResources(false))
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
