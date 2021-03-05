package io.javaoperatorsdk.quarkus.it;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/operator")
public class TestOperatorApp {

  @Inject Instance<ResourceController<? extends CustomResource>> controllers;
  @Inject ConfigurationService configurationService;

  @GET
  @Path("validateCR")
  public boolean validateCR() {
    return configurationService.validateCustomResources();
  }

  @GET
  @Path("{name}")
  public boolean getController(@PathParam("name") String name) {
    return configurationService.getKnownControllerNames().contains(name);
  }

  @GET
  @Path("{name}/config")
  public JSONControllerConfiguration getConfig(@PathParam("name") String name) {
    final var configuration =
        controllers.stream()
            .map(c -> configurationService.getConfigurationFor(c))
            .filter(c -> c.getName().equals(name))
            .findFirst()
            .map(JSONControllerConfiguration::new)
            .orElse(null);
    return configuration;
  }

  static class JSONControllerConfiguration {
    private final ControllerConfiguration conf;

    public JSONControllerConfiguration(ControllerConfiguration conf) {
      this.conf = conf;
    }

    public String getName() {
      return conf.getName();
    }

    @JsonProperty("crdName")
    public String getCRDName() {
      return conf.getCRDName();
    }

    public String getFinalizer() {
      return conf.getFinalizer();
    }

    public boolean isGenerationAware() {
      return conf.isGenerationAware();
    }

    public String getCustomResourceClass() {
      return conf.getCustomResourceClass().getCanonicalName();
    }

    public String getAssociatedControllerClassName() {
      return conf.getAssociatedControllerClassName();
    }

    public String[] getNamespaces() {
      return (String[]) conf.getNamespaces().toArray(new String[0]);
    }

    public boolean watchAllNamespaces() {
      return conf.watchAllNamespaces();
    }

    public RetryConfiguration getRetryConfiguration() {
      return conf.getRetryConfiguration();
    }
  }
}
