package de.dlr.shepard.plugins.storage.s3.cli;

import picocli.CommandLine.Command;

/**
 * FS1b — container for {@code shepard-admin storage s3 <verb>}
 * subcommands.
 *
 * <p>Ten verbs cover the full operator runtime story: status,
 * enable/disable, set-endpoint, set-region, set-bucket,
 * set-credentials, clear-credentials, and test-connection.
 */
@Command(
  name = "s3",
  mixinStandardHelpOptions = true,
  description = "Configure the S3-compatible file storage plugin (FS1b).",
  subcommands = {
    S3StatusCommand.class,
    S3EnableCommand.class,
    S3DisableCommand.class,
    S3SetEndpointCommand.class,
    S3SetRegionCommand.class,
    S3SetBucketCommand.class,
    S3SetCredentialsCommand.class,
    S3ClearCredentialsCommand.class,
    S3TestConnectionCommand.class,
  }
)
public final class S3StorageCommand implements Runnable {

  @Override
  public void run() {
    // No-op — Picocli prints usage when the user types just
    // `shepard-admin storage s3`.
  }
}
