package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.cucumber.core.backend.Container;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaggerBackendTest {

  @BeforeEach
  @AfterEach
  void resetHolder() {
    ObjectFactoryHolder.register(null);
  }

  // ---------------------------------------------------------------------------
  // getSnippet
  // ---------------------------------------------------------------------------

  @Test
  void getSnippetReturnsNull() {
    DaggerBackend backend =
        new DaggerBackend(
            null, mock(Container.class), Thread.currentThread()::getContextClassLoader);

    assertThat(backend.getSnippet()).isNull();
  }

  // ---------------------------------------------------------------------------
  // buildWorld / disposeWorld delegation
  // ---------------------------------------------------------------------------

  @Test
  void buildWorldDelegatesToRegisteredFactory() {
    DaggerObjectFactory mockFactory = mock(DaggerObjectFactory.class);
    ObjectFactoryHolder.register(mockFactory);
    DaggerBackend backend =
        new DaggerBackend(
            null, mock(Container.class), Thread.currentThread()::getContextClassLoader);

    backend.buildWorld();

    verify(mockFactory).buildWorld();
  }

  @Test
  void disposeWorldDelegatesToRegisteredFactory() {
    DaggerObjectFactory mockFactory = mock(DaggerObjectFactory.class);
    ObjectFactoryHolder.register(mockFactory);
    DaggerBackend backend =
        new DaggerBackend(
            null, mock(Container.class), Thread.currentThread()::getContextClassLoader);

    backend.disposeWorld();

    verify(mockFactory).disposeWorld();
  }

  // ---------------------------------------------------------------------------
  // loadGlue — failure paths
  // ---------------------------------------------------------------------------

  @Test
  void loadGlueThrowsWhenServiceFileIsAbsent() throws Exception {
    URLClassLoader emptyLoader = new URLClassLoader(new URL[0], null);
    DaggerBackend backend = new DaggerBackend(null, mock(Container.class), () -> emptyLoader);

    assertThatThrownBy(() -> backend.loadGlue(null, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No CucumberDaggerComponent found in service file");
  }

  @Test
  void loadGlueThrowsWhenServiceFileHasMultipleEntries(@TempDir Path tempDir) throws IOException {
    writeServiceFile(tempDir, "com.example.FirstFactory", "com.example.SecondFactory");
    URLClassLoader loader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);
    DaggerBackend backend = new DaggerBackend(null, mock(Container.class), () -> loader);

    assertThatThrownBy(() -> backend.loadGlue(null, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Multiple CucumberDaggerComponent factories found");
  }

  @Test
  void loadGlueThrowsWhenFactoryClassNotFound(@TempDir Path tempDir) throws IOException {
    writeServiceFile(tempDir, "com.example.NonExistentDaggerFactory");
    URLClassLoader loader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);
    DaggerBackend backend = new DaggerBackend(null, mock(Container.class), () -> loader);

    assertThatThrownBy(() -> backend.loadGlue(null, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Could not load component class");
  }

  @Test
  void loadGlueThrowsWhenServiceFileHasOnlyBlankLinesAndComments(@TempDir Path tempDir)
      throws IOException {
    writeServiceFile(tempDir, "# this is a comment", "  ", "");
    URLClassLoader loader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);
    DaggerBackend backend = new DaggerBackend(null, mock(Container.class), () -> loader);

    assertThatThrownBy(() -> backend.loadGlue(null, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No CucumberDaggerComponent found in service file");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static void writeServiceFile(Path tempDir, String... entries) throws IOException {
    Path servicesDir = tempDir.resolve("META-INF/services");
    Files.createDirectories(servicesDir);
    Files.write(
        servicesDir.resolve("dev.joss.dagger.cucumber.api.CucumberDaggerComponent"),
        List.of(entries));
  }
}
