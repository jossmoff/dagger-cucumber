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

class DetectComponentBuilderStepTest {

  @Test
  void componentWithoutBuilderLeavesBuildersNull() {
    AtomicReference<StepResult<FoundRootComponent>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              Set<? extends Element> annotated =
                  ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.cucumberDaggerConfiguration);
              StepResult<FoundRootComponent> found =
                  new FindRootComponentStep().execute(ctx, annotated);
              captured.set(new DetectComponentBuilderStep().execute(ctx, found.value()));
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
    assertThat(captured.get().value().componentBuilder()).isNull();
  }

  @Test
  void componentWithBuilderSetsComponentBuilderField() {
    AtomicReference<StepResult<FoundRootComponent>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              Set<? extends Element> annotated =
                  ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.cucumberDaggerConfiguration);
              StepResult<FoundRootComponent> found =
                  new FindRootComponentStep().execute(ctx, annotated);
              captured.set(new DetectComponentBuilderStep().execute(ctx, found.value()));
            },
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {",
                "  @Component.Builder",
                "  interface Builder {",
                "    AppComponent build();",
                "  }",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().componentBuilder()).isNotNull();
    assertThat(captured.get().value().componentBuilder().getSimpleName().toString())
        .isEqualTo("Builder");
  }

  @Test
  void componentWithBindsInstanceBuilderSetsComponentBuilderField() {
    AtomicReference<StepResult<FoundRootComponent>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              Set<? extends Element> annotated =
                  ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.cucumberDaggerConfiguration);
              StepResult<FoundRootComponent> found =
                  new FindRootComponentStep().execute(ctx, annotated);
              captured.set(new DetectComponentBuilderStep().execute(ctx, found.value()));
            },
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.BindsInstance;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {",
                "  @Component.Builder",
                "  interface Builder {",
                "    @BindsInstance Builder label(String label);",
                "    AppComponent build();",
                "  }",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    assertThat(captured.get().value().componentBuilder()).isNotNull();
  }
}
