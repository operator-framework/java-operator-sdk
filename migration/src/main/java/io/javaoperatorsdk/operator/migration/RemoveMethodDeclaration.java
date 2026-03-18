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

import java.util.Objects;

import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class RemoveMethodDeclaration extends Recipe {

  @Option(
      displayName = "Interface name",
      description = "Fully qualified or simple name of the interface.",
      example = "com.example.YourInterface")
  String interfaceName;

  @Option(
      displayName = "Method name",
      description = "Name of the method to remove.",
      example = "removedMethod")
  String methodName;

  @Override
  public String getDisplayName() {
    return "Remove obsolete method from implementing classes";
  }

  @Override
  public @NlsRewrite.Description String getDescription() {
    return "Remove obsolete method from implementing classes";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {

      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

        if (cd.getType() == null || !typeMatchesOrImplements(cd.getType())) {
          return cd;
        }

        // Mutate the type info in place to remove the method from the declared methods list,
        // so all AST nodes sharing this type reference stay consistent.
        var type = cd.getType();
        if (type instanceof JavaType.Class classType) {
          var updatedMethods =
              classType.getMethods().stream().filter(m -> !m.getName().equals(methodName)).toList();
          classType.unsafeSet(
              classType.getTypeParameters(),
              classType.getSupertype(),
              classType.getOwningClass(),
              classType.getAnnotations(),
              classType.getInterfaces(),
              classType.getMembers(),
              updatedMethods);
        }

        return cd;
      }

      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        if (!method.getSimpleName().equals(methodName)) {
          return super.visitMethodDeclaration(method, ctx);
        }

        J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
        if (classDecl == null || classDecl.getType() == null) {
          return super.visitMethodDeclaration(method, ctx);
        }

        if (typeMatchesOrImplements(classDecl.getType())) {
          //noinspection DataFlowIssue
          return null;
        }

        return super.visitMethodDeclaration(method, ctx);
      }

      private boolean typeMatchesOrImplements(JavaType.FullyQualified type) {
        for (var iface : type.getInterfaces()) {
          if (iface.getFullyQualifiedName().equals(interfaceName)
              || typeMatchesOrImplements(iface)) {
            return true;
          }
        }
        var supertype = type.getSupertype();
        if (supertype != null && !supertype.getFullyQualifiedName().equals("java.lang.Object")) {
          return typeMatchesOrImplements(supertype);
        }
        return false;
      }
    };
  }

  public String getInterfaceName() {
    return interfaceName;
  }

  public void setInterfaceName(String interfaceName) {
    this.interfaceName = interfaceName;
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    RemoveMethodDeclaration that = (RemoveMethodDeclaration) o;
    return Objects.equals(interfaceName, that.interfaceName)
        && Objects.equals(methodName, that.methodName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), interfaceName, methodName);
  }
}
