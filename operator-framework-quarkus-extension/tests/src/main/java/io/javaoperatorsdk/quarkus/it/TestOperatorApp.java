package io.javaoperatorsdk.quarkus.it;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
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
    return name.equals(controller.getName());
  }

  @GET
  @Path("{name}/config")
  public ControllerConfiguration getConfig(@PathParam("name") String name) {
    return getController(name) ? configurationService.getConfigurationFor(controller) : null;
  }
}
