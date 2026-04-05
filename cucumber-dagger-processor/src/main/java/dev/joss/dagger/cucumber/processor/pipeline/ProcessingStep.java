package dev.joss.dagger.cucumber.processor.pipeline;

/**
 * One discrete unit of work in a {@link Pipeline}.
 *
 * <p>A step receives a shared context {@code C} and an input value {@code I}, and returns a {@link
 * StepResult}{@code <O>} that is either:
 *
 * <ul>
 *   <li>A <em>success</em> carrying the output value — the pipeline continues to the next step.
 *   <li>A <em>failure</em> — processing has failed and downstream steps must not run.
 * </ul>
 *
 * <p>Making the context a type parameter keeps this interface free of any annotation-processing
 * dependencies, allowing it to be used and tested in isolation.
 *
 * @param <C> shared context type threaded through all steps unchanged
 * @param <I> input type consumed by this step
 * @param <O> output type produced by this step on success
 */
@FunctionalInterface
public interface ProcessingStep<C, I, O> {
  /**
   * Executes this step.
   *
   * @param context the shared context threaded through the pipeline
   * @param input the value produced by the previous step
   * @return a {@link StepResult} carrying the output, or {@link StepResult#failed()} if processing
   *     should stop
   */
  StepResult<O> execute(C context, I input);
}
