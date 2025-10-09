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
