import json,pathlib,hashlib,shutil
base=pathlib.Path(__file__).resolve().parent
use=base.parents[2]
data=json.loads((base/'exact-milestone-comparison.json').read_text())
milestones=data[0]['milestones']
assert 'a2' in data[0]['label'] and len(milestones)==7
groups=[]
for item in milestones:
    if groups and all(item[key]==groups[-1][-1][key] for key in ['cslHash','scopedObjectsHash','scopedLinksHash']):
        groups[-1].append(item)
    else:groups.append([item])
numbers=[[item['milestone'] for item in group] for group in groups]
assert numbers==[[1],[2,3],[4,5,6],[7]]
source=use/'use-assembly/target/new/use-plugin/target/owned-runtime/45559bdd-a732-47ea-a3bb-acc47a30bf30/producer.stdout.log'
shutil.copyfile(source,base/'producer.stdout.snapshot.log')
def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
installed=use/'use-assembly/target/new/lib/plugins/use-jacamo-plugin-1.0.1.jar'
built=use/'use-plugin/target/use-plugin-1.0.1.jar'
assert digest(installed)==digest(built)=='61ed0afa669d952f7e6dd5d2913c24fc811cb6ae5545c9dcb10764d1eecd0a62'
receipt={'result':'EXACT_INSTALLED_REPLAY_CONFIRMED','stateGroups':numbers,'execution':data[0]['label'],
         'installedPlugin':str(installed),'installedPluginSha256':digest(installed),
         'productionChanged':False,'newLiveRun':False,'files':[]}
for path in sorted(base.iterdir()):
    if path.is_file() and path.name!='diagnosis-receipt.json':receipt['files'].append({'path':str(path),'sha256':digest(path)})
(base/'diagnosis-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf8')
print(receipt['result'],receipt['execution'],numbers)
