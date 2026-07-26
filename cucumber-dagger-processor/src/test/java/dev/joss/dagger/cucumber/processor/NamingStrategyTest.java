package dev.joss.dagger.cucumber.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NamingStrategyTest {

  @Mock private TypeElement typeElement;

  @Mock private Name simpleName;

  @Mock private Name qualifiedName;

  private static Stream<Arguments> provideClassNamesWithExpectedConversion() {
    return Stream.of(
        Arguments.of("", ""),
        Arguments.of("URLParser", "URLParser"),
        Arguments.of("UserService", "userService"));
  }

  @ParameterizedTest
  @MethodSource("provideClassNamesWithExpectedConversion")
  void provisionMethodNameCorrectlyConverts(String className, String expectedMethodName) {
    when(simpleName.toString()).thenReturn(className);
    when(typeElement.getSimpleName()).thenReturn(simpleName);
    assertThat(NamingStrategy.provisionMethodName(typeElement)).isEqualTo(expectedMethodName);
  }

  @ParameterizedTest
  @MethodSource("provideClassNamesWithExpectedConversion")
  void decapitalizeCorrectlyConverts(String className, String expectedMethodName) {
    assertThat(NamingStrategy.decapitalize(className)).isEqualTo(expectedMethodName);
  }

  @Test
  void qualifiedProvisionMethodNameConvertsFullyQualifiedName() {
    when(qualifiedName.toString()).thenReturn("com.example.checkout.CheckoutSteps");
    when(typeElement.getQualifiedName()).thenReturn(qualifiedName);
    assertThat(NamingStrategy.qualifiedProvisionMethodName(typeElement))
        .isEqualTo("comExampleCheckoutCheckoutSteps");
  }

  @Test
  void deduplicateMethodNamesLeavesUniqueNamesUnchanged() {
    TypeName typeA = ClassName.get("a", "Foo");
    TypeName typeB = ClassName.get("b", "Bar");

    Map<TypeName, String> names = new LinkedHashMap<>();
    names.put(typeA, "foo");
    names.put(typeB, "bar");
    // No disambiguation needed — TypeElement values are never consulted for unique names
    Map<TypeName, TypeElement> elements = new LinkedHashMap<>();
    elements.put(typeA, null);
    elements.put(typeB, null);

    Map<TypeName, String> result = NamingStrategy.deduplicateMethodNames(names, elements);

    assertThat(result).containsEntry(typeA, "foo").containsEntry(typeB, "bar");
  }

  @Test
  void deduplicateMethodNamesDisambiguatesOnSimpleNameCollision() {
    // deduplicateMethodNames only calls qualifiedProvisionMethodName (getQualifiedName) on
    // colliders; it never calls provisionMethodName (getSimpleName).
    TypeElement checkoutSteps = mockWithQualifiedName("checkout.CheckoutSteps");
    TypeElement adminSteps = mockWithQualifiedName("admin.CheckoutSteps");
    TypeName typeCheckout = ClassName.get("checkout", "CheckoutSteps");
    TypeName typeAdmin = ClassName.get("admin", "CheckoutSteps");

    Map<TypeName, String> names = new LinkedHashMap<>();
    names.put(typeCheckout, "checkoutSteps");
    names.put(typeAdmin, "checkoutSteps");
    Map<TypeName, TypeElement> elements = new LinkedHashMap<>();
    elements.put(typeCheckout, checkoutSteps);
    elements.put(typeAdmin, adminSteps);

    Map<TypeName, String> result = NamingStrategy.deduplicateMethodNames(names, elements);

    assertThat(result)
        .containsEntry(typeCheckout, "checkoutCheckoutSteps")
        .containsEntry(typeAdmin, "adminCheckoutSteps");
    assertThat(result.get(typeCheckout)).isNotEqualTo(result.get(typeAdmin));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private TypeElement mockWithQualifiedName(String fqn) {
    TypeElement elem = org.mockito.Mockito.mock(TypeElement.class);
    Name fqnName = org.mockito.Mockito.mock(Name.class);
    when(fqnName.toString()).thenReturn(fqn);
    when(elem.getQualifiedName()).thenReturn(fqnName);
    return elem;
  }
}
