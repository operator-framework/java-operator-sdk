package io.javaoperatorsdk.operator.sample;

import java.util.concurrent.atomic.LongAccumulator;

public class PureJavaApplicationRunner {

  public static void main(String[] args) {
    //    KubernetesClient client = new DefaultKubernetesClient();
    //    Operator operator = new Operator(client, DefaultConfigurationService.INSTANCE);
    //    operator.registerController(new CustomServiceController(client));

    final var acc = new LongAccumulator(Long::min, Integer.MAX_VALUE);
    acc.accumulate(10);
    acc.accumulate(20);
    acc.accumulate(90);

    System.out.println(acc.get());
  }
}
