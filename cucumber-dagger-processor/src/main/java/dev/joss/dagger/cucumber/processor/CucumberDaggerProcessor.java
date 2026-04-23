package dev.joss.dagger.cucumber.processor;

import com.google.auto.service.AutoService;
import dev.joss.dagger.cucumber.processor.pipeline.Pipeline;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Annotation processor that generates the Dagger subcomponent wiring needed by the cucumber-dagger
 * runtime.
 *
 * <p>Triggered by {@code @CucumberDaggerConfiguration}. For each annotated root component interface
 * it generates the following source files in the same package:
 *
 * <ul>
 *   <li><strong>{@code GeneratedScopedModule}</strong> - a Dagger {@code @Module} that optionally
 *       includes any user modules that contain {@code @Provides @ScenarioScope} methods.
 *   <li><strong>{@code GeneratedScopedComponent}</strong> - a Dagger {@code @Subcomponent} scoped
 *       with {@code @ScenarioScope} that declares provision methods for all scenario-scoped types
 *       and step-definition classes discovered in the glue package.
 *   <li><strong>{@code CucumberDaggerModule}</strong> - the module that declares the subcomponent
 *       and provides the raw-type {@code ScenarioScopedComponent.Builder} binding needed by the
 *       runtime.
 *   <li><strong>{@code GeneratedCucumber{UserSimpleName}}</strong> - a wrapper {@code @Component}
 *       that combines the user's modules with {@code CucumberDaggerModule} and extends the user's
 *       root interface so Dagger generates implementations for its provision methods.
 *   <li><strong>{@code dev.joss.dagger.cucumber.generated.GeneratedComponentResolver}</strong> - a
 *       type-dispatching {@code ComponentResolver} implementation that replaces all runtime
 *       reflection for component type dispatch.
 *   <li><strong>{@code META-INF/services/…CucumberDaggerComponent}</strong> - service file entry
 *       pointing to the Dagger-generated factory for the wrapper component.
 * </ul>
 *
 * <p>All discovery and validation logic lives in four discrete pipeline steps:
 *
 * <ol>
 *   <li>{@link FindRootComponentStep} - locates and validates the root component interface.
 *   <li>{@link DetectComponentBuilderStep} - optionally detects an inner {@code @Component.Builder}
 *       and branches generation accordingly.
 *   <li>{@link CollectStepDefsStep} - finds step-definition classes.
 *   <li>{@link BuildProcessingModelStep} - assembles the {@link ProcessingModel} for generation.
 * </ol>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration")
public final class CucumberDaggerProcessor extends AbstractProcessor {

  /** Creates a new {@code CucumberDaggerProcessor}. Required by the annotation-processor SPI. */
  public CucumberDaggerProcessor() {}

  private KnownTypes knownTypes;
  private AnnotationUtils annotationUtils;
  private CucumberDaggerGenerator generator;

  @Override
  public synchronized void init(javax.annotation.processing.ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    knownTypes = new KnownTypes(processingEnv);
    annotationUtils = new AnnotationUtils(processingEnv);
    generator = new CucumberDaggerGenerator(processingEnv);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return processingEnv.getSourceVersion();
  }

  @Override
  public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) return false;
    if (knownTypes.cucumberDaggerConfiguration == null) return false;

    Set<? extends Element> annotated =
        roundEnv.getElementsAnnotatedWith(knownTypes.cucumberDaggerConfiguration);
    if (annotated.isEmpty()) return false;

    ProcessingContext ctx =
        new ProcessingContext(processingEnv, roundEnv, knownTypes, annotationUtils);

    StepResult<ProcessingModel> result =
        Pipeline.<ProcessingContext, Set<? extends Element>>of(ctx, annotated)
            .pipe(new FindRootComponentStep())
            .pipe(new DetectComponentBuilderStep())
            .pipe(new CollectStepDefsStep())
            .pipe(new BuildProcessingModelStep())
            .result();

    if (!result.isFailed()) {
      generator.generate(result.value());
    }
    return false;
  }
}
