Before committing run ktlintFormat.

Use `openapi_helper.py` to inspect the Homebox OpenAPI spec without loading the full file into context.
Examples:
- `python openapi_helper.py -v` lists endpoints with HTTP verbs and summaries.
- `python openapi_helper.py /v1/locations --method get` prints the schema for a specific operation.
- Combine with tools like `sed`, `jq`, or shell pipes to focus on relevant sections, e.g. `python openapi_helper.py /v1/locations --method get | sed -n '1,40p'`.
