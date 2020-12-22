package io.javaoperatorsdk.operator.springboot.starter.test;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import okhttp3.mockwebserver.MockWebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

@Configuration
public class TestConfiguration {

  private static final Logger log = LoggerFactory.getLogger(TestConfiguration.class);

  @Value("${io.javaoperatorsdk.test.crdPaths}")
  private List<String> crdPaths;

  @Bean
  public KubernetesMockServer k8sMockServer() {
    final var server =
        new KubernetesMockServer(
            new Context(),
            new MockWebServer(),
            new HashMap<>(),
            new KubernetesCrudDispatcher(Collections.emptyList()),
            true);
    server.init();
    return server;
  }

  @Bean
  public KubernetesClient kubernetesClient(KubernetesMockServer server) {
    final var client = server.createClient();

    crdPaths.forEach(
        crdPath -> {
          CustomResourceDefinition crd = null;
          try {
            crd = Serialization.unmarshal(new FileInputStream(ResourceUtils.getFile(crdPath)));
          } catch (FileNotFoundException e) {
            log.warn("CRD with path {} not found!", crdPath);
            e.printStackTrace();
            return;
          }

          client.apiextensions().v1().customResourceDefinitions().create(crd);
        });

    return client;
  }
}
