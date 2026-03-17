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
package io.javaoperatorsdk.operator.migration;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoveMethodDeclaration extends Recipe {

  @Option(
      displayName = "Method pattern",
      description = "A method pattern used to find matching method declarations.",
      example = "com.example.Foo bar(..)")
  private final String methodPattern;

  @JsonCreator
  public RemoveMethodDeclaration(@JsonProperty("methodPattern") String methodPattern) {
    this.methodPattern = methodPattern;
  }

  @Override
  public String getDisplayName() {
    return "Remove method declaration";
  }

  @Override
  public String getDescription() {
    return "Removes method declarations matching the given method pattern.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    var matcher = new MethodMatcher(methodPattern, true);
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        var classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
        if (classDecl != null && matcher.matches(method, classDecl)) {
          //noinspection DataFlowIssue
          return null;
        }
        return super.visitMethodDeclaration(method, ctx);
      }
    };
  }
}
