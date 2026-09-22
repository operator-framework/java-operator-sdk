#!/usr/bin/env python3
"""Print the version of a Maven pom, falling back to the parent's version.

Used by the release workflows instead of `help:evaluate`, whose banner and log
output would have to be filtered off stdout first.
"""

import sys
import xml.etree.ElementTree as ET

NS = "{http://maven.apache.org/POM/4.0.0}"


def main(path):
    root = ET.parse(path).getroot()
    version = root.findtext(NS + "version")
    if version is None:
        version = root.findtext(NS + "parent/" + NS + "version")
    if version is None:
        raise SystemExit("no version and no parent version in " + path)
    print(version.strip())


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: pom-version.py <path-to-pom.xml>")
    main(sys.argv[1])
