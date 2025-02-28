import json
import time
from loguru import logger
from check_connections import check_connectivity
from find_invalid_influx_series import check_influx_tags
from fix_neo4j_consistency import fix_neo4j
from rename_invalid_influx_tags import fix_invalid_tags


def main():
    check_connectivity()
    errors = check_influx_tags()
    if errors:
        logger.warning(
            "Errors have been detected, migration from influx to timescaleDB can NOT be performed"
        )
    else:
        logger.success(
            "No errors detected, migration from influx to timescaleDB can be performed"
        )


if __name__ == "__main__":
    main()
