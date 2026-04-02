package dev.joss.dagger.cucumber.processor.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StepResultTest {

  @Test
  void success_isNotHalt() {
    StepResult<String> result = StepResult.success("hello");
    assertThat(result.isHalt()).isFalse();
  }

  @Test
  void success_returnsValue() {
    StepResult<String> result = StepResult.success("hello");
    assertThat(result.value()).isEqualTo("hello");
  }

  @Test
  void success_nullValue_isAllowed() {
    StepResult<String> result = StepResult.success(null);
    assertThat(result.isHalt()).isFalse();
    assertThat(result.value()).isNull();
  }

  @Test
  void halt_isHalt() {
    StepResult<String> result = StepResult.halt();
    assertThat(result.isHalt()).isTrue();
  }

  @Test
  void halt_value_throwsIllegalStateException() {
    StepResult<String> result = StepResult.halt();
    assertThatThrownBy(result::value).isInstanceOf(IllegalStateException.class);
  }
}
