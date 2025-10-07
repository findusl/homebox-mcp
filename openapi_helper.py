#!/usr/bin/env python3
"""Simple helper to inspect the Firefly III OpenAPI specification.

Usage:
  python openapi_helper.py                # list all endpoints
  python openapi_helper.py <path>         # show details for a single endpoint
  python openapi_helper.py <path> --request  # perform request to the endpoint using BASE_URL and ACCESS_TOKEN
"""
import argparse
import os
import sys
import json
from typing import Optional, Union
from pprint import pprint

import requests
from prance import ResolvingParser

SPEC_FILE = "firefly-iii-6.3.0-v1.yaml"


def load_spec():
    parser = ResolvingParser(SPEC_FILE)
    return parser.specification


def list_endpoints(spec):
    for path in spec.get("paths", {}):
        print(path)


def show_endpoint(spec, path):
    info = spec.get("paths", {}).get(path)
    if info is None:
        print(f"Endpoint {path} not found")
        return
    pprint(info)


def _choose_media_type(content: dict) -> Optional[str]:
    if not content:
        return None
    for media_type in content:
        if "json" in media_type:
            return media_type
    return next(iter(content))


def _censor(value: Union[str, bytes], base_url: str, token: str) -> Union[str, bytes]:
    """Replace any occurrence of BASE_URL or ACCESS_TOKEN with placeholders."""
    if isinstance(value, bytes):
        if base_url:
            value = value.replace(base_url.encode(), b"<BASE_URL>")
        if token:
            value = value.replace(token.encode(), b"<ACCESS_TOKEN>")
    else:
        if base_url:
            value = value.replace(base_url, "<BASE_URL>")
        if token:
            value = value.replace(token, "<ACCESS_TOKEN>")
    return value


def fetch_endpoint(
    spec,
    path,
    method="get",
    accept_override=None,
    content_override=None,
    data=None,
):
    base_url = os.environ.get("BASE_URL")
    token = os.environ.get("ACCESS_TOKEN")
    if not base_url or not token:
        raise RuntimeError("BASE_URL and ACCESS_TOKEN environment variables must be set")
    path_item = spec.get("paths", {}).get(path)
    if path_item is None:
        raise RuntimeError(f"Endpoint {path} not found in spec")
    op = path_item.get(method.lower())
    if op is None:
        raise RuntimeError(f"Method {method} not supported for {path}")

    accept = accept_override
    if accept is None:
        responses = op.get("responses", {})
        for code in sorted(responses):
            if str(code).startswith("2"):
                content = responses[code].get("content", {})
                accept = _choose_media_type(content)
                break

    content_type = content_override
    if content_type is None:
        request_body = op.get("requestBody")
        if request_body:
            content = request_body.get("content", {})
            content_type = _choose_media_type(content)

    url = f"{base_url.rstrip('/')}/api{path}"
    headers = {"Authorization": f"Bearer {token}"}
    if accept:
        headers["Accept"] = accept
    if content_type and method.lower() in {"post", "put", "patch"}:
        headers["Content-Type"] = content_type

    response = requests.request(method.upper(), url, headers=headers, data=data)
    response.raise_for_status()
    return response


def main(argv):
    parser = argparse.ArgumentParser(description="Inspect Firefly III OpenAPI spec")
    parser.add_argument("path", nargs="?", help="Endpoint path to inspect")
    parser.add_argument("--request", action="store_true", help="Perform request to the given path using BASE_URL and ACCESS_TOKEN")
    parser.add_argument("--method", default="get", help="HTTP method for --request")
    parser.add_argument("--accept", help="Override Accept header")
    parser.add_argument("--content-type", help="Override Content-Type header")
    parser.add_argument(
        "--data",
        help="Request body to send. Prefix with @ to read from file",
    )
    args = parser.parse_args(argv)

    spec = load_spec()

    if args.path is None:
        list_endpoints(spec)
    elif args.request:
        body = None
        if args.data:
            body = (
                open(args.data[1:], "rb").read()
                if args.data.startswith("@")
                else args.data
            )
        resp = fetch_endpoint(
            spec,
            args.path,
            method=args.method,
            accept_override=args.accept,
            content_override=args.content_type,
            data=body,
        )
        ctype = resp.headers.get("Content-Type", "")
        base_url = os.environ.get("BASE_URL", "")
        token = os.environ.get("ACCESS_TOKEN", "")
        if "json" in ctype:
            text = json.dumps(resp.json(), indent=2)
            print(_censor(text, base_url, token))
        else:
            content = _censor(resp.content, base_url, token)
            sys.stdout.buffer.write(content)
    else:
        show_endpoint(spec, args.path)


if __name__ == "__main__":
    main(sys.argv[1:])
