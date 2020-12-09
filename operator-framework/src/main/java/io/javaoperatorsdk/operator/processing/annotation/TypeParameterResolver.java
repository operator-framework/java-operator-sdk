package io.javaoperatorsdk.operator.processing.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

class TypeParameterResolver {

  private final DeclaredType interestedClass;
  private final int interestedTypeArgumentIndex;

  public TypeParameterResolver(DeclaredType interestedClass, int interestedTypeArgumentIndex) {

    this.interestedClass = interestedClass;
    this.interestedTypeArgumentIndex = interestedTypeArgumentIndex;
  }


  public List<DeclaredType> findChain(Types typeUtils, Elements elementUtils,
      DeclaredType declaredType) {

    final var result = new ArrayList<DeclaredType>();
    result.add(declaredType);
    var superElement = ((TypeElement) ((DeclaredType) declaredType).asElement());
    var superclass = (DeclaredType) superElement.getSuperclass();
    boolean interfaceFound = false;
    final var matchingInterfaces =
        superElement.getInterfaces().stream()
            .filter(
                intface ->
                    typeUtils.isAssignable(intface, interestedClass))
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
                .filter(
                    intface ->
                        typeUtils
                            .isAssignable(intface, interestedClass))
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
              .filter(
                  intface ->
                      typeUtils.isAssignable(intface, interestedClass))
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
