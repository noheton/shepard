package de.dlr.shepard.v2.publish.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.publish.entities.Publication;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublicationIOTest {

  @Test
  void fromProjectsAllFields() {
    Publication p = new Publication();
    p.setAppId("pub-1");
    p.setPid("mock:shepard:data-objects:01HF-A:1747000000000");
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("mock");
    p.setPublishedBy("alice");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");

    PublicationIO io = PublicationIO.from(p, "https://shepard.example/v2/.well-known/kip/" + p.getPid());

    assertEquals("pub-1", io.appId());
    assertEquals("mock:shepard:data-objects:01HF-A:1747000000000", io.pid());
    assertEquals(Instant.ofEpochMilli(1_747_000_000_000L), io.mintedAt());
    assertEquals("mock", io.minterId());
    assertEquals("alice", io.publishedBy());
    assertEquals("data-objects", io.entityKind());
    assertEquals("01HF-A", io.entityAppId());
    assertEquals("https://shepard.example/v2/.well-known/kip/mock:shepard:data-objects:01HF-A:1747000000000", io.resolverUrl());
  }

  @Test
  void fromNullReturnsNull() {
    assertNull(PublicationIO.from(null, "irrelevant"));
  }

  @Test
  void fromNullMintedAtSurfacesAsNullInstant() {
    Publication p = new Publication();
    p.setAppId("pub-1");
    p.setPid("pid");
    p.setMintedAt(null);
    p.setMinterId("mock");
    PublicationIO io = PublicationIO.from(p, "https://x");
    assertNull(io.mintedAt());
  }
}
