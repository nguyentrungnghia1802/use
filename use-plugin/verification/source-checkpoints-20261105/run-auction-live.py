"""Run the compiled live-test main with the last Surefire reactor classpath.

Same unchanged-source/native-verdict assertions as VerifiedCheckpointsAuctionIT;
this does not claim another JUnit suite run or any desktop interaction.
"""
from pathlib import Path
import json,os,subprocess,time,zipfile,xml.etree.ElementTree as ET

plugin=Path(__file__).resolve().parents[2]
report=plugin/'target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.VerifiedCheckpointsAuctionIT.xml'
properties={p.get('name'):p.get('value') for p in ET.parse(report).findall('./properties/property')}
classpath=properties['java.class.path']
evidence=Path(__file__).resolve().parent/('live-auction-final-'+str(int(time.time()*1000)))
evidence.mkdir()
agent=evidence/'source-evidence-input.jar'
with zipfile.ZipFile(agent,'w') as archive:
    archive.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nPremain-Class: org.tzi.use.plugins.jacamo.AuctionEvidenceInputAgent\r\n\r\n')
    for name in ['AuctionEvidenceInputAgent','AuctionEvidenceInputAgent$Input']:
        resource='org/tzi/use/plugins/jacamo/'+name+'.class'
        archive.write(plugin/'target/test-classes'/resource,resource)
env=dict(os.environ)
env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' "-javaagent:'+str(agent)+'='+str(evidence)+'"'
print('LIVE_AUCTION_EVIDENCE',evidence,flush=True)
with (evidence/'consumer.log').open('w') as output:
    result=subprocess.run(['java','-Djava.awt.headless=true','-Duse.jacamo.runtime.journal.maxBytes=536870912','-cp',classpath,'org.tzi.use.plugins.jacamo.VerifiedCheckpointsAuctionIT',str(evidence)],cwd=plugin,env=env,stdout=output,stderr=subprocess.STDOUT,timeout=480)
(evidence/'runner-receipt.json').write_text(json.dumps({'method':'COMPILED_LIVE_TEST_MAIN_WITH_SUREFIRE_CLASSPATH','exitCode':result.returncode,'manualDesktopAcceptance':False,'source':'VerifiedCheckpointsAuctionIT.java'},indent=2)+'\n')
result.check_returncode()
print('LIVE_AUCTION_NATIVE_CHECKPOINTS_MAIN_PASS',flush=True)
