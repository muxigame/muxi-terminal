"""Regression: actual released TOML dependency overrides survive GUI QA setup."""
from pathlib import Path
import tempfile,tomllib,unittest
from qa_fml_config import configure_file,patch_properties

class LoaderConfigurationTests(unittest.TestCase):
    def test_released_configuration_survives(self):
        workspace=Path(__file__).resolve().parents[4]
        source=workspace/'integration/better-mc-remake/pack/source/Better MC Remake [FORGE]/config/fml.toml'
        original=source.read_text(encoding='utf-8');before=tomllib.loads(original)
        self.assertEqual(before['dependencyOverrides']['citresewn'],['-connector'])
        self.assertEqual(before['dependencyOverrides']['sable'],['-scalablelux'])
        with tempfile.TemporaryDirectory() as raw:
            path=Path(raw)/'fml.toml';path.write_text(original,encoding='utf-8');configure_file(path)
            after=tomllib.loads(path.read_text(encoding='utf-8'))
            expected=dict(before);expected.update(earlyWindowControl=False,earlyWindowProvider='',versionCheck=False)
            self.assertEqual(after,expected)
            self.assertIn('#Define dependency overrides below',path.read_text(encoding='utf-8'))
            first=path.read_bytes();configure_file(path);self.assertEqual(first,path.read_bytes())

    def test_released_iris_and_browser_settings_preserved(self):
        workspace=Path(__file__).resolve().parents[4]
        source=workspace/'integration/better-mc-remake/pack/source/Better MC Remake [FORGE]/config'
        for name,keys in [('iris.properties',{'enableShaders':'true','shaderPack':'Better MC - Low','disableUpdateMessage':'true'}),('mcef/mcef.properties',{'skip-download':'true','use-cache':'false','user-agent':'','download-mirror':''})]:
            original=(source/name).read_text(encoding='utf-8')
            with tempfile.TemporaryDirectory() as raw:
                path=Path(raw)/'profile.properties';path.write_text(original+'\nfutureCompatFlag=keep\n',encoding='utf-8');patch_properties(path,keys)
                after=path.read_text(encoding='utf-8')
                for line in original.splitlines():
                    if line.strip() and not any(line.startswith(key+'=') for key in keys):self.assertIn(line,after)
                self.assertIn('futureCompatFlag=keep',after)
                for key,value in keys.items():self.assertIn(key+'='+value,after)
                first=path.read_bytes();patch_properties(path,keys);self.assertEqual(first,path.read_bytes())

if __name__=='__main__':unittest.main()
