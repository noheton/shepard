from logging.handlers import RotatingFileHandler
import sys
from loguru import logger
import os

logger.remove(0)
logger.add(sys.stderr, level="INFO")
handler = RotatingFileHandler(
    "log/log.log",
    mode="a",
    # maxBytes=1024 * 1024 * 100,
    backupCount=5,
    encoding="utf-8",
)
handler.doRollover()
logger.add(
    sink=handler,
    level="DEBUG",
    colorize=False,
    backtrace=True,
    diagnose=True,
)

INFLUX_HOST = "influxdb"
INFLUX_PORT = "8086"
INFLUX_USER = "admin"
INFLUX_PASSWORD = None
INFLUX_CHUNK_SIZE = 1_000_000_000
INFLUX_TIME_CHUNK_SIZE = 1_000_000_000 * 60 * 60 * 24 * 1  # 1 day in nanoseconds
INFLUX_BATCH_SIZE = 100_000
INFLUX_TIMESERIES_KEYS = ["symbolic_name", "device", "location"]

NEO4J_URI = "neo4j://neo4j:7687"
NEO4J_AUTH = None

try:
    NEO4J_AUTH = ("neo4j", os.environ["NEO4J_PW"])
    INFLUX_PASSWORD = os.environ["INFLUX_PW"]
    logger.info("got credentials from environment")
except KeyError as e:
    logger.critical(f"environment variable {e} not set.")
    logger.critical("aborting.")
    exit(1)
