Use `openapi_helper.py` to inspect the Homebox OpenAPI spec without loading the full file into context.
Examples:
- `python openapi_helper.py -v` lists endpoints with HTTP verbs and summaries.
- `python openapi_helper.py /v1/locations --method get` prints the schema for a specific operation.
- Combine with tools like `sed`, `jq`, or shell pipes to focus on relevant sections, e.g. `python openapi_helper.py /v1/locations --method get | sed -n '1,40p'`.

## Code formatting
- Kotlin files use tabs for indentation; see `.editorconfig` for all style rules.
- Run `./gradlew ktlintFormat` before committing to apply the project-specific Kotlin style.

## Testing
- Add tests for new features whenever possible.
- Use `kotlinx.coroutines.test` (e.g. `runTest`) for coroutine-based code.
- Run `./gradlew check  --parallel --console=plain` before committing.
