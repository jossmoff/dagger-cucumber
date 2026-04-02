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

  // ---------------------------------------------------------------------------
  // Happy paths
  // ---------------------------------------------------------------------------

  @Test
  void concreteClassWithInjectConstructor_returnsSuccessWithOneEntry() {
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "import jakarta.inject.Inject;",
                "@CucumberScoped",
                "public class MyScoped {",
                "  @Inject public MyScoped() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isHalt()).isFalse();
    assertThat(captured.get().value().scopedClasses()).hasSize(1);
    assertThat(captured.get().value().scopedClasses().getFirst().getSimpleName().toString())
        .isEqualTo("MyScoped");
  }

  @Test
  void scopedClassOutsideRootPackage_excluded() {
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "import jakarta.inject.Inject;",
                "@CucumberScoped",
                "public class MyScoped {",
                "  @Inject public MyScoped() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isHalt()).isFalse();
    assertThat(captured.get().value().scopedClasses()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Validation errors
  // ---------------------------------------------------------------------------

  @Test
  void interfaceAnnotatedWithCucumberScoped_haltsAndEmitsError() {
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public interface BadScoped {}"));

    assertThat(compilation).hadErrorContaining("@CucumberScoped can only be applied to concrete");
    assertThat(captured.get().isHalt()).isTrue();
  }

  @Test
  void abstractClassAnnotatedWithCucumberScoped_haltsAndEmitsError() {
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public abstract class BadScoped {}"));

    assertThat(compilation).hadErrorContaining("@CucumberScoped can only be applied to concrete");
    assertThat(captured.get().isHalt()).isTrue();
  }

  @Test
  void concreteClassWithoutInjectConstructor_haltsAndEmitsError() {
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public class BadScoped {}"));

    assertThat(compilation).hadErrorContaining("must declare an @Inject constructor");
    assertThat(captured.get().isHalt()).isTrue();
  }

  @Test
  void multipleErrors_allEmittedBeforeHalting() {
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public interface BadInterface {}"),
            JavaFileObjects.forSourceLines(
                "test.BadNoInject",
                "package test;",
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public class BadNoInject {}"));

    assertThat(compilation).hadErrorContaining("@CucumberScoped can only be applied to concrete");
    assertThat(compilation).hadErrorContaining("must declare an @Inject constructor");
    assertThat(captured.get().isHalt()).isTrue();
  }
}
