#!/usr/bin/env python3
"""Gate 4b: the authored contract is the source of truth.

Compares the OpenAPI document Quarkus generates from the running code against the hand-authored
contract. Divergence means the code is defective, not that the contract needs regenerating.
"""
import sys, pathlib, yaml

AUTHORED = pathlib.Path("specs/001-note-management-api/contracts/openapi.yaml")
GENERATED = pathlib.Path("target/generated/openapi.yaml")


def operations(doc):
    out = set()
    for path, item in (doc.get("paths") or {}).items():
        for method, op in item.items():
            if method in ("get", "post", "put", "patch", "delete"):
                out.add((method.upper(), path, tuple(sorted((op.get("responses") or {}).keys()))))
    return out


def main():
    if not AUTHORED.exists():
        print(f"::error::authored contract missing at {AUTHORED}")
        return 1
    if not GENERATED.exists():
        print(f"::error::generated contract missing at {GENERATED}; run `mvn -DskipTests package` first")
        return 1

    authored = yaml.safe_load(AUTHORED.read_text())
    generated = yaml.safe_load(GENERATED.read_text())

    a_ops = {(m, p) for m, p, _ in operations(authored)}
    # Quarkus serves the versioned prefix on the resources themselves, so paths align directly.
    g_ops = {(m, p) for m, p, _ in operations(generated)}

    missing = sorted(a_ops - g_ops)
    extra = sorted(g_ops - a_ops)

    for m, p in missing:
        print(f"::error::contract declares {m} {p} but the service does not implement it")
    for m, p in extra:
        print(f"::error::service exposes {m} {p} which the contract does not declare")

    if missing or extra:
        print(f"\n{len(missing)} undelivered, {len(extra)} undeclared. The authored contract is correct.")
        return 1
    print(f"contract and implementation agree on all {len(a_ops)} operations")
    return 0


if __name__ == "__main__":
    sys.exit(main())
