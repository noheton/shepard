import click

from .releases import create_release

GITLAB_INSTANCE = "https://gitlab.com"
PROJECTS = {"backend": 27250272, "frontend": 27250279}


@click.group
def cli():
    pass


@cli.command
@click.argument("token_file", type=click.File())
@click.option(
    "--project",
    type=click.Choice(["backend", "frontend"], case_sensitive=False),
    prompt=True,
)
def release(token_file, project):
    """Create a gitlab release for a given project."""
    token = token_file.read()

    create_release(GITLAB_INSTANCE, token, PROJECTS[project])


if __name__ == "__main__":
    cli()
