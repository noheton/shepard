package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Instance-form tests for {@link TargetEntityResolver} — exercise the
 * numeric-id → appId lookup path that closes PROV-V1-NUMERIC-LOOKUP.
 *
 * <p>The static surface lives in {@link TargetEntityResolverTest}; the parser
 * internals in {@link PathTargetParserTest}.
 */
class TargetEntityResolverInstanceTest {

  private static final String UUID_D = "018f9c5a-7e26-7000-a000-000000000020";

  @Mock
  EntityAppIdLookup lookup;

  @InjectMocks
  TargetEntityResolver resolver;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void v1NumericCollectionResolves() {
    String collAppId = "018f9c5a-7e26-7000-a000-0000000000c1";
    when(lookup.findAppIdByNumericId("Collection", 42L)).thenReturn(Optional.of(collAppId));
    Optional<TargetEntityResolver.TargetRef> r = resolver.resolve("/shepard/api/collections/42");
    assertTrue(r.isPresent());
    assertEquals("Collection", r.get().kind());
    assertEquals(collAppId, r.get().appId());
  }

  @Test
  void v1DeepNumericDataObjectResolves() {
    // POST /shepard/api/collections/42/dataObjects/45/timeseriesReferences →
    // attribute to the DataObject (id 45), NOT the Collection (id 42).
    String doAppId = "018f9c5a-7e26-7000-a000-0000000000d1";
    when(lookup.findAppIdByNumericId("DataObject", 45L)).thenReturn(Optional.of(doAppId));
    Optional<TargetEntityResolver.TargetRef> r = resolver.resolve(
      "/shepard/api/collections/42/dataObjects/45/timeseriesReferences"
    );
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
    assertEquals(doAppId, r.get().appId());
  }

  @Test
  void v1NumericMissReturnsEmpty() {
    // Entity may have been deleted; DAO lookup misses. Resolver yields empty;
    // capture filter then writes Activity with targetKind=null, targetAppId=null.
    when(lookup.findAppIdByNumericId("Collection", 99999L)).thenReturn(Optional.empty());
    Optional<TargetEntityResolver.TargetRef> r = resolver.resolve("/shepard/api/collections/99999");
    assertTrue(r.isEmpty());
  }

  @Test
  void v2UuidPathBypassesLookup() {
    // UUID ids in v2 paths don't need a DAO lookup. The resolver must not
    // call the lookup helper for them (verified by leaving the mock with no
    // stubs — any call would throw a Mockito strict-stubbing failure on
    // verify if we were verifying, but the simpler assertion is that the
    // result lands correctly with no stub registered).
    Optional<TargetEntityResolver.TargetRef> r = resolver.resolve("/v2/data-objects/" + UUID_D);
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
    assertEquals(UUID_D, r.get().appId());
  }

  @Test
  void unknownPathReturnsEmptyWithoutLookup() {
    Optional<TargetEntityResolver.TargetRef> r = resolver.resolve("/v2/things/anything");
    assertTrue(r.isEmpty());
  }
}
