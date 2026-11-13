import unittest
from merchant_demo import is_reset_message, reset_token_from_message


class MerchantDemoTest(unittest.TestCase):
    def message(self, text='http://localhost:4200/reset-password?token=private-test-token'):
        return {'Subject': 'Reset your SwiftDrop password', 'To': [{'Address': 'merchant@example.test'}], 'Text': text}

    def test_selects_the_intended_recipient_and_reset_subject(self):
        self.assertTrue(is_reset_message(self.message(), 'MERCHANT@example.test'))
        self.assertFalse(is_reset_message(self.message(), 'other@example.test'))
        self.assertFalse(is_reset_message(dict(self.message(), Subject='Parcel update'), 'merchant@example.test'))

    def test_extracts_the_opaque_token_without_following_the_link(self):
        self.assertEqual(reset_token_from_message(self.message(), 'merchant@example.test'), 'private-test-token')

    def test_rejects_another_merchants_message_without_exposing_its_secret(self):
        with self.assertRaises(ValueError) as failure:
            reset_token_from_message(self.message(), 'other@example.test')
        self.assertNotIn('private-test-token', str(failure.exception))
        self.assertNotIn('merchant@example.test', str(failure.exception))

    def test_rejects_external_links_missing_tokens_and_ambiguous_token_parameters(self):
        for link in ['https://external.example/reset-password?token=secret',
                     'http://localhost:4200/reset-password',
                     'http://localhost:4200/reset-password?token=one&token=two']:
            with self.subTest(link=link), self.assertRaises(ValueError):
                reset_token_from_message(self.message(link), 'merchant@example.test')


if __name__ == '__main__':
    unittest.main()
