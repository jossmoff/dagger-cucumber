package dev.joss.dagger.cucumber.processor.pipeline;

/**
 * Result of a single pipeline step — either a success carrying a typed value, or a failure
 * indicating that one or more compile errors have already been emitted and downstream steps must
 * not run.
 *
 * @param <T> type of the success value
 */
public final class StepResult<T> {

  public enum Status {
    SUCCEEDED,
    FAILED
  }

  private final T value;
  private final Status status;

  private StepResult(T value, Status status) {
    this.value = value;
    this.status = status;
  }

  /** Returns a successful result carrying {@code value}. */
  public static <T> StepResult<T> succeeded(T value) {
    return new StepResult<>(value, Status.SUCCEEDED);
  }

  /**
   * Returns a failed result. Callers are expected to have already emitted one or more {@link
   * javax.tools.Diagnostic.Kind#ERROR} messages via the {@link
   * javax.annotation.processing.Messager} before returning this.
   */
  public static <T> StepResult<T> failed() {
    return new StepResult<>(null, Status.FAILED);
  }

  /** Returns the status of this result. */
  public Status status() {
    return status;
  }

  /** Returns {@code true} if this result represents a failure. */
  public boolean isFailed() {
    return status == Status.FAILED;
  }

  /**
   * Returns the success value.
   *
   * @throws IllegalStateException if called on a failed result
   */
  public T value() {
    if (status == Status.FAILED)
      throw new IllegalStateException("step failed — no value available");
    return value;
  }
}
