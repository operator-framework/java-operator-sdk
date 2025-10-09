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
package io.javaoperatorsdk.operator.config.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;

import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;

/** This class can resolve a type parameter in the given index to the actual type defined. */
class TypeParameterResolver {

  private final DeclaredType interestedClass;
  private final int interestedTypeArgumentIndex;

  public TypeParameterResolver(DeclaredType interestedClass, int interestedTypeArgumentIndex) {

    this.interestedClass = interestedClass;
    this.interestedTypeArgumentIndex = interestedTypeArgumentIndex;
  }

  /**
   * @param typeUtils Type utilities, During the annotation processing processingEnv.getTypeUtils()
   *     can be passed.
   * @param declaredType Class or Interface which extends or implements the interestedClass, and the
   *     interest is getting the actual declared type is used.
   * @return the type of the parameter if it can be resolved from the given declareType, otherwise
   *     it returns null
   */
  public TypeMirror resolve(Types typeUtils, DeclaredType declaredType) {
    final var chain = findChain(typeUtils, declaredType);
    var lastIndex = chain.size() - 1;
    String typeName = "";
    final List<? extends TypeMirror> typeArguments = (chain.get(lastIndex)).getTypeArguments();
    if (typeArguments.isEmpty()) {
      return null;
    }
    if (typeArguments.get(interestedTypeArgumentIndex).getKind() == TYPEVAR) {
      typeName =
          ((TypeVariable) typeArguments.get(interestedTypeArgumentIndex))
              .asElement()
              .getSimpleName()
              .toString();
    } else if (typeArguments.get(interestedTypeArgumentIndex).getKind() == DECLARED) {
      return typeArguments.get(0);
    }

    while (lastIndex > 0) {
      lastIndex -= 1;
      final List<? extends TypeMirror> tArguments = (chain.get(lastIndex)).getTypeArguments();
      final List<? extends TypeParameterElement> typeParameters =
          ((TypeElement) ((chain.get(lastIndex)).asElement())).getTypeParameters();

      final var typeIndex = getTypeIndexWithName(typeName, typeParameters);

      final TypeMirror matchedType = tArguments.get(typeIndex);
      if (matchedType.getKind() == TYPEVAR) {
        typeName = ((TypeVariable) matchedType).asElement().getSimpleName().toString();
      } else if (matchedType.getKind() == DECLARED) {
        return matchedType;
      }
    }
    return null;
  }

  private int getTypeIndexWithName(
      String typeName, List<? extends TypeParameterElement> typeParameters) {
    return IntStream.range(0, typeParameters.size())
        .filter(i -> typeParameters.get(i).getSimpleName().toString().equals(typeName))
        .findFirst()
        .orElseThrow();
  }

  private List<DeclaredType> findChain(Types typeUtils, DeclaredType declaredType) {

    final var result = new ArrayList<DeclaredType>();
    result.add(declaredType);
    var superElement = ((TypeElement) declaredType.asElement());
    var superclass = (DeclaredType) superElement.getSuperclass();

    final var matchingInterfaces = getMatchingInterfaces(typeUtils, superElement);
    // if chain of interfaces is not empty, there is no reason to continue the lookup
    // as interfaces do not extend the classes
    if (matchingInterfaces.size() > 0) {
      result.addAll(matchingInterfaces);
      return result;
    }

    while (superclass.getKind() != TypeKind.NONE) {

      if (typeUtils.isAssignable(superclass, interestedClass)) {
        result.add(superclass);
      }

      superElement = (TypeElement) superclass.asElement();
      ArrayList<DeclaredType> ifs = getMatchingInterfaces(typeUtils, superElement);
      if (ifs.size() > 0) {
        result.addAll(ifs);
        return result;
      }

      if (superElement.getSuperclass().getKind() == TypeKind.NONE) {
        break;
      }
      superclass = (DeclaredType) superElement.getSuperclass();
    }
    return result;
  }

  private ArrayList<DeclaredType> getMatchingInterfaces(Types typeUtils, TypeElement superElement) {
    final var result = new ArrayList<DeclaredType>();

    final var matchedInterfaces =
        superElement.getInterfaces().stream()
            .filter(intface -> typeUtils.isAssignable(intface, interestedClass))
            .map(i -> (DeclaredType) i)
            .collect(Collectors.toList());
    if (matchedInterfaces.size() > 0) {
      result.addAll(matchedInterfaces);
      final var lastFoundInterface = result.get(result.size() - 1);
      final var marchingInterfaces = findChainOfInterfaces(typeUtils, lastFoundInterface);
      result.addAll(marchingInterfaces);
    }
    return result;
  }

  private List<DeclaredType> findChainOfInterfaces(Types typeUtils, DeclaredType parentInterface) {
    final var result = new ArrayList<DeclaredType>();
    var matchingInterfaces =
        ((TypeElement) parentInterface.asElement())
            .getInterfaces().stream()
                .filter(i -> typeUtils.isAssignable(i, interestedClass))
                .map(i -> (DeclaredType) i)
                .collect(Collectors.toList());
    while (matchingInterfaces.size() > 0) {
      result.addAll(matchingInterfaces);
      final var lastFoundInterface = matchingInterfaces.get(matchingInterfaces.size() - 1);
      matchingInterfaces =
          ((TypeElement) lastFoundInterface.asElement())
              .getInterfaces().stream()
                  .filter(i -> typeUtils.isAssignable(i, interestedClass))
                  .map(i -> (DeclaredType) i)
                  .collect(Collectors.toList());
    }
    return result;
  }
}
