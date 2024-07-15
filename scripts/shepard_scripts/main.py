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
    create_release,
    create_release_mr,
    get_project,
    get_release_notes,
    get_release_version,
)

GITLAB_INSTANCE = "https://gitlab.com"
PROJECT_ID = 59082852


@click.group
def cli():
    """shepard scripts Entrypoint."""


@cli.command
@click.argument("token_file", type=click.File("r"))
def release(token_file):
    """Create a gitlab release for a given project."""
    token = str(token_file.readline()).rstrip("\n")
    project = get_project(GITLAB_INSTANCE, token, PROJECT_ID)
    version = get_release_version()
    title, notes = get_release_notes(project, version)
    create_release_mr(project, f"Release {version}")
    create_release(project, title, f"{version}-release", notes)


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
