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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

public class LeaderElectionTestOperator {

  private static final Logger log = LoggerFactory.getLogger(LeaderElectionTestOperator.class);

  public static void main(String[] args) {
    String identity = System.getenv("POD_NAME");
    String namespace = System.getenv("POD_NAMESPACE");

    log.info("Starting operator with identity: {}", identity);

    LeaderElectionConfiguration leaderElectionConfiguration =
        namespace == null
            ? new LeaderElectionConfiguration("leader-election-test")
            : new LeaderElectionConfiguration("leader-election-test", namespace, identity);

    Operator operator =
        new Operator(c -> c.withLeaderElectionConfiguration(leaderElectionConfiguration));

    operator.register(new LeaderElectionTestReconciler(identity));
    operator.start();
  }
}
