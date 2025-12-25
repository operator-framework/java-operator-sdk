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
package io.javaoperatorsdk.operator.api.reconciler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.javaoperatorsdk.operator.api.reconciler.ReconcileUtils.compareResourceVersions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ReconcileUtilsTest {

  private static final Logger log = LoggerFactory.getLogger(ReconcileUtilsTest.class);

  @Test
  public void compareResourceVersionsTest() {
    assertThat(compareResourceVersions("11", "22")).isNegative();
    assertThat(compareResourceVersions("22", "11")).isPositive();
    assertThat(compareResourceVersions("1", "1")).isZero();
    assertThat(compareResourceVersions("11", "11")).isZero();
    assertThat(compareResourceVersions("123", "2")).isPositive();
    assertThat(compareResourceVersions("3", "211")).isNegative();

    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("aa", "22"));
    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("11", "ba"));
    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("", "22"));
    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("11", ""));
    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("01", "123"));
    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("123", "01"));
    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("3213", "123a"));
    assertThrows(
        NonComparableResourceVersionException.class, () -> compareResourceVersions("321", "123a"));
  }

  // naive performance test that compares the work case scenario for the parsing and non-parsing
  // variants
  @Test
  @Disabled
  public void compareResourcePerformanceTest() {
    var execNum = 30000000;
    var startTime = System.currentTimeMillis();
    for (int i = 0; i < execNum; i++) {
      var res = compareResourceVersions("123456788", "123456789");
    }
    var dur1 = System.currentTimeMillis() - startTime;
    log.info("Duration without parsing: {}", dur1);
    startTime = System.currentTimeMillis();
    for (int i = 0; i < execNum; i++) {
      var res = Long.parseLong("123456788") > Long.parseLong("123456789");
    }
    var dur2 = System.currentTimeMillis() - startTime;
    log.info("Duration with parsing:   {}", dur2);

    assertThat(dur1).isLessThan(dur2);
  }

  @Test
  void retriesAddingFinalizerWithoutSSA() {
    // todo
  }

  @Test
  void nullResourceIsGracefullyHandledOnFinalizerRemovalRetry() {
    // todo
  }

  @Test
  void retriesFinalizerRemovalWithFreshResource() {
    // todo
  }
}
