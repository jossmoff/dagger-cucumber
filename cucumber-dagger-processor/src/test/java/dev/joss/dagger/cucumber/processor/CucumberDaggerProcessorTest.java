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

  @Test
  void validRootComponentGeneratesAllExpectedFiles() {
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
        .generatedSourceFile("dev.joss.dagger.cucumber.generated.GeneratedComponentResolver");
  }

  @Test
  void singletonScopeAnnotationCopiedToGeneratedWrapper() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.AppComponent",
                "package test;",
                "import dagger.Component;",
                "import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;",
                "import jakarta.inject.Singleton;",
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
  void userModulesIncludedInGeneratedWrapperComponent() {
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
  void generatedWrapperExtendsUserRootInterface() {
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
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .contains("AppComponent");
  }

  @Test
  void stepDefClassGeneratesProvisionMethodInScopedComponent() {
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
                "import jakarta.inject.Inject;",
                "public class MySteps {",
                "  @Inject public MySteps() {}",
                "}"));

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("testMySteps()");
  }

  @Test
  void scenarioScopeModuleMethodGeneratesProvisionMethodInScopedComponent() {
    Compilation compilation =
        compile(
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
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("testSomeService()");
  }

  @Test
  void rootProvisionMethodsAppearsInGeneratedResolver() {
    Compilation compilation =
        compile(
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
    assertThat(compilation)
        .generatedSourceFile("dev.joss.dagger.cucumber.generated.GeneratedComponentResolver")
        .contentsAsUtf8String()
        .contains("priceList()");
  }

  @Test
  void multipleRootComponentsEmitsCompileError() {
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
  void rootOnClassEmitsCompileError() {
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
  void componentBuilderGeneratesBuilderInWrapper() {
    Compilation compilation =
        compile(
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
    // Generated wrapper must declare an inner @Component.Builder extending AppComponent.Builder
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .contains("@Component.Builder");
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .contains("AppComponent.Builder");
    // build() return type must be the wrapper type, not the user's interface type
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .contains("GeneratedCucumberAppComponent build()");
  }

  @Test
  void componentWithoutBuilderHasNoBuilderInWrapper() {
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
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedCucumberAppComponent")
        .contentsAsUtf8String()
        .doesNotContain("@Component.Builder");
  }

  @Test
  void qualifiedScenarioScopeProviderMethodEmitsCompileError() {
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
                "import dev.joss.dagger.cucumber.api.ScenarioScope;",
                "import jakarta.inject.Named;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @ScenarioScope @Named(\"foo\")",
                "  public static SomeService provideSomeService() {",
                "    return new SomeService();",
                "  }",
                "}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Qualified @ScenarioScope provider methods are not currently supported");
  }

  @Test
  void stepDefsWithSameSimpleNameInDifferentPackagesGetUniqueProvisionMethods() {
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
                "test.checkout.CheckoutSteps",
                "package test.checkout;",
                "import jakarta.inject.Inject;",
                "public class CheckoutSteps {",
                "  @Inject public CheckoutSteps() {}",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.admin.CheckoutSteps",
                "package test.admin;",
                "import jakarta.inject.Inject;",
                "public class CheckoutSteps {",
                "  @Inject public CheckoutSteps() {}",
                "}"));

    assertThat(compilation).succeeded();
    // Both types must appear with distinct, FQN-derived method names
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("testCheckoutCheckoutSteps()");
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("testAdminCheckoutSteps()");
    // The bare colliding name must not appear
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .doesNotContain("CheckoutSteps checkoutSteps()");
  }

  @Test
  void scopedProvidersWithSameSimpleReturnTypeInDifferentPackagesGetUniqueProvisionMethods() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "test.checkout.Basket", "package test.checkout;", "public class Basket {}"),
            JavaFileObjects.forSourceLines(
                "test.admin.Basket", "package test.admin;", "public class Basket {}"),
            JavaFileObjects.forSourceLines(
                "test.SomeModule",
                "package test;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dev.joss.dagger.cucumber.api.ScenarioScope;",
                "@Module",
                "public class SomeModule {",
                "  @Provides @ScenarioScope",
                "  public static test.checkout.Basket provideCheckoutBasket() {",
                "    return new test.checkout.Basket();",
                "  }",
                "  @Provides @ScenarioScope",
                "  public static test.admin.Basket provideAdminBasket() {",
                "    return new test.admin.Basket();",
                "  }",
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
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("testCheckoutBasket()");
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .contains("testAdminBasket()");
    assertThat(compilation)
        .generatedSourceFile("test.GeneratedScopedComponent")
        .contentsAsUtf8String()
        .doesNotContain("Basket basket()");
  }
}
