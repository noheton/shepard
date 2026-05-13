# shepard-v2-client-java (Kiota-generated)

Maven artifact: `de.dlr.shepard:shepard-v2-client-java`

Kiota-generated Java client for shepard's `/v2/` API surface, produced from
the per-shelf OpenAPI document `/shepard/doc/openapi/v2.json` (P4c, shipped).

This is the **CG1a** new-baseline client per
[ADR-0022](../../aidocs/63-architecture-decision-log.md#adr-0022). The legacy
`/shepard/api/...` Java client lives under `../../clients/java/` (CG1b).

## Install (Maven)

```xml
<dependency>
  <groupId>de.dlr.shepard</groupId>
  <artifactId>shepard-v2-client-java</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

(Published to Maven Central / GHCR by CG1c — until then, build locally.)

## Quick start

```java
import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import de.dlr.shepard.v2.clients.ShepardV2Client;

var auth = new AnonymousAuthenticationProvider();   // or Bearer / X-API-Key
var adapter = new OkHttpRequestAdapter(auth);
adapter.setBaseUrl("https://shepard.example.com");
var client = new ShepardV2Client(adapter);

// Fluent path-builder mirrors the URL:
//   /v2/dataobjects/{appId}  ->  client.v2().dataobjects().byAppId(appId).get()
```

## Regenerating

```bash
# Boot shepard locally so /shepard/doc/openapi/v2.json is reachable, then:
make -C .. generate-java
```

Pinned Kiota version: see `clients-v2/Makefile` (`KIOTA_VERSION`).

## Layout

- `src/main/java/de/dlr/shepard/v2/clients/` — generated sources (machine-emitted)
- `pom.xml` — Maven manifest (hand-maintained)
- `README.md` — this file
