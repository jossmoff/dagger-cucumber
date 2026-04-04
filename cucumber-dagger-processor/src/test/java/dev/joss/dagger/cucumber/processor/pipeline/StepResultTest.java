package dev.joss.dagger.cucumber.processor.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StepResultTest {

  @Test
  void succeededIsNotFailed() {
    StepResult<String> result = StepResult.succeeded("hello");
    assertThat(result.isFailed()).isFalse();
  }

  @Test
  void succeededReturnsValue() {
    StepResult<String> result = StepResult.succeeded("hello");
    assertThat(result.value()).isEqualTo("hello");
  }

  @Test
  void succeededNullValueIsAllowed() {
    StepResult<String> result = StepResult.succeeded(null);
    assertThat(result.isFailed()).isFalse();
    assertThat(result.value()).isNull();
  }

  @Test
  void failedIsFailed() {
    StepResult<String> result = StepResult.failed();
    assertThat(result.isFailed()).isTrue();
  }

  @Test
  void failedValueThrowsIllegalStateException() {
    StepResult<String> result = StepResult.failed();
    assertThatThrownBy(result::value).isInstanceOf(IllegalStateException.class);
  }
}
