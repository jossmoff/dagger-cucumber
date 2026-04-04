package dev.joss.dagger.cucumber.processor.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineTest {

  @Test
  void singleStepSuccessfulStepReturnsStepOutput() {
    StepResult<Integer> result =
        Pipeline.of("ctx", "hello")
            .pipe((_ctx, input) -> StepResult.succeeded(input.length()))
            .result();

    assertThat(result.isFailed()).isFalse();
    assertThat(result.value()).isEqualTo(5);
  }

  @Test
  void multipleStepsAllSucceedFinalValueIsReturned() {
    StepResult<String> result =
        Pipeline.of("ctx", 1)
            .pipe((_ctx, n) -> StepResult.succeeded(n * 10))
            .pipe((_ctx, n) -> StepResult.succeeded("value=" + n))
            .result();

    assertThat(result.isFailed()).isFalse();
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
                  return StepResult.succeeded(n + 1);
                })
            .pipe(
                (ctx, n) -> {
                  capturedCtx[1] = ctx;
                  return StepResult.succeeded(n + 1);
                })
            .result();

    assertThat(result.isFailed()).isFalse();

    assertThat(capturedCtx).containsExactly("myCtx", "myCtx");
  }

  @Test
  void haltingStepDownstreamStepsAreNotInvoked() {
    boolean[] invoked = {false};

    StepResult<Integer> result =
        Pipeline.of("ctx", "input")
            .pipe((_ctx, _input) -> StepResult.failed())
            .pipe(
                (_ctx, _input) -> {
                  invoked[0] = true;
                  return StepResult.succeeded(42);
                })
            .result();

    assertThat(result.isFailed()).isTrue();
    assertThat(invoked[0]).isFalse();
  }

  @Test
  void haltPropagatesThroughMultipleSubsequentSteps() {
    int[] invokedCount = {0};

    StepResult<String> result =
        Pipeline.of("ctx", "start")
            .pipe((_ctx, _input) -> StepResult.<String>failed())
            .pipe(
                (_ctx, _input) -> {
                  invokedCount[0]++;
                  return StepResult.succeeded("a");
                })
            .pipe(
                (_ctx, _input) -> {
                  invokedCount[0]++;
                  return StepResult.succeeded("b");
                })
            .result();

    assertThat(result.isFailed()).isTrue();
    assertThat(invokedCount[0]).isZero();
  }

  @Test
  void ofWrapsInputInSuccess() {
    StepResult<String> result = Pipeline.of("ctx", "hello").result();
    assertThat(result.isFailed()).isFalse();
    assertThat(result.value()).isEqualTo("hello");
  }
}
