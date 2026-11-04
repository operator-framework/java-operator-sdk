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
package io.javaoperatorsdk.operator.processing.matcher;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MatcherUtilsTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void mergePatchMatchesNestedValuesWithoutChangingActual() {
    var actual =
        MAPPER.readTree(
            """
            {"spec":{"replicas":1,"extra":"value"}}
            """);
    var desired =
        MAPPER.readTree(
            """
            {"spec":{"replicas":1}}
            """);
    var original = actual.deepCopy();

    assertThat(MatcherUtils.mergePatchMatches(actual, desired)).isTrue();
    assertThat(actual).isEqualTo(original);
  }

  @Test
  void mergePatchDoesNotMatchWhenNullRemovesExistingField() {
    var actual =
        MAPPER.readTree(
            """
            {"spec":{"replicas":1}}
            """);
    var desired =
        MAPPER.readTree(
            """
            {"spec":{"replicas":null}}
            """);
    var original = actual.deepCopy();

    assertThat(MatcherUtils.mergePatchMatches(actual, desired)).isFalse();
    assertThat(actual).isEqualTo(original);
  }

  @Test
  void mergePatchMatchesWhenNullTargetsAbsentField() {
    var actual =
        MAPPER.readTree(
            """
            {"spec":{}}
            """);
    var desired =
        MAPPER.readTree(
            """
            {"spec":{"replicas":null}}
            """);

    assertThat(MatcherUtils.mergePatchMatches(actual, desired)).isTrue();
  }

  @Test
  void mergePatchReplacesScalarWithObject() {
    var actual =
        MAPPER.readTree(
            """
            {"spec":"value"}
            """);
    var desired =
        MAPPER.readTree(
            """
            {"spec":{"replicas":1}}
            """);

    assertThat(MatcherUtils.mergePatchMatches(actual, desired)).isFalse();
  }

  @Test
  void mergePatchReplacesArraysRatherThanMergingThem() {
    var actual =
        MAPPER.readTree(
            """
            {"values":[1,2]}
            """);
    var desired =
        MAPPER.readTree(
            """
            {"values":[1]}
            """);

    assertThat(MatcherUtils.mergePatchMatches(actual, actual)).isTrue();
    assertThat(MatcherUtils.mergePatchMatches(actual, desired)).isFalse();
  }

  @Test
  void jsonPatchUsesJackson3Nodes() {
    var actual =
        MAPPER.readTree(
            """
            {"spec":{"replicas":1}}
            """);
    var desired =
        MAPPER.readTree(
            """
            {"spec":{"replicas":2}}
            """);

    assertThat(MatcherUtils.jsonPatchMatches(actual, actual.deepCopy())).isTrue();
    assertThat(MatcherUtils.jsonPatchMatches(actual, desired)).isFalse();
  }
}
