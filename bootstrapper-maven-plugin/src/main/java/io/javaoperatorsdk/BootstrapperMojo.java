package io.javaoperatorsdk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "create")
public class BootstrapperMojo
    extends AbstractMojo {
  /**
   * Location of the file.
   *
   * @parameter expression="${project.build.directory}"
   * @required
   */
  private File outputDirectory;

  public void execute()
      throws MojoExecutionException {
    File f = outputDirectory;

    if (!f.exists()) {
      f.mkdirs();
    }

    File touch = new File(f, "touch.txt");

    FileWriter w = null;
    try {
      w = new FileWriter(touch);

      w.write("touch.txt");
    } catch (IOException e) {
      throw new MojoExecutionException("Error creating file " + touch, e);
    } finally {
      if (w != null) {
        try {
          w.close();
        } catch (IOException e) {
          // ignore
        }
      }
    }
  }
}
