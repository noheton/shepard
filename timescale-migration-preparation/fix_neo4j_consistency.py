from config import NEO4J_AUTH, NEO4J_URI
from loguru import logger
from neo4j import GraphDatabase


def fix_neo4j(replacements):
    logger.info(f"{replacements=}")
    with GraphDatabase.driver(NEO4J_URI, auth=NEO4J_AUTH) as driver:
        driver.verify_connectivity()
        for replacement in replacements:
            logger.info(f"Processing \n {replacement}")
            # Neo4j data model conversion
            if replacement["key"] == "symbolic_name":
                replacement["key"] = "symbolicName"
            # escape neo4j spaces
            replacement["old_tag"] = replacement["old_tag"].replace(" ", "\ ")

            query = (
                f"MATCH (ts_c:TimeseriesContainer)-[*..2]-(n:Timeseries) "
                f'WHERE ts_c.database = "{replacement["database"]}" '
                f'AND n.measurement="{replacement["measurement"]}" and n.{replacement["key"]} ="{replacement["old_tag"]}" '
                f'SET n.{replacement["key"]} = "{replacement["new_tag"]}" '
                "return DISTINCT n"
            )

            logger.info(query)

            result = driver.execute_query(query)
            logger.info(result)
