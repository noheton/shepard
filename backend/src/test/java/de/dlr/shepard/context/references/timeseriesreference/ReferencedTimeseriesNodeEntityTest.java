package de.dlr.shepard.context.references.timeseriesreference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * APPID-CHILD-MINT-REGRESSION — proves the {@code :Timeseries} node
 * ({@link ReferencedTimeseriesNodeEntity}) carries a v7 appId the moment it is
 * constructed for a WRITE, since it is only ever persisted as a cascaded child
 * of a {@code :TimeseriesReference} (where {@code GenericDAO.createOrUpdate}
 * never mints it). The no-arg constructor (OGM hydration path) must NOT mint,
 * so a loaded row's DB appId is never clobbered.
 */
class ReferencedTimeseriesNodeEntityTest {

  @Test
  void fiveArgConstructor_mintsV7AppId() {
    var node = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "sym", "field");
    assertNotNull(node.getAppId(), "value ctor must mint a non-null appId");
    assertEquals(7, UUID.fromString(node.getAppId()).version(), "appId must be UUID v7");
  }

  @Test
  void timeseriesConstructor_mintsV7AppId() {
    var ts = new Timeseries("meas", "dev", "loc", "sym", "field");
    var node = new ReferencedTimeseriesNodeEntity(ts);
    assertNotNull(node.getAppId(), "Timeseries-ctor must mint a non-null appId");
    assertEquals(7, UUID.fromString(node.getAppId()).version(), "appId must be UUID v7");
  }

  @Test
  void noArgConstructor_doesNotMint_soOgmHydrationIsSafe() {
    // OGM instantiates via the no-arg ctor then sets fields (incl. the persisted
    // appId) by reflection. If the no-arg ctor minted, a loaded row's appId could
    // be transiently clobbered — so it must stay null here.
    var node = new ReferencedTimeseriesNodeEntity();
    assertNull(node.getAppId(), "no-arg ctor must NOT mint (OGM hydration safety)");
  }

  @Test
  void distinctInstances_getDistinctAppIds() {
    var a = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "sym", "field");
    var b = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "sym", "field");
    assertNotEquals(a.getAppId(), b.getAppId(), "each minted appId is unique");
  }

  @Test
  void equalsAndHashCode_ignoreAppId_soFiveTupleIdentityHolds() {
    // Two nodes with the same 5-tuple but different (minted) appIds must remain
    // equal — the value identity is the 5-tuple, not the surrogate appId. This
    // is what keeps service dedup + Mockito arg-matching working post-mint.
    var a = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "sym", "field");
    var b = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "sym", "field");
    assertNotEquals(a.getAppId(), b.getAppId());
    assertEquals(a, b, "equals must ignore appId (5-tuple identity)");
    assertEquals(a.hashCode(), b.hashCode(), "hashCode must ignore appId");
  }
}
