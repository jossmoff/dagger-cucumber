package dev.joss.dagger.cucumber.processor.pipeline;

/**
 * Result of a single pipeline step — either a success carrying a typed value, or a failure
 * indicating that one or more compile errors have already been emitted and downstream steps must
 * not run.
 *
 * @param <T> type of the success value
 */
public final class StepResult<T> {

  /** Represents the outcome of a single pipeline step. */
  public enum Status {
    /** The step completed successfully and produced an output value. */
    SUCCEEDED,
    /** The step failed; one or more compiler errors have been emitted. */
    FAILED
  }

  private final T value;
  private final Status status;

  private StepResult(T value, Status status) {
    this.value = value;
    this.status = status;
  }

  /**
   * Returns a successful result carrying {@code value}.
   *
   * @param <T> type of the success value
   * @param value the output produced by the step
   * @return a succeeded result wrapping {@code value}
   */
  public static <T> StepResult<T> succeeded(T value) {
    return new StepResult<>(value, Status.SUCCEEDED);
  }

  /**
   * Returns a failed result. Callers are expected to have already emitted one or more {@link
   * javax.tools.Diagnostic.Kind#ERROR} messages via the {@link
   * javax.annotation.processing.Messager} before returning this.
   *
   * @param <T> type parameter kept for compatibility with the pipeline's type chain
   * @return a failed result with no value
   */
  public static <T> StepResult<T> failed() {
    return new StepResult<>(null, Status.FAILED);
  }

  /**
   * Returns the status of this result.
   *
   * @return {@link Status#SUCCEEDED} or {@link Status#FAILED}
   */
  public Status status() {
    return status;
  }

  /**
   * Returns {@code true} if this result represents a failure.
   *
   * @return {@code true} if the step failed
   */
  public boolean isFailed() {
    return status == Status.FAILED;
  }

  /**
   * Returns the success value.
   *
   * @return the value produced by the step
   * @throws IllegalStateException if called on a failed result
   */
  public T value() {
    if (status == Status.FAILED)
      throw new IllegalStateException("step failed — no value available");
    return value;
  }
}
