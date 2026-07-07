package dev.joss.dagger.cucumber.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import java.util.Optional;
import java.util.Set;

/**
 * Steps that verify non-scoped binding styles: {@code @Binds}, {@code Provider<T>},
 * {@code @BindsOptionalOf}, and {@code @IntoSet} multibindings.
 */
public final class BindingSteps {

  private final Provider<Formatter> formatter;
  private final Optional<OptionalPlugin> optionalPlugin;
  private final Set<String> tags;

  @Inject
  public BindingSteps(
      Provider<Formatter> formatter, Optional<OptionalPlugin> optionalPlugin, Set<String> tags) {
    this.formatter = formatter;
    this.optionalPlugin = optionalPlugin;
    this.tags = tags;
  }

  @Then("formatting {string} produces {string}")
  public void formattingProduces(String input, String expected) {
    assertThat(formatter.get().format(input)).isEqualTo(expected);
  }

  @Then("no optional plugin is bound")
  public void noOptionalPluginIsBound() {
    assertThat(optionalPlugin).isEmpty();
  }

  @Then("the tag set contains {string}")
  public void theTagSetContains(String tag) {
    assertThat(tags).contains(tag);
  }

  @Then("the tag set has {int} entries")
  public void theTagSetHasEntries(int size) {
    assertThat(tags).hasSize(size);
  }
}
