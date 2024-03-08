import click

from shepard_scripts.scripts.packages import cleanup_packages
from shepard_scripts.scripts.releases import create_release

GITLAB_INSTANCE = "https://gitlab.com"
PROJECTS = {"backend": 27250272, "frontend": 27250279}
PROJECT_OPTION = "--project"
TOKEN_ARG = "token_file"  # noqa: S105


@click.group
def cli():
    """shepard scripts Entrypoint."""


@cli.command
@click.argument(TOKEN_ARG, type=click.File())
@click.option(
    PROJECT_OPTION,
    type=click.Choice(list(PROJECTS.keys()), case_sensitive=False),
    prompt=True,
)
def release(token_file, project):
    """Create a gitlab release for a given project."""
    create_release(GITLAB_INSTANCE, token_file.read(), PROJECTS[project])


@cli.command
@click.argument(TOKEN_ARG, type=click.File())
@click.option(
    PROJECT_OPTION,
    type=click.Choice(list(PROJECTS.keys()), case_sensitive=False),
    prompt=True,
)
def packages(token_file, project):
    """Cleanup gitlab packages for a given project."""
    cleanup_packages(GITLAB_INSTANCE, token_file.read(), PROJECTS[project])


if __name__ == "__main__":
    cli()
