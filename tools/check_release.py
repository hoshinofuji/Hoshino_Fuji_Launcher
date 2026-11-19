#!/usr/bin/env python3
"""Fail closed on secret-like strings, private user paths and unexpected release files.
A conservative local check; it is not antivirus or a complete secret detector.
"""
import argparse, hashlib, json, pathlib, re, struct, sys, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
TOP={'README.md','LICENSE','LICENSE-ASSETS','THIRD_PARTY_NOTICES.md','SECURITY.md','CONTRIBUTING.md','CHANGELOG.md','Launch.command','Launch.bat','.gitignore','.gitattributes'}
DIRS={'src','tools','art','licenses','docs','.github'}
FORBIDDEN={'__pycache__','.DS_Store','.env','node_modules','.venv','instances','saves','logs','accounts.json','launcher_accounts.json','launcher_profiles.json','usercache.json','servers.dat','options.txt'}
PATTERNS=[
 ('private-key',re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----')),
 ('GitHub-token',re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})')),
 ('AWS-access-key',re.compile(rb'(?:AKIA|ASIA)[A-Z0-9]{16}')),
 ('webhook',re.compile(rb'https://(?:discord(?:app)?\.com/api/webhooks/|hooks\.slack\.com/services/)[^\s"\']+')),
 ('private-user-path',re.compile(rb'(?:/Users/|/home/)[A-Za-z0-9_.-]+/|[A-Za-z]:\\Users\\[^\\\s]+\\')),
 ('assigned-token',re.compile(rb'(?i)(?:access_token|refresh_token|client_secret|api_key|password)[\"\']?\s*[:=]\s*[\"\'][A-Za-z0-9_+./=-]{24,}[\"\']')),
 ('embedded-credential',re.compile(rb'https?://[^/\s:"\']+:[^/@\s"\']+@')),
]

def source_files(root):
    for first in sorted(root.iterdir()):
        if first.name in TOP:
            if first.is_symlink(): raise RuntimeError('Source symlink: '+str(first))
            yield first
        elif first.name in DIRS:
            for p in sorted(first.rglob('*')):
                if p.is_symlink(): raise RuntimeError('Source symlink: '+str(p))
                if any(part in FORBIDDEN for part in p.relative_to(root).parts):
                    if '__pycache__' in p.parts: continue
                    raise RuntimeError('Forbidden source entry: '+str(p.relative_to(root)))
                if p.is_file(): yield p
        elif first.name not in {'.git','.cache','build','dist','release','.idea','.vscode'}:
            raise RuntimeError('Unexpected top-level entry: '+first.name)

def check_data(name,data,artifact=False):
    if len(data)>128*1024*1024: raise RuntimeError('Unexpected large file: '+name)
    if data.startswith(b'\x89PNG\r\n\x1a\n'):
        i=8
        while i<len(data):
            size=int.from_bytes(data[i:i+4],'big'); kind=data[i+4:i+8]
            if kind in (b'eXIf',b'tEXt',b'zTXt',b'iTXt'): raise RuntimeError('Remove PNG metadata: '+name)
            i+=12+size
        return
    if name.endswith(('.jar','.zip','.bin')): return
    # Rules themselves contain the markers, but never actual credentials. Scan them as well.
    for label,regex in PATTERNS:
        if regex.search(data): raise RuntimeError(label+' detected in '+name+' (value withheld)')
    if artifact and any(part in FORBIDDEN for part in pathlib.PurePosixPath(name).parts):
        raise RuntimeError('Forbidden artifact entry: '+name)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=pathlib.Path);args=parser.parse_args()
    count=0
    for p in source_files(ROOT): check_data(p.relative_to(ROOT).as_posix(),p.read_bytes());count+=1
    if args.artifact:
        with zipfile.ZipFile(args.artifact) as z:
            lock = json.loads((ROOT/'tools/dependencies.lock.json').read_text())
            if json.loads(z.read('META-INF/dependencies.lock.json')) != lock: raise RuntimeError('JAR lock differs from source lock')
            for dep in lock['dependencies']:
                target = dep.get('target','')
                if target and not target.startswith('lib/linux/'):
                    if hashlib.sha256(z.read(target)).hexdigest() != dep['sha256']:
                        raise RuntimeError('Bundled dependency differs from lock: '+target)
            names=z.namelist()
            if len(names)!=len(set(names)): raise RuntimeError('Duplicate JAR entries')
            for name in names:
                if name.startswith('/') or '..' in pathlib.PurePosixPath(name).parts: raise RuntimeError('Unsafe JAR path')
                if name.endswith(('.exe','.bat','.ps1','.command','.sh')): raise RuntimeError('Unexpected executable script in JAR')
                check_data(name,z.read(name),True)
            if z.read('META-INF/MANIFEST.MF').find(b'Main-Class: Bootstrap')<0: raise RuntimeError('Wrong JAR entry point')
    print('PASS publication scan:',count,'source files'+(' and JAR' if args.artifact else ''))

if __name__=='__main__':
    try: main()
    except (RuntimeError,OSError,zipfile.BadZipFile) as e: print('CHECK FAILED:',e,file=sys.stderr);sys.exit(1)
