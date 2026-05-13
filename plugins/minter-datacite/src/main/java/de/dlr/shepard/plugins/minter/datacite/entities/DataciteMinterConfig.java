package de.dlr.shepard.plugins.minter.datacite.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * KIP1d — runtime-mutable DataCite-minter config singleton.
 *
 * <p>Single-instance Neo4j node mirroring the A3b / N1c2 / UH1a
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :DataciteMinterConfig} node is seeded on
 * first startup from the {@code shepard.minters.datacite.*}
 * install-time defaults; subsequent runtime PATCHes against
 * {@code /v2/admin/minters/datacite/config} mutate this node in
 * place.
 *
 * <p>Field set is the runtime-mutable subset of the feature's knobs:
 *
 * <ul>
 *   <li>{@link #enabled} — master toggle. When {@code false},
 *       {@code DataciteMinter.mint()} throws {@code MinterException}
 *       ("DataCite minter disabled"). Defaults to {@code false} so a
 *       fresh install never accidentally hits DataCite without an
 *       operator's review.</li>
 *   <li>{@link #apiBaseUrl} — base URL of the DataCite REST API.
 *       Defaults to {@code https://api.test.datacite.org} (the safe
 *       Fabrica test environment). Production is
 *       {@code https://api.datacite.org}.</li>
 *   <li>{@link #handlePrefix} — DataCite-allocated DOI prefix
 *       (e.g. {@code 10.5072} for Fabrica test, member-specific in
 *       production). Required for mint to succeed.</li>
 *   <li>{@link #repositoryId} — DataCite Member account login (the
 *       repository identifier embedded in the API's HTTP Basic auth
 *       user portion).</li>
 *   <li>{@link #passwordCipher} — encrypted DataCite Member password.
 *       Stored reversibly because the API requires Basic auth at
 *       mint-time; the encryption is operator-managed-secret level —
 *       AES-GCM keyed off {@code shepard.instance.id}. NEVER returned
 *       through the admin REST.</li>
 *   <li>{@link #passwordHash} — SHA-256 hex of the password. Surfaced
 *       only via its first-8-hex fingerprint on the admin GET so an
 *       operator can confirm "yes that's the password I set".</li>
 *   <li>{@link #publisher} — publisher name embedded in every minted
 *       DOI's metadata (DataCite's {@code publisher} attribute).</li>
 *   <li>{@link #landingPageBase} — base URL prepended to
 *       {@code /<kind>/<appId>} when building the DOI's
 *       {@code url} attribute (DataCite resolves the DOI to this).</li>
 *   <li>{@link #defaultState} — DOI state on mint: {@code draft}
 *       (default — DOI not findable, can be deleted),
 *       {@code registered} (committed, resolvable, not findable),
 *       or {@code findable} (fully discoverable). Operators can
 *       promote draft DOIs via the DataCite Fabrica UI.</li>
 *   <li>{@link #updatedAt}, {@link #updatedBy} — audit shape.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field values win; deploy-time
 * {@code shepard.minters.datacite.*} properties are install defaults
 * that seed the singleton on first start. Password is never a
 * deploy-time key — must always be set via the
 * {@code POST /v2/admin/minters/datacite/credential} endpoint
 * (security posture; gitleaks would otherwise flag operator
 * application.properties as a credential leak).
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DataciteMinterConfig implements HasAppId {

  /** Default state for newly-minted DOIs. */
  public static final String STATE_DRAFT = "draft";

  /** Registered but not findable. */
  public static final String STATE_REGISTERED = "registered";

  /** Fully findable in DataCite Commons. */
  public static final String STATE_FINDABLE = "findable";

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO.createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * Master runtime toggle. When {@code false}, the
   * {@code DataciteMinter.mint()} call throws {@code MinterException}
   * before any HTTP call to DataCite is attempted. Default
   * {@code false}: an operator must explicitly enable after
   * configuring credentials + prefix.
   */
  @Property("enabled")
  private boolean enabled = false;

  /**
   * DataCite REST API base URL. Default
   * {@code https://api.test.datacite.org} (Fabrica test environment);
   * production is {@code https://api.datacite.org}. Switching is a
   * runtime knob so a deployment can be re-pointed at production
   * after a Fabrica trial without redeploy.
   */
  @Property("apiBaseUrl")
  private String apiBaseUrl = "https://api.test.datacite.org";

  /**
   * DataCite-allocated DOI prefix — e.g. {@code 10.5072} for Fabrica
   * test or a member-specific prefix in production. Required;
   * {@code DataciteMinter.mint()} throws when blank.
   */
  @Property("handlePrefix")
  private String handlePrefix;

  /**
   * DataCite Member account login. Used as the HTTP Basic auth
   * user; required.
   */
  @Property("repositoryId")
  private String repositoryId;

  /**
   * Encrypted DataCite Member password — AES-GCM keyed off the
   * instance id (best-effort scheme; the API requires reversible
   * storage because it sends plaintext over Basic auth). NEVER
   * returned through admin REST; only the {@link #passwordHash}
   * fingerprint is surfaced.
   */
  @Property("passwordCipher")
  private String passwordCipher;

  /**
   * SHA-256 hex of the plaintext password. Surfaced only via its
   * first-8-hex fingerprint on {@code GET .../config}. Recomputed
   * on each credential-set call; cleared when the credential is
   * deleted.
   */
  @Property("passwordHash")
  private String passwordHash;

  /**
   * Publisher name embedded in every minted DOI's metadata
   * (DataCite's {@code publisher} attribute — DataCite requires
   * a non-empty value). Operators typically set this to the host
   * institution's official name ("DLR e.V." / "Helmholtz-Zentrum
   * Berlin" / ...).
   */
  @Property("publisher")
  private String publisher;

  /**
   * Base URL prepended to {@code /<kind>/<appId>} when building
   * the DOI's {@code url} attribute. E.g.
   * {@code https://shepard.example.dlr.de/v2}. Required for mint
   * to produce a working resolution.
   */
  @Property("landingPageBase")
  private String landingPageBase;

  /**
   * Default DOI state on mint — one of {@link #STATE_DRAFT},
   * {@link #STATE_REGISTERED}, {@link #STATE_FINDABLE}. Defaults to
   * {@code draft} so a misconfigured mint is a recoverable DataCite-
   * side delete rather than a permanent registered DOI.
   */
  @Property("defaultState")
  private String defaultState = STATE_DRAFT;

  /** Epoch millis of the most recent config mutation. */
  @Property("updatedAt")
  private Long updatedAt;

  /**
   * Username of the operator who last patched / set credentials.
   * Surfaced on the admin GET. Not used for authz — just an audit
   * convenience read.
   */
  @Property("updatedBy")
  private String updatedBy;

  /** For testing purposes only. */
  public DataciteMinterConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof DataciteMinterConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
