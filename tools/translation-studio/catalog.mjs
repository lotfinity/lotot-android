import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
const root=process.argv[2];
const names={'en':['en.js','EN'],'fr':['fr.js','FR'],'ar-DZ':['ar-DZ.js','AR_DZ']};
const result={};
for(const [locale,[file,key]] of Object.entries(names)) result[locale]=(await import(pathToFileURL(`${root}/lotot-ui/src/i18n/${file}`)))[key];
process.stdout.write(JSON.stringify(result));
