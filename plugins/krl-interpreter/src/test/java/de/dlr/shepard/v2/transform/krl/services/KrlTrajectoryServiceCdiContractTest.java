package de.dlr.shepard.v2.transform.krl.services;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.Test;

/**
 * RESEED-FIND-KRL-CDI — regression guard for the live-broken KRL materialize.
 *
 * <p>{@link KrlTrajectoryService} is reached ONLY through the programmatic
 * {@code CDI.current().select(KrlTrajectoryService.class)} lookup in
 * {@link de.dlr.shepard.v2.transform.krl.KrlTrajectoryTransformExecutor}; it is
 * never statically {@code @Inject}ed anywhere and implements no SPI interface.
 * Quarkus/Arc's default dead-bean elimination therefore removed it from the live
 * CDI container, so the lazy lookup threw {@code UnsatisfiedResolutionException}
 * ("No bean found for ... KrlTrajectoryService") at materialize time — surfacing
 * as a {@code transform.internal-error} 422 BEFORE the sidecar was contacted.
 *
 * <p>The fix is {@code @Unremovable} on the bean (keeps it, and transitively its
 * injected collaborators, in the container). A full {@code @QuarkusTest} would
 * boot the whole backend app — disproportionate for a single-bean retention
 * contract — so this test asserts the two annotations whose presence is exactly
 * the property that makes the programmatic lookup succeed in the live image. If a
 * future refactor drops either annotation, the materialize regression returns and
 * this test fails first.
 */
class KrlTrajectoryServiceCdiContractTest {

  @Test
  void isApplicationScopedSoTheProgrammaticLookupResolvesAStableInstance() {
    assertThat(KrlTrajectoryService.class.isAnnotationPresent(ApplicationScoped.class))
      .as("KrlTrajectoryService must be a CDI bean for CDI.current().select(...) to resolve it")
      .isTrue();
  }

  @Test
  void isUnremovableSoArcKeepsItDespiteNoStaticInjectionPoint() {
    assertThat(KrlTrajectoryService.class.isAnnotationPresent(Unremovable.class))
      .as(
        "KrlTrajectoryService is only reached via CDI.current() (never statically @Inject-ed); "
          + "without @Unremovable, Arc dead-bean elimination removes it and the executor's "
          + "lazy lookup throws 'No bean found' (RESEED-FIND-KRL-CDI)")
      .isTrue();
  }
}
