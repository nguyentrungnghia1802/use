"""Synchronize reviewed authored artifacts; preserve diagram cell IDs and layout.

This is an example-maintenance tool, never a runtime mapping generator. The user
selected the visible Core diagrams as authority on 2026-11-05.
"""
from pathlib import Path
import hashlib
import html
import re
import xml.etree.ElementTree as ET

plugin = Path(__file__).resolve().parents[2]
example = plugin / 'examples/auction'
core = plugin / 'Core/auction'
sha = lambda data: hashlib.sha256(data).hexdigest()

for name in ('auction.csl','auction.istar','auction.ocl'):
    path=example/name
    path.write_text(path.read_text(encoding='utf-8'),encoding='utf-8',newline='\n')

def set_attribute(text, key, value):
    encoded = html.escape(value, quote=True).replace('\n', '&#10;').replace('\r', '&#13;')
    pattern = r'\b' + re.escape(key) + r'="[^"]*"'
    head, tail = text.split('>', 1)
    head = re.sub(pattern, lambda _: key + '="' + encoded + '"', head) if re.search(pattern, head) else head + ' ' + key + '="' + encoded + '"'
    return head + '>' + tail

for model, canonical, diagram, attribute in [
    ('auction.csl', 'auction_csl.csl', 'auction_csl.drawio', 'cslSource'),
    ('auction.istar', 'auction_istar_final.istar', 'auction_istar_final_fixed.drawio', 'istarSource'),
]:
    source = (example / model).read_text(encoding='utf-8')
    (core / canonical).write_text(source, encoding='utf-8', newline='\n')
    path = core / diagram
    text = path.read_text(encoding='utf-8')
    # Only the root metadata changes; all geometry, semantic edges and labels stay.
    text = set_attribute(text, attribute, source)
    text = set_attribute(text, 'sourceAuthority', 'VISIBLE_CORE_DIAGRAM_USER_CONFIRMED_2026_11_05')
    if attribute == 'cslSource':
        text = set_attribute(text, 'semanticAudit', 'The CSL capability viewpoint is represented by a singleton-player OrgCtx. No Role inheritance. Auction belongs to auctionGroup.entities. Atom is projected as String; optional typed beliefs require unambiguous qualified observations and otherwise remain undefined.')
        text = set_attribute(text, 'beliefViewUpdate', 'Optional scalar belief_best_bid is Real; Atom maps to String. Multiple qualified values are not collapsed. No belief observer is requested by this example.')
    path.write_text(text, encoding='utf-8', newline='\n')
    assert ET.fromstring(text).get(attribute) == source

for directory in (example, plugin / 'examples/auction-false'):
    if directory != example:
        for name in ('auction.csl', 'auction.istar', 'auction.ocl'):
            (directory / name).write_bytes((example / name).read_bytes())
    path = directory / 'auction-mapping.xml'
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'^.*<self target="O_bid_accepted"[^\n]*\n', '', text, flags=re.M)
    text = re.sub(r'^.*<elementRuntime target="O_bid_(?:accepted|attempt)"[^\n]*\n', '', text, flags=re.M)
    text = re.sub(r'^.*<acceptedFailure term="\.fail"[^\n]*\n', '', text, flags=re.M)
    text = re.sub(r'^.*<dependencyPopulation target="D_capability_[^\n]*\n', '', text, flags=re.M)
    text = re.sub(r'(<elementRuntime target="(?:A_start|A_decide|P_bid)"[^>]*?)derived="false"',r'\1derived="true"',text)
    text=re.sub(r'(<actor actor="auction_capabilities"[^>]*?)kind="RUNTIME_AGENT"',r'\1kind="PHYSICAL_PLAYER_VIEW"',text)
    if directory!=example:
        declarations=re.findall(r'^    <goalRealization[^\n]*\n',(example/'auction-mapping.xml').read_text(encoding='utf-8'),re.M)
        text=re.sub(r'^    <goalRealization[^\n]*\n','',text,flags=re.M)
        text=text.replace('    <dependencyPopulation target="D_bid" quantifier="ANY" />\n','    <dependencyPopulation target="D_bid" quantifier="ANY" />\n'+''.join(declarations))
    text = text.replace('<resource target="A_artifact" column="domain" type="Auction" />','<resource target="AuctionArtifact" column="domain" type="Auction" />')
    text = re.sub(r'^.*<resource target="P_artifact"[^\n]*\n', '', text, flags=re.M)
    for attr, name in [('cslSha256', 'auction.csl'), ('istarSha256', 'auction.istar')]:
        text = re.sub(attr + r'="[0-9a-f]{64}"', attr + '="' + sha((directory / name).read_bytes()) + '"', text)
    text=re.sub(r'<ocl sha256="[0-9a-f]{64}"', '<ocl sha256="'+sha((directory/'auction.ocl').read_bytes())+'"', text)
    path.write_text(text, encoding='utf-8', newline='\n')
    ET.parse(path)
contracts=[]
for name in ('auction.csl','auction.istar'):
    contracts.extend(re.findall(r'/\* @use-native-ocl 1\n([\s\S]*?)\*/',(example/name).read_text(encoding='utf-8')))
(core/'auction_constraints_final_fixed.ocl').write_text('-- Canonical contract inventory for review; executable blocks are embedded in the models.\n-- Do not select this inventory as additional OCL beside the same embedded clauses.\n-- Source-confirmed ordering is explicitly distinguished from internal MOISE truth-change time.\n\n'+'\n'.join(contracts),encoding='utf-8',newline='\n')
print('CORE_EMBEDDED_SOURCE_AND_BOTH_EXAMPLE_MODELS_SYNCHRONIZED')
