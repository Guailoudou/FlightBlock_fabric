"""Convert generated 1.16.5 sources to Forge's MCP class names.

The small class-name subset joins Mojang's 1.16.5 client mappings with
MCPConfig 1.16.5-20210115.111550 (config/joined.tsrg), distributed at
https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/ .
Member names remain official; ForgeGradle remaps them for the final JAR.
"""
import json
import re
import sys
from pathlib import Path


def convert(source, classes):
    simple = {}

    def imports(match):
        name = match[1]
        if name.endswith('.*'):
            package = name[:-2]
            used = [old for old in classes if '$' not in old
                    and old.rsplit('.', 1)[0] == package
                    and re.search(r'\b' + re.escape(old.rsplit('.', 1)[1]) + r'\b', source)]
            for old in used:
                simple[old.rsplit('.', 1)[1]] = classes[old].rsplit('.', 1)[1]
            return '\n'.join('import ' + old + ';' for old in used)
        if name in classes:
            simple[name.rsplit('.', 1)[1]] = classes[name].rsplit('.', 1)[1]
        return match[0]

    source = re.sub(r'import (net\.minecraft[\w.*]+);', imports, source)
    if simple:
        # Skip qualified names so a newly mapped Container is not renamed again.
        source = re.sub(r'(?<![\w.$/])(' + '|'.join(map(re.escape, simple)) + r')\b',
                        lambda m: simple[m[1]], source)
    qualified = {}
    for old, new in classes.items():
        qualified[old.replace('$', '.')] = new.replace('$', '.')
        qualified[old.replace('.', '/')] = new.replace('.', '/')
    pattern = '|'.join(re.escape(name) for name in sorted(qualified, key=len, reverse=True))
    return re.sub('(?:' + pattern + r')(?![\w$])', lambda m: qualified[m[0]], source)


if __name__ == '__main__':
    classes = json.loads(Path(__file__).with_name('forge16-classnames.json').read_text(encoding='utf-8'))
    for path in Path(sys.argv[1]).rglob('*.java'):
        path.write_text(convert(path.read_text(encoding='utf-8'), classes), encoding='utf-8')
