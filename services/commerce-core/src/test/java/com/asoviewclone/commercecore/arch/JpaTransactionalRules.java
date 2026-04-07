package com.asoviewclone.commercecore.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pitfall 4 (PR #22): every {@code @Modifying} JPA query must run inside a writable transaction, so
 * it must have {@code @Transactional} at the method level OR at the class level. Caller-side
 * {@code @Transactional} alone is fragile (easy to lose during refactor) and we enforce the
 * stricter guarantee here.
 *
 * <p>ArchUnit handles meta-annotations, multi-line formatting, and class-level propagation
 * natively, which is why this is an ArchUnit rule instead of a shell grep.
 */
@AnalyzeClasses(
    packages = "com.asoviewclone.commercecore",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class JpaTransactionalRules {

  @ArchTest
  static final ArchRule modifying_queries_must_be_writable_transactional =
      methods()
          .that()
          .areAnnotatedWith(Modifying.class)
          .should(
              new ArchCondition<>("be annotated with a WRITABLE @Transactional (not readOnly)") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  Transactional onMethod =
                      method.tryGetAnnotationOfType(Transactional.class).orElse(null);
                  Transactional onClass =
                      method.getOwner().tryGetAnnotationOfType(Transactional.class).orElse(null);
                  if (onMethod == null && onClass == null) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "Method "
                                + method.getFullName()
                                + " is @Modifying but has no @Transactional at the method or"
                                + " class level. PR #22 — see CLAUDE.md 'Review Pitfalls"
                                + " (PR #22)'."));
                    return;
                  }
                  // Method-level beats class-level. If the method itself is
                  // @Transactional, use only that declaration; otherwise
                  // fall back to the class-level one.
                  Transactional effective = onMethod != null ? onMethod : onClass;
                  if (effective.readOnly()) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "Method "
                                + method.getFullName()
                                + " is @Modifying but its effective @Transactional is"
                                + " readOnly=true, which Spring rejects for writes at runtime."
                                + " PR #22 — see CLAUDE.md 'Review Pitfalls (PR #22)'."));
                  }
                }
              })
          .because(
              "PR #22: @Modifying JPA queries require a WRITABLE @Transactional at the method or"
                  + " class level; readOnly=true breaks writes silently.");
}
