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
    p.setPid("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1");
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("local");
    p.setPublishedBy("alice");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    p.setVersionNumber(1);

    PublicationIO io = PublicationIO.from(p, "https://shepard.example/v2/.well-known/kip/" + p.getPid());

    assertEquals("pub-1", io.appId());
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", io.pid());
    assertEquals(Instant.ofEpochMilli(1_747_000_000_000L), io.mintedAt());
    assertEquals("local", io.minterId());
    assertEquals("alice", io.publishedBy());
    assertEquals("data-objects", io.entityKind());
    assertEquals("01HF-A", io.entityAppId());
    assertEquals(Integer.valueOf(1), io.versionNumber());
    assertEquals(
      "https://shepard.example/v2/.well-known/kip/shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1",
      io.resolverUrl()
    );
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
    p.setMinterId("local");
    p.setVersionNumber(1);
    PublicationIO io = PublicationIO.from(p, "https://x");
    assertNull(io.mintedAt());
  }

  @Test
  void fromNullVersionNumberDefaultsToOne() {
    // KIP1h V31 backfill sets versionNumber=1 on every pre-existing row,
    // but a row that somehow slipped past the migration (rare) is
    // normalised at the wire boundary so the field is never absent.
    Publication p = new Publication();
    p.setAppId("pub-legacy");
    p.setPid("mock:shepard:data-objects:01HF-A:1700000000000");
    p.setMintedAt(1_700_000_000_000L);
    p.setMinterId("mock");
    p.setVersionNumber(null);
    PublicationIO io = PublicationIO.from(p, "https://x");
    assertEquals(Integer.valueOf(1), io.versionNumber());
  }

  @Test
  void fromNegativeVersionNumberClampedToOne() {
    Publication p = new Publication();
    p.setAppId("pub-clamp");
    p.setPid("pid");
    p.setMintedAt(0L);
    p.setMinterId("local");
    p.setVersionNumber(-7);
    PublicationIO io = PublicationIO.from(p, "https://x");
    assertEquals(Integer.valueOf(1), io.versionNumber());
  }

  @Test
  void fromHigherVersionNumberPreserved() {
    Publication p = new Publication();
    p.setAppId("pub-v3");
    p.setPid("shepard:lab-a:data-objects:01HF-A:v3");
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("local");
    p.setVersionNumber(3);
    PublicationIO io = PublicationIO.from(p, "https://x");
    assertEquals(Integer.valueOf(3), io.versionNumber());
  }
}
