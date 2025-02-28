import pprint

from influxdb import InfluxDBClient
from loguru import logger
from neo4j import GraphDatabase

from config import (
    INFLUX_HOST,
    INFLUX_PASSWORD,
    INFLUX_PORT,
    INFLUX_TIMESERIES_KEYS,
    INFLUX_USER,
    NEO4J_AUTH,
    NEO4J_URI,
)

# https://github.com/influxdata/influxdb/issues/3904#issuecomment-268918613
# https://github.com/hgomez/influxdb
# https://www.neteye-blog.com/2018/11/how-to-drop-a-tag-in-influxdb/


def neo4j():
    with GraphDatabase.driver(NEO4J_URI, auth=NEO4J_AUTH) as driver:
        driver.verify_connectivity()


def check_influx_tags():
    errors = []
    logger.info("scanning for invalid symbols in tags")
    client = InfluxDBClient(INFLUX_HOST, INFLUX_PORT, INFLUX_USER, INFLUX_PASSWORD)
    logger.info(client.ping())
    databases = client.get_list_database()
    for database in databases:
        # INFLUX_QUERY_GET_TAGS = f'SHOW TAG KEYS ON "{database["name"]}"'
        # result_keys = client.query(INFLUX_QUERY_GET_TAGS)
        logger.debug(f"processing '{database['name']=}'")
        if database["name"] in ["_internal", "telegraf"]:
            logger.debug(f"skipping {database['name']=}, this is normal")
            continue
        INFLUX_QUERY_MEASUREMENTS = f'SHOW MEASUREMENTS ON "{database["name"]}"'
        measurements = list(
            client.query(INFLUX_QUERY_MEASUREMENTS).get_points()
        )  # an array is always returned
        if measurements:
            logger.debug(measurements)
            for measurement in measurements:
                logger.debug(f"{measurement['name']=}")
                for key in INFLUX_TIMESERIES_KEYS:
                    logger.debug(f"{database['name']}, {key=}")

                    INFLUX_QUERY_INVALID_TAG_KEYS = f'SHOW TAG VALUES ON "{database["name"]}" FROM "{measurement["name"]}" WITH KEY = "{key}" WHERE "{key}" =~ /[\s|\.|,|\/]/'
                    logger.debug(INFLUX_QUERY_INVALID_TAG_KEYS)
                    result = client.query(INFLUX_QUERY_INVALID_TAG_KEYS)
                    if result.items():
                        # logger.debug(result.raw)
                        series = result.raw["series"]
                        logger.debug(series)
                        invalid_tags = set()
                        for s in series:
                            for v in s["values"]:
                                invalid_tags.add(v[1])
                        logger.warning(
                            f"'{database['name']=}' '{measurement['name']=} contains invalid tags '{invalid_tags=}' detected for {key=}"
                        )
                        errors.append(
                            {
                                "database": database["name"],
                                "measurement": measurement["name"],
                                "key": key,
                                "tags": invalid_tags,
                            }
                        )
    logger.info("done.")
    return errors


def get_deletion_queries_invalid_influx_tags(errors):
    for error in errors:
        for tag in error["tags"]:
            logger.debug(f"deleting invalid value {tag} for key '{error['key']}'")
            INFLUX_QUERY_DROP_INVALID_TAG_KEYS = f'USE "{error["database"]}"; DROP SERIES FROM /.*/ WHERE "{error["key"]}" = \'{tag}\''

            logger.info(INFLUX_QUERY_DROP_INVALID_TAG_KEYS)

def main():
    fix_errors = False
    errors = check_influx_tags()
    logger.info(pprint.pformat(errors))
    if errors and fix_errors:
        get_deletion_queries_invalid_influx_tags(errors)


if __name__ == "__main__":
    main()
