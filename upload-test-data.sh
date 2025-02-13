#!/bin/bash

# Add data
curl -X 'PATCH' \
  'http://localhost:8080/shepard/api/spatialDataContainer/1111/payload?databaseType=POSTGIS' \
  -H 'accept: application/json' \
  -H 'X-API-KEY: eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvc2hlcGFyZC9hcGkvIiwibmJmIjoxNzM5MzcwNDY2LCJpYXQiOjE3MzkzNzA0NjYsImp0aSI6IjY2YTAyZDhkLTdkYmItNDM1NS04NDA1LTJmOTIzODJlNjA1OCJ9.q7hcW92UELE3R26FyKMDbX0tarqlp65D1vX53AyjL3ozqRtu3RpmN9jF08SmoeunQwnY5BRZ6paTGpv0fZN_4BlIvJGfOFEd5WN4mGSr_-IPINUWHp65C3b_zKH-KssyiDZyNnIghig6RMo5SV-KBSkaR9jtgmc5A9W30C8hHO_8l3M0jdSqyDBGLj9N3HoSuwcuPyp9iXox8_tSbVpRW5YKK5CnhsHefKUgBmY69J5owpcNVJSAOk5YDKTUKvpOLhz1uU8Ymaq1O9uiU0EmOjGb0j_IikyGOHkMU8Lz3Ts04nv3UOC7Ici9yvqUVTC5dKeFMrL3NZiFZ7sfg5rqDw' \
  -H 'Content-Type: application/json' \
  -d '[
  {
    "timestamp": 123456,
    "x": 2,
    "y": 2,
    "z": 2,
    "measurements": {
      "value1": 10,
      "value2": "10"
    },
    "metadata": {
      "additionalProp1": "string1",
      "additionalProp2": "string2",
      "additionalProp3": "string3"
    }
  }
]'

# Bounding Box Query
curl -X 'POST' \
  'http://localhost:8080/shepard/api/spatialDataContainer/1111/payload?databaseType=POSTGIS' \
  -H 'accept: application/json' \
  -H 'X-API-KEY: eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvc2hlcGFyZC9hcGkvIiwibmJmIjoxNzM5MzcwNDY2LCJpYXQiOjE3MzkzNzA0NjYsImp0aSI6IjY2YTAyZDhkLTdkYmItNDM1NS04NDA1LTJmOTIzODJlNjA1OCJ9.q7hcW92UELE3R26FyKMDbX0tarqlp65D1vX53AyjL3ozqRtu3RpmN9jF08SmoeunQwnY5BRZ6paTGpv0fZN_4BlIvJGfOFEd5WN4mGSr_-IPINUWHp65C3b_zKH-KssyiDZyNnIghig6RMo5SV-KBSkaR9jtgmc5A9W30C8hHO_8l3M0jdSqyDBGLj9N3HoSuwcuPyp9iXox8_tSbVpRW5YKK5CnhsHefKUgBmY69J5owpcNVJSAOk5YDKTUKvpOLhz1uU8Ymaq1O9uiU0EmOjGb0j_IikyGOHkMU8Lz3Ts04nv3UOC7Ici9yvqUVTC5dKeFMrL3NZiFZ7sfg5rqDw' \
  -H 'Content-Type: application/json' \
  -d '{
  "geometryFilter": {
    "type": "AXIS_ALIGNED_BOUNDING_BOX",
  "minX" : 0,
  "minY" : 0,
  "minZ" : 0,
  "maxX" : 10,
  "maxY" : 10,
  "maxZ" : 10
  }
}'

# Bounding Sphere Query
curl -X 'POST' \
  'http://localhost:8080/shepard/api/spatialDataContainer/1111/payload?databaseType=POSTGIS' \
  -H 'accept: application/json' \
  -H 'X-API-KEY: eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvc2hlcGFyZC9hcGkvIiwibmJmIjoxNzM5MzcwNDY2LCJpYXQiOjE3MzkzNzA0NjYsImp0aSI6IjY2YTAyZDhkLTdkYmItNDM1NS04NDA1LTJmOTIzODJlNjA1OCJ9.q7hcW92UELE3R26FyKMDbX0tarqlp65D1vX53AyjL3ozqRtu3RpmN9jF08SmoeunQwnY5BRZ6paTGpv0fZN_4BlIvJGfOFEd5WN4mGSr_-IPINUWHp65C3b_zKH-KssyiDZyNnIghig6RMo5SV-KBSkaR9jtgmc5A9W30C8hHO_8l3M0jdSqyDBGLj9N3HoSuwcuPyp9iXox8_tSbVpRW5YKK5CnhsHefKUgBmY69J5owpcNVJSAOk5YDKTUKvpOLhz1uU8Ymaq1O9uiU0EmOjGb0j_IikyGOHkMU8Lz3Ts04nv3UOC7Ici9yvqUVTC5dKeFMrL3NZiFZ7sfg5rqDw' \
  -H 'Content-Type: application/json' \
  -d '{
  "geometryFilter": {
    "type": "BOUNDING_SPHERE",
    "r" : 10,
    "centerX" : 0,
    "centerY" : 0,
    "centerZ" : 0
  }
}'

# Knn Query
curl -X 'POST' \
  'http://localhost:8080/shepard/api/spatialDataContainer/1111/payload?databaseType=POSTGIS' \
  -H 'accept: application/json' \
  -H 'X-API-KEY: eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvc2hlcGFyZC9hcGkvIiwibmJmIjoxNzM5MzcwNDY2LCJpYXQiOjE3MzkzNzA0NjYsImp0aSI6IjY2YTAyZDhkLTdkYmItNDM1NS04NDA1LTJmOTIzODJlNjA1OCJ9.q7hcW92UELE3R26FyKMDbX0tarqlp65D1vX53AyjL3ozqRtu3RpmN9jF08SmoeunQwnY5BRZ6paTGpv0fZN_4BlIvJGfOFEd5WN4mGSr_-IPINUWHp65C3b_zKH-KssyiDZyNnIghig6RMo5SV-KBSkaR9jtgmc5A9W30C8hHO_8l3M0jdSqyDBGLj9N3HoSuwcuPyp9iXox8_tSbVpRW5YKK5CnhsHefKUgBmY69J5owpcNVJSAOk5YDKTUKvpOLhz1uU8Ymaq1O9uiU0EmOjGb0j_IikyGOHkMU8Lz3Ts04nv3UOC7Ici9yvqUVTC5dKeFMrL3NZiFZ7sfg5rqDw' \
  -H 'Content-Type: application/json' \
  -d '{
  "geometryFilter": {
    "type": "K_NEAREST_NEIGHBOR",
    "k" : 1,
    "x" : 0,
    "y" : 0,
    "z" : 0
  }
}'
