package de.dlr.shepard.plugins.kip.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.kip.io.KipRecordIO;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KipResolverRestTest {

  private KipResolverRest rest;
  private PublicationDAO publicationDAO;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    rest = new KipResolverRest();
    publicationDAO = mock(PublicationDAO.class);
    rest.publicationDAO = publicationDAO;
    rest.kindRegistry = new PublishableKindRegistry();
    uriInfo = mock(UriInfo.class);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("https://shepard.example.dlr.de/shepard/api/"));
  }

  private Publication publication() {
    Publication p = new Publication();
    p.setAppId("pub-1");
    p.setPid("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1");
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("local");
    p.setPublishedBy("alice");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    p.setVersionNumber(1);
    return p;
  }

  @Test
  void happyPathReturnsKipRecord() {
    Publication p = publication();
    when(publicationDAO.findByPid(p.getPid())).thenReturn(Optional.of(p));

    Response r = rest.resolve(p.getPid(), uriInfo);
    assertEquals(200, r.getStatus());

    KipRecordIO record = (KipRecordIO) r.getEntity();
    assertEquals(KipRecordIO.JSONLD_CONTEXT, record.context());
    assertEquals(p.getPid(), record.id());
    KipRecordIO.KernelInformationProfile body = record.kernelInformationProfile();
    assertNotNull(body);
    assertEquals(p.getPid(), body.id());
    assertEquals("https://shepard.example.dlr.de/v2/data-objects/01HF-A", body.landingPage());
    assertEquals("http://shepard.dlr.de/types/dlr:DataObject", body.digitalObjectType());
    assertEquals("alice", body.rightsHolder());
    assertEquals(java.time.Instant.ofEpochMilli(1_747_000_000_000L).toString(), body.dateCreated());
    assertEquals(body.dateCreated(), body.dateModified());
    assertNull(body.license());
    // KIP1h: digitalObjectVersion segment surfaces as v1 for a fresh
    // publish.
    assertEquals("v1", body.digitalObjectVersion());
  }

  @Test
  void higherVersionNumberSurfacesAsDigitalObjectVersion() {
    Publication p = publication();
    p.setPid("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v7");
    p.setVersionNumber(7);
    when(publicationDAO.findByPid(p.getPid())).thenReturn(Optional.of(p));

    Response r = rest.resolve(p.getPid(), uriInfo);
    KipRecordIO record = (KipRecordIO) r.getEntity();
    assertEquals("v7", record.kernelInformationProfile().digitalObjectVersion());
  }

  @Test
  void legacyMockPidWithNullVersionDefaultsToV1() {
    // KIP1h: a row that escaped the V31 backfill (rare; defensive
    // behaviour) still surfaces a cleanly-formed digitalObjectVersion
    // rather than null. The resolver's default-to-1 logic catches it.
    Publication legacy = new Publication();
    legacy.setAppId("pub-legacy");
    legacy.setPid("mock:shepard:data-objects:01HF-A:1700000000000");
    legacy.setMintedAt(1_700_000_000_000L);
    legacy.setMinterId("mock");
    legacy.setEntityKind("data-objects");
    legacy.setEntityAppId("01HF-A");
    legacy.setPublishedBy("alice");
    legacy.setVersionNumber(null);
    when(publicationDAO.findByPid(legacy.getPid())).thenReturn(Optional.of(legacy));

    Response r = rest.resolve(legacy.getPid(), uriInfo);
    assertEquals(200, r.getStatus());
    KipRecordIO record = (KipRecordIO) r.getEntity();
    assertEquals("v1", record.kernelInformationProfile().digitalObjectVersion());
    // The legacy PID format still resolves cleanly.
    assertEquals(legacy.getPid(), record.id());
  }

  @Test
  void unknownPidSuffixReturns404ProblemJson() {
    when(publicationDAO.findByPid("nope")).thenReturn(Optional.empty());
    Response r = rest.resolve("nope", uriInfo);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("kip.pid.not-found"));
  }

  @Test
  void blankSuffixReturns404() {
    Response r = rest.resolve("", uriInfo);
    assertEquals(404, r.getStatus());
    Response rNull = rest.resolve(null, uriInfo);
    assertEquals(404, rNull.getStatus());
  }

  @Test
  void collectionKindResolvesLandingPageAndType() {
    Publication p = publication();
    p.setEntityKind("collections");
    p.setEntityAppId("01HF-C");
    p.setPid("shepard:dlr.de/shepard-prod:collections:01HF-C:v1");
    when(publicationDAO.findByPid(p.getPid())).thenReturn(Optional.of(p));

    Response r = rest.resolve(p.getPid(), uriInfo);
    KipRecordIO record = (KipRecordIO) r.getEntity();
    assertEquals("https://shepard.example.dlr.de/v2/collections/01HF-C", record.kernelInformationProfile().landingPage());
    assertEquals("http://shepard.dlr.de/types/dlr:Collection", record.kernelInformationProfile().digitalObjectType());
  }

  @Test
  void unknownEntityKindFallsBackToDataObjectsAndUnknownType() {
    // Defensive: if a future Publication row has an entityKind segment
    // not registered in this build, the resolver still returns 200
    // with a generic IRI rather than 5xx.
    Publication p = publication();
    p.setEntityKind("future-kind");
    p.setEntityAppId("01HF-FK");
    p.setPid("shepard:dlr.de/shepard-prod:future-kind:01HF-FK:v1");
    when(publicationDAO.findByPid(p.getPid())).thenReturn(Optional.of(p));

    Response r = rest.resolve(p.getPid(), uriInfo);
    KipRecordIO record = (KipRecordIO) r.getEntity();
    assertEquals(
      "https://shepard.example.dlr.de/v2/future-kind/01HF-FK",
      record.kernelInformationProfile().landingPage()
    );
    assertEquals("http://shepard.dlr.de/types/dlr:Unknown", record.kernelInformationProfile().digitalObjectType());
  }

  @Test
  void nullEntityKindFallsBackToDataObjectsLandingAndUnknownType() {
    Publication p = publication();
    p.setEntityKind(null);
    when(publicationDAO.findByPid(p.getPid())).thenReturn(Optional.of(p));
    Response r = rest.resolve(p.getPid(), uriInfo);
    KipRecordIO record = (KipRecordIO) r.getEntity();
    assertTrue(record.kernelInformationProfile().landingPage().contains("/v2/data-objects/"));
    assertEquals("http://shepard.dlr.de/types/dlr:Unknown", record.kernelInformationProfile().digitalObjectType());
  }
}
