import click

from shepard_scripts.scripts.packages import cleanup_packages
from shepard_scripts.scripts.releases import (
    create_release,
    create_release_mr,
    get_project,
    get_release_notes,
    get_release_version,
)

GITLAB_INSTANCE = "https://gitlab.com"
PROJECT_ID = 59082852
TOKEN_ARG = "token_file"  # noqa: S105


@click.group
def cli():
    """shepard scripts Entrypoint."""


@cli.command
@click.argument(TOKEN_ARG, type=click.File())
def release(token_file):
    """Create a gitlab release for a given project."""
    project = get_project(GITLAB_INSTANCE, token_file.read(), PROJECT_ID)
    version = get_release_version()
    title, notes = get_release_notes(project, version)
    create_release_mr(project, f"Release {version}")
    create_release(project, title, f"{version}-release", notes)


@cli.command
@click.argument(TOKEN_ARG, type=click.File())
def packages(token_file):
    """Cleanup gitlab packages for a given project."""
    cleanup_packages(GITLAB_INSTANCE, token_file.read(), PROJECT_ID)


if __name__ == "__main__":
    cli()
