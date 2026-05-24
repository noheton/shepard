package de.dlr.shepard.common.healthz;

import ac.simons.neo4j.migrations.core.MigrationChain;
import ac.simons.neo4j.migrations.core.MigrationChain.ChainBuilderMode;
import ac.simons.neo4j.migrations.core.MigrationState;
import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Pure helper that asks the migrations library whether the applied
 * Neo4j migration chain matches the classpath. Stateless and free of
 * Quarkus / CDI dependencies.
 *
 * <p>The library's {@link Migrations#validate()} is the authority on
 * chain integrity. It uses the exact same checksum logic that
 * {@code Migrations.apply()} relies on to decide which migrations are
 * already in the database — so the readiness signal and the startup
 * gate share their definition of "chain matches". We deliberately do
 * <em>not</em> reimplement
 * {@code DefaultCypherResource.computeChecksum} here; that would risk
 * drift the day the library's algorithm changes (e.g. the
 * Flyway-compatibility flag in v3 of the library introduced a second
 * variant — we'd silently fall behind).
 *
 * <p>{@link Migrations#info(ChainBuilderMode)} with
 * {@code ChainBuilderMode.COMPARE} gives per-element states
 * (PENDING/APPLIED) which we surface in the readiness response so a
 * runbook reader sees exactly which versions are missing.
 *
 * <p>The class accepts a {@code Function<Migrations, ChainSnapshot>}
 * extractor in its package-private constructor: the production path
 * uses {@link #defaultExtractor()} and the test path substitutes a
 * fake — because the library's {@link MigrationChain} is a
 * <em>sealed</em> interface, neither Mockito nor anonymous inner
 * classes can stand in for it. Funneling the only two pieces of data
 * we actually need through a tiny value object
 * ({@link ChainSnapshot}) keeps the inspector trivially testable
 * without sacrificing the live wiring's grip on the library.
 */
final class MigrationChainInspector {

  /**
   * The minimal slice of {@link MigrationChain} we care about. Kept
   * package-private so tests can build one directly; the library's
   * sealed types never need to be reproduced.
   */
  record ChainSnapshot(List<PendingEntry> elements) {
    record PendingEntry(String version, MigrationState state) {}
  }

  private final LongSupplier clock;
  private final Function<Migrations, ChainSnapshot> snapshotFn;

  MigrationChainInspector() {
    this(System::currentTimeMillis, defaultExtractor());
  }

  MigrationChainInspector(LongSupplier clock) {
    this(clock, defaultExtractor());
  }

  MigrationChainInspector(LongSupplier clock, Function<Migrations, ChainSnapshot> snapshotFn) {
    this.clock = clock;
    this.snapshotFn = snapshotFn;
  }

  /**
   * Inspect the chain against the given live {@link Migrations}
   * instance. Any throwable is captured into a
   * {@link MigrationChainStatus#checkFailed} value so a transient
   * outage flips readiness DOWN rather than crashing the probe loop.
   */
  MigrationChainStatus inspect(Migrations migrations) {
    long now = clock.getAsLong();
    if (migrations == null) {
      return MigrationChainStatus.checkFailed("Migrations instance not initialised", now);
    }
    try {
      ValidationResult validation = migrations.validate();
      ChainSnapshot snapshot = snapshotFn.apply(migrations);
      List<String> pending = new ArrayList<>();
      for (ChainSnapshot.PendingEntry el : snapshot.elements()) {
        if (el.state() == MigrationState.PENDING) {
          pending.add(el.version());
        }
      }
      List<String> warnings = new ArrayList<>(validation.getWarnings());
      String outcome = validation.getOutcome().name();
      boolean ok = validation.isValid() && pending.isEmpty();
      return new MigrationChainStatus(
        ok,
        outcome,
        pending,
        warnings,
        ok ? null : describeFailure(outcome, pending, warnings),
        now
      );
    } catch (Exception e) {
      return MigrationChainStatus.checkFailed(
        e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "<no message>" : e.getMessage()),
        now
      );
    }
  }

  /** The live extractor — talks to the library's sealed types. */
  static Function<Migrations, ChainSnapshot> defaultExtractor() {
    return migrations -> {
      MigrationChain chain = migrations.info(ChainBuilderMode.COMPARE);
      List<ChainSnapshot.PendingEntry> els = new ArrayList<>();
      for (MigrationChain.Element el : chain.getElements()) {
        els.add(new ChainSnapshot.PendingEntry(el.getVersion(), el.getState()));
      }
      return new ChainSnapshot(els);
    };
  }

  private static String describeFailure(String outcome, List<String> pending, List<String> warnings) {
    StringBuilder sb = new StringBuilder();
    sb.append("Neo4j migration chain integrity violation (outcome=").append(outcome).append(")");
    if (!pending.isEmpty()) {
      sb.append("; pending versions: ").append(String.join(", ", pending));
    }
    if (!warnings.isEmpty()) {
      sb.append("; warnings: ").append(String.join(" | ", warnings));
    }
    sb.append(". See docs/admin/runbooks/migration-chain-integrity.md for remediation.");
    return sb.toString();
  }
}
