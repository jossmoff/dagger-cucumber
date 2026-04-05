package dev.joss.dagger.cucumber.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NamingStrategyTest {

  @Mock private TypeElement typeElement;

  @Mock private Name name;

  private static Stream<Arguments> provideClassNamesWithExpectedConversion() {
    return Stream.of(
        Arguments.of("", ""),
        Arguments.of("URLParser", "URLParser"),
        Arguments.of("UserService", "userService"));
  }

  @ParameterizedTest
  @MethodSource("provideClassNamesWithExpectedConversion")
  void provisionMethodNameCorrectlyConverts(String className, String expectedMethodName) {
    when(name.toString()).thenReturn(className);
    when(typeElement.getSimpleName()).thenReturn(name);
    assertThat(NamingStrategy.provisionMethodName(typeElement)).isEqualTo(expectedMethodName);
  }

  @ParameterizedTest
  @MethodSource("provideClassNamesWithExpectedConversion")
  void decapitalizeCorrectlyConverts(String className, String expectedMethodName) {
    assertThat(NamingStrategy.decapitalize(className)).isEqualTo(expectedMethodName);
  }
}
