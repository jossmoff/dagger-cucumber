package dev.joss.dagger.cucumber.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.LinkedHashMap;
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
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
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
    assertThat(model.rootProvisionMethods()).isEmpty();
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
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
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
  void userModuleWithScenarioScopeMethodAddedToScopedModules() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.SomeService", "package test;", "public class SomeService {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeModule",
                "package test;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dev.joss.dagger.cucumber.api.ScenarioScope;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @ScenarioScope",
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
  void namedQualifiedScenarioScopeMethodSucceedsAndGeneratesNamedProvision() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.SomeService", "package test;", "public class SomeService {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeModule",
                "package test;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dev.joss.dagger.cucumber.api.ScenarioScope;",
                "import jakarta.inject.Named;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @ScenarioScope @Named(\"primary\")",
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

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    ProcessingModel model = captured.get().value();
    assertThat(model.userScopedModules()).hasSize(1);
    assertThat(model.scopedProvisionMethods()).isEmpty();
    assertThat(model.namedScopedProvisionMethods()).hasSize(1);
    NamedScopedProvision named = model.namedScopedProvisionMethods().getFirst();
    assertThat(named.methodName()).isEqualTo("primarySomeService");
    assertThat(named.namedValue()).isEqualTo("primary");
  }

  @Test
  void nonNamedQualifiedScenarioScopeMethodSucceedsAndIncludesModuleWithoutProvision() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.SomeService", "package test;", "public class SomeService {}"),
            JavaFileObjects.forSourceLines(
                "test.MyQualifier",
                "package test;",
                "import jakarta.inject.Qualifier;",
                "import java.lang.annotation.*;",
                "@Qualifier",
                "@Retention(RetentionPolicy.RUNTIME)",
                "@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})",
                "public @interface MyQualifier {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeModule",
                "package test;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dev.joss.dagger.cucumber.api.ScenarioScope;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @ScenarioScope @MyQualifier",
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

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    ProcessingModel model = captured.get().value();
    assertThat(model.userScopedModules()).hasSize(1);
    assertThat(model.scopedProvisionMethods()).isEmpty();
    assertThat(model.namedScopedProvisionMethods()).isEmpty();
  }

  @Test
  void rootProvisionMethodsCollectedFromRootInterface() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.PriceList", "package test;", "public class PriceList {}"),
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {",
                "  PriceList priceList();",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    ProcessingModel model = captured.get().value();
    assertThat(model.rootProvisionMethods()).hasSize(1);
    assertThat(model.rootProvisionMethods().values()).containsExactly("priceList");
  }

  @Test
  void rootProvisionMethodsEmptyWhenRootInterfaceHasNone() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
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
    assertThat(captured.get().value().rootProvisionMethods()).isEmpty();
  }

  @Test
  void rootProvisionMethodsListedInModel() {
    AtomicReference<StepResult<ProcessingModel>> captured = new AtomicReference<>();

    Compilation compilation =
        StepTestSupport.compile(
            ctx -> {
              TypeElement root =
                  ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
              CollectedStepDefs input =
                  new CollectedStepDefs(root, "test", new LinkedHashMap<>(), null);
              captured.set(new BuildProcessingModelStep().execute(ctx, input));
            },
            JavaFileObjects.forSourceLines(
                "test.ServiceA", "package test;", "public class ServiceA {}"),
            JavaFileObjects.forSourceLines(
                "test.ServiceB", "package test;", "public class ServiceB {}"),
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {",
                "  ServiceA serviceA();",
                "  ServiceB serviceB();",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(captured.get().isFailed()).isFalse();
    ProcessingModel model = captured.get().value();
    assertThat(model.rootProvisionMethods()).hasSize(2);
    assertThat(model.rootProvisionMethods().values()).containsExactly("serviceA", "serviceB");
  }
}
