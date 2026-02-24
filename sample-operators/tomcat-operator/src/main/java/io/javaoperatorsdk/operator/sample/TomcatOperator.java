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
package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.net.URL;

import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.config.loader.ConfigLoader;
import io.javaoperatorsdk.operator.sample.smallryeconfig.SmallryeConfigProvider;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;

public class TomcatOperator {

  public static void main(String[] args) throws IOException {

    URL configUrl = TomcatOperator.class.getResource("/application.yaml");
    if (configUrl == null) {
      throw new IllegalStateException("application.yaml not found on classpath");
    }
    var configLoader =
        new ConfigLoader(
            new SmallryeConfigProvider(
                new SmallRyeConfigBuilder().withSources(new YamlConfigSource(configUrl)).build()));

    Operator operator = new Operator(configLoader.applyConfigs());
    operator.register(
        new TomcatReconciler(), configLoader.applyControllerConfigs(TomcatReconciler.NAME));
    operator.register(
        new WebappReconciler(operator.getKubernetesClient()),
        configLoader.applyControllerConfigs(WebappReconciler.NAME));
    operator.start();

    new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD.")), 8080).start(Exit.NEVER);
  }
}
