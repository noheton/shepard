import json
import sys


def main(input_spec: str, output_spec: str):
    with open(input_spec) as fp:
        data = fp.read()
        openapi_spec = json.loads(data)

    for schema in openapi_spec["components"]["schemas"]:
        if "required" in openapi_spec["components"]["schemas"][schema]:
            readonly: list[str] = [
                k
                for k, v in openapi_spec["components"]["schemas"][schema][
                    "properties"
                ].items()
                if "readOnly" in v
            ]
            nullable: list[str] = [
                k
                for k, v in openapi_spec["components"]["schemas"][schema][
                    "properties"
                ].items()
                if "nullable" in v
            ]
            required: list[str] = openapi_spec["components"]["schemas"][schema][
                "required"
            ]
            truly_required = list(set(required) - set(readonly) - set(nullable))
            openapi_spec["components"]["schemas"][schema]["required"] = truly_required

    with open(output_spec, "w") as fp:
        fp.write(json.dumps(openapi_spec, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("You need to specify an input and an output filepath!", file=sys.stderr)
        sys.exit(1)
    main(*sys.argv[1:])
