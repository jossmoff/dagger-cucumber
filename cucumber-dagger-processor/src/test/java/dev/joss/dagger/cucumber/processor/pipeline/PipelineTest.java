package dev.joss.dagger.cucumber.processor.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineTest {

  // ---------------------------------------------------------------------------
  // Happy path — all steps succeed
  // ---------------------------------------------------------------------------

  @Test
  void singleStep_successfulStep_returnsStepOutput() {
    StepResult<Integer> result =
        Pipeline.of("ctx", "hello")
            .pipe((_ctx, input) -> StepResult.success(input.length()))
            .result();

    assertThat(result.isHalt()).isFalse();
    assertThat(result.value()).isEqualTo(5);
  }

  @Test
  void multipleSteps_allSucceed_finalValueIsReturned() {
    StepResult<String> result =
        Pipeline.of("ctx", 1)
            .pipe((_ctx, n) -> StepResult.success(n * 10))
            .pipe((_ctx, n) -> StepResult.success("value=" + n))
            .result();

    assertThat(result.isHalt()).isFalse();
    assertThat(result.value()).isEqualTo("value=10");
  }

  @Test
  void contextIsThreadedUnchangedToEveryStep() {
    String[] capturedCtx = new String[2];

    StepResult<Integer> result =
        Pipeline.of("myCtx", 0)
            .pipe(
                (ctx, n) -> {
                  capturedCtx[0] = ctx;
                  return StepResult.success(n + 1);
                })
            .pipe(
                (ctx, n) -> {
                  capturedCtx[1] = ctx;
                  return StepResult.success(n + 1);
                })
            .result();

    assertThat(result.isHalt()).isFalse();

    assertThat(capturedCtx[0]).isEqualTo("myCtx");
    assertThat(capturedCtx[1]).isEqualTo("myCtx");
  }

  // ---------------------------------------------------------------------------
  // Short-circuit — halt propagation
  // ---------------------------------------------------------------------------

  @Test
  void haltingStep_downstreamStepsAreNotInvoked() {
    boolean[] invoked = {false};

    StepResult<Integer> result =
        Pipeline.of("ctx", "input")
            .pipe((_ctx, _input) -> StepResult.halt())
            .pipe(
                (_ctx, _input) -> {
                  invoked[0] = true;
                  return StepResult.success(42);
                })
            .result();

    assertThat(result.isHalt()).isTrue();
    assertThat(invoked[0]).isFalse();
  }

  @Test
  void haltPropagatesThroughMultipleSubsequentSteps() {
    int[] invokedCount = {0};

    StepResult<String> result =
        Pipeline.of("ctx", "start")
            .pipe((_ctx, _input) -> StepResult.<String>halt())
            .pipe(
                (_ctx, _input) -> {
                  invokedCount[0]++;
                  return StepResult.success("a");
                })
            .pipe(
                (_ctx, _input) -> {
                  invokedCount[0]++;
                  return StepResult.success("b");
                })
            .result();

    assertThat(result.isHalt()).isTrue();
    assertThat(invokedCount[0]).isZero();
  }

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------

  @Test
  void of_wrapsInputInSuccess() {
    StepResult<String> result = Pipeline.of("ctx", "hello").result();
    assertThat(result.isHalt()).isFalse();
    assertThat(result.value()).isEqualTo("hello");
  }
}
