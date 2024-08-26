import json
import time
from math import radians, sin

from shepard_client.api.collection_api import CollectionApi
from shepard_client.api.collection_reference_api import CollectionReferenceApi
from shepard_client.api.data_object_api import DataObjectApi
from shepard_client.api.data_object_reference_api import DataObjectReferenceApi
from shepard_client.api.file_container_api import FileContainerApi
from shepard_client.api.file_reference_api import FileReferenceApi
from shepard_client.api.structured_data_container_api import StructuredDataContainerApi
from shepard_client.api.structured_data_reference_api import StructuredDataReferenceApi
from shepard_client.api.timeseries_container_api import TimeseriesContainerApi
from shepard_client.api.timeseries_reference_api import TimeseriesReferenceApi
from shepard_client.api.uri_reference_api import UriReferenceApi
from shepard_client.models.collection import Collection
from shepard_client.models.collection_reference import CollectionReference
from shepard_client.models.data_object import DataObject
from shepard_client.models.data_object_reference import DataObjectReference
from shepard_client.models.file_container import FileContainer
from shepard_client.models.file_reference import FileReference
from shepard_client.models.influx_point import InfluxPoint
from shepard_client.models.structured_data import StructuredData
from shepard_client.models.structured_data_container import StructuredDataContainer
from shepard_client.models.structured_data_payload import StructuredDataPayload
from shepard_client.models.structured_data_reference import StructuredDataReference
from shepard_client.models.timeseries import Timeseries
from shepard_client.models.timeseries_container import TimeseriesContainer
from shepard_client.models.timeseries_payload import TimeseriesPayload
from shepard_client.models.timeseries_reference import TimeseriesReference
from shepard_client.models.uri_reference import URIReference

NANOS = int(1e9)  # Convert nanoseconds to seconds


def create_collection(client) -> Collection:
    collection_api = CollectionApi(client)
    collection = Collection(
        attributes={"a": "b", "c": "123"},
        name="A Collection",
        description="A collection for testing purposes",
    )
    collection = collection_api.create_collection(collection)
    return collection


def create_data_objects(
    client, collection_id
) -> tuple[DataObject, DataObject, DataObject]:
    data_object_api = DataObjectApi(client)
    data_object = DataObject(
        name="Parent",
        description="A parent object",
        attributes={"one": "two three"},
    )
    data_object = data_object_api.create_data_object(collection_id, data_object)
    child = DataObject(
        name="Child",
        description="A child object",
        parentId=data_object.id,
    )
    child = data_object_api.create_data_object(collection_id, child)

    if not child.id:
        raise RuntimeError("No child")

    successor = DataObject(
        name="Successor",
        description="A different child object",
        parentId=data_object.id,
        predecessorIds=[child.id],
    )
    successor = data_object_api.create_data_object(collection_id, successor)
    return data_object, child, successor


def create_file(client, collection_id, data_object_id) -> FileReference:
    file_container_api = FileContainerApi(client)
    container = FileContainer(name="A File Container")
    container = file_container_api.create_file_container(container)

    if not container.id:
        raise RuntimeError("No file container")

    file1 = file_container_api.create_file(container.id, "files/hello-world.txt")
    file2 = file_container_api.create_file(container.id, "files/augsburg.jpg")

    if not (file1.oid and file2.oid):
        raise RuntimeError("No file oids")

    file_reference_api = FileReferenceApi(client)
    reference = FileReference(
        name="A File Reference",
        fileContainerId=container.id,
        fileOids=[file1.oid, file2.oid],
    )
    reference = file_reference_api.create_file_reference(
        collection_id, data_object_id, reference
    )
    return reference


def create_structured_data(
    client, collection_id, data_object_id
) -> StructuredDataReference:
    structured_data_container_api = StructuredDataContainerApi(client)
    container = StructuredDataContainer(name="A SD Container")
    container = structured_data_container_api.create_structured_data_container(container)

    if not container.id:
        raise RuntimeError("No sd container")

    payload1 = StructuredDataPayload(
        structuredData=StructuredData(name="My StructuredData"),
        payload=json.dumps(
            {
                "Hello": "World",
                "List": [1, 3, 3, 7],
                "Number": 42,
                "Nested": {"Maps are": "also possible"},
            }
        ),
    )
    payload2 = StructuredDataPayload(
        structuredData=StructuredData(name="My Second StructuredData"),
        payload=json.dumps(
            {
                "String": "123",
                "Number": 123,
            }
        ),
    )
    structured_data1 = structured_data_container_api.create_structured_data(
        structured_data_container_id=container.id,
        structured_data_payload=payload1,
    )
    structured_data2 = structured_data_container_api.create_structured_data(
        structured_data_container_id=container.id,
        structured_data_payload=payload2,
    )

    if not (structured_data1.oid and structured_data2.oid):
        raise RuntimeError("No sd oids")

    structured_data_reference_api = StructuredDataReferenceApi(client)
    reference = StructuredDataReference(
        name="A SD Reference",
        structuredDataContainerId=container.id,
        structuredDataOids=[structured_data1.oid, structured_data2.oid],
    )
    reference = structured_data_reference_api.create_structured_data_reference(
        collection_id, data_object_id, reference
    )
    return reference


def create_timeseries(client, collection_id, data_object_id) -> TimeseriesReference:
    timeseries_container_api = TimeseriesContainerApi(client)
    container = TimeseriesContainer(name="A Timeseries Container")
    container = timeseries_container_api.create_timeseries_container(container)

    if not container.id:
        raise RuntimeError("No timeseries container")

    nanos = int(round(time.time_ns()))  # current nanoseconds
    points1 = []
    for i in range(0, 360):
        value = sin(radians(i))
        timestamp = nanos - (360 - i) * NANOS
        points1.append(InfluxPoint(value=value, timestamp=timestamp))

    payload1 = TimeseriesPayload(
        timeseries=Timeseries(
            measurement="MyMeas",
            device="MyDev",
            location="MyLoc",
            symbolicName="MySymName",
            field="value",
        ),
        points=points1,
    )
    payload2 = TimeseriesPayload(
        timeseries=Timeseries(
            measurement="Different",
            device="Just",
            location="For",
            symbolicName="Testing",
            field="Purposes",
        ),
        points=[InfluxPoint(value=123, timestamp=nanos)],
    )
    timeseries1 = timeseries_container_api.create_timeseries(container.id, payload1)
    timeseries2 = timeseries_container_api.create_timeseries(container.id, payload2)
    timeseries_reference_api = TimeseriesReferenceApi(client)
    reference = TimeseriesReference(
        name="My Timeseries",
        start=nanos - 360 * NANOS,
        end=nanos,
        timeseriesContainerId=container.id,
        timeseries=[timeseries1, timeseries2],
    )
    reference = timeseries_reference_api.create_timeseries_reference(
        collection_id, data_object_id, reference
    )
    return reference


def create_data_object_reference(
    client, collection_id, data_object_id
) -> tuple[CollectionReference, DataObjectReference]:
    collection_api = CollectionApi(client)
    data_object_api = DataObjectApi(client)
    collection = collection_api.create_collection(
        Collection(name="A different collection")
    )

    if not collection.id:
        raise RuntimeError("No collection")

    data_object = data_object_api.create_data_object(
        collection.id, DataObject(name="A different data object")
    )

    if not data_object.id:
        raise RuntimeError("No data object")

    cr_api = CollectionReferenceApi(client)
    cr = CollectionReference(
        name="My Collection Reference",
        referencedCollectionId=collection.id,
        relationship="related",
    )
    cr = cr_api.create_collection_reference(collection_id, data_object_id, cr)

    dor_api = DataObjectReferenceApi(client)
    dor = DataObjectReference(
        name="My Data Object Reference",
        referencedDataObjectId=data_object.id,
        relationship="related",
    )
    dor = dor_api.create_data_object_reference(collection_id, data_object_id, dor)

    return cr, dor


def create_uri_reference(client, collection_id, data_object_id) -> URIReference:
    uri_reference_api = UriReferenceApi(client)
    reference = URIReference(
        name="My URI Reference", uri="https://gitlab.com/dlr-shepard"
    )
    reference = uri_reference_api.create_uri_reference(
        collection_id, data_object_id, reference
    )
    return reference
