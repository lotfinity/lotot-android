import importlib.util
import unittest
import tempfile
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path
spec=importlib.util.spec_from_file_location("studio",Path(__file__).with_name("server.py"))
studio=importlib.util.module_from_spec(spec);spec.loader.exec_module(studio)

class TranslationSafety(unittest.TestCase):
    def test_native_aapt_and_xml_roundtrip(self):
        for value in ["l'apostrophe & <tag> "+chr(34)+"quote"+chr(34), "سطر\nثاني", r"path\folder", "%1$s · {count}", "@reference ?attribute"]:
            with self.subTest(value=value):
                self.assertEqual(studio.android_value(ET.fromstring('<string>'+studio.xmltext(value)+'</string>').text),value)
    def test_placeholder_reordering_and_loss(self):
        self.assertEqual(studio.tokens('{count} %1$s'),studio.tokens('%1$s {count}'))
        self.assertNotEqual(studio.tokens('{count} {count}'),studio.tokens('{count}'))
        self.assertNotEqual(studio.tokens('%1$s'),studio.tokens('%s'))
    def test_job_paths_reject_traversal(self):
        for ident in ['../latest','a'*13,'bad-id','']:
            with self.assertRaises(ValueError):studio.jobpath(ident)
    def test_stale_save_rejected(self):
        original=studio.catalog
        try:
            studio.catalog=lambda:{'revision':'current','rows':[]}
            with self.assertRaisesRegex(ValueError,'Strings changed'):studio.save({'revision':'stale','changes':[]})
        finally:studio.catalog=original

class SourceSaveIntegration(unittest.TestCase):
    def test_save_all_three_layers_and_conflict(self):
        original_root,original_state,original_run=studio.ROOT,studio.STATE,studio.run
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            def fixture_run(args,**kw):
                return 'fixture' if args[0]=='git' else original_run(args,**kw)
            try:
                for relative in ['lotot-ui/src/i18n','lotot-ui/scripts','androbd/src/main/res/values','androbd/src/main/res/values-fr','androbd/src/main/res/values-ar-rDZ']:
                    shutil.copytree(original_root/relative,root/relative)
                assets=root/'androbd/src/main/assets';assets.mkdir(parents=True)
                for name in ['dtc_translations.json','dtc_codes.db']:
                    shutil.copy2(original_root/'androbd/src/main/assets'/name,assets/name)
                (root/'tools').mkdir()
                for name in ['check-native-translations.py','check-dtc-translations.py']:
                    shutil.copy2(original_root/'tools'/name,root/'tools'/name)
                (root/'lotot-ui/package.json').write_text('{"type":"module"}')
                studio.ROOT=root;studio.STATE=root/'state';studio.STATE.mkdir();studio.run=fixture_run
                c=studio.catalog()
                ids=['ui:category.all','native:action_connect','dtc:P0100']
                changes=[{'id':i,'locale':'ar-DZ','value':"تجربة & l'apostrophe"} for i in ids]
                result,count=studio.save({'revision':c['revision'],'changes':changes})
                self.assertEqual(count,3)
                for ident in ids:
                    self.assertEqual(next(r for r in result['rows'] if r['id']==ident)['ar-DZ'],"تجربة & l'apostrophe")
                self.assertTrue(list((studio.STATE/'history').glob('*/changes.json')))
                with self.assertRaisesRegex(ValueError,'Strings changed'):
                    studio.save({'revision':c['revision'],'changes':changes})
            finally:studio.ROOT,studio.STATE,studio.run=original_root,original_state,original_run

if __name__=='__main__':unittest.main()
