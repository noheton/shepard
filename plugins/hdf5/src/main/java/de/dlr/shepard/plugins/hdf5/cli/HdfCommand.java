package de.dlr.shepard.plugins.hdf5.cli;

import picocli.CommandLine.Command;

/**
 * FTOGGLE-CLI-PARITY-1 — container for {@code shepard-admin hdf <verb>}
 * sub-commands. Contributed to the {@code shepard-admin} root via
 * {@link HdfAdminCliCommandProvider} (PM1d SPI).
 *
 * <ul>
 *   <li>{@code status} — read-only view of {@code :HdfConfig} via
 *       {@code GET /v2/admin/config/hdf}.</li>
 *   <li>{@code enable} / {@code disable} — flip the runtime
 *       {@code enabled} toggle.</li>
 * </ul>
 *
 * <p>See {@code aidocs/16-dispatcher-backlog.md} FTOGGLE-CLI-PARITY-1 row
 * and {@code plugins/hdf5/docs/reference.md} for the operator runbook.
 */
@Command(
  name = "hdf",
  mixinStandardHelpOptions = true,
  description = "Manage the HDF5/HSDS plugin runtime configuration.",
  subcommands = {
    HdfStatusCommand.class,
    HdfEnableCommand.class,
    HdfDisableCommand.class,
  }
)
public final class HdfCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin hdf` gets the usage banner
    // from Picocli's default no-subcommand behaviour.
  }
}
