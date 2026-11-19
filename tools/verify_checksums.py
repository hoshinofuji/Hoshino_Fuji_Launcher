#!/usr/bin/env python3
"""Verify all files named by SHA256SUMS.txt; reject missing and unlisted release files."""
import argparse,hashlib,pathlib,re,sys

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('directory',type=pathlib.Path);args=parser.parse_args()
    root=args.directory.resolve(); names=set()
    for line in (root/'SHA256SUMS.txt').read_text(encoding='utf-8').splitlines():
        match=re.fullmatch(r'([0-9a-f]{64})  (.+)',line)
        if not match: raise RuntimeError('Malformed checksum line')
        expected,name=match.groups();relative=pathlib.PurePosixPath(name)
        if relative.is_absolute() or '..' in relative.parts or '\\' in name or name in names: raise RuntimeError('Unsafe checksum path')
        names.add(name);p=root.joinpath(*relative.parts)
        if p.is_symlink() or not p.is_file() or not p.resolve().is_relative_to(root): raise RuntimeError('Unsafe or missing file: '+name)
        digest=hashlib.sha256()
        with p.open('rb') as f:
            for block in iter(lambda:f.read(1024*1024),b''):digest.update(block)
        if digest.hexdigest()!=expected:raise RuntimeError('Checksum mismatch: '+name)
    actual={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() and p!=root/'SHA256SUMS.txt'}
    if names!=actual:raise RuntimeError('Release contains unlisted or missing files')
    print('PASS SHA-256:',len(names),'release files')

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError) as error:print('VERIFY FAILED:',error,file=sys.stderr);sys.exit(1)
