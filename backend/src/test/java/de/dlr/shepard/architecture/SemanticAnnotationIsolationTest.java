package de.dlr.shepard.architecture;

import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-010 — architectural isolation fences for the semantic sub-system and
 * the EAV-on-graph antipattern ({@code DataObject.attributes}).
 *
 * <p>Two rules:
 *
 * <ol>
 *   <li><b>n10s API isolation</b> — no class <em>outside</em>
 *       {@code de.dlr.shepard.context.semantic.*} may call methods whose
 *       declaring class lives in the {@code org.neo4j.rdf.schema} (n10s) API.
 *       The semantic package uses n10s exclusively through Cypher strings
 *       passed to the OGM session; if a future contributor ever adds a direct
 *       Java-level n10s binding outside that package, this rule fires.
 *       (NEO-AUDIT-018 mitigation — prevents bare {@code :Resource} nodes from
 *       being created outside the guarded n10s bootstrap path.)
 *   <li><b>EAV write guard</b> — new v2 REST resources must not call
 *       {@code setAttributes(...)} on {@link
 *       de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO} or its subclasses.
 *       Two pre-existing callers are grandfathered below; any addition beyond
 *       that baseline must be reviewed against NEO-AUDIT-005 before landing.
 *       (NEO-AUDIT-005 mitigation — attributes-map EAV debt must not grow.)
 * </ol>
 */
class SemanticAnnotationIsolationTest {

  private static final String SEMANTIC_PACKAGE = "de.dlr.shepard.context.semantic";
  private static final String V2_PACKAGE = "de.dlr.shepard.v2";

  /**
   * Grandfathered v2 REST callers of {@code setAttributes} that predate this
   * fence. These carry the pre-existing EAV debt and are explicitly allowed.
   * Do NOT add new entries here without a NEO-AUDIT-005 justification comment.
   */
  private static final String[] GRANDFATHERED_EAV_CALLERS = {
    // T1 template instantiation: maps template attribute keys to the new
    // DataObject's attributes map during one-shot creation (pre-NEO-AUDIT-005).
    "de.dlr.shepard.v2.template.resources.TemplateInstantiationRest",
    // Batch DataObject creator: passes through caller-supplied attributes to
    // the IO object for bulk creation (pre-NEO-AUDIT-005).
    "de.dlr.shepard.v2.dataobject.resources.DataObjectBatchV2Rest",
  };

  private static JavaClasses shepardClasses;

  @BeforeAll
  static void importClasses() {
    shepardClasses =
      new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("de.dlr.shepard");
  }

  // ── Rule 1: n10s API isolation ────────────────────────────────────────────

  /**
   * No class outside {@code de.dlr.shepard.context.semantic.*} may call
   * methods from the n10s Java API (package {@code org.neo4j.rdf.schema}).
   *
   * <p>In the current codebase the semantic package does not call n10s via the
   * Java API at all — it passes Cypher strings with {@code CALL n10s.*}
   * procedure calls to the OGM session. This rule is therefore a
   * <em>preemptive guard</em>: if someone adds a direct n10s Java dependency
   * and calls it from outside the semantic package, this test fails and forces
   * a design review. {@code allowEmptyShould(true)} keeps the rule green on a
   * codebase that currently has zero such callers.
   */
  @Test
  void n10sApiCallsMustStayInsideSemanticPackage() {
    ArchRuleDefinition.noClasses()
      .that()
      .resideOutsideOfPackage(SEMANTIC_PACKAGE + "..")
      .should(callMethodsInPackage("org.neo4j.rdf.schema"))
      .allowEmptyShould(true)
      .check(shepardClasses);
  }

  // ── Rule 2: EAV write guard ───────────────────────────────────────────────

  /**
   * New v2 REST classes must not call {@code setAttributes} on
   * {@link de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO}.
   *
   * <p>The two grandfathered callers in {@link #GRANDFATHERED_EAV_CALLERS} are
   * explicitly excluded. All other {@code de.dlr.shepard.v2.*} classes are
   * subject to the rule. {@code allowEmptyShould(true)} keeps the test green
   * when the non-grandfathered v2 surface has no callers at all (the desired
   * steady state).
   */
  @Test
  void newV2ClassesMustNotCallSetAttributesOnDataObjectIO() {
    ArchRuleDefinition.noClasses()
      .that(are(inV2Package()).and(are(notGrandfathered())))
      .should(callSetAttributesOnAbstractDataObjectIO())
      .allowEmptyShould(true)
      .check(shepardClasses);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static DescribedPredicate<JavaClass> inV2Package() {
    return new DescribedPredicate<>("reside in package " + V2_PACKAGE + "..") {
      @Override
      public boolean test(JavaClass javaClass) {
        String pkg = javaClass.getPackageName();
        return pkg.equals(V2_PACKAGE) || pkg.startsWith(V2_PACKAGE + ".");
      }
    };
  }

  private static DescribedPredicate<JavaClass> notGrandfathered() {
    return new DescribedPredicate<>("are not grandfathered EAV callers") {
      @Override
      public boolean test(JavaClass javaClass) {
        String name = javaClass.getName();
        for (String allowed : GRANDFATHERED_EAV_CALLERS) {
          if (name.equals(allowed)) return false;
        }
        return true;
      }
    };
  }

  /**
   * ArchCondition that fires when a class calls any method named
   * {@code setAttributes} whose declaring class is
   * {@link de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO} or a subtype.
   */
  private static ArchCondition<JavaClass> callSetAttributesOnAbstractDataObjectIO() {
    String targetOwner = "de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO";
    String methodName = "setAttributes";
    return new ArchCondition<>("not call " + targetOwner + "." + methodName) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass
          .getMethodCallsFromSelf()
          .stream()
          .filter(call -> {
            String declaringClass = call.getTarget().getOwner().getName();
            return (declaringClass.equals(targetOwner) ||
              isSubtypeOf(call.getTarget().getOwner(), targetOwner)) &&
              call.getTarget().getName().equals(methodName);
          })
          .forEach(call ->
            events.add(
              SimpleConditionEvent.violated(
                javaClass,
                javaClass.getName() +
                  " calls " +
                  targetOwner +
                  "." +
                  methodName +
                  " at line " +
                  call.getLineNumber() +
                  " — review against NEO-AUDIT-005 before adding to GRANDFATHERED_EAV_CALLERS"
              )
            )
          );
      }

      private boolean isSubtypeOf(JavaClass candidate, String targetFqn) {
        return candidate.getAllRawSuperclasses().stream().anyMatch(c -> c.getName().equals(targetFqn));
      }
    };
  }

  /**
   * ArchCondition that fires when a class calls any method whose declaring
   * class belongs to the given package.
   */
  private static ArchCondition<JavaClass> callMethodsInPackage(String targetPackage) {
    return new ArchCondition<>("not call methods in package " + targetPackage) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass
          .getMethodCallsFromSelf()
          .stream()
          .filter(call -> {
            String pkg = call.getTarget().getOwner().getPackageName();
            return pkg.equals(targetPackage) || pkg.startsWith(targetPackage + ".");
          })
          .forEach(call -> {
            JavaMethod target = null;
            try {
              target = call.getTarget().resolveMember().orElse(null);
            } catch (Exception ignored) {
              // unresolvable; still flag by owner package
            }
            String desc =
              javaClass.getName() +
              " calls " +
              call.getTarget().getFullName() +
              " in package " +
              targetPackage +
              " at line " +
              call.getLineNumber();
            events.add(SimpleConditionEvent.violated(javaClass, desc));
          });
      }
    };
  }
}
