import datetime
import os.path
from collections.abc import Generator
from os import getenv

import numpy as np
import pandas as pd
from dotenv import load_dotenv
from shepard_client import (
    ApiClient,
    Collection,
    CollectionApi,
    CollectionSearchBody,
    CollectionSearchParams,
    Configuration,
    ContainerSearchBody,
    ContainerSearchParams,
    ContainerType,
    DataObject,
    DataObjectApi,
    FileContainer,
    FileContainerApi,
    FileReference,
    FileReferenceApi,
    LabJournalEntry,
    LabJournalEntryApi,
    Permissions,
    PermissionType,
    SearchApi,
    SemanticAnnotation,
    SemanticAnnotationApi,
    SemanticRepository,
    SemanticRepositoryApi,
    SemanticRepositoryType,
    ShepardFile,
    SpatialDataContainerApi,
    StructuredData,
    StructuredDataContainer,
    StructuredDataContainerApi,
    StructuredDataPayload,
    StructuredDataReference,
    StructuredDataReferenceApi,
    Timeseries,
    TimeseriesContainer,
    TimeseriesContainerApi,
    TimeseriesDataPoint,
    TimeseriesReference,
    TimeseriesReferenceApi,
    TimeseriesWithDataPoints,
    URIReference,
    UriReferenceApi,
)

load_dotenv()
rng = np.random.default_rng(42)

HOST = getenv("BACKEND_URL")
API_KEY = getenv("API_KEY")
CONF = Configuration(host=HOST, api_key={"apikey": API_KEY})
CLIENT = ApiClient(configuration=CONF)
PLANET_PICS = ["mercury.jpg", "venus.jpg", "earth.jpg", "mars.jpg"]
FILE_PATH = "files"

FILE_CONTAINER_API = FileContainerApi(CLIENT)
FILE_REFERENCE_API = FileReferenceApi(CLIENT)
DATA_OBJECT_API = DataObjectApi(CLIENT)
COLLECTION_API = CollectionApi(CLIENT)
JOURNAL_API = LabJournalEntryApi(CLIENT)
TIMESERIES_CONTAINER_API = TimeseriesContainerApi(CLIENT)
TIMESERIES_REFERENCE_API = TimeseriesReferenceApi(CLIENT)
ANNOTATION_API = SemanticAnnotationApi(CLIENT)

COLLECTION_NAME = "Inner Solar System"
CLIMATE_CONTAINER_NAME = "Climate Data"
STRUCT_CONTAINER_NAME = "JSON Descriptions"
IMAGE_CONTAINER_NAME = "Planet images"
MYSTERY_CONTAINER_NAME = "Mystery Readings"


def generate_sample_data(delete_old: bool):
    if delete_old:
        delete_old_data()

    solar_system = Collection(
        name=COLLECTION_NAME,
        description="This collection represents the four inner planets of our solar system.",
    )
    solar_system = COLLECTION_API.create_collection(solar_system)
    permissions: Permissions = COLLECTION_API.get_collection_permissions(
        solar_system.id
    )
    permissions.permission_type = PermissionType.PUBLIC
    COLLECTION_API.edit_collection_permissions(solar_system.id, permissions)

    mercury, venus, earth, mars = create_planet_dos(solar_system)

    ts_refs = list(create_timeseries_data(solar_system, earth))

    image_container = create_image_container()
    file_data = list(create_file_data(image_container.id))
    for do, file in zip([mercury, venus, earth, mars], file_data, strict=False):
        fr = FileReference(
            name="satellite image",
            dataObjectId=do.id,
            fileContainerId=image_container.id,
            fileOids=[file.oid],
        )
        fr = FILE_REFERENCE_API.create_file_reference(solar_system.id, do.id, fr)

    write_journal(earth, image_container)

    sc, sd = create_structured_data()
    sr = StructuredDataReference(
        name="Structured description of earth",
        dataObjectId=earth.id,
        structuredDataContainerId=sc.id,
        structuredDataOids=[sd.oid],
    )
    sd_ref_api = StructuredDataReferenceApi(CLIENT)
    sd_ref_api.create_structured_data_reference(solar_system.id, earth.id, sr)

    uri_api = UriReferenceApi(CLIENT)
    uri_ref = URIReference(
        name="Wiki", uri="https://en.wikipedia.org/wiki/Earth", relationship="URI"
    )
    uri_api.create_uri_reference(solar_system.id, earth.id, uri_ref)

    create_annotations(solar_system, earth, ts_refs)

    mystery_container = create_mystery_container()
    create_mystery_timeseries(solar_system, venus, mystery_container)


def delete_old_data():
    delete_all_collections_with_name(COLLECTION_NAME)
    delete_all_containers_with_name(CLIMATE_CONTAINER_NAME, ContainerType.TIMESERIES)
    delete_all_containers_with_name(STRUCT_CONTAINER_NAME, ContainerType.STRUCTUREDDATA)
    delete_all_containers_with_name(IMAGE_CONTAINER_NAME, ContainerType.FILE)
    delete_all_containers_with_name(MYSTERY_CONTAINER_NAME, ContainerType.TIMESERIES)


def create_planet_dos(
    solar_system: Collection,
) -> tuple[DataObject, DataObject, DataObject, DataObject]:
    mercury = DataObject(name="Mercury")
    mercury = DATA_OBJECT_API.create_data_object(solar_system.id, mercury)
    venus = DataObject(name="Venus", predecessorIds=[mercury.id])
    venus = DATA_OBJECT_API.create_data_object(solar_system.id, venus)
    earth = DataObject(
        name="Earth",
        description="The only planet so far known to harbour life.",
        predecessorIds=[venus.id],
        attributes={"danger assessment": "mostly harmless"},
    )
    earth = DATA_OBJECT_API.create_data_object(solar_system.id, earth)
    moon = DataObject(
        name="Moon", description="Earth's little companion", parentId=earth.id
    )
    moon = DATA_OBJECT_API.create_data_object(solar_system.id, moon)
    mars = DataObject(name="Mars", predecessorIds=[earth.id])
    mars = DATA_OBJECT_API.create_data_object(solar_system.id, mars)
    return mercury, venus, earth, mars


def write_journal(earth_do: DataObject, image_container: FileContainer):
    augsburg_image = FILE_CONTAINER_API.create_file(
        image_container.id, os.path.join(FILE_PATH, "augsburg.jpg")
    )
    journal_entries = [
        LabJournalEntry(dataObjectId=earth_do.id, journalContent="Landed on earth."),
        LabJournalEntry(
            dataObjectId=earth_do.id,
            journalContent=f"""
    Visited DLR <strong>ZLP</strong> in <i>Augsburg</i>:\n
    [[image fcid="{image_container.id}" oid="{augsburg_image.oid}" alt="ZLP Augsburg"]]
    """,
        ),
        LabJournalEntry(dataObjectId=earth_do.id, journalContent="Left earth."),
        LabJournalEntry(dataObjectId=earth_do.id, journalContent="Updated guide."),
    ]
    for entry in journal_entries:
        JOURNAL_API.create_lab_journal(earth_do.id, entry)


def create_annotations(
    solar_system: Collection, earth: DataObject, ts_refs: list[TimeseriesReference]
):
    semantic_repo_api = SemanticRepositoryApi(CLIENT)
    semantic_repo = SemanticRepository(
        name="ontobee",
        type=SemanticRepositoryType.SPARQL,
        endpoint="https://sparql.hegroup.org/sparql/ ",
    )
    semantic_repo = semantic_repo_api.create_semantic_repository(semantic_repo)

    solar_system_annotation = SemanticAnnotation(
        propertyIRI="http://semanticscience.org/resource/SIO_000202",
        propertyRepositoryId=semantic_repo.id,
        valueIRI="http://purl.obolibrary.org/obo/OMIT_0018724",
        valueRepositoryId=semantic_repo.id,
    )
    ANNOTATION_API.create_collection_annotation(
        solar_system.id, solar_system_annotation
    )

    earth_annotation = SemanticAnnotation(
        propertyIRI="http://semanticscience.org/resource/SIO_000202",
        propertyRepositoryId=semantic_repo.id,
        valueIRI="http://purl.obolibrary.org/obo/OMIT_0019432",
        valueRepositoryId=semantic_repo.id,
    )
    ANNOTATION_API.create_data_object_annotation(
        collection_id=solar_system.id,
        data_object_id=earth.id,
        semantic_annotation=earth_annotation,
    )

    temp_annotation = SemanticAnnotation(
        propertyIRI="http://purl.obolibrary.org/obo/UO_0000005",
        propertyRepositoryId=semantic_repo.id,
        valueIRI="http://purl.obolibrary.org/obo/UO_0000027",
        valueRepositoryId=semantic_repo.id,
    )
    for tsr in ts_refs:
        ANNOTATION_API.create_reference_annotation(
            collection_id=solar_system.id,
            data_object_id=earth.id,
            reference_id=tsr.id,
            semantic_annotation=temp_annotation,
        )


def create_timeseries_data(
    collection_for_ref: Collection, data_object_for_ref: DataObject
) -> Generator[TimeseriesReference]:
    tsc_api = TimeseriesContainerApi(CLIENT)
    tsc = TimeseriesContainer(name=CLIMATE_CONTAINER_NAME)
    tsc = tsc_api.create_timeseries_container(tsc)
    permissions = tsc_api.get_timeseries_permissions(tsc.id)
    permissions.permission_type = PermissionType.PUBLIC
    permissions = tsc_api.edit_timeseries_permissions(tsc.id, permissions)

    ts_data = list(generate_data_points())
    for location, ts in ts_data:
        import_single_temperature_timeseries(tsc_api, tsc.id, location, ts)
    ts_to_reference = [
        timeseries_for_location("Mecklenburg-Vorpommern"),
        timeseries_for_location("Niedersachsen"),
        timeseries_for_location("Sachsen"),
        timeseries_for_location("Bayern"),
    ]
    tsr_api = TimeseriesReferenceApi(CLIENT)
    tsr = TimeseriesReference(
        name="Climate since 1980",
        start=year_to_timestamp(1980),
        end=year_to_timestamp(datetime.datetime.now().year),
        timeseries=ts_to_reference,
        timeseriesContainerId=tsc.id,
    )
    yield tsr_api.create_timeseries_reference(
        collection_for_ref.id, data_object_for_ref.id, tsr
    )
    tsr = TimeseriesReference(
        name="Climate in the 90s",
        start=year_to_timestamp(1990),
        end=year_to_timestamp(2000),
        timeseries=ts_to_reference,
        timeseriesContainerId=tsc.id,
    )
    yield tsr_api.create_timeseries_reference(
        collection_for_ref.id, data_object_for_ref.id, tsr
    )


def create_structured_data() -> tuple[StructuredDataContainer, StructuredData]:
    sc_api = StructuredDataContainerApi(CLIENT)
    sc = StructuredDataContainer(name=STRUCT_CONTAINER_NAME)
    sc = sc_api.create_structured_data_container(sc)
    permissions = sc_api.get_structured_data_permissions(sc.id)
    permissions.permission_type = PermissionType.PUBLIC
    permissions = sc_api.edit_structured_data_permissions(sc.id, permissions)

    with open(os.path.join(FILE_PATH, "earth.json")) as f:
        payload = StructuredDataPayload(
            structuredData=StructuredData(name="earth_desc.json"),
            payload=f.read(),
        )
        sd = sc_api.create_structured_data(sc.id, payload)

    return sc, sd


def create_image_container() -> FileContainer:
    fc = FileContainer(name=IMAGE_CONTAINER_NAME)
    fc = FILE_CONTAINER_API.create_file_container(fc)
    permissions = FILE_CONTAINER_API.get_file_permissions(fc.id)
    permissions.permission_type = PermissionType.PUBLIC
    permissions = FILE_CONTAINER_API.edit_file_permissions(fc.id, permissions)
    return fc


def create_file_data(fc_id: int) -> Generator[ShepardFile]:
    for planet_pic in PLANET_PICS:
        yield FILE_CONTAINER_API.create_file(fc_id, os.path.join(FILE_PATH, planet_pic))


def generate_data_points() -> Generator[tuple[str, list[TimeseriesDataPoint]]]:
    df = pd.read_csv(
        os.path.join(FILE_PATH, "germany_yearly_temperatures.csv"), sep=";", header=2
    )
    # any earlier date will not work on windows
    df = df[df["Jahr"] > 1970]
    num_cols = len(df.columns)
    n_first_data_col = 3
    df.insert(loc=0, column="timestamp", value=df["Jahr"].map(year_to_timestamp))

    for i in range(n_first_data_col, num_cols):
        ts_df = df["timestamp"]
        location_df = df.iloc[:, i]
        df_for_location = pd.concat([ts_df, location_df], axis=1)
        yield location_df.name.replace("/", "_"), list(df_to_tspoint(df_for_location))


def df_to_tspoint(df: pd.DataFrame) -> Generator[TimeseriesDataPoint]:
    for timestamp, temperature in df.itertuples(index=False):
        yield TimeseriesDataPoint(timestamp=timestamp, value=temperature)


def year_to_timestamp(year: int) -> int:
    dt = datetime.datetime(year=year, month=1, day=1)
    return int(dt.timestamp() * 1e9)


def timeseries_for_location(location: str) -> Timeseries:
    return Timeseries(
        measurement="Climate",
        device="Thermometer",
        location=location,
        symbolicName="Jesstherm",
        field="Temperature",
    )


def import_single_temperature_timeseries(
    api: TimeseriesContainerApi,
    container_id: int,
    location: str,
    data_points: list[TimeseriesDataPoint],
):
    api.create_timeseries(
        timeseries_container_id=container_id,
        timeseries_with_data_points=TimeseriesWithDataPoints(
            timeseries=timeseries_for_location(location),
            points=data_points,
        ),
    )


def _query(name: str):
    return f'{{"property":"name","value":"{name}","operator":"eq"}}'


def delete_all_collections_with_name(name: str):
    search_api = SearchApi(CLIENT)
    collections_to_delete = search_api.search_collections(
        CollectionSearchBody(
            searchParams=CollectionSearchParams(
                query=_query(name),
            )
        )
    )
    collection_api = CollectionApi(CLIENT)
    for collection in collections_to_delete.results:
        collection_api.delete_collection(collection.id)


def delete_all_containers_with_name(name: str, container_type: ContainerType):
    search_api = SearchApi(CLIENT)
    container_to_delete = search_api.search_containers(
        ContainerSearchBody(
            searchParams=ContainerSearchParams(
                query=_query(name), queryType=container_type
            ),
        )
    )
    if container_type == ContainerType.TIMESERIES:
        container_api = TimeseriesContainerApi(CLIENT)
        for container in container_to_delete.results:
            container_api.delete_timeseries_container(container.id)

    if container_type == ContainerType.FILE:
        container_api = FileContainerApi(CLIENT)
        for container in container_to_delete.results:
            container_api.delete_file_container(container.id)

    if container_type == ContainerType.STRUCTUREDDATA:
        container_api = StructuredDataContainerApi(CLIENT)
        for container in container_to_delete.results:
            container_api.delete_structured_data_container(container.id)

    if container_type == ContainerType.SPATIALDATA:
        container_api = SpatialDataContainerApi(CLIENT)
        for container in container_to_delete.results:
            container_api.delete_spatial_data_container(container.id)


def create_mystery_container() -> TimeseriesContainer:
    tsc = TimeseriesContainer(name=MYSTERY_CONTAINER_NAME)
    tsc = TIMESERIES_CONTAINER_API.create_timeseries_container(tsc)
    permissions = TIMESERIES_CONTAINER_API.get_timeseries_permissions(tsc.id)
    permissions.permission_type = PermissionType.PUBLIC
    permissions = TIMESERIES_CONTAINER_API.edit_timeseries_permissions(
        tsc.id, permissions
    )
    return tsc


def create_mystery_timeseries(
    coll: Collection, do: DataObject, tsc: TimeseriesContainer
):
    n_threads = 10
    n_data_per_ts = 100
    m = generate_brownian_matrix(n_threads, n_data_per_ts)

    all_ts = [create_timeseries(tsc.id, str(idx), m[idx]) for idx in range(n_threads)]

    tsr = TimeseriesReference(
        name="Mystery readings in the last 40 years",
        start=years_before_now_to_timestamp(40),
        end=int(datetime.datetime.now().timestamp() * 1e9),
        timeseries=all_ts,
        timeseriesContainerId=tsc.id,
    )
    tsr = TIMESERIES_REFERENCE_API.create_timeseries_reference(coll.id, do.id, tsr)


def data_to_tspoint(
    timestamps: list[int], data: np.ndarray
) -> Generator[TimeseriesDataPoint]:
    assert len(timestamps) == len(data)
    for stamp, value in zip(timestamps, data, strict=False):
        yield TimeseriesDataPoint(timestamp=int(stamp), value=value)


def create_timeseries(tsc_id: int, name: str, data: np.ndarray) -> Timeseries:
    assert len(data.shape) == 1

    timestamps = create_timestamps(len(data))
    ts_data_points = list(data_to_tspoint(timestamps, data))

    return TIMESERIES_CONTAINER_API.create_timeseries(
        timeseries_container_id=tsc_id,
        timeseries_with_data_points=TimeseriesWithDataPoints(
            timeseries=Timeseries(
                measurement="unknown",
                device="mystery-sensor",
                location="orbit",
                symbolicName=f"sensor-{name}",
                field="mysterium",
            ),
            points=ts_data_points,
        ),
    )


def years_before_now_to_timestamp(years: int):
    return int(
        (datetime.datetime.now() - datetime.timedelta(days=years * 365)).timestamp()
        * 1e9
    )


def create_timestamps(n: int) -> list[int]:
    year_40_before_now_ns = years_before_now_to_timestamp(40)
    now_ns = int(datetime.datetime.now().timestamp() * 1e9)
    time_step = (now_ns - year_40_before_now_ns) / n
    timestamps = np.arange(n) * time_step + year_40_before_now_ns
    timestamps = list(map(int, timestamps))
    return timestamps


def generate_brownian_matrix(paths: int, points: int):
    # drift and volatility
    # mu, sigma = 0.0, 1.0
    mu, sigma = 0.3, 2
    return rng.normal(mu, sigma, (paths, points)).cumsum(axis=1)
