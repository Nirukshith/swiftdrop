"""Exercise M6 orders over real HTTP; credentials stay in memory and are never printed.

Requires isolated running services and a prepared ADMIN account. See the M6-02
verification document for environment variables and fixture setup.
"""
import concurrent.futures
import json
import os
import urllib.error
import urllib.request
from urllib.parse import urlparse
import uuid

AUTH = os.environ['M6_VERIFY_AUTH_URL'].rstrip('/')
INTEGRATION = os.environ['M6_VERIFY_INTEGRATION_URL'].rstrip('/')
PARCEL = os.environ['M6_VERIFY_PARCEL_URL'].rstrip('/')
for endpoint in [AUTH, INTEGRATION, PARCEL, os.environ.get('M6_VERIFY_PRICING_PROVIDER_URL', PARCEL)]:
    if urlparse(endpoint).hostname not in {'localhost', '127.0.0.1', '::1'}:
        raise ValueError('Verification requires isolated services on loopback URLs')


def call(base, path, method='GET', body=None, token=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    request = urllib.request.Request(base + path, method=method, headers=headers,
        data=None if body is None else json.dumps(body).encode())
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None


def expect(result, status, label):
    actual, body = result
    if actual != status:
        raise AssertionError(f'{label}: expected HTTP {status}, received HTTP {actual}')
    print(f'PASS {label}: HTTP {actual}', flush=True)
    return body


def check(condition, label):
    if not condition:
        raise AssertionError(label)
    print('PASS ' + label, flush=True)


def main():
    run_id = uuid.uuid4().hex[:10]
    admin = expect(call(AUTH, '/api/v1/auth/login', 'POST', {
        'email': os.environ['M6_VERIFY_ADMIN_EMAIL'],
        'password': os.environ['M6_VERIFY_ADMIN_PASSWORD'],
    }), 200, 'real ADMIN login')['accessToken']
    def merchant(suffix):
        registered = expect(call(INTEGRATION, '/api/v1/merchants/register', 'POST', {
            'businessName': 'M6 Runtime ' + suffix, 'contactName': 'Verification Contact',
            'email': f'm6-{run_id}-{suffix}@example.test', 'phoneNumber': '+94770000000',
        }), 201, 'merchant registration ' + suffix)
        check(registered['status'] == 'PENDING', 'registration remains pending ' + suffix)
        approved = expect(call(INTEGRATION, f'/api/v1/admin/merchants/{registered["id"]}/approve',
            'POST', {}, admin), 200, 'real auth provisioning ' + suffix)
        check(approved['merchant']['status'] == 'APPROVED', 'approved merchant state ' + suffix)
        token = expect(call(AUTH, '/api/v1/auth/token', 'POST', {
            'grant_type': 'client_credentials', 'client_id': approved['clientId'],
            'client_secret': approved['clientSecret'],
        }), 200, 'real client-credentials token ' + suffix)['access_token']
        expect(call(AUTH, '/api/v1/auth/token', 'POST', {
            'grant_type': 'client_credentials', 'client_id': approved['clientId'],
            'client_secret': 'intentionally-invalid-test-secret',
        }), 401, 'invalid client secret rejected ' + suffix)
        return registered['id'], token

    first_id, first_token = merchant('first')
    second_id, second_token = merchant('second')
    provider = os.environ.get('M6_VERIFY_PRICING_PROVIDER_URL')
    if provider:
        quote_path = '/internal/v1/pricing/quote?pickupDistrict=Colombo&dropoffDistrict=Gampaha&weightKg=1.5&size=SMALL'
        expect(call(provider, quote_path), 401, 'current pricing provider rejects unauthenticated M6 call')
        expect(call(provider, quote_path, token=first_token), 403, 'merchant token cannot satisfy dispatcher-only pricing policy')
    address = {'line1': '10 Verification Road', 'city': 'Colombo', 'district': 'Colombo', 'postalCode': '00100'}
    payload = {'externalReference': 'm6-' + run_id, 'recipientName': 'Test Recipient',
        'recipientPhone': '+94770000000', 'pickupAddress': address,
        'dropoffAddress': dict(address, city='Gampaha', district='Gampaha', postalCode='11000'),
        'size': 'SMALL', 'weightKg': 1.5}
    path = '/api/v1/merchant/orders'
    first = expect(call(INTEGRATION, path, 'POST', payload, first_token, 'same-key'), 201, 'real parcel creation')
    check(first['status'] == 'CREATED' and first['fee'] == 850, 'configured contract quote and real parcel result')
    replay = expect(call(INTEGRATION, path, 'POST', payload, first_token, 'same-key'), 201, 'same-key create replay')
    check(replay == first, 'create replay returns entire original response')
    conflicting = dict(payload, externalReference='different-' + run_id)
    expect(call(INTEGRATION, path, 'POST', conflicting, first_token, 'same-key'), 409, 'conflicting create-key reuse')
    other = expect(call(INTEGRATION, path, 'POST', payload, second_token, 'same-key'), 201, 'same key in another merchant tenant')
    check(other['parcelId'] != first['parcelId'], 'merchant keys produce distinct parcels')

    concurrent_body = dict(payload, externalReference='concurrent-' + run_id)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(call, INTEGRATION, path, 'POST', concurrent_body, first_token, 'concurrent-key') for _ in range(2)]
        results = [expect(f.result(), 201, 'concurrent same-key creation') for f in futures]
    check(results[0] == results[1], 'concurrent requests share one original response')
    third = results[0]
    parcels = expect(call(PARCEL, '/internal/v1/parcels?page=0&size=100'), 200, 'read parcel-service verification fixture')
    relevant = [p for p in parcels['content'] if p['merchantId'] in [first_id, second_id]]
    check(len(relevant) == 3, 'replays and conflicts caused no duplicate parcel side effects')

    cancel_path = f'{path}/{first["orderId"]}/cancel'
    cancel_body = {'reason': 'M6 verification duplicate order'}
    cancelled = expect(call(INTEGRATION, cancel_path, 'POST', cancel_body, first_token, 'cancel-key'), 200, 'cancel before pickup')
    check(cancelled['status'] == 'CANCELLED', 'cancellation persisted in merchant mapping')
    cancelled_replay = expect(call(INTEGRATION, cancel_path, 'POST', cancel_body, first_token, 'cancel-key'), 200, 'same-key cancellation replay')
    check(cancelled_replay == cancelled, 'cancellation replay returns entire original response')
    expect(call(INTEGRATION, cancel_path, 'POST', {'reason': 'changed'}, first_token, 'cancel-key'), 409, 'conflicting cancellation body')
    expect(call(INTEGRATION, f'{path}/{third["orderId"]}/cancel', 'POST', cancel_body, first_token, 'cancel-key'), 409, 'cancellation key cannot replay for another owned order')
    expect(call(INTEGRATION, f'{path}/{other["orderId"]}/cancel', 'POST', cancel_body, first_token, 'foreign-key'), 404, 'foreign order ownership rejection')
    expect(call(INTEGRATION, f'{path}/{other["orderId"]}/cancel', 'POST', cancel_body, first_token, 'cancel-key'), 404, 'ownership checked before replay')
    check(expect(call(INTEGRATION, path, 'POST', payload, first_token, 'same-key'), 201,
        'creation replay after cancellation') == first, 'creation replay preserves original state')

    previous = 'CREATED'
    for state in ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED']:
        expect(call(PARCEL, f'/internal/v1/parcels/{third["parcelId"]}/status', 'PATCH', {
            'status': state, 'expectedCurrentStatus': previous,
        }), 200, 'real parcel transition ' + state)
        if state == 'PICKED_UP':
            conflict_body = {'status': 'CANCELLED', 'expectedCurrentStatus': 'CREATED', 'reason': 'Expected conflict probe'}
            anonymous_status, _ = call(PARCEL, f'/internal/v1/parcels/{third["parcelId"]}/status', 'PATCH', conflict_body)
            print(f'DIAGNOSTIC anonymous parcel conflict response: HTTP {anonymous_status}', flush=True)
            expect(call(PARCEL, f'/internal/v1/parcels/{third["parcelId"]}/status', 'PATCH', conflict_body, first_token),
                409, 'authenticated parcel call preserves original lifecycle conflict')
        expect(call(INTEGRATION, f'{path}/{third["orderId"]}/cancel', 'POST', cancel_body, first_token,
            'state-' + state), 409, 'real lifecycle rejects cancellation in ' + state)
        previous = state
    expect(call(INTEGRATION, f'{path}/{uuid.uuid4()}/cancel', 'POST', cancel_body, first_token, 'missing-key'), 404, 'missing order')
    expect(call(INTEGRATION, path, 'POST', payload, admin, 'admin-key'), 403, 'non-merchant role rejected')
    expect(call(INTEGRATION, path, 'POST', payload, 'invalid-token', 'invalid-key'), 401, 'invalid bearer token rejected')
    expect(call(INTEGRATION, path, 'POST', payload, None, 'anonymous-key'), 401, 'anonymous request rejected')
    print('ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED', flush=True)


if __name__ == '__main__':
    main()
