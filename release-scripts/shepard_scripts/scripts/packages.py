import re
from datetime import UTC, datetime, timedelta

import click
from gitlab.client import Gitlab
from gitlab.v4.objects.packages import ProjectPackage

PACKAGES_KEEP_DAYS = 90
PACKAGES_CLEANUP_REGEX = (
    ".*(?:\\.|-)?(?:SNAPSHOT|dev)(?:\\.|-)?(?:[0-9]+)?(?:-SNAPSHOT)?"
)


def cleanup_packages(gitlab_instance: str, token: str, project_id: int):
    instance = Gitlab(gitlab_instance, token)

    try:
        project = instance.projects.get(project_id)
    except KeyError as ex:
        raise click.Abort(f"Project {ex} could not be found") from ex

    created_limit = datetime.now(UTC) - timedelta(days=PACKAGES_KEEP_DAYS)
    click.echo(f"Deleting dev packages that were created before {created_limit}")

    packages: list[ProjectPackage] = []
    packages_iter: list[ProjectPackage] = project.packages.list(
        iterator=True, order_by="created_at", sort="asc"
    )  # type: ignore
    for package in packages_iter:
        created_at = datetime.fromisoformat(package.created_at)
        if created_at >= created_limit:
            # Ignore recent packages
            break

        if re.search(PACKAGES_CLEANUP_REGEX, package.version):
            packages.append(package)

    click.echo(f"Deleting {len(packages)} packages")
    click.echo(", ".join(package.version for package in packages))
    if not click.confirm("OK?", abort=True):
        raise click.Abort("Not confirmed")

    with click.progressbar(packages, label="Deleting packages") as progressbar:
        for package in progressbar:
            package.delete()

    click.echo("Successfully deleted")
