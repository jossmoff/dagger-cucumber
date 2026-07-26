package dev.joss.dagger.cucumber.processor;

import java.util.Arrays;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;

/** Derives provision-method names from type names in a consistent, acronym-aware way. */
final class NamingStrategy {

  private NamingStrategy() {}

  /**
   * Returns a camel-case method name derived from the fully-qualified name of {@code typeElement}.
   * Using the FQN guarantees uniqueness across all types in the same compilation, even when two
   * classes share a simple name in different packages.
   *
   * <p>Each dot-separated segment is title-cased and joined; the first segment is lower-cased via
   * {@link #decapitalize}.
   *
   * <p>Example: {@code com.example.checkout.CheckoutSteps} → {@code
   * comExampleCheckoutCheckoutSteps}.
   */
  static String provisionMethodName(TypeElement typeElement) {
    String fqn = typeElement.getQualifiedName().toString();
    int dot = fqn.indexOf('.');
    if (dot < 0) return decapitalize(fqn);
    String tail =
        Arrays.stream(fqn.substring(dot + 1).split("\\."))
            .map(NamingStrategy::titleCase)
            .collect(Collectors.joining());
    return decapitalize(fqn.substring(0, dot)) + tail;
  }

  private static String titleCase(String s) {
    if (s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * Returns {@code name} with its leading uppercase character(s) handled as follows:
   *
   * <ul>
   *   <li>Empty string → returned unchanged.
   *   <li>First two characters both upper-case (e.g. {@code URLParser}) → returned unchanged, to
   *       avoid producing {@code uRLParser}.
   *   <li>Otherwise → first character is lower-cased (e.g. {@code Basket} → {@code basket}, {@code
   *       PriceList} → {@code priceList}).
   * </ul>
   */
  static String decapitalize(String name) {
    if (name.isEmpty()) return name;
    if (name.length() > 1
        && Character.isUpperCase(name.charAt(0))
        && Character.isUpperCase(name.charAt(1))) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
