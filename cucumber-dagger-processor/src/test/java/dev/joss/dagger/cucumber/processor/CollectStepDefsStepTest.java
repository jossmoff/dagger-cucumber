package dev.joss.dagger.cucumber.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class CollectStepDefsStepTest {

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
  void injectConstructorInGluePackageIncludedAsStepDef() {
    AtomicReference<StepResult<CollectedStepDefs>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedScopedClasses previous = new CollectedScopedClasses(root, "test", List.of());
              captured.set(new CollectStepDefsStep().execute(ctx, previous));
            },
            APP_COMPONENT,
            JavaFileObjects.forSourceLines(
                "test.MySteps",
                "package test;",
                "import jakarta.inject.Inject;",
                "public class MySteps {",
                "  @Inject public MySteps() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().stepDefMethods()).hasSize(1);
    assertThat(captured.get().value().stepDefMethods().values()).containsExactly("mySteps");
  }

  @Test
  void injectConstructorOutsideGluePackageExcluded() {
    AtomicReference<StepResult<CollectedStepDefs>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedScopedClasses previous = new CollectedScopedClasses(root, "test", List.of());
              captured.set(new CollectStepDefsStep().execute(ctx, previous));
            },
            APP_COMPONENT,
            JavaFileObjects.forSourceLines(
                "other.OutsideSteps",
                "package other;",
                "import jakarta.inject.Inject;",
                "public class OutsideSteps {",
                "  @Inject public OutsideSteps() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().stepDefMethods()).isEmpty();
  }

  @Test
  void injectConstructorOfScopedClassExcluded() {
    AtomicReference<StepResult<CollectedStepDefs>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              TypeElement scoped =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.MyScoped");
              CollectedScopedClasses previous =
                  new CollectedScopedClasses(root, "test", List.of(scoped));
              captured.set(new CollectStepDefsStep().execute(ctx, previous));
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
    assertThat(captured.get().value().stepDefMethods()).isEmpty();
  }

  @Test
  void noInjectAnnotatedElementsReturnsEmptyStepDefs() {
    AtomicReference<StepResult<CollectedStepDefs>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedScopedClasses previous = new CollectedScopedClasses(root, "test", List.of());
              captured.set(new CollectStepDefsStep().execute(ctx, previous));
            },
            APP_COMPONENT);

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().stepDefMethods()).isEmpty();
  }
}
