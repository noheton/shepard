package de.dlr.shepard.publish.minter;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;

/**
 * KIP1a default minter. Generates synthetic Handle-shaped PIDs
 * locally — useful for dev / casual installs with no external
 * dependency.
 *
 * <p>PID format: {@code mock:shepard:<entity-kind>:<appId>:<epoch-millis>}.
 * The {@code mock:} prefix makes it visually unambiguous that this
 * is not a resolvable Handle / DOI; the epoch-millis suffix means a
 * forced re-mint (via {@code POST /publish?force=true}) produces a
 * distinct PID every call without needing any external counter.
 *
 * <p>Activated when {@code shepard.publish.minter=mock} (the
 * default). The {@link LookupIfProperty} annotation keeps the bean
 * out of CDI when an operator switches to ePIC / DataCite — that
 * way a plugin can register a {@code Minter id()="mock"} of its own
 * (for tests / fallback) without ambiguous-resolution warnings.
 *
 * <p>Per the KIP1a plugin-first call (see {@code CLAUDE.md}
 * "plugin-first" + {@code aidocs/66 §5}), this minter and the SPI
 * itself stay in core; ePIC and DataCite adapters ship as separate
 * plugin modules (KIP1c / KIP1d).
 */
@ApplicationScoped
@LookupIfProperty(name = "shepard.publish.minter", stringValue = "mock", lookupIfMissing = true)
public class MockMinter implements Minter {

  public static final String ID = "mock";

  private final Clock clock;

  public MockMinter() {
    this.clock = Clock.systemUTC();
  }

  /** Visible for testing. */
  MockMinter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public MintResult mint(MintRequest req) {
    Instant now = clock.instant();
    String pid = "mock:shepard:" + req.entityKind() + ":" + req.appId() + ":" + now.toEpochMilli();
    return new MintResult(pid, now, ID);
  }
}
