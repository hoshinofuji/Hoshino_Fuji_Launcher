#!/usr/bin/env python3
"""Package a verified --release build. Run only after tools/build.py --test --release."""
import os,pathlib,shutil,subprocess,sys
from build import VERSION,sha,write_zip
ROOT=pathlib.Path(__file__).resolve().parents[1]

def main():
    if os.environ.get('GITHUB_REF_TYPE')=='tag' and os.environ.get('GITHUB_REF_NAME')!='v'+VERSION:
        raise RuntimeError('Release tag must match Launcher.VERSION: v'+VERSION)
    dist=ROOT/'dist'
    subprocess.run([sys.executable,str(ROOT/'tools/verify_checksums.py'),str(dist)],check=True)
    required=['HFML.jar','HFML-source.zip','third-party-sources/openjfx-21.0.13.1-source.zip','third-party-sources/gson-2.15.0-sources.jar']
    for name in required:
        if not (dist/name).is_file():raise RuntimeError('Missing complete release content: '+name)
    output=ROOT/'release'
    if output.exists():shutil.rmtree(output)
    output.mkdir()
    entries={'HFML/'+p.relative_to(dist).as_posix():p.read_bytes() for p in sorted(dist.rglob('*')) if p.is_file()}
    write_zip(output/'HFML-release.zip',entries)
    for name in ['HFML.jar','HFML-source.zip']:shutil.copyfile(dist/name,output/name)
    (output/'SHA256SUMS.txt').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in sorted(output.iterdir()) if p.is_file()),encoding='utf-8')
    print('Release ready:',output)

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,subprocess.CalledProcessError) as error:print('PACKAGE FAILED:',error,file=sys.stderr);sys.exit(1)
