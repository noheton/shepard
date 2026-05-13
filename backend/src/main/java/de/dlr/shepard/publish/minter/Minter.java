package de.dlr.shepard.publish.minter;

/**
 * KIP1a SPI seam — a {@code Minter} mints persistent identifiers
 * (Handles, DOIs, or mock strings) for shepard entities ahead of
 * publishing them via the HMC Kernel Information Profile flow.
 *
 * <p>Designed in {@code aidocs/66 §5}. Shape mirrors the
 * {@link de.dlr.shepard.context.references.git.adapters.GitAdapter}
 * registry idiom (G1b / G1d): an in-tree interface every plugin
 * compiles against, with the SPI itself living in core and the
 * adapters arriving as separate Maven modules later.
 *
 * <p>KIP1a ships exactly one implementation,
 * {@link MockMinter} — every other adapter
 * (ePIC for KIP1c, DataCite for KIP1d) lands as a
 * {@code shepard-plugin-minter-{epic,datacite}} module per the
 * plugin-first rule in {@code CLAUDE.md} §"Always: think plugin-first".
 *
 * <p>Lifecycle: discovered by CDI under {@code @ApplicationScoped} +
 * {@code @Default}; resolved by {@link MinterRegistry} via the
 * deploy-time {@code shepard.publish.minter} key. Switching the
 * active minter is a re-bootstrap decision (PID provider change is
 * not safely runtime-flippable — the {@code CLAUDE.md}
 * "cluster identity / topology" exception applies), so KIP1a keeps
 * this as a deploy-time-only knob. Per-minter admin config (e.g.
 * ePIC handle prefix) lands with the relevant plugin in KIP1c/d
 * and uses the runtime {@code :*Config} pattern then.
 */
public interface Minter {
  /**
   * Stable identifier for this minter — e.g. {@code "mock"},
   * {@code "epic"}, {@code "datacite"}. Must match the value an
   * operator would put in {@code shepard.publish.minter=…} to
   * activate this adapter.
   *
   * <p>If two beans return the same {@code id()} the registry logs
   * a WARN and picks the first one CDI hands it.
   */
  String id();

  /**
   * Whether this minter is currently usable (credentials present,
   * upstream reachable, feature toggle on). KIP1a's
   * {@code MockMinter} always returns {@code true}; KIP1c's ePIC
   * minter will return {@code false} when its handle prefix /
   * credentials aren't configured.
   *
   * <p>The registry refuses to activate a {@code !isEnabled()}
   * minter — operators see a clean RFC 7807 error rather than a
   * cryptic upstream 5xx mid-publish.
   */
  boolean isEnabled();

  /**
   * Mint a PID for the entity described by {@code req}.
   *
   * @throws MinterException with operator-readable message when the
   *                        mint cannot complete (network failure,
   *                        upstream rejection, credential expiry).
   *                        The {@code MockMinter} never throws.
   */
  MintResult mint(MintRequest req);
}
