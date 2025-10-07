#!/usr/bin/env python3
"""Helper to inspect the Homebox OpenAPI (Swagger) specification.

Usage:
  python openapi_helper.py                # list all endpoints
  python openapi_helper.py <path>         # show details for a single endpoint
  python openapi_helper.py <path> --method <verb>  # show details for a specific operation
"""
import argparse
import json
import sys
from typing import Optional
from pprint import pprint

SPEC_FILE = "homebox-swagger.json"


def load_spec():
    with open(SPEC_FILE, "r", encoding="utf-8") as handle:
        return json.load(handle)


def list_endpoints(spec, verbose: bool = False):
    for path, operations in spec.get("paths", {}).items():
        if not verbose:
            print(path)
            continue

        methods = [
            method.upper()
            for method in sorted(operations)
            if not method.startswith("x-")
        ]
        print(f"{path} ({', '.join(methods)})")
        for method in sorted(operations):
            if method.startswith("x-"):
                continue
            summary = operations[method].get("summary") or ""
            print(f"  {method.upper():<6} {summary}")


def show_endpoint(spec, path: str, method: Optional[str] = None):
    info = spec.get("paths", {}).get(path)
    if info is None:
        print(f"Endpoint {path} not found")
        return

    if method is None:
        pprint(info)
        return

    op = info.get(method.lower())
    if op is None:
        available = ", ".join(sorted(m.upper() for m in info if not m.startswith("x-")))
        print(f"Method {method.upper()} not found for {path}. Available: {available}")
        return

    pprint(op)


def main(argv):
    parser = argparse.ArgumentParser(description="Inspect the Homebox OpenAPI spec")
    parser.add_argument("path", nargs="?", help="Endpoint path to inspect")
    parser.add_argument("--method", help="HTTP method to inspect for a given path")
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Show HTTP methods and summaries when listing endpoints",
    )
    args = parser.parse_args(argv)

    spec = load_spec()

    if args.path is None:
        list_endpoints(spec, verbose=args.verbose)
    else:
        show_endpoint(spec, args.path, method=args.method)


if __name__ == "__main__":
    main(sys.argv[1:])
