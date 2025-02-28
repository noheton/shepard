from influxdb import InfluxDBClient
from loguru import logger
from neo4j import GraphDatabase
from config import (
    INFLUX_HOST,
    INFLUX_PASSWORD,
    INFLUX_PORT,
    INFLUX_USER,
    NEO4J_AUTH,
    NEO4J_URI,
)


def check_connectivity():
    with GraphDatabase.driver(NEO4J_URI, auth=NEO4J_AUTH) as driver:
        try:
            driver.verify_connectivity()
            logger.info("neo4j connection successful")
        except Exception:
            logger.critical("neo4j connection could not be established")
            exit(-1)
    client = InfluxDBClient(INFLUX_HOST, INFLUX_PORT, INFLUX_USER, INFLUX_PASSWORD)
    try:
        logger.info(client.ping())
        logger.info("influx connection successful")
    except Exception:
        logger.critical("influx connection could not be established")
        exit(-1)


if __name__ == "__main__":
    check_connectivity()
