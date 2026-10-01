"""Keep the released loader compatibility configuration in isolated GUI QA."""
from pathlib import Path
import re

def configure_file(path: Path):
    text=path.read_text(encoding='utf-8') if path.exists() else ''
    # Root keys end at the first TOML table. Never write over dependencyOverrides.
    table=re.search(r'(?m)^\s*\[',text)
    root,rest=(text[:table.start()],text[table.start():]) if table else (text,'')
    for key,value in [('earlyWindowControl','false'),('earlyWindowProvider','""'),('versionCheck','false')]:
        pattern=r'(?m)^(\s*'+re.escape(key)+r'\s*=\s*)[^\r\n]*'
        if re.search(pattern,root):root=re.sub(pattern,lambda match:match.group(1)+value,root,count=1)
        else:root=root.rstrip('\r\n')+'\n'+key+' = '+value+'\n'
    path.write_text(root+rest,encoding='utf-8')

