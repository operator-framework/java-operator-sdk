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
package io.javaoperatorsdk.operator.api.config.loader;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBindingTest {

  @Test
  void storesKeyTypeAndSetter() {
    List<String> calls = new ArrayList<>();
    ConfigBinding<List<String>, String> binding =
        new ConfigBinding<>("my.key", String.class, (list, v) -> list.add(v));

    assertThat(binding.key()).isEqualTo("my.key");
    assertThat(binding.type()).isEqualTo(String.class);

    binding.setter().accept(calls, "hello");
    assertThat(calls).containsExactly("hello");
  }
}
