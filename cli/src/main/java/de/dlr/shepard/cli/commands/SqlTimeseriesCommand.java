package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * FTOGGLE-CLI-PARITY-1 — container for
 * {@code shepard-admin sql-timeseries <verb>} sub-commands.
 *
 * <ul>
 *   <li>{@code status} — read-only view of the current
 *       {@code :SqlTimeseriesConfig} singleton via
 *       {@code GET /v2/admin/sql-timeseries/config}.</li>
 *   <li>{@code enable} / {@code disable} — flip the runtime
 *       {@code enabled} toggle (requires FTOGGLE-SQL-ENABLE-1
 *       to be active on the target server).</li>
 *   <li>{@code set-max-rows} — update the per-query row cap at runtime.</li>
 *   <li>{@code set-max-duration} — update the per-query time window at runtime.</li>
 * </ul>
 *
 * <p>Precedence of effective values: runtime singleton (this config)
 * &gt; deploy-time default ({@code shepard.timeseries.sql.*} in
 * application.properties).
 *
 * <p>See {@code aidocs/16-dispatcher-backlog.md} FTOGGLE-CLI-PARITY-1 row.
 */
@Command(
  name = "sql-timeseries",
  mixinStandardHelpOptions = true,
  description = "Manage the instance-wide SQL timeseries configuration.",
  subcommands = {
    SqlTimeseriesStatusCommand.class,
    SqlTimeseriesEnableCommand.class,
    SqlTimeseriesDisableCommand.class,
    SqlTimeseriesSetMaxRowsCommand.class,
    SqlTimeseriesSetMaxDurationCommand.class,
  }
)
public final class SqlTimeseriesCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin sql-timeseries` gets the usage
    // banner from Picocli's default no-subcommand behaviour.
  }
}
