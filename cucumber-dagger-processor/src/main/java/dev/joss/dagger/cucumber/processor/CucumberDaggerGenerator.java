package dev.joss.dagger.cucumber.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * Generates all source files and resources needed by the cucumber-dagger runtime.
 *
 * <p>Each file is generated at most once per processor lifetime, tracked via an explicit set of
 * qualified names rather than relying on {@link javax.annotation.processing.FilerException} as a
 * sentinel.
 */
final class CucumberDaggerGenerator {

  private static final String API_PKG = KnownTypes.API_PKG;
  private static final String GENERATED_PKG = KnownTypes.GENERATED_PKG;

  private final ProcessingEnvironment processingEnv;

  /** Qualified names of Java sources already written; guards against duplicate generation. */
  private final Set<String> generatedFiles = new HashSet<>();

  CucumberDaggerGenerator(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  /**
   * Generates all six outputs for the given model. Invoked once per processing round in which a
   * {@code @CucumberDaggerConfiguration} element is found.
   */
  void generate(ProcessingModel model) {
    generateGeneratedScopedModule(model);
    generateGeneratedScopedComponent(model);
    generateCucumberDaggerModule(model);
    generateCucumberScopedComponentAccessor(model);
    generateCucumberRootComponent(model);
    generateServiceFile(model);
  }

  /**
   * Generates {@code GeneratedScopedModule} — a Dagger {@code @Module} used by {@code
   * GeneratedScopedComponent}. If the user has provided Style-B scoped modules (containing
   * {@code @Provides @CucumberScoped} methods), they are listed in the {@code includes} attribute.
   */
  private void generateGeneratedScopedModule(ProcessingModel model) {
    AnnotationSpec.Builder moduleAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Module"));
    if (!model.userScopedModules().isEmpty()) {
      CodeBlock.Builder includes = CodeBlock.builder().add("{");
      for (int i = 0; i < model.userScopedModules().size(); i++) {
        if (i > 0) includes.add(", ");
        includes.add("$T.class", ClassName.get(model.userScopedModules().get(i)));
      }
      includes.add("}");
      moduleAnnotation.addMember("includes", includes.build());
    }

    TypeSpec moduleType =
        TypeSpec.classBuilder("GeneratedScopedModule")
            .addAnnotation(moduleAnnotation.build())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build();

    writeJavaFile(model.rootPackage(), moduleType, model.rootComponent());
  }

  /**
   * Generates {@code GeneratedScopedComponent} — a {@code @CucumberScoped @Subcomponent} that
   * exposes a provision method for every scenario-scoped type and every step-definition class. Also
   * generates the inner {@code Builder} interface.
   */
  private void generateGeneratedScopedComponent(ProcessingModel model) {
    ClassName generatedScopedModuleName =
        ClassName.get(model.rootPackage(), "GeneratedScopedModule");
    ClassName generatedScopedComponentName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent");
    ClassName cucumberScopedComponentName = ClassName.get(API_PKG, "CucumberScopedComponent");

    AnnotationSpec cucumberScopedAnnotation =
        AnnotationSpec.builder(ClassName.get(API_PKG, "CucumberScoped")).build();
    AnnotationSpec subcomponentAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Subcomponent"))
            .addMember("modules", "$T.class", generatedScopedModuleName)
            .build();

    TypeSpec.Builder componentBuilder =
        TypeSpec.interfaceBuilder("GeneratedScopedComponent")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(cucumberScopedAnnotation)
            .addAnnotation(subcomponentAnnotation)
            .addSuperinterface(cucumberScopedComponentName);

    for (Map.Entry<TypeName, String> entry : model.scopedProvisionMethods().entrySet()) {
      componentBuilder.addMethod(
          MethodSpec.methodBuilder(entry.getValue())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(entry.getKey())
              .build());
    }

    for (Map.Entry<TypeName, String> entry : model.stepDefMethods().entrySet()) {
      componentBuilder.addMethod(
          MethodSpec.methodBuilder(entry.getValue())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(entry.getKey())
              .build());
    }

    ParameterizedTypeName builderSuperInterface =
        ParameterizedTypeName.get(
            ClassName.get(API_PKG, "CucumberScopedComponent", "Builder"),
            generatedScopedComponentName);
    TypeSpec builderInterface =
        TypeSpec.interfaceBuilder("Builder")
            .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"))
            .addSuperinterface(builderSuperInterface)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .build();

    componentBuilder.addType(builderInterface);

    writeJavaFile(model.rootPackage(), componentBuilder.build(), model.rootComponent());
  }

  /**
   * Generates {@code CucumberDaggerModule} — declares {@code GeneratedScopedComponent} as a
   * subcomponent and provides a raw-type {@code CucumberScopedComponent.Builder} binding that
   * bridges the generated builder to the interface method on {@code CucumberDaggerComponent}.
   */
  private void generateCucumberDaggerModule(ProcessingModel model) {
    ClassName generatedScopedComponentName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent");
    ClassName generatedScopedBuilderName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent", "Builder");
    ClassName cucumberScopedBuilderName =
        ClassName.get(API_PKG, "CucumberScopedComponent", "Builder");

    AnnotationSpec moduleAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Module"))
            .addMember("subcomponents", "$T.class", generatedScopedComponentName)
            .build();

    AnnotationSpec suppressWarnings =
        AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "rawtypes").build();

    MethodSpec providesMethod =
        MethodSpec.methodBuilder("provideScopedBuilder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(ClassName.get("dagger", "Provides"))
            .addAnnotation(suppressWarnings)
            .returns(cucumberScopedBuilderName)
            .addParameter(generatedScopedBuilderName, "builder")
            .addStatement("return builder")
            .build();

    TypeSpec moduleType =
        TypeSpec.classBuilder("CucumberDaggerModule")
            .addJavadoc(
                "Generated by cucumber-dagger-processor."
                    + " Include in your @Component modules list.")
            .addAnnotation(moduleAnnotation)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addMethod(providesMethod)
            .build();

    writeJavaFile(model.rootPackage(), moduleType, model.rootComponent());
  }

  /**
   * Generates {@code dev.joss.dagger.cucumber.generated.CucumberScopedComponentAccessor} — a simple
   * class whose {@code getScopedComponentClass()} method returns the {@code
   * GeneratedScopedComponent} class literal. The runtime uses this to avoid a classpath scan at
   * startup.
   */
  private void generateCucumberScopedComponentAccessor(ProcessingModel model) {
    ClassName cucumberScopedComponentName = ClassName.get(API_PKG, "CucumberScopedComponent");
    ClassName generatedScopedComponentName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent");

    ParameterizedTypeName returnType =
        ParameterizedTypeName.get(
            ClassName.get(Class.class), WildcardTypeName.subtypeOf(cucumberScopedComponentName));

    MethodSpec method =
        MethodSpec.methodBuilder("getScopedComponentClass")
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .addStatement("return $T.class", generatedScopedComponentName)
            .build();

    TypeSpec accessorType =
        TypeSpec.classBuilder("CucumberScopedComponentAccessor")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(method)
            .build();

    writeJavaFile(GENERATED_PKG, accessorType, model.rootComponent());
  }

  /**
   * Generates {@code GeneratedCucumber{UserSimpleName}} — a wrapper {@code @Component} that
   * combines the user's modules with the generated {@code CucumberDaggerModule}. Users do not need
   * to include {@code CucumberDaggerModule} themselves.
   */
  private void generateCucumberRootComponent(ProcessingModel model) {
    ClassName cucumberDaggerComponentName = ClassName.get(API_PKG, "CucumberDaggerComponent");
    ClassName cucumberDaggerModuleName = ClassName.get(model.rootPackage(), "CucumberDaggerModule");

    CodeBlock.Builder modulesBlock = CodeBlock.builder().add("{");
    for (int i = 0; i < model.userModules().size(); i++) {
      if (i > 0) modulesBlock.add(", ");
      modulesBlock.add("$T.class", TypeName.get(model.userModules().get(i)));
    }
    if (!model.userModules().isEmpty()) modulesBlock.add(", ");
    modulesBlock.add("$T.class}", cucumberDaggerModuleName);

    AnnotationSpec componentAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Component"))
            .addMember("modules", modulesBlock.build())
            .build();

    String simpleName = model.rootComponent().getSimpleName().toString();
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder("GeneratedCucumber" + simpleName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(componentAnnotation)
            .addSuperinterface(cucumberDaggerComponentName);

    for (AnnotationMirror scope : model.scopeAnnotations()) {
      builder.addAnnotation(AnnotationSpec.get(scope));
    }

    writeJavaFile(model.rootPackage(), builder.build(), model.rootComponent());
  }

  /**
   * Writes the {@code META-INF/services/…CucumberDaggerComponent} service file pointing to the
   * Dagger-generated factory for the wrapper component.
   */
  private void generateServiceFile(ProcessingModel model) {
    String serviceFileName = "META-INF/services/" + API_PKG + ".CucumberDaggerComponent";
    if (!generatedFiles.add(serviceFileName)) return;

    String simpleName = model.rootComponent().getSimpleName().toString();
    String implementation = model.rootPackage() + ".DaggerGeneratedCucumber" + simpleName;
    try {
      FileObject fo =
          processingEnv
              .getFiler()
              .createResource(StandardLocation.CLASS_OUTPUT, "", serviceFileName);
      try (Writer w = fo.openWriter()) {
        w.write(implementation);
        w.write(System.lineSeparator());
      }
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Failed to write service file: " + e.getMessage(),
              model.rootComponent());
    }
  }

  // ---------------------------------------------------------------------------
  // File I/O
  // ---------------------------------------------------------------------------

  /**
   * Writes a generated Java source file to the filer. Skips the filer write silently if a file with
   * the same qualified name has already been generated in a prior processing round (explicit
   * duplicate tracking replaces {@link javax.annotation.processing.FilerException} swallowing).
   * Passes {@code originatingElement} to {@link javax.annotation.processing.Filer#createSourceFile}
   * to improve incremental compilation behaviour in IDEs and build tools.
   */
  private void writeJavaFile(
      String packageName, TypeSpec typeSpec, TypeElement originatingElement) {
    String qualifiedName =
        packageName.isEmpty() ? typeSpec.name : packageName + "." + typeSpec.name;
    if (!generatedFiles.add(qualifiedName)) return;

    JavaFile javaFile = JavaFile.builder(packageName, typeSpec).build();
    try {
      JavaFileObject jfo =
          processingEnv.getFiler().createSourceFile(qualifiedName, originatingElement);
      try (Writer w = jfo.openWriter()) {
        javaFile.writeTo(w);
      }
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Failed to write " + qualifiedName + ": " + e.getMessage(),
              originatingElement);
    }
  }
}
