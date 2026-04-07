package com.asoviewclone.commercecore.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
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
  static final ArchRule modifying_queries_must_be_transactional =
      methods()
          .that()
          .areAnnotatedWith(Modifying.class)
          .should()
          .beAnnotatedWith(Transactional.class)
          .orShould()
          .beDeclaredInClassesThat()
          .areAnnotatedWith(Transactional.class)
          .because(
              "PR #22: @Modifying JPA queries require @Transactional (method or class level) to"
                  + " obtain a writable transaction. See CLAUDE.md 'Review Pitfalls (PR #22)'.");
}
