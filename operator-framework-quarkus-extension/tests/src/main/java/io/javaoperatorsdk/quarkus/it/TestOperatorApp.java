package io.javaoperatorsdk.quarkus.it;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/operator")
public class TestOperatorApp {

  @Inject TestController controller;
  @Inject ConfigurationService configurationService;

  @GET
  @Path("{name}")
  //  @Produces(MediaType.TEXT_PLAIN)
  public boolean getController(@PathParam("name") String name) {
    return name.equals(configurationService.getConfigurationFor(controller).getName());
  }

  @GET
  @Path("{name}/config")
  public JSONControllerConfiguration getConfig(@PathParam("name") String name) {
    var config = configurationService.getConfigurationFor(controller);
    if (config == null) {
      return null;
    }
    return name.equals(config.getName()) ? new JSONControllerConfiguration(config) : null;
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

    public boolean isClusterScoped() {
      return conf.isClusterScoped();
    }

    public Set<String> getNamespaces() {
      return conf.getNamespaces();
    }

    public boolean watchAllNamespaces() {
      return conf.watchAllNamespaces();
    }

    public RetryConfiguration getRetryConfiguration() {
      return conf.getRetryConfiguration();
    }
  }
}
