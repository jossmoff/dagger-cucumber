package dev.joss.dagger.cucumber.processor;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Immutable bundle of annotation-processing services and per-round state passed to every pipeline
 * step. Constructed once per processing round in {@link CucumberDaggerProcessor#process}.
 */
final class ProcessingContext {

  final ProcessingEnvironment processingEnv;
  final RoundEnvironment roundEnv;
  final KnownTypes knownTypes;
  final AnnotationUtils annotationUtils;

  ProcessingContext(
      ProcessingEnvironment processingEnv,
      RoundEnvironment roundEnv,
      KnownTypes knownTypes,
      AnnotationUtils annotationUtils) {
    this.processingEnv = processingEnv;
    this.roundEnv = roundEnv;
    this.knownTypes = knownTypes;
    this.annotationUtils = annotationUtils;
  }

  /** Convenience accessor so steps do not have to reach through {@code processingEnv}. */
  Messager messager() {
    return processingEnv.getMessager();
  }

  /**
   * Returns {@code true} if {@code te} lives in {@code rootPackage} or a sub-package of it. Used by
   * steps to restrict discovery to the user's glue package tree.
   */
  boolean inGluePackage(TypeElement te, String rootPackage) {
    String pkg = processingEnv.getElementUtils().getPackageOf(te).getQualifiedName().toString();
    return pkg.equals(rootPackage) || pkg.startsWith(rootPackage + ".");
  }
}
