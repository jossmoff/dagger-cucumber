package dev.joss.dagger.cucumber.processor;

import com.palantir.javapoet.TypeName;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;

/** Derives provision-method names from type names in a consistent, acronym-aware way. */
final class NamingStrategy {

  private NamingStrategy() {}

  /**
   * Returns a camel-case method name derived from the simple name of {@code typeElement}. Delegates
   * to {@link #decapitalize(String)}.
   */
  static String provisionMethodName(TypeElement typeElement) {
    return decapitalize(typeElement.getSimpleName().toString());
  }

  /**
   * Returns a camel-case method name derived from the fully-qualified name of {@code typeElement},
   * guaranteed to be unique across all types in the same compilation. Each dot-separated segment is
   * title-cased and joined; the first segment is lower-cased via {@link #decapitalize}.
   *
   * <p>Example: {@code com.example.checkout.CheckoutSteps} → {@code
   * comExampleCheckoutCheckoutSteps}.
   */
  static String qualifiedProvisionMethodName(TypeElement typeElement) {
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
   * Returns a new {@link LinkedHashMap} with the same keys as {@code namesByType} but with any
   * duplicate method-name values replaced by FQN-based names from {@link
   * #qualifiedProvisionMethodName}. Types whose simple-name-derived method name is already unique
   * keep their short name.
   *
   * @param namesByType ordered map from {@link TypeName} to a simple-name-derived method name
   * @param typeElements companion map from the same {@link TypeName} keys to their {@link
   *     TypeElement}, used only for disambiguating collisions
   */
  static Map<TypeName, String> deduplicateMethodNames(
      Map<TypeName, String> namesByType, Map<TypeName, TypeElement> typeElements) {
    // Identify which simple names collide
    Set<String> seen = new HashSet<>();
    Set<String> duplicates = new HashSet<>();
    for (String name : namesByType.values()) {
      if (!seen.add(name)) duplicates.add(name);
    }
    if (duplicates.isEmpty()) return namesByType;

    Map<TypeName, String> result = new LinkedHashMap<>();
    for (Map.Entry<TypeName, String> entry : namesByType.entrySet()) {
      if (duplicates.contains(entry.getValue())) {
        TypeElement element = typeElements.get(entry.getKey());
        result.put(
            entry.getKey(),
            element != null ? qualifiedProvisionMethodName(element) : entry.getValue());
      } else {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
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
