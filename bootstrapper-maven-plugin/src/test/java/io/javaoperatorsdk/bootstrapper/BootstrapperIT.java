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
package io.javaoperatorsdk.bootstrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.boostrapper.Bootstrapper;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapperIT {

  private static final Logger log = LoggerFactory.getLogger(BootstrapperIT.class);

  Bootstrapper bootstrapper = new Bootstrapper();

  @Test
  void copiesFilesToTarget() {
    bootstrapper.create(new File("target"), "io.sample", "test-project");

    var targetDir = new File("target", "test-project");
    assertThat(targetDir.list()).contains("pom.xml");
    assertProjectCompiles();
  }

  private void assertProjectCompiles() {
    try {
      var process =
          Runtime.getRuntime()
              .exec(
                  "mvn clean install -f target/test-project/pom.xml -DskipTests"
                      + " -Dspotless.apply.skip");

      BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));

      log.info("Maven output:");
      String logLine;
      while ((logLine = stdOut.readLine()) != null) {
        log.info(logLine);
      }
      var res = process.waitFor();
      log.info("exit code: {}", res);
      assertThat(res).isZero();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
