package io.javaoperatorsdk.operator.baseapi.multiversioncrd;

import java.time.Duration;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultiVersionCRDIT {

  private static final Logger log = LoggerFactory.getLogger(MultiVersionCRDIT.class);

  public static final String CR_V1_NAME = "crv1";
  public static final String CR_V2_NAME = "crv2";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(MultiVersionCRDTestReconciler1.class)
          .withReconciler(MultiVersionCRDTestReconciler2.class)
          .withConfigurationService(
              overrider -> overrider.withInformerStoppedHandler(informerStoppedHandler))
          .build();

  private static class TestInformerStoppedHandler implements InformerStoppedHandler {
    private volatile String resourceClassName;
    private volatile String resourceCreateAsVersion;

    private volatile String failedResourceVersion;
    private volatile String errorMessage;

    public void reset() {
      resourceClassName = null;
      resourceCreateAsVersion = null;
      failedResourceVersion = null;
      errorMessage = null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onStop(SharedIndexInformer informer, Throwable ex) {
      if (ex instanceof WatcherException watcherEx) {
        watcherEx
            .getRawWatchMessage()
            .ifPresent(
                raw -> {
                  try {
                    // extract the resource at which the version is attempted to be created (i.e.
                    // the stored
                    // version)
                    final var unmarshal = Serialization.jsonMapper().readTree(raw);
                    final var object = unmarshal.get("object");
                    resourceCreateAsVersion =
                        acceptOnlyIfUnsetOrEqualToAlreadySet(
                            resourceCreateAsVersion, object.get("apiVersion").asText());
                    // extract the asked resource version
                    failedResourceVersion =
                        acceptOnlyIfUnsetOrEqualToAlreadySet(
                            failedResourceVersion,
                            object
                                .get("metadata")
                                .get("managedFields")
                                .get(0)
                                .get("apiVersion")
                                .asText());
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                });

        // extract error message
        errorMessage =
            acceptOnlyIfUnsetOrEqualToAlreadySet(errorMessage, watcherEx.getCause().getMessage());
      }
      final var apiTypeClass = informer.getApiTypeClass();

      log.debug("Current resourceClassName: " + resourceClassName);

      resourceClassName =
          acceptOnlyIfUnsetOrEqualToAlreadySet(resourceClassName, apiTypeClass.getName());

      log.debug(
          "API Type Class: "
              + apiTypeClass.getName()
              + "  -  resource class name: "
              + resourceClassName);
      log.info(
          "Informer for "
              + HasMetadata.getFullResourceName(apiTypeClass)
              + " stopped due to: "
              + ex.getMessage());
    }

    public String getResourceClassName() {
      return resourceClassName;
    }

    public String getResourceCreateAsVersion() {
      return resourceCreateAsVersion;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public String getFailedResourceVersion() {
      return failedResourceVersion;
    }

    private String acceptOnlyIfUnsetOrEqualToAlreadySet(String existing, String newValue) {
      return (existing == null || existing.equals(newValue)) ? newValue : null;
    }
  }

  private static final TestInformerStoppedHandler informerStoppedHandler =
      new TestInformerStoppedHandler();

  @Test
  void multipleCRDVersions() {
    informerStoppedHandler.reset();
    operator.create(createTestResourceV1WithoutLabel());
    operator.create(createTestResourceV2WithLabel());

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              var crV1Now = operator.get(MultiVersionCRDTestCustomResource1.class, CR_V1_NAME);
              var crV2Now = operator.get(MultiVersionCRDTestCustomResource2.class, CR_V2_NAME);
              assertThat(crV1Now.getStatus()).isNotNull();
              assertThat(crV2Now.getStatus()).isNotNull();
              assertThat(crV1Now.getStatus().getReconciledBy())
                  .containsExactly(MultiVersionCRDTestReconciler1.class.getSimpleName());
              assertThat(crV2Now.getStatus().getReconciledBy())
                  .containsExactly(MultiVersionCRDTestReconciler2.class.getSimpleName());
            });
  }

  @Test
  void invalidEventsShouldStopInformerAndCallInformerStoppedHandler() {
    informerStoppedHandler.reset();
    var v2res = createTestResourceV2WithLabel();
    v2res.getMetadata().getLabels().clear();
    operator.create(v2res);
    var v1res = createTestResourceV1WithoutLabel();
    operator.create(v1res);

    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              // v1 is the stored version so trying to create a v2 version should fail because we
              // cannot
              // convert a String (as defined by the spec of the v2 CRD) to an int (which is what
              // the
              // spec of the v1 CRD defines)
              assertThat(informerStoppedHandler.getResourceCreateAsVersion())
                  .isEqualTo(HasMetadata.getApiVersion(MultiVersionCRDTestCustomResource1.class));
              assertThat(informerStoppedHandler.getResourceClassName())
                  .isEqualTo(MultiVersionCRDTestCustomResource1.class.getName());
              assertThat(informerStoppedHandler.getFailedResourceVersion())
                  .isEqualTo(HasMetadata.getApiVersion(MultiVersionCRDTestCustomResource2.class));
              assertThat(informerStoppedHandler.getErrorMessage())
                  .contains(
                      "Cannot deserialize value of type `int` from String \"string value\": not a"
                          + " valid `int` value");
            });
    assertThat(operator.get(MultiVersionCRDTestCustomResource2.class, CR_V2_NAME).getStatus())
        .isNull();
  }

  MultiVersionCRDTestCustomResource1 createTestResourceV1WithoutLabel() {
    MultiVersionCRDTestCustomResource1 cr = new MultiVersionCRDTestCustomResource1();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(CR_V1_NAME);
    cr.setSpec(new MultiVersionCRDTestCustomResourceSpec1());
    cr.getSpec().setValue(1);
    return cr;
  }

  MultiVersionCRDTestCustomResource2 createTestResourceV2WithLabel() {
    MultiVersionCRDTestCustomResource2 cr = new MultiVersionCRDTestCustomResource2();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(CR_V2_NAME);
    cr.getMetadata().setLabels(new HashMap<>());
    cr.getMetadata().getLabels().put("version", "v2");
    cr.setSpec(new MultiVersionCRDTestCustomResourceSpec2());
    cr.getSpec().setValue("string value");
    return cr;
  }
}
