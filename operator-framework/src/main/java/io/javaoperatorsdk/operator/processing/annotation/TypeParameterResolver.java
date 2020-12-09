package io.javaoperatorsdk.operator.processing.annotation;

import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;

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

class TypeParameterResolver {

  private final DeclaredType interestedClass;
  private final int interestedTypeArgumentIndex;

  public TypeParameterResolver(DeclaredType interestedClass, int interestedTypeArgumentIndex) {

    this.interestedClass = interestedClass;
    this.interestedTypeArgumentIndex = interestedTypeArgumentIndex;
  }

  public TypeMirror resolve(Types typeUtils, DeclaredType declaredType) {
    final var chain = findChain(typeUtils, declaredType);
    var lastIndex = chain.size() - 1;
    String typeName;
    final List<? extends TypeMirror> typeArguments = (chain.get(lastIndex)).getTypeArguments();
    if (typeArguments.get(0).getKind() == TYPEVAR) {
      typeName = ((TypeVariable) typeArguments.get(0)).asElement().getSimpleName().toString();
    } else if (typeArguments.get(0).getKind() == DECLARED) {
      return typeArguments.get(0);
    } else {
      typeName = "";
    }

    while (lastIndex > 0) {
      lastIndex -= 1;
      final List<? extends TypeMirror> tArguments = (chain.get(lastIndex)).getTypeArguments();
      final List<? extends TypeParameterElement> typeParameters =
          ((TypeElement) ((chain.get(lastIndex)).asElement())).getTypeParameters();
      final String tName = typeName;
      final var typeIndex =
          IntStream.range(0, typeParameters.size())
              .filter(i -> typeParameters.get(i).getSimpleName().toString().equals(tName))
              .findFirst()
              .getAsInt();

      final TypeMirror matchedType = tArguments.get(typeIndex);
      if (matchedType.getKind() == TYPEVAR) {
        typeName = ((TypeVariable) matchedType).asElement().getSimpleName().toString();
      } else if (matchedType.getKind() == DECLARED) {
        return matchedType;
      } else {
        typeName = "";
      }
    }
    return null;
  }

  private List<DeclaredType> findChain(Types typeUtils, DeclaredType declaredType) {

    final var result = new ArrayList<DeclaredType>();
    result.add(declaredType);
    var superElement = ((TypeElement) ((DeclaredType) declaredType).asElement());
    var superclass = (DeclaredType) superElement.getSuperclass();
    boolean interfaceFound = false;
    final var matchingInterfaces =
        superElement.getInterfaces().stream()
            .filter(intface -> typeUtils.isAssignable(intface, interestedClass))
            .map(i -> (DeclaredType) i)
            .collect(Collectors.toList());
    if (!matchingInterfaces.isEmpty()) {
      result.addAll(matchingInterfaces);
      interfaceFound = true;
    }

    while (superclass.getKind() != TypeKind.NONE) {
      if (interfaceFound) {
        final var lastFoundInterface = result.get(result.size() - 1);
        final var marchingInterfaces =
            ((TypeElement) lastFoundInterface.asElement())
                .getInterfaces().stream()
                    .filter(intface -> typeUtils.isAssignable(intface, interestedClass))
                    .map(i -> (DeclaredType) i)
                    .collect(Collectors.toList());

        if (marchingInterfaces.size() > 0) {
          result.addAll(marchingInterfaces);
          continue;
        } else {
          break;
        }
      }

      if (typeUtils.isAssignable(superclass, interestedClass)) {
        result.add(superclass);
      }

      superElement = (TypeElement) superclass.asElement();
      final var matchedInterfaces =
          superElement.getInterfaces().stream()
              .filter(intface -> typeUtils.isAssignable(intface, interestedClass))
              .map(i -> (DeclaredType) i)
              .collect(Collectors.toList());
      if (matchedInterfaces.size() > 0) {
        result.addAll(matchedInterfaces);
        interfaceFound = true;
        continue;
      }

      if (superElement.getSuperclass().getKind() == TypeKind.NONE) {
        break;
      }
      superclass = (DeclaredType) superElement.getSuperclass();
    }

    return result;
  }
}
