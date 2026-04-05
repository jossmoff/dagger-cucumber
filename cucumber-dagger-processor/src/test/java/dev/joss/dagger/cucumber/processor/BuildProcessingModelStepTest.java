package dev.joss.dagger.cucumber.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.Test;

class BuildProcessingModelStepTest {

  @Test
  void noUserModulesProducesModelWithEmptyCollections() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", List.of(), new LinkedHashMap<>());
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    ProcessingModel model = captured.get().value();
    assertThat(model.userModules()).isEmpty();
    assertThat(model.userScopedModules()).isEmpty();
    assertThat(model.scopedProvisionMethods()).isEmpty();
    assertThat(model.scopeAnnotations()).isEmpty();
  }

  @Test
  void scopeAnnotationOnRootCopiedToModel() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", List.of(), new LinkedHashMap<>());
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import jakarta.inject.Singleton;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Singleton",
                "@Component(modules = {})",
                "public interface AppComponent {}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().scopeAnnotations()).hasSize(1);
    assertThat(captured.get().value().scopeAnnotations().getFirst().getAnnotationType().toString())
        .isEqualTo("jakarta.inject.Singleton");
  }

  @Test
  void styleAClassAddsProvisionMethodToModel() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              TypeElement scoped =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.MyScoped");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", List.of(scoped), new LinkedHashMap<>());
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {}"),
            JavaFileObjects.forSourceLines(
                "test.MyScoped",
                "package test;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "import jakarta.inject.Inject;",
                "@ScenarioScoped",
                "public class MyScoped {",
                "  @Inject public MyScoped() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().scopedProvisionMethods().values())
        .containsExactly("myScoped");
  }

  @Test
  void userModuleWithStyleBMethodAddedToScopedModules() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", List.of(), new LinkedHashMap<>());
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.SomeService", "package test;", "public class SomeService {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeModule",
                "package test;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @ScenarioScoped",
                "  public static SomeService provideSomeService() { return new SomeService(); }",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {test.SomeModule.class})",
                "public interface AppComponent {}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    ProcessingModel model = captured.get().value();
    assertThat(model.userScopedModules()).hasSize(1);
    assertThat(model.userScopedModules().getFirst().getSimpleName().toString())
        .isEqualTo("SomeModule");
    assertThat(model.scopedProvisionMethods().values()).containsExactly("someService");
  }

  @Test
  void qualifiedStyleBMethodHaltsAndEmitsError() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", List.of(), new LinkedHashMap<>());
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.SomeService", "package test;", "public class SomeService {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeModule",
                "package test;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "import jakarta.inject.Named;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @ScenarioScoped @Named(\"foo\")",
                "  public static SomeService provide() { return new SomeService(); }",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {test.SomeModule.class})",
                "public interface AppComponent {}"));

    assertThat(compilation)
        .hadErrorContaining(
            "Qualified @ScenarioScoped provider methods are not currently supported");
    assertThat(captured.get().isFailed()).isTrue();
  }
}
