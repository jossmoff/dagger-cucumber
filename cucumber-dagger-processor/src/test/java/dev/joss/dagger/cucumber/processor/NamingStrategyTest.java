package dev.joss.dagger.cucumber.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NamingStrategyTest {

  private static Stream<Arguments> provisionMethodNameCases() {
    return Stream.of(
        // single segment (no dot) — just decapitalize
        Arguments.of("MySteps", "mySteps"),
        // two segments
        Arguments.of("test.MySteps", "testMySteps"),
        // deep package
        Arguments.of("com.example.checkout.CheckoutSteps", "comExampleCheckoutCheckoutSteps"),
        // disambiguates classes that share a simple name
        Arguments.of("checkout.CheckoutSteps", "checkoutCheckoutSteps"),
        Arguments.of("admin.CheckoutSteps", "adminCheckoutSteps"),
        // acronym-style simple name preserved by decapitalize
        Arguments.of("test.URLParser", "testURLParser"));
  }

  @ParameterizedTest
  @MethodSource("provisionMethodNameCases")
  void provisionMethodNameDerivesFromFqn(String fqn, String expectedMethodName) {
    TypeElement typeElement = mock(TypeElement.class);
    Name qualifiedName = mock(Name.class);
    when(qualifiedName.toString()).thenReturn(fqn);
    when(typeElement.getQualifiedName()).thenReturn(qualifiedName);
    assertThat(NamingStrategy.provisionMethodName(typeElement)).isEqualTo(expectedMethodName);
  }

  private static Stream<Arguments> decapitalizeCases() {
    return Stream.of(
        Arguments.of("", ""),
        Arguments.of("URLParser", "URLParser"),
        Arguments.of("UserService", "userService"));
  }

  @ParameterizedTest
  @MethodSource("decapitalizeCases")
  void decapitalizeCorrectlyConverts(String input, String expected) {
    assertThat(NamingStrategy.decapitalize(input)).isEqualTo(expected);
  }
}
