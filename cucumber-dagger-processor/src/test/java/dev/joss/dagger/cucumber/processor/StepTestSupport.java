package dev.joss.dagger.cucumber.processor;

import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/**
 * Shared test helper that compiles source files with a capturing {@link Processor} so that
 * individual pipeline steps can be exercised in isolation against real {@link
 * javax.lang.model.element.Element} objects.
 *
 * <p>Usage pattern:
 *
 * <pre>{@code
 * AtomicReference<StepResult<CollectedScopedClasses>> captured = new AtomicReference<>();
 *
 * Compilation c = StepTestSupport.compile(
 *     ctx -> {
 *         TypeElement root = ctx.processingEnv.getElementUtils().getTypeElement("test.AppComponent");
 *         captured.set(new CollectScopedClassesStep().execute(ctx, new FoundRootComponent(root, "test")));
 *     },
 *     source1, source2);
 * }</pre>
 */
final class StepTestSupport {

  private StepTestSupport() {}

  @FunctionalInterface
  interface ContextConsumer {
    void accept(ProcessingContext ctx);
  }

  /**
   * Compiles {@code sources} with an anonymous processor that sets up a real {@link
   * ProcessingContext} and passes it to {@code consumer} on the first non-final processing round.
   */
  static Compilation compile(ContextConsumer consumer, JavaFileObject... sources) {
    return javac().withProcessors(capturingProcessor(consumer)).compile(sources);
  }

  private static Processor capturingProcessor(ContextConsumer consumer) {
    return new AbstractProcessor() {
      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;
        KnownTypes kt = new KnownTypes(processingEnv);
        AnnotationUtils au = new AnnotationUtils(processingEnv);
        ProcessingContext ctx = new ProcessingContext(processingEnv, roundEnv, kt, au);
        consumer.accept(ctx);
        return false;
      }
    };
  }
}
