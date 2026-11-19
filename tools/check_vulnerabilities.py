#!/usr/bin/env python3
"""Query OSV for locked Maven packages. A network failure is a failed check.
This checks published advisories for mapped packages, not malware or every native dependency.
"""
import json,pathlib,sys,urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]

def main():
    deps=json.loads((ROOT/'tools/dependencies.lock.json').read_text())['dependencies']
    packages=sorted({(d['maven'],d['version']) for d in deps if d.get('maven')})
    queries=[{'package':{'name':name,'ecosystem':'Maven'},'version':version} for name,version in packages]
    request=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=json.dumps({'queries':queries}).encode(),headers={'Content-Type':'application/json','User-Agent':'HFML-security-check/1.0'},method='POST')
    with urllib.request.urlopen(request,timeout=60) as response: result=json.load(response)
    if len(result.get('results',[]))!=len(packages):raise RuntimeError('Unexpected OSV response')
    found=[]
    for (name,version),entry in zip(packages,result['results']):
        for vuln in entry.get('vulns',[]):found.append((name,version,vuln['id']))
    if found:
        for name,version,identifier in found: print('ADVISORY:',name,version,identifier)
        raise RuntimeError('Review published advisories and update affected dependencies before release')
    print('PASS OSV:',len(packages),'locked Maven packages; no matching published advisories returned')

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,KeyError) as error:print('ADVISORY CHECK FAILED:',error,file=sys.stderr);sys.exit(1)
