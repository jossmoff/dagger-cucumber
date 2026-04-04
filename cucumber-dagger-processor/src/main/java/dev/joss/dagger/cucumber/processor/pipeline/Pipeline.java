package dev.joss.dagger.cucumber.processor.pipeline;

/**
 * A fluent, immutable pipeline that threads a shared context {@code C} through a sequence of {@link
 * ProcessingStep}s, short-circuiting on the first {@link StepResult#halt()}.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * StepResult<ProcessingModel> result =
 *     Pipeline.of(ctx, annotatedElements)
 *         .pipe(new FindRootComponentStep())
 *         .pipe(new CollectScopedClassesStep())
 *         .pipe(new CollectStepDefsStep())
 *         .pipe(new BuildProcessingModelStep())
 *         .result();
 * }</pre>
 *
 * <p>Each call to {@link #pipe} returns a new {@code Pipeline} instance; the original is
 * unmodified. If any step halts, all subsequent {@code pipe} calls are no-ops and {@link #result}
 * returns the halt immediately.
 *
 * @param <C> shared context type passed unchanged to every step
 * @param <T> type of the value currently held by the pipeline
 */
public final class Pipeline<C, T> {

  private final C context;
  private final StepResult<T> current;

  private Pipeline(C context, StepResult<T> current) {
    this.context = context;
    this.current = current;
  }

  /**
   * Creates a pipeline with {@code input} wrapped in a {@link StepResult#success(Object) success}
   * result. The pipeline is ready to accept its first {@link #pipe} call.
   *
   * @param context shared context threaded through all steps
   * @param input initial input value for the first step
   */
  public static <C, T> Pipeline<C, T> of(C context, T input) {
    return new Pipeline<>(context, StepResult.succeeded(input));
  }

  /**
   * Applies {@code step} to the current value if the pipeline has not halted. If the pipeline has
   * already halted, returns a new halted pipeline without invoking {@code step}.
   *
   * @param step the next step to apply
   * @param <U> the output type of {@code step}, which becomes the new pipeline value type
   * @return a new pipeline carrying either the step's output or a propagated halt
   */
  public <U> Pipeline<C, U> pipe(ProcessingStep<C, T, U> step) {
    if (current.isFailed()) return new Pipeline<>(context, StepResult.failed());
    return new Pipeline<>(context, step.execute(context, current.value()));
  }

  /** Returns the current {@link StepResult}, either a success value or a halt. */
  public StepResult<T> result() {
    return current;
  }
}
