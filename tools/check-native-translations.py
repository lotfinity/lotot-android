#!/usr/bin/env python3
from pathlib import Path
import re, xml.etree.ElementTree as E
root=Path(__file__).resolve().parents[1]/'androbd/src/main/res'
def read(locale):
    nodes=[x for x in E.parse(root/locale/'strings.xml').getroot() if x.tag=='string' and x.get('translatable')!='false']
    result={x.get('name'):''.join(x.itertext()) for x in nodes}
    assert len(result)==len(nodes),f'Duplicate native keys: {locale}'
    return result
base=read('values')
pattern=r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[dsf]'
for locale in ['values-fr','values-ar-rDZ']:
    translated=read(locale)
    missing=base.keys()-translated.keys()
    assert not missing,(locale,sorted(missing))
    for key in base:
        assert sorted(re.findall(pattern,base[key]))==sorted(re.findall(pattern,translated[key])),(locale,key)
    print(f'{locale}: {len(base)} native strings covered; format placeholders match')
