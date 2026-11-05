"""Read-only source inventory for the requested diagram/example comparison."""
from pathlib import Path
import hashlib
import html
import json
import re
import xml.etree.ElementTree as ET

here = Path(__file__).resolve().parent
plugin = here.parents[1]
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
records = {}
for name in ('auction_csl.drawio', 'auction_istar_final_fixed.drawio'):
    path = plugin / 'Core/auction' / name
    root = ET.parse(path).getroot()
    cells = []
    for node in root.iter('mxCell'):
        value = html.unescape(re.sub(r'<[^>]*>', ' ', node.get('value', '')))
        if value.strip() or node.get('edge') == '1':
            cells.append({key: node.get(key, '') for key in ('id', 'parent', 'source', 'target', 'style')} | {'text': value.strip()})
    records[name] = {'path': str(path), 'sha256': digest(path), 'embeddedCslSource': root.get('cslSource', ''), 'cells': cells}
for name in ('auction.csl', 'auction.istar', 'auction-mapping.xml', 'auction.ocl', 'auction.jcm'):
    path = plugin / 'examples/auction' / name
    records[name] = {'path': str(path), 'sha256': digest(path), 'bytes': path.stat().st_size}
xml = ET.parse(plugin / 'examples/auction/auction-mapping.xml').getroot()
records['sourceTaskSegments'] = [dict(node.attrib) for node in xml.iter() if node.tag.rsplit('}', 1)[-1] == 'step']
(here / 'model-audit.json').write_text(json.dumps(records, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print('MODEL_AUDIT_INVENTORY_WRITTEN', here / 'model-audit.json')
