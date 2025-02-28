from influxdb import InfluxDBClient
import pandas as pd
from tqdm import trange
from loguru import logger
from config import (
    INFLUX_BATCH_SIZE,
    INFLUX_HOST,
    INFLUX_PASSWORD,
    INFLUX_PORT,
    INFLUX_USER,
    INFLUX_CHUNK_SIZE,
    INFLUX_TIME_CHUNK_SIZE,
)

# https://stackoverflow.com/questions/41990346/change-tag-value-in-influxdb


def replace_tag(
    database_name: str,
    measurement_name: str,
    tag: str,
    old_value: str,
    new_value: str,
):
    """Replaces an existing tag into a measurement, with a new tag for all affected records by deleting and reuploading"""

    client = InfluxDBClient(
        host=INFLUX_HOST,
        port=INFLUX_PORT,
        username=INFLUX_USER,
        password=INFLUX_PASSWORD,
    )
    INFLUX_MEASUREMENT_SIZE_QUERY = f'SELECT COUNT(*) FROM "{database_name}"."autogen"."{measurement_name}" WHERE "{tag}" = \'{old_value}\''
    series_size = list(client.query(INFLUX_MEASUREMENT_SIZE_QUERY).get_points())[0][
        "count_value"
    ]
    logger.info(f"points to migrate {series_size=}")
    INFLUX_MEASUREMENT_FIRST_QUERY = f'SELECT FIRST(*) FROM "{database_name}"."autogen"."{measurement_name}" WHERE "{tag}" = \'{old_value}\''
    series_start_timestamp = list(
        client.query(INFLUX_MEASUREMENT_FIRST_QUERY, epoch="ns").get_points()
    )[0]["time"]
    logger.info(f"{series_start_timestamp=}")

    INFLUX_MEASUREMENT_LAST_QUERY = f'SELECT LAST(*) FROM "{database_name}"."autogen"."{measurement_name}" WHERE "{tag}" = \'{old_value}\''
    series_end_timestamp = list(
        client.query(INFLUX_MEASUREMENT_LAST_QUERY, epoch="ns").get_points()
    )[0]["time"]
    logger.info(f"{series_end_timestamp=}")

    tags_keys = influx_get_tag_keys(database_name=database_name)
    field_keys = influx_get_field_keys(
        database_name=database_name, measurement_name=measurement_name
    )
    sum = 0
    for timestamp_offset in trange(
        series_start_timestamp, series_end_timestamp, INFLUX_TIME_CHUNK_SIZE
    ):
        q = (
            f'SELECT * FROM "{measurement_name}"'
            f" WHERE \"{tag}\" = '{old_value}'"
            f" AND time >= {timestamp_offset}ns AND time < {timestamp_offset + INFLUX_TIME_CHUNK_SIZE}ns"
        )

        logger.info(q)

        # Get a dataframe of selected data
        df = influx_get_read_query(query=q, database_name=database_name)
        sum = sum + len(df)
        logger.info(f"{sum} / {series_size}")

        # Here we collect all the new records to be written to influx
        new_points = []

        # Loop through each row of the returned dataframe
        for i in trange(0, len(df)):
            row = df.iloc[i]
            # logger.debug("row:", i)
            row_dict = row.to_dict()
            # logger.debug("old row dict:", row_dict)

            new_tags = {}
            new_fields = {}
            new_time = ""

            for key in row_dict.keys():
                if key in tags_keys:
                    new_tags[key] = row_dict[key]

                elif key in field_keys:
                    new_fields[key] = row_dict[key]

                elif key == "time":
                    new_time = row_dict[key]

                else:
                    logger.warning("WARNING: A KEY WAS NOT FOUND: " + str(key))

            # Replace the old value with a new value
            new_tags[tag] = new_value

            new_row_dict = {}
            new_row_dict["measurement"] = measurement_name
            new_row_dict["tags"] = new_tags
            new_row_dict["time"] = new_time
            new_row_dict["fields"] = new_fields

            # logger.debug('new row dict:', new_row_dict)
            new_points.append(new_row_dict)

        # Write the revised records back to the database
        influx_write_multiple_dicts(data_dicts=new_points, database_name=database_name)

    # When finished, delete all records.
    influx_delete_series(
        database_name=database_name,
        measurement_name=measurement_name,
        tag=tag,
        tag_value=old_value,
    )


def influx_delete_series(database_name, measurement_name, tag, tag_value):
    q = (
        'DROP SERIES FROM "'
        + measurement_name
        + '"'
        + ' WHERE "'
        + tag
        + '" = '
        + "'"
        + tag_value
        + "'"
    )
    client = InfluxDBClient(
        host=INFLUX_HOST,
        port=INFLUX_PORT,
        username=INFLUX_USER,
        password=INFLUX_PASSWORD,
    )
    client.switch_database(database_name)
    client.query(q, chunked=True, chunk_size=INFLUX_CHUNK_SIZE)


def influx_write_multiple_dicts(data_dicts: list, database_name):
    """Write a list of dicts with following structure:
    database_output_influx['measurement'] = 'SENSOR_ELEMENT_SUMMARY_TEST2'
        database_output_influx['tags'] = {'serialNumber':'1234', 'partNumber':'5678'}
        d = datetime.now()
        timestamp = d.isoformat('T')
        database_output_influx['time'] = timestamp
        database_output_influx['fields'] = summary_results_dict
    """
    client = InfluxDBClient(
        host=INFLUX_HOST,
        port=INFLUX_PORT,
        username=INFLUX_USER,
        password=INFLUX_PASSWORD,
    )
    client.switch_database(database_name)
    logger.debug(
        "Return code for influx write:",
        client.write_points(data_dicts, batch_size=INFLUX_BATCH_SIZE),
    )


def influx_get_tag_keys(database_name):
    client = InfluxDBClient(
        host=INFLUX_HOST,
        port=INFLUX_PORT,
        username=INFLUX_USER,
        password=INFLUX_PASSWORD,
    )
    # client.create_database('SIEMENS_ENERGY_TEST')
    client.switch_database(database_name)

    results = client.query("SHOW TAG KEYS ")
    point_list = []
    points = results.get_points()
    for point in points:
        point_list.append(point["tagKey"])

    return point_list


def influx_get_field_keys(measurement_name, database_name):
    client = InfluxDBClient(
        host=INFLUX_HOST,
        port=INFLUX_PORT,
        username=INFLUX_USER,
        password=INFLUX_PASSWORD,
    )
    client.switch_database(database_name)

    results = client.query("SHOW FIELD KEYS FROM " + measurement_name)
    point_list = []
    points = results.get_points()
    for point in points:
        point_list.append(point["fieldKey"])

    return point_list


def influx_get_read_query(query, database_name):
    """Returns a df of all measurements that have a certain field or value, for example stage. Note: single quotes for tag values, double quotes for al else. So best to use triple quotes surrounding statement. example:"""

    client = InfluxDBClient(
        host=INFLUX_HOST,
        port=INFLUX_PORT,
        username=INFLUX_USER,
        password=INFLUX_PASSWORD,
    )
    client.switch_database(database_name)
    # logger.debug("Dataframe of all measurments of type:", measurement_name)

    q = query
    logger.info(f"{q}")
    df = pd.DataFrame(client.query(q, epoch="ns").get_points())
    # logger.debug("DF: ", tabulate(df, headers=df.columns.tolist(), tablefmt="psql"))
    return df


def sanitize_tag(tag):
    REPLACEMENT_CHAR = "_"
    INVALID_CHARS = ["/", " ", "."]
    tag = tag.strip()  # remove preceeding and trailing whitespace
    for char in INVALID_CHARS:
        tag = tag.replace(char, REPLACEMENT_CHAR)
    return tag


def is_unique_influx_combination(database, measurement, key, new_tag):
    client = InfluxDBClient(
        host=INFLUX_HOST,
        port=INFLUX_PORT,
        username=INFLUX_USER,
        password=INFLUX_PASSWORD,
    )
    INFLUX_DB_MEAS_TAG_VALUE_COMBO_SIZE_QUERY = f'SELECT COUNT(*) FROM "{database}"."autogen"."{measurement}" WHERE "{key}" = \'{new_tag}\''
    result = list(client.query(INFLUX_DB_MEAS_TAG_VALUE_COMBO_SIZE_QUERY).get_points())
    if len(result) > 0:
        series_size = result[0]["count_value"]
        logger.debug(f"{series_size=}")
        logger.warning(f"Tag/Value {key} and {new_tag} is not unique")
        return False

    else:
        logger.info(f"Tag/Value {key} and {new_tag} is unique")
        return True


def fix_invalid_tags(errors):
    replacements = []
    for error in errors:
        for tag in set(error["tags"]):
            new_tag = sanitize_tag(tag)
            logger.info(f"replacing '{tag}' with '{new_tag}'")

            # TODO: check if measurement & tag combination unique?
            while not is_unique_influx_combination(
                error["database"], error["measurement"], error["key"], new_tag
            ):
                new_tag = new_tag + "_"

            replace_tag(
                error["database"],
                error["measurement"],
                error["key"],
                tag,
                new_tag,
            )
            replacement = {
                "database": error["database"],
                "measurement": error["measurement"],
                "key": error["key"],
                "old_tag": tag,
                "new_tag": new_tag,
            }
            replacements.append(replacement)
            logger.info(f"{replacement} performed")
    return replacements


if __name__ == "__main__":
    print(
        is_unique_influx_combination(
            "8b930ca1-7dbd-4b68-9f3b-6256e5a6284a",
            "mbar",
            "symbolic_name",
            "MTLH_DrumPosition_TER_I_ValveExtendActVal_INT",
        )
    )
    print(
        is_unique_influx_combination(
            "8b930ca1-7dbd-4b68-9f3b-6256e5a6284a",
            "mbar",
            "symbolic_name",
            "MTLH_DrumPosition_TER_I_ValveExtendActVal_INT...",
        )
    )
