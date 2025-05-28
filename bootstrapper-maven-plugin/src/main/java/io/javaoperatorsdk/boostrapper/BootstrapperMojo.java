package io.javaoperatorsdk.boostrapper;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "create", requiresProject = false)
public class BootstrapperMojo extends AbstractMojo {

  @Parameter(defaultValue = "${projectGroupId}")
  protected String projectGroupId;

  @Parameter(defaultValue = "${projectArtifactId}")
  protected String projectArtifactId;

  public void execute() throws MojoExecutionException {
    String userDir = System.getProperty("user.dir");
    new Bootstrapper().create(new File(userDir), projectGroupId, projectArtifactId);
  }
}
