"""Reject equivalent Flyway versions within each service's migration directory."""
from collections import defaultdict
from pathlib import Path


def version_key(filename):
    parts = [int(part) for part in filename.split('__')[0][1:].replace('_', '.').split('.')]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)


collisions = []
for directory in Path('.').glob('*/src/main/resources/db/migration'):
    versions = defaultdict(list)
    for migration in directory.glob('V*__*.sql'):
        versions[version_key(migration.name)].append(migration.name)
    for version, files in versions.items():
        if len(files) > 1:
            collisions.append((str(directory), version, files))
for collision in collisions:
    print('Duplicate Flyway version:', collision)
raise SystemExit(bool(collisions))
