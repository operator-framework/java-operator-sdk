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
package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeExecutorTest {

  private NodeExecutor errorThrowingNodeExecutor =
      new NodeExecutor(null, null, null) {
        @Override
        protected void doRun(DependentResourceNode dependentResourceNode) {
          throw new NoSuchFieldError();
        }
      };

  // for manual testing only to verify you can see the log message
  @Disabled
  @Test
  void nodeExecutorLogsError() throws InterruptedException {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(errorThrowingNodeExecutor);
    Thread.sleep(500);
  }
}
