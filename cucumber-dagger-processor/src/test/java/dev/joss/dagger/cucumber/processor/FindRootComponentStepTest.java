package dev.joss.dagger.cucumber.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.Element;
import org.junit.jupiter.api.Test;

class FindRootComponentStepTest {

  @Test
  void validInterfaceReturnsSuccessWithPackageAndElement() {
    AtomicReference<StepResult<FoundRootComponent>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> captured.set(
                new FindRootComponentStep()
                    .execute(
                        ctx,
                        ctx.roundEnv.getElementsAnnotatedWith(
                            ctx.knownTypes.cucumberDaggerConfiguration))),
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isHalt()).isFalse();
    assertThat(captured.get().value().rootPackage()).isEqualTo("test");
    assertThat(captured.get().value().rootComponent().getSimpleName().toString())
        .isEqualTo("AppComponent");
  }

  // ---------------------------------------------------------------------------
  // Multiple annotated elements
  // ---------------------------------------------------------------------------

  @Test
  void multipleAnnotatedElements_haltsAndEmitsError() {
    AtomicReference<StepResult<FoundRootComponent>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              Set<? extends Element> annotated =
                  ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.cucumberDaggerConfiguration);
              captured.set(new FindRootComponentStep().execute(ctx, annotated));
            },
            JavaFileObjects.forSourceLines(
                "test.First",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface First {}"),
            JavaFileObjects.forSourceLines(
                "test.Second",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface Second {}"));

    assertThat(compilation).hadErrorContaining("Only one @CucumberDaggerConfiguration is allowed");
    assertThat(captured.get().isHalt()).isTrue();
  }

  @Test
  void annotatedClassHaltsAndEmitsError() {
    AtomicReference<StepResult<FoundRootComponent>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> captured.set(
                new FindRootComponentStep()
                    .execute(
                        ctx,
                        ctx.roundEnv.getElementsAnnotatedWith(
                            ctx.knownTypes.cucumberDaggerConfiguration))),
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "public class AppComponent {}"));

    assertThat(compilation)
        .hadErrorContaining("@CucumberDaggerConfiguration can only be applied to interfaces");
    assertThat(captured.get().isHalt()).isTrue();
  }
}
