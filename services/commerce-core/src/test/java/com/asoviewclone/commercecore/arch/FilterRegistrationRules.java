package com.asoviewclone.commercecore.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.servlet.Filter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Pitfall 6 (PR #21): a Servlet {@link Filter} annotated {@code @Component} is auto-registered by
 * Spring Boot AND separately wired into the security chain via {@code addFilterBefore(...)}. The
 * filter then runs twice per request. The fix is a {@code @Bean FilterRegistrationBean<Self>} that
 * calls {@code setEnabled(false)} to suppress the auto-registration. The suppressor does not have
 * to live in the same package as the filter (it currently lives in {@code SecurityConfig}).
 *
 * <p>Implemented as a plain JUnit test (not an {@code @ArchTest}) so we can walk the full class
 * graph freely instead of being narrowed to one subject class at a time.
 */
public class FilterRegistrationRules {

  @Test
  void component_filters_must_disable_auto_registration() {
    JavaClasses all =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.asoviewclone.commercecore");

    // A method is a valid suppressor iff:
    //   1. It is @Bean-annotated,
    //   2. Returns FilterRegistrationBean<X>,
    //   3. Takes the filter class as a constructor/parameter (so we can bind
    //      it to its target filter), AND
    //   4. Calls FilterRegistrationBean.setEnabled(...) somewhere in the
    //      body. (Without setEnabled(false), the suppressor is a lie.)
    Set<String> filtersWithSuppressor = new HashSet<>();
    for (JavaClass c : all) {
      for (JavaMethod m : c.getMethods()) {
        if (!m.isAnnotatedWith(Bean.class)) continue;
        if (!m.getRawReturnType().isAssignableTo(FilterRegistrationBean.class)) continue;
        boolean callsSetEnabled =
            m.getMethodCallsFromSelf().stream()
                .map(JavaMethodCall::getTarget)
                .anyMatch(
                    t ->
                        "setEnabled".equals(t.getName())
                            && t.getOwner().isAssignableTo(FilterRegistrationBean.class));
        if (!callsSetEnabled) continue;
        for (JavaClass p : m.getRawParameterTypes()) {
          if (p.isAssignableTo(Filter.class)) {
            filtersWithSuppressor.add(p.getFullName());
          }
        }
      }
    }

    Set<String> offenders =
        all.stream()
            .filter(c -> c.isAssignableTo(Filter.class))
            .filter(c -> c.isAnnotatedWith(Component.class))
            .map(JavaClass::getFullName)
            .filter(fqcn -> !filtersWithSuppressor.contains(fqcn))
            .collect(Collectors.toCollection(java.util.TreeSet::new));

    if (!offenders.isEmpty()) {
      throw new AssertionError(
          "PR #21 rule — @Component Filter classes without a sibling @Bean"
              + " FilterRegistrationBean<Self>.setEnabled(false) suppressor (they will run twice"
              + " per request). See CLAUDE.md 'Review Pitfalls (PR #21)'. Offenders:\n  - "
              + String.join("\n  - ", offenders));
    }
  }
}
