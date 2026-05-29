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
package io.javaoperatorsdk.operator.support;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Resets the {@link ExternalIDGenServiceMock} singleton before each test method so that state from
 * one test does not leak into the next. Apply via
 * {@code @ExtendWith(ExternalServiceResetExtension.class)}.
 */
public class ExternalServiceResetExtension implements BeforeEachCallback {

  @Override
  public void beforeEach(@NonNull ExtensionContext context) {
    ExternalIDGenServiceMock.getInstance().reset();
  }
}
