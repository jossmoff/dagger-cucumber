package dev.joss.dagger.cucumber.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/** Utility methods for inspecting {@link AnnotationMirror}s during annotation processing. */
final class AnnotationUtils {

  private final Types types;

  AnnotationUtils(ProcessingEnvironment processingEnv) {
    this.types = processingEnv.getTypeUtils();
  }

  /**
   * Returns {@code true} if {@code element} is annotated with the annotation represented by {@code
   * annotationType}.
   */
  boolean hasAnnotation(Element element, TypeElement annotationType) {
    if (annotationType == null) return false;
    for (AnnotationMirror am : element.getAnnotationMirrors()) {
      if (types.isSameType(am.getAnnotationType(), annotationType.asType())) return true;
    }
    return false;
  }

  /**
   * Returns {@code true} if {@code annotationType} is itself annotated (meta-annotated) with {@code
   * metaAnnotation}. For example, returns {@code true} for {@code isMetaAnnotatedWith(Singleton,
   * Scope)}.
   */
  boolean isMetaAnnotatedWith(TypeElement annotationType, TypeElement metaAnnotation) {
    if (metaAnnotation == null) return false;
    for (AnnotationMirror am : annotationType.getAnnotationMirrors()) {
      if (types.isSameType(am.getAnnotationType(), metaAnnotation.asType())) return true;
    }
    return false;
  }

  /**
   * Returns the subset of {@code annotationMirrors} whose annotation type is meta-annotated with
   * {@code targetMeta}. Used to find scope annotations (meta-annotated with {@code
   * jakarta.inject.Scope}) and qualifier annotations on an element.
   */
  List<AnnotationMirror> findMetaAnnotated(
      List<? extends AnnotationMirror> annotationMirrors, TypeElement targetMeta) {
    List<AnnotationMirror> result = new ArrayList<>();
    if (targetMeta == null) return result;
    for (AnnotationMirror am : annotationMirrors) {
      TypeElement annoType = (TypeElement) am.getAnnotationType().asElement();
      if (isMetaAnnotatedWith(annoType, targetMeta)) {
        result.add(am);
      }
    }
    return result;
  }

  /**
   * Returns the {@link TypeMirror}s found in the {@code Class[]}-valued attribute {@code
   * memberName} of annotation {@code annoType} on {@code element}.
   *
   * <p>Unresolvable entries — e.g. generated types that do not yet exist in round 1 — are silently
   * skipped, preserving the existing processor behaviour.
   *
   * @return an empty list if the annotation or the attribute is absent
   */
  List<TypeMirror> getClassArrayValue(Element element, TypeElement annoType, String memberName) {
    if (annoType == null) return List.of();
    for (AnnotationMirror am : element.getAnnotationMirrors()) {
      if (!types.isSameType(am.getAnnotationType(), annoType.asType())) continue;
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
          am.getElementValues().entrySet()) {
        if (!entry.getKey().getSimpleName().contentEquals(memberName)) continue;
        @SuppressWarnings("unchecked")
        List<? extends AnnotationValue> values =
            (List<? extends AnnotationValue>) entry.getValue().getValue();
        List<TypeMirror> result = new ArrayList<>();
        for (AnnotationValue av : values) {
          Object val = av.getValue();
          if (val instanceof TypeMirror typeMirror) {
            result.add(typeMirror);
          }
          // else: unresolvable type (e.g. CucumberDaggerModule before generation) — skip
        }
        return result;
      }
    }
    return List.of();
  }
}
