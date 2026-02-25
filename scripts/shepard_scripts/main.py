import click

import shepard_scripts.scripts.example_data as solar_system_example
from shepard_scripts.scripts.change_version import change_version
from shepard_scripts.scripts.packages import cleanup_packages
from shepard_scripts.scripts.releases import (
    create_dependency_issue,
    create_release,
    create_release_details,
    get_project,
    get_user_id,
    prompt_confirm,
    prompt_title,
)

GITLAB_INSTANCE = "https://gitlab.com"
PROJECT_ID = 59082852


@click.group
def cli():
    """shepard scripts Entrypoint."""


@cli.command
@click.option(
    "--hotfix-release",
    is_flag=True,
    default=False,
    help="If this flag is set, the script performs a hotfix release.",
)
@click.option(
    "--since-release",
    help="Release since which changelog should be created. "
    + "Must be part of the 20 latest releases!",
)
@click.argument("token_file", type=click.File("r"))
def release(hotfix_release, token_file, since_release):
    """Create a gitlab release for a given project."""
    click.confirm(
        "The next steps create a new release for shepard.\n"
        + "If you are unsure if you set up all needed steps for a release,\n"
        + "please refer to the official documentation on shepard releases: https://shepard-dlr-shepard-e573f5a4116ef73f64fe76039b5c0aad01da3a88afa.gitlab.io/architecture-docs/#_performing_releases.\n"
        + "Press 'y' to proceed",
        abort=True,
    )
    click.echo("\n")
    token = str(token_file.readline()).rstrip("\n")
    isHotfixRelease = bool(hotfix_release)
    project = get_project(GITLAB_INSTANCE, token, PROJECT_ID)
    user_id = get_user_id(GITLAB_INSTANCE, token)
    tag, notes = create_release_details(project, isHotfixRelease, since_release)
    title = prompt_title(tag)
    prompt_confirm(title, tag, notes)
    create_release(project, title, tag, notes)
    create_dependency_issue(project, user_id)


@cli.command
@click.argument("token_file", type=click.File("r"))
def packages(token_file):
    """Cleanup gitlab packages for a given project."""
    token = str(token_file.readline()).rstrip("\n")
    cleanup_packages(GITLAB_INSTANCE, token, PROJECT_ID)


@cli.command
@click.option(
    "--delete-old-data",
    "-d",
    is_flag=True,
    help="""Delete all Collections and Containers that have been created by this script.
This essentially replaces them with fresh data.""",
)
def sample(delete_old_data):
    """
    Create sample data for a given shepard instance.
    A valid api key and the backend url need to be provided as part of the environment.
    You may use the .env.example, copy it to .env and fill in both values for it to take effect.
    """
    solar_system_example.generate_sample_data(delete_old_data)


@cli.command
@click.argument("version")
def update_version(version: str):
    """Update the version number."""
    change_version(version)


if __name__ == "__main__":
    cli()
