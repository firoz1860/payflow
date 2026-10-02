"""Validate repository paths before a Render Docker build. Requires PyYAML."""

import shlex
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml


def validate(root):
    errors = []
    blueprint = yaml.safe_load((root / "render.yaml").read_text())
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    parent = ET.parse(root / "pom.xml")
    modules = [node.text for node in parent.findall("m:modules/m:module", ns)]
    for module in modules:
        for path in (f"{module}/pom.xml", f"{module}/src"):
            if not (root / path).exists():
                errors.append(f"Missing reactor input: {path}")

    for service in blueprint["services"]:
        if service.get("runtime") != "docker":
            continue
        name = service["name"]
        if service.get("rootDir", ".") != ".":
            errors.append(f"{name}: rootDir must be '.' (all modules share the reactor)")
        if service.get("dockerContext", ".") != ".":
            errors.append(f"{name}: dockerContext must be '.'")
        dockerfile = root / service["dockerfilePath"]
        if not dockerfile.is_file():
            errors.append(f"{name}: Dockerfile is missing: {dockerfile}")
            continue
        for line in dockerfile.read_text().splitlines():
            if not line.lstrip().upper().startswith("COPY "):
                continue
            words = shlex.split(line, comments=True)
            if not words or words[0].upper() != "COPY":
                continue
            if any(word.startswith("--from=") for word in words):
                continue
            sources = [word for word in words[1:] if not word.startswith("--")][:-1]
            for source in sources:
                if not list(root.glob(source)):
                    errors.append(f"{name}: COPY input is missing from repo root: {source}")
    return errors


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    errors = validate(root)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(1)
    print("PASS: all Render Docker contexts and COPY inputs exist at repository root")
