package dev.joss.dagger.cucumber.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

class CucumberDaggerProcessorTest {

  private static Compilation compile(javax.tools.JavaFileObject... sources) {
    return javac().withProcessors(new CucumberDaggerProcessor()).compile(sources);
  }

  // ---------------------------------------------------------------------------
  // Happy-path
  // ---------------------------------------------------------------------------

  @Test
  void validRootComponent_generatesAllExpectedFiles() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {}"));

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.GeneratedScopedModule");
    assertThat(compilation).generatedSourceFile("test.GeneratedScopedComponent");
    assertThat(compilation).generatedSourceFile("test.CucumberDaggerModule");
    assertThat(compilation).generatedSourceFile("test.GeneratedCucumberAppComponent");
    assertThat(compilation)
        .generatedSourceFile("dev.joss.dagger.cucumber.generated.CucumberScopedComponentAccessor");
  }

  @Test
  void singletonScopeAnnotation_copiedToGeneratedWrapper() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "import javax.inject.Singleton;",
                "@CucumberDaggerConfiguration",
                "@Singleton",
                "@Component(modules = {})",
                "public interface AppComponent {}"));

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .contains("@Singleton");
  }

  @Test
  void userModules_includedInGeneratedWrapperComponent() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppModule",
                "package test;",
                "import dagger.Module;",
                "@Module",
                "public class AppModule {}"),
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {AppModule.class})",
                "public interface AppComponent {}"));

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .contains("AppModule.class");
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .contains("CucumberDaggerModule.class");
  }

  @Test
  void styleAClass_generatesProvisionMethodInScopedComponent() {
    Compilation compilation =
        compile(
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "import javax.inject.Inject;",
                "@CucumberScoped",
                "public class MyScoped {",
                "  @Inject public MyScoped() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("MyScoped myScoped()");
  }

  @Test
  void stepDefClass_generatesProvisionMethodInScopedComponent() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {}"),
            JavaFileObjects.forSourceLines(
                "test.MySteps",
                "package test;",
                "import javax.inject.Inject;",
                "public class MySteps {",
                "  @Inject public MySteps() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("MySteps mySteps()");
  }

  // ---------------------------------------------------------------------------
  // Validation errors
  // ---------------------------------------------------------------------------

  @Test
  void multipleRootComponents_emitsCompileError() {
    Compilation compilation =
        compile(
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

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Only one @CucumberDaggerConfiguration is allowed");
  }

  @Test
  void rootOnClass_emitsCompileError() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "public class AppComponent {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@CucumberDaggerConfiguration can only be applied to interfaces");
  }

  @Test
  void cucumberScopedOnInterface_emitsCompileError() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {}"),
            JavaFileObjects.forSourceLines(
                "test.BadScoped",
                "package test;",
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public interface BadScoped {}"));

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("@CucumberScoped can only be applied to concrete");
  }

  @Test
  void cucumberScopedOnAbstractClass_emitsCompileError() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {})",
                "public interface AppComponent {}"),
            JavaFileObjects.forSourceLines(
                "test.BadScoped",
                "package test;",
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public abstract class BadScoped {}"));

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("@CucumberScoped can only be applied to concrete");
  }

  @Test
  void cucumberScopedClassWithoutInjectConstructor_emitsCompileError() {
    Compilation compilation =
        compile(
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
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "@CucumberScoped",
                "public class MyScoped {}"));

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("must declare an @Inject constructor");
  }

  @Test
  void qualifiedCucumberScopedProviderMethod_emitsCompileError() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "@CucumberDaggerConfiguration",
                "@Component(modules = {test.SomeModule.class})",
                "public interface AppComponent {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeService", "package test;", "public class SomeService {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeModule",
                "package test;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dev.joss.dagger.cucumber.api.CucumberScoped;",
                "import javax.inject.Named;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @CucumberScoped @Named(\"foo\")",
                "  public static SomeService provideSomeService() {",
                "    return new SomeService();",
                "  }",
                "}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Qualified @CucumberScoped provider methods are not currently supported");
  }
}
