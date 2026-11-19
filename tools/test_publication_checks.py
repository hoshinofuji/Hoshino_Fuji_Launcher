#!/usr/bin/env python3
"""Exercise publication defenses with generated fixtures; never keep real secrets."""
import pathlib,tempfile,unittest
from check_release import check_data

class PublicationTests(unittest.TestCase):
    def test_tokens_and_paths_are_rejected(self):
        cases=[b'ghp_'+b'A'*40,b'AKIA'+b'B'*16,b'https://hooks.'+b'slack.com/services/T000/B000/test',b'/' + b'Users/' + b'example/private',b'-----BEGIN '+b'OPENSSH PRIVATE KEY-----',b'access_token="'+b'C'*40+b'"',b'https://' + b'user:' + b'pass@' + b'example.invalid/file']
        for value in cases:
            with self.subTest(kind=value[:5]),self.assertRaises(RuntimeError):check_data('fixture.java',value)
    def test_public_content_is_accepted(self):
        check_data('README.md',b'HFML public source; https://example.invalid/project')
    def test_png_metadata_is_rejected(self):
        # A tiny fabricated PNG chunk tests metadata detection without adding any private fields.
        data=b'\x89PNG\r\n\x1a\n'+(5).to_bytes(4,'big')+b'tEXt'+b'hello'+bytes(4)
        with self.assertRaises(RuntimeError):check_data('fixture.png',data)
    def test_game_data_is_rejected(self):
        with self.assertRaises(RuntimeError):check_data('accounts.json',b'{}',True)

if __name__=='__main__':unittest.main(verbosity=2)
