package io.javaoperatorsdk.operator.api.config;

public class LeaderElectionConfiguration {

  private String leaderElectionNamespaces;
  private String leaderElectionID;

  // todo discuss
  private boolean syncEventSources;

  // todo leader election with lease vs for life, other options:
  // see: https://pkg.go.dev/sigs.k8s.io/controller-runtime/pkg/manager#Options
}
