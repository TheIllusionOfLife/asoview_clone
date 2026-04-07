package com.asoviewclone.commercecore.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;

/**
 * Pitfall 7 (PR #21): a {@code @Profile}-restricted bean injected unconditionally into a
 * non-{@code @Profile} consumer crashes every excluded profile at context init (bean wiring
 * failure). The fix is either narrow the consumer's profile to match OR receive the dependency via
 * {@code Optional<X>} / {@code ObjectProvider<X>} so its absence is tolerated.
 *
 * <p>We check constructor-parameter types: for every constructor parameter of a class whose type is
 * another application class annotated with {@code @Profile}, the consumer class must itself be
 * {@code @Profile}-annotated OR the parameter must be wrapped in {@code Optional} / {@code
 * ObjectProvider}.
 */
@AnalyzeClasses(
    packages = "com.asoviewclone.commercecore",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ProfileConsumerRules {

  @ArchTest
  static final ArchRule profile_restricted_beans_require_profile_or_optional_consumers =
      classes()
          .that()
          .resideInAPackage("com.asoviewclone.commercecore..")
          .should(
              new ArchCondition<>(
                  "only inject @Profile-restricted beans through Optional/ObjectProvider"
                      + " unless the consumer is itself @Profile-restricted") {
                @Override
                public void check(JavaClass consumer, ConditionEvents events) {
                  boolean consumerIsProfileRestricted = consumer.isAnnotatedWith(Profile.class);
                  consumer.getConstructors().stream()
                      .flatMap(c -> c.getRawParameterTypes().stream())
                      .distinct()
                      .forEach(
                          paramType -> {
                            if (paramType.isEquivalentTo(Optional.class)
                                || paramType.isEquivalentTo(ObjectProvider.class)) {
                              return;
                            }
                            if (!paramType
                                .getPackageName()
                                .startsWith("com.asoviewclone.commercecore")) {
                              return;
                            }
                            if (paramType.isAnnotatedWith(Profile.class)
                                && !consumerIsProfileRestricted) {
                              events.add(
                                  SimpleConditionEvent.violated(
                                      consumer,
                                      "Class "
                                          + consumer.getFullName()
                                          + " injects @Profile-restricted bean "
                                          + paramType.getFullName()
                                          + " unconditionally. Either annotate "
                                          + consumer.getSimpleName()
                                          + " with @Profile matching the producer's profiles, OR"
                                          + " wrap the dependency in Optional<> / ObjectProvider<>."
                                          + " PR #21 rule — see CLAUDE.md 'Review Pitfalls"
                                          + " (PR #21)'."));
                            }
                          });
                }
              })
          .because(
              "PR #21: @Profile-restricted beans injected unconditionally into non-@Profile"
                  + " consumers break every excluded profile at context init.");
}
