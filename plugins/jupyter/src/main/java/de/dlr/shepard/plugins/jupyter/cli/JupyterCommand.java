package de.dlr.shepard.plugins.jupyter.cli;

import picocli.CommandLine.Command;

/**
 * J1e — container for {@code shepard-admin jupyter <verb>} sub-commands.
 *
 * <ul>
 *   <li>{@code status} — read-only view of the current
 *       {@code :JupyterConfig} singleton via
 *       {@code GET /v2/admin/plugins/jupyter/config}.</li>
 *   <li>{@code enable} / {@code disable} — flip the master switch
 *       gating the "Open in JupyterHub" affordance.</li>
 *   <li>{@code set-hub-url} — point operators at the JupyterHub
 *       instance the affordance targets.</li>
 * </ul>
 *
 * <p>The launch button is visible only when {@code enabled === true}
 * AND {@code hubUrl != null}; either knob being clear suppresses the
 * affordance.
 *
 * <p>See {@code aidocs/16-dispatcher-backlog.md} J1e row and
 * {@code docs/admin/runbooks/jupyterhub-config.md} for the operator
 * runbook.
 */
@Command(
  name = "jupyter",
  mixinStandardHelpOptions = true,
  description = "Manage the instance-wide JupyterHub link-out configuration.",
  subcommands = {
    JupyterStatusCommand.class,
    JupyterEnableCommand.class,
    JupyterDisableCommand.class,
    JupyterSetHubUrlCommand.class,
  }
)
public final class JupyterCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin jupyter` gets the usage
    // banner from Picocli's default no-subcommand behaviour.
  }
}
