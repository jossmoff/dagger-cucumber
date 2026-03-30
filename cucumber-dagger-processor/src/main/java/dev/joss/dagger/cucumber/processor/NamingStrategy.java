package dev.joss.dagger.cucumber.processor;

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
