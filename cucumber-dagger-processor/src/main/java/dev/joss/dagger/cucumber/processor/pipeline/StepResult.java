package dev.joss.dagger.cucumber.processor.pipeline;

/**
 * Result of a single pipeline step — either a success carrying a typed value, or a halt indicating
 * that one or more compile errors have already been emitted and downstream steps must not run.
 *
 * @param <T> type of the success value
 */
public final class StepResult<T> {

  private final T value;
  private final boolean halted;

  private StepResult(T value, boolean halted) {
    this.value = value;
    this.halted = halted;
  }

  /** Returns a successful result carrying {@code value}. */
  public static <T> StepResult<T> success(T value) {
    return new StepResult<>(value, false);
  }

  /**
   * Returns a halt result. Callers are expected to have already emitted one or more {@link
   * javax.tools.Diagnostic.Kind#ERROR} messages via the {@link
   * javax.annotation.processing.Messager} before returning this.
   */
  public static <T> StepResult<T> halt() {
    return new StepResult<>(null, true);
  }

  /** Returns {@code true} if this result represents a halt (processing failure). */
  public boolean isHalt() {
    return halted;
  }

  /**
   * Returns the success value.
   *
   * @throws IllegalStateException if called on a halt result
   */
  public T value() {
    if (halted) throw new IllegalStateException("step halted — no value available");
    return value;
  }
}
