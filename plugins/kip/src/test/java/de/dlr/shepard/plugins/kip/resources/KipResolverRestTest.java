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
    p.setPid("mock:shepard:data-objects:01HF-A:1747000000000");
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("mock");
    p.setPublishedBy("alice");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    return p;
  }

  @Test
  void happyPathReturnsKipRecord() {
    when(publicationDAO.findByPid("mock:shepard:data-objects:01HF-A:1747000000000"))
      .thenReturn(Optional.of(publication()));

    Response r = rest.resolve("mock:shepard:data-objects:01HF-A:1747000000000", uriInfo);
    assertEquals(200, r.getStatus());

    KipRecordIO record = (KipRecordIO) r.getEntity();
    assertEquals(KipRecordIO.JSONLD_CONTEXT, record.context());
    assertEquals("mock:shepard:data-objects:01HF-A:1747000000000", record.id());
    KipRecordIO.KernelInformationProfile body = record.kernelInformationProfile();
    assertNotNull(body);
    assertEquals("mock:shepard:data-objects:01HF-A:1747000000000", body.id());
    assertEquals("https://shepard.example.dlr.de/v2/data-objects/01HF-A", body.landingPage());
    assertEquals("http://shepard.dlr.de/types/dlr:DataObject", body.digitalObjectType());
    assertEquals("alice", body.rightsHolder());
    assertEquals(java.time.Instant.ofEpochMilli(1_747_000_000_000L).toString(), body.dateCreated());
    assertEquals(body.dateCreated(), body.dateModified());
    assertNull(body.license());
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
    p.setPid("mock:shepard:collections:01HF-C:1747000000000");
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
    p.setPid("mock:shepard:future-kind:01HF-FK:1747000000000");
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
