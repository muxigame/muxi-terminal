"""Regression: actual released TOML dependency overrides survive GUI QA setup."""
from pathlib import Path
import tempfile,tomllib,unittest
from qa_fml_config import configure_file

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

if __name__=='__main__':unittest.main()
