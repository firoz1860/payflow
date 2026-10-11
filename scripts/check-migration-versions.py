from pathlib import Path
from collections import defaultdict
bad=[]
for root in Path('.').glob('*/src/main/resources/db/migration'):
 versions=defaultdict(list)
 for file in root.glob('V*__*.sql'):versions[tuple(int(part) for part in file.name.split('__')[0][1:].replace('_','.').split('.'))].append(file.name)
 for v,files in versions.items():
  if len(files)>1:bad.append((str(root),v,files))
for item in bad:print(item)
raise SystemExit(bool(bad))
