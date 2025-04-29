import click
from shepard_client.api_client import ApiClient
from shepard_client.configuration import Configuration

from shepard_scripts.scripts.example_data import (
    create_collection,
    create_data_object_reference,
    create_data_objects,
    create_file,
    create_structured_data,
    create_timeseries,
    create_uri_reference,
)
from shepard_scripts.scripts.packages import cleanup_packages
from shepard_scripts.scripts.releases import (
    create_dependency_issue,
    create_release,
    get_project,
    get_release_details,
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
    help="Release since which changelog should be created. " +
         "Must be part of the 20 latest releases!",
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
    tag, notes = get_release_details(project, isHotfixRelease, since_release)
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
@click.argument("host")
@click.argument("api_key_file", type=click.File("r"))
def example_data(host, api_key_file):
    """Create example data."""

    # Set up configuration
    api_key = str(api_key_file.readline()).rstrip("\n")
    conf = Configuration(host=host, api_key={"apikey": api_key})
    client = ApiClient(configuration=conf)

    # Create things
    collection = create_collection(client)
    data_object, child, successor = create_data_objects(client, collection.id)
    file_reference = create_file(client, collection.id, data_object.id)
    sd_reference = create_structured_data(client, collection.id, data_object.id)
    timeseries_reference = create_timeseries(client, collection.id, data_object.id)
    collection_reference, data_object_reference = create_data_object_reference(
        client, collection.id, data_object.id
    )
    uri_reference = create_uri_reference(client, collection.id, data_object.id)

    # Print result
    created = [
        obj.name
        for obj in [
            collection,
            data_object,
            child,
            successor,
            file_reference,
            sd_reference,
            timeseries_reference,
            collection_reference,
            data_object_reference,
            uri_reference,
        ]
    ]
    created_names = ", ".join(created)
    click.echo(f"done. created the following objects: {created_names}")


if __name__ == "__main__":
    cli()
