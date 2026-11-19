#!/usr/bin/env python3
"""HFML portable, locked build. Python 3.10+, JDK 21+. No shell commands or plugins."""
import argparse, hashlib, json, os, pathlib, platform, re, shutil, subprocess, sys, urllib.request, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
EPOCH = (2026, 1, 1, 0, 0, 0)
VERSION = re.search(r'static final String VERSION = "([0-9]+\.[0-9]+\.[0-9]+)";', (ROOT/'src/main/java/Launcher.java').read_text(encoding='utf-8')).group(1)

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''): h.update(chunk)
    return h.hexdigest()

def write_zip(path, entries):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, value in sorted(entries.items()):
            info = zipfile.ZipInfo(name, EPOCH)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = (0o100755 if name.endswith('.command') else 0o100644) << 16
            z.writestr(info, value)

def download(item, cache, offline):
    target = cache/item['name']
    if target.exists():
        if sha(target) == item['sha256']: return target
        raise RuntimeError('Cached dependency hash mismatch: '+item['name']+'; remove it and retry')
    if offline: raise RuntimeError('Missing locked artifact in offline cache: '+item['name'])
    print('Downloading', item['name'], flush=True)
    request = urllib.request.Request(item['url'], headers={'User-Agent':'HFML-build/1.0'})
    part = target.with_suffix(target.suffix+'.part')
    try:
        with urllib.request.urlopen(request, timeout=120) as src, part.open('wb') as dst:
            total = 0
            while block := src.read(1024*1024):
                total += len(block)
                if total > item['size']: raise RuntimeError('Artifact larger than lock entry')
                dst.write(block)
        if total != item['size'] or sha(part) != item['sha256']:
            raise RuntimeError('Dependency hash/size mismatch: '+item['name'])
        os.replace(part,target)
    finally: part.unlink(missing_ok=True)
    return target

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--test',action='store_true',help='run headless security regression tests')
    parser.add_argument('--network-test',action='store_true',help='with --test, query real channels and download a small mod as data')
    parser.add_argument('--release',action='store_true',help='include matching third-party sources and source archive')
    parser.add_argument('--offline',action='store_true')
    parser.add_argument('--cache-dir',type=pathlib.Path,default=ROOT/'.cache/dependencies')
    args = parser.parse_args()
    if args.network_test and not args.test: parser.error('--network-test requires --test')
    subprocess.run([sys.executable,str(ROOT/'tools/check_release.py')],check=True)
    cache = args.cache_dir.resolve(); cache.mkdir(parents=True,exist_ok=True)
    deps = json.loads((ROOT/'tools/dependencies.lock.json').read_text())['dependencies']
    system = platform.system()
    target = 'win' if system == 'Windows' else ('mac-aarch64' if platform.machine().lower() in ('arm64','aarch64') else 'mac') if system == 'Darwin' else 'linux'
    if system == 'Linux' and platform.machine().lower() not in ('x86_64','amd64'): raise RuntimeError('Build runner requires Linux x64')
    java_home = os.environ.get('JAVA_HOME')
    def java_tool(name):
        path = pathlib.Path(java_home)/'bin'/(name+'.exe' if system=='Windows' else name) if java_home else shutil.which(name)
        if not path: raise RuntimeError('Install JDK 21+ and set JAVA_HOME')
        return str(path)
    java, javac = java_tool('java'), java_tool('javac')
    artifacts = {d['name']:download(d,cache,args.offline) for d in deps if not d.get('source')}
    modules = os.pathsep.join(str(artifacts[d['name']]) for d in deps if d.get('target','').startswith('lib/'+target+'/'))
    gson = next(str(artifacts[d['name']]) for d in deps if d.get('target')=='lib/gson.jar')
    build = ROOT/'build'; classes=build/'classes'; tests=build/'tests'
    if build.exists(): shutil.rmtree(build)
    classes.mkdir(parents=True)
    options=['-encoding','UTF-8','--release','21','-proc:none','--module-path',modules,'--add-modules','javafx.controls']
    subprocess.run([javac,*options,'-cp',gson,'-d',str(classes),*[str(p) for p in sorted((ROOT/'src/main/java').glob('*.java'))]],check=True)
    if args.test:
        subprocess.run([sys.executable,str(ROOT/'tools/test_publication_checks.py')],check=True)
        tests.mkdir()
        subprocess.run([javac,*options,'-cp',str(classes)+os.pathsep+gson,'-d',str(tests),*[str(p) for p in sorted((ROOT/'src/test/java').glob('*.java'))]],check=True)
        subprocess.run([java,'--module-path',modules,'--add-modules','javafx.controls','-cp',os.pathsep.join([str(tests),str(classes),gson]),'SecurityTests'],check=True)
    if args.network_test:
        subprocess.run([java,'--module-path',modules,'--add-modules','javafx.controls','-cp',os.pathsep.join([str(tests),str(classes),gson]),'NetworkChecks'],check=True)
    entries={'META-INF/MANIFEST.MF':('Manifest-Version: 1.0\r\nMain-Class: Bootstrap\r\nImplementation-Title: Hoshino Fuji Launcher\r\nImplementation-Version: '+VERSION+'\r\n\r\n').encode('ascii')}
    for base in [classes,ROOT/'src/main/resources']:
        for path in sorted(base.rglob('*')):
            if path.is_file(): entries[path.relative_to(base).as_posix()]=path.read_bytes()
    for dep in deps:
        if 'target' in dep and not dep['target'].startswith('lib/linux/'):
            entries[dep['target']]=artifacts[dep['name']].read_bytes()
    for name in ['LICENSE','LICENSE-ASSETS','THIRD_PARTY_NOTICES.md']:
        entries['META-INF/licenses/'+name]=(ROOT/name).read_bytes()
    for path in (ROOT/'licenses').rglob('*'):
        if path.is_file(): entries['META-INF/licenses/'+path.relative_to(ROOT/'licenses').as_posix()]=path.read_bytes()
    entries['META-INF/dependencies.lock.json']=(ROOT/'tools/dependencies.lock.json').read_bytes()
    dist=ROOT/'dist'
    if dist.exists(): shutil.rmtree(dist)
    dist.mkdir()
    write_zip(dist/'HFML.jar',entries)
    for name in ['Launch.command','Launch.bat','README.md','LICENSE','LICENSE-ASSETS','THIRD_PARTY_NOTICES.md']:
        shutil.copyfile(ROOT/name,dist/name)
    (dist/'Launch.command').chmod(0o755)
    shutil.copytree(ROOT/'licenses',dist/'licenses')
    components=[]
    seen=set()
    for d in deps:
        if d.get('maven') and (d['maven'],d['version']) not in seen:
            seen.add((d['maven'],d['version']))
            components.append({'type':'library','name':d['maven'],'version':d['version'],'purl':'pkg:maven/'+d['maven'].replace(':','/')+'@'+d['version']})
    (dist/'bom.json').write_text(json.dumps({'bomFormat':'CycloneDX','specVersion':'1.5','version':1,'components':components},indent=2)+'\n',encoding='utf-8')
    if args.release:
        source_entries={p.relative_to(ROOT).as_posix():p.read_bytes() for p in __import__('check_release').source_files(ROOT)}
        write_zip(dist/'HFML-source.zip',source_entries)
        thirdparty=dist/'third-party-sources';thirdparty.mkdir()
        for d in deps:
            if d.get('source'): shutil.copyfile(download(d,cache,args.offline),thirdparty/d['name'])
    files=sorted(p for p in dist.rglob('*') if p.is_file())
    (dist/'SHA256SUMS.txt').write_text(''.join(sha(p)+'  '+p.relative_to(dist).as_posix()+'\n' for p in files),encoding='utf-8')
    subprocess.run([sys.executable,str(ROOT/'tools/check_release.py'),'--artifact',str(dist/'HFML.jar')],check=True)
    print('Built',dist/'HFML.jar','SHA-256',sha(dist/'HFML.jar'),flush=True)

if __name__=='__main__':
    try: main()
    except (RuntimeError,OSError,subprocess.CalledProcessError) as error:
        print('BUILD FAILED:',error,file=sys.stderr);sys.exit(1)
