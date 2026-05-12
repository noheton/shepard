package de.dlr.shepard.architecture;

import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Architectural fence enforcing the {@code /v2/...} namespace convention
 * spelled out in {@code CLAUDE.md} §"API-version policy" and {@code
 * aidocs/47 §2}.
 *
 * <p>Three rules:
 *
 * <ol>
 *   <li>Classes in {@code de.dlr.shepard.v2..} annotated with {@link Path}
 *       MUST have a path value starting with {@code /v2/}.
 *   <li>Classes outside {@code de.dlr.shepard.v2..} annotated with {@link
 *       Path} MUST NOT have a path value starting with {@code /v2/}.
 *   <li>Classes outside {@code de.dlr.shepard.v2..} annotated with {@link
 *       Path} MUST have a path value starting with {@code /shepard/api/}.
 *       This is the post-P4 explicit-prefix enforcement — catches an
 *       accidental {@code @Path("/foo")} that would silently mount at the
 *       application root.
 * </ol>
 *
 * <p>Rule 3 evaluates the path's <em>resolved</em> string: most resources
 * declare {@code @Path(Constants.SHEPARD_API + "/" + Constants.X)}, which
 * ArchUnit's annotation reader presents to us as the constant-folded
 * compile-time string {@code "shepard/api/x"}. JAX-RS prepends a {@code /}
 * at routing time, so for the purposes of this fence the prefix check is
 * shape-equivalent to {@code "shepard/api/"}.
 */
class V2NamespaceTest {

  private static final String V2_PACKAGE = "de.dlr.shepard.v2";
  private static final String V2_PREFIX_WITH_SLASH = "/v2/";
  private static final String V2_PREFIX_NO_SLASH = "v2/";
  private static final String API_PREFIX_WITH_SLASH = "/shepard/api/";
  private static final String API_PREFIX_NO_SLASH = "shepard/api/";

  private static JavaClasses shepardClasses;

  @BeforeAll
  static void importClasses() {
    shepardClasses = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("de.dlr.shepard");
  }

  @Test
  void v2PackageResourcesMustUseV2PathPrefix() {
    // No /v2/ resources land in this slice — L2d (`aidocs/25 §4 Phase 4`)
    // populates the package. allowEmptyShould keeps the rule armed; once
    // L2d ships its first resource the rule starts evaluating real
    // classes without needing a tweak here.
    ArchRuleDefinition.classes()
      .that(are(inV2Package()))
      .and()
      .areAnnotatedWith(Path.class)
      .should(haveJaxRsPathStartingWith(V2_PREFIX_WITH_SLASH, V2_PREFIX_NO_SLASH))
      .allowEmptyShould(true)
      .check(shepardClasses);
  }

  @Test
  void nonV2PackageResourcesMustNotUseV2PathPrefix() {
    ArchRuleDefinition.classes()
      .that()
      .resideOutsideOfPackage(V2_PACKAGE + "..")
      .and()
      .areAnnotatedWith(Path.class)
      .should(notHaveJaxRsPathStartingWith(V2_PREFIX_WITH_SLASH, V2_PREFIX_NO_SLASH))
      .check(shepardClasses);
  }

  @Test
  void nonV2PackageResourcesMustUseShepardApiPathPrefix() {
    ArchRuleDefinition.classes()
      .that()
      .resideOutsideOfPackage(V2_PACKAGE + "..")
      // OpenAPI emission classes mount at `/shepard/doc/...` to mirror
      // the existing smallrye-openapi extension's combined-doc path
      // (`/openapi.json` / `/shepard/doc/openapi.json`). These are
      // documentation routes, not REST resources — they sit beside the
      // `/shepard/api/...` and `/v2/...` shelves, not inside either.
      // Exempt from the API-prefix rule by package.
      .and()
      .resideOutsideOfPackage("de.dlr.shepard.common.openapi")
      .and()
      .areAnnotatedWith(Path.class)
      .should(haveJaxRsPathStartingWith(API_PREFIX_WITH_SLASH, API_PREFIX_NO_SLASH))
      .check(shepardClasses);
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private static DescribedPredicate<JavaClass> inV2Package() {
    return new DescribedPredicate<>("reside in package " + V2_PACKAGE + "..") {
      @Override
      public boolean test(JavaClass javaClass) {
        String pkg = javaClass.getPackageName();
        return pkg.equals(V2_PACKAGE) || pkg.startsWith(V2_PACKAGE + ".");
      }
    };
  }

  private static ArchCondition<JavaClass> haveJaxRsPathStartingWith(String slashed, String unslashed) {
    String description = "have @Path value starting with \"" + slashed + "\"";
    return new ArchCondition<>(description) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String value = readPathValue(javaClass);
        if (value == null) return;
        if (!value.startsWith(slashed) && !value.startsWith(unslashed)) {
          events.add(
            SimpleConditionEvent.violated(
              javaClass,
              javaClass.getName() + " has @Path value \"" + value + "\" which does not start with \"" + slashed + "\""
            )
          );
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notHaveJaxRsPathStartingWith(String slashed, String unslashed) {
    String description = "NOT have @Path value starting with \"" + slashed + "\"";
    return new ArchCondition<>(description) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String value = readPathValue(javaClass);
        if (value == null) return;
        if (value.startsWith(slashed) || value.startsWith(unslashed)) {
          events.add(
            SimpleConditionEvent.violated(
              javaClass,
              javaClass.getName() + " has @Path value \"" + value + "\" which starts with the v2 prefix"
            )
          );
        }
      }
    };
  }

  /** Returns the constant-folded compile-time string of {@code @Path(...)}, or {@code null}. */
  private static String readPathValue(JavaClass javaClass) {
    JavaAnnotation<?> annotation = javaClass.getAnnotationOfType(Path.class.getName());
    Object raw = annotation.get("value").orElse(null);
    return raw == null ? null : raw.toString();
  }
}
