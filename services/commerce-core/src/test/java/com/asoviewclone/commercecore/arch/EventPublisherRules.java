package com.asoviewclone.commercecore.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pitfall 5 (PR #21): any method that calls {@link ApplicationEventPublisher#publishEvent} must
 * itself be {@code @Transactional} (or declared in a {@code @Transactional} class). Otherwise an
 * {@code @TransactionalEventListener(AFTER_COMMIT)} consumer silently never fires because there is
 * no outer transaction to hang the AFTER_COMMIT hook on.
 *
 * <p>We enforce a coarse but safe rule: every publisher method must be @Transactional, regardless
 * of the event class. In practice, every event worth publishing has at least one listener, and
 * making the rule event-class-aware adds significant complexity for no extra coverage.
 */
@AnalyzeClasses(
    packages = "com.asoviewclone.commercecore",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class EventPublisherRules {

  private static final DescribedPredicate<JavaCall<?>> PUBLISH_EVENT_CALL =
      new DescribedPredicate<>("call ApplicationEventPublisher.publishEvent(...)") {
        @Override
        public boolean test(JavaCall<?> call) {
          return call.getTargetOwner().isAssignableTo(ApplicationEventPublisher.class)
              && "publishEvent".equals(call.getName());
        }
      };

  private static final ArchCondition<JavaMethod> BE_TRANSACTIONAL =
      new ArchCondition<>("be @Transactional (method or enclosing class)") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
          boolean ok =
              method.isAnnotatedWith(Transactional.class)
                  || method.getOwner().isAnnotatedWith(Transactional.class);
          if (!ok) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    "Method "
                        + method.getFullName()
                        + " calls ApplicationEventPublisher.publishEvent(...) but is not"
                        + " @Transactional. PR #21: AFTER_COMMIT listeners silently do not fire"
                        + " without an enclosing transaction. See CLAUDE.md 'Review Pitfalls"
                        + " (PR #21)'."));
          }
        }
      };

  // NOTE: private methods are intentionally NOT excluded. Allowing a
  // public non-transactional method to delegate the publish call to a
  // private helper would be a trivial loophole — CodeRabbit/Gemini PR
  // #23 feedback. Recipients of this check's violations on private
  // methods whose boundary is established by a TransactionTemplate
  // (e.g. PaymentReconciliationJob.reconcileBatch) should annotate the
  // private method with @Transactional(propagation = MANDATORY) to
  // assert the expectation at runtime, OR inline the private helper.
  @ArchTest
  static final ArchRule publish_event_callers_must_be_transactional =
      methods()
          .that()
          .areNotDeclaredIn(ApplicationEventPublisher.class)
          .and(
              new DescribedPredicate<JavaMethod>("call publishEvent") {
                @Override
                public boolean test(JavaMethod method) {
                  return method.getCallsFromSelf().stream().anyMatch(PUBLISH_EVENT_CALL);
                }
              })
          .should(BE_TRANSACTIONAL)
          .because(
              "PR #21: every method calling ApplicationEventPublisher.publishEvent must be"
                  + " @Transactional so AFTER_COMMIT listeners can hang off the enclosing"
                  + " transaction.");
}
