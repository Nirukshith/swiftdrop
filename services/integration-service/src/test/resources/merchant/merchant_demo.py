"""Read only the intended merchant's reset message in disposable demo Mailpit."""
import re
from urllib.parse import parse_qs, urlsplit


def is_reset_message(message, email):
    return (message.get('Subject') == 'Reset your SwiftDrop password'
            and any(recipient.get('Address', '').lower() == email.lower()
                    for recipient in message.get('To', [])))


def reset_token_from_message(message, email):
    if is_reset_message(message, email):
        for link in re.findall(r'https?://[^\s<>]+', message.get('Text', '')):
            parsed = urlsplit(link)
            tokens = parse_qs(parsed.query).get('token', [])
            if (parsed.hostname in {'localhost', '127.0.0.1', '::1'}
                    and parsed.path == '/reset-password' and len(tokens) == 1 and tokens[0]):
                return tokens[0]
    # Never include a message body, reset URL or token in a failure report.
    raise ValueError('Expected merchant reset message with one loopback reset link')
