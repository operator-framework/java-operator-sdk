package io.javaoperatorsdk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;

public class Bootstrapper {

  private MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  private static final List<String> TOP_LEVEL_STATIC_FILES =
      List.of(".gitignore", "mvnw", "mvnw.cmd", "README.md");
  private static final List<String> MVN_WRAPPER_STATIC_FILES =
      List.of(".gitignore", "maven-wrapper.properties", "MavenWrapperDownloader.java");
  private static final List<String> JAVA_FILES =
      List.of("CustomResource.java", "Reconciler.java",
          "Spec.java", "Status.java");

  public void create(File targetDir, String groupId, String artifactId) {
    try {
      var projectDir = new File(targetDir, artifactId);
      FileUtils.forceMkdir(projectDir);
      addStaticFiles(projectDir);
      addTemplatedFiles(projectDir, groupId, artifactId);
      addJavaFiles(projectDir, groupId, artifactId);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void addJavaFiles(File projectDir, String groupId, String artifactId) {
    try {
      var packages = groupId.replace(".", File.separator);
      var targetDir = new File(projectDir, "src/main/java/" + packages);
      FileUtils.forceMkdir(targetDir);
      var classFileNamePrefix = artifactClassId(artifactId);
      JAVA_FILES.forEach(f -> addTemplatedFile(projectDir, f, groupId, artifactId, targetDir,
          classFileNamePrefix + f));

      addTemplatedFile(projectDir, "Runner.java", groupId, artifactId, targetDir,
          "Runner.java");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  private void addTemplatedFiles(File projectDir, String groupId, String artifactId) {
    addTemplatedFile(projectDir, "pom.xml", groupId, artifactId);
    addTemplatedFile(projectDir, "k8s/test-resource.yaml", groupId, artifactId);
  }

  private void addTemplatedFile(File projectDir, String fileName, String groupId,
      String artifactId) {
    addTemplatedFile(projectDir, fileName, groupId, artifactId, null, null);
  }

  private void addTemplatedFile(File projectDir, String fileName, String groupId, String artifactId,
      File targetDir, String targetFileName) {
    try {
      var values = Map.of("groupId", groupId, "artifactId", artifactId,
          "artifactClassId", artifactClassId(artifactId));

      var mustache = mustacheFactory.compile("templates/" + fileName);
      var targetFile = new File(targetDir == null ? projectDir : targetDir,
          targetFileName == null ? fileName : targetFileName);
      FileUtils.forceMkdir(targetFile.getParentFile());
      var writer = new FileWriter(targetFile);
      mustache.execute(writer, values);
      writer.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void addStaticFiles(File projectDir) {
    TOP_LEVEL_STATIC_FILES.forEach(f -> addStaticFile(projectDir, f));
    MVN_WRAPPER_STATIC_FILES.forEach(f -> addStaticFile(projectDir, f, ".mvn/wrapper/"));
  }

  private void addStaticFile(File targetDir, String fileName) {
    addStaticFile(targetDir, fileName, null);
  }

  private void addStaticFile(File targetDir, String fileName, String subDir) {
    String sourcePath = subDir == null ? "/static/" : "/static/" + subDir;
    String path = sourcePath + fileName;
    try (var is = Bootstrapper.class.getResourceAsStream(path)) {
      targetDir = subDir == null ? targetDir : new File(targetDir, subDir);
      if (subDir != null) {
        FileUtils.forceMkdir(targetDir);
      }
      FileUtils.copyInputStreamToFile(is, new File(targetDir, fileName));
    } catch (IOException e) {
      throw new RuntimeException("File path: " + path, e);
    }

  }

  public static String artifactClassId(String artifactId) {
    var parts = artifactId.split("-");
    return Arrays.stream(parts).map(p -> p.substring(0, 1)
        .toUpperCase() + p.substring(1))
        .collect(Collectors.joining(""));
  }

}
