package dev.joss.dagger.cucumber.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class CollectScopedClassesStepTest {

  private static final JavaFileObject APP_COMPONENT =
      JavaFileObjects.forSourceLines(
          "test.AppComponent",
          "package test;",
          "import dagger.Component;",
          "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
          "@CucumberDaggerConfiguration",
          "@Component(modules = {})",
          "public interface AppComponent {}");

  @Test
  void concreteClassWithInjectConstructorReturnsSuccessWithOneEntry() {
    AtomicReference<StepResult<CollectedScopedClasses>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              captured.set(
                  new CollectScopedClassesStep()
                      .execute(ctx, new FoundRootComponent(root, "test")));
            },
            APP_COMPONENT,
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
    assertThat(captured.get().value().scopedClasses()).hasSize(1);
    assertThat(captured.get().value().scopedClasses().getFirst().getSimpleName().toString())
        .isEqualTo("MyScoped");
  }

  @Test
  void scopedClassOutsideRootPackageExcluded() {
    AtomicReference<StepResult<CollectedScopedClasses>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              captured.set(
                  new CollectScopedClassesStep()
                      .execute(ctx, new FoundRootComponent(root, "test")));
            },
            APP_COMPONENT,
            JavaFileObjects.forSourceLines(
                "other.MyScoped",
                "package other;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "import jakarta.inject.Inject;",
                "@ScenarioScoped",
                "public class MyScoped {",
                "  @Inject public MyScoped() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().scopedClasses()).isEmpty();
  }

  @Test
  void interfaceAnnotatedWithScenarioScopedHaltsAndEmitsError() {
    AtomicReference<StepResult<CollectedScopedClasses>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              captured.set(
                  new CollectScopedClassesStep()
                      .execute(ctx, new FoundRootComponent(root, "test")));
            },
            APP_COMPONENT,
            JavaFileObjects.forSourceLines(
                "test.BadScoped",
                "package test;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "@ScenarioScoped",
                "public interface BadScoped {}"));

    assertThat(compilation).hadErrorContaining("@ScenarioScoped can only be applied to concrete");
    assertThat(captured.get().isFailed()).isTrue();
  }

  @Test
  void abstractClassAnnotatedWithScenarioScopedHaltsAndEmitsError() {
    AtomicReference<StepResult<CollectedScopedClasses>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              captured.set(
                  new CollectScopedClassesStep()
                      .execute(ctx, new FoundRootComponent(root, "test")));
            },
            APP_COMPONENT,
            JavaFileObjects.forSourceLines(
                "test.BadScoped",
                "package test;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "@ScenarioScoped",
                "public abstract class BadScoped {}"));

    assertThat(compilation).hadErrorContaining("@ScenarioScoped can only be applied to concrete");
    assertThat(captured.get().isFailed()).isTrue();
  }

  @Test
  void concreteClassWithoutInjectConstructorHaltsAndEmitsError() {
    AtomicReference<StepResult<CollectedScopedClasses>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              captured.set(
                  new CollectScopedClassesStep()
                      .execute(ctx, new FoundRootComponent(root, "test")));
            },
            APP_COMPONENT,
            JavaFileObjects.forSourceLines(
                "test.BadScoped",
                "package test;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "@ScenarioScoped",
                "public class BadScoped {}"));

    assertThat(compilation).hadErrorContaining("must declare an @Inject constructor");
    assertThat(captured.get().isFailed()).isTrue();
  }

  @Test
  void multipleErrorsAllEmittedBeforeHalting() {
    AtomicReference<StepResult<CollectedScopedClasses>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              captured.set(
                  new CollectScopedClassesStep()
                      .execute(ctx, new FoundRootComponent(root, "test")));
            },
            APP_COMPONENT,
            JavaFileObjects.forSourceLines(
                "test.BadInterface",
                "package test;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "@ScenarioScoped",
                "public interface BadInterface {}"),
            JavaFileObjects.forSourceLines(
                "test.BadNoInject",
                "package test;",
                "import dev.joss.dagger.cucumber.api.ScenarioScoped;",
                "@ScenarioScoped",
                "public class BadNoInject {}"));

    assertThat(compilation).hadErrorContaining("@ScenarioScoped can only be applied to concrete");
    assertThat(compilation).hadErrorContaining("must declare an @Inject constructor");
    assertThat(captured.get().isFailed()).isTrue();
  }
}
