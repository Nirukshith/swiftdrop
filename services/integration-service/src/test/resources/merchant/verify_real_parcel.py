"""Exercise M6 orders over real HTTP; credentials stay in memory and are never printed.

Requires isolated running services and a prepared ADMIN account. See the M6-02
verification document for environment variables and fixture setup.
"""
import concurrent.futures
import base64
import hashlib
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


def call(base, path, method='GET', body=None, token=None, key=None, internal_token=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    if internal_token:
        headers['X-Internal-Service-Token'] = internal_token
    data = None if body is None else body.encode('utf-8') if content_type == 'text/csv' else json.dumps(body).encode()
    request = urllib.request.Request(base + path, method=method, headers=headers, data=data)
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
    pending = expect(call(INTEGRATION, '/api/v1/merchants/register', 'POST', {
        'businessName': 'M6 Pending Verification', 'contactName': 'Verification Contact',
        'email': f'm6-{run_id}-pending@example.test', 'phoneNumber': '+94770000000',
    }), 201, 'pending merchant registration')
    pending_list = expect(call(INTEGRATION, '/api/v1/admin/merchants?status=PENDING', token=admin),
        200, 'M5 pending approval list contract')
    check(any(m['id'] == pending['id'] and m['status'] == 'PENDING' for m in pending_list['content']),
        'pending merchant is visible to admin')
    check(all('clientSecret' not in m for m in pending_list['content']), 'pending list never exposes a secret')
    # Fixture-only M10 provisioning creates an inactive account/client to exercise negative token issuance.
    internal_token = os.environ['M6_VERIFY_INTERNAL_TOKEN']
    expect(call(AUTH, '/internal/v1/users', 'POST', {
        'email': pending['email'], 'fullName': pending['contactName'], 'phoneNumber': pending['phoneNumber'],
        'role': 'MERCHANT', 'merchantId': pending['id'], 'enabled': False,
    }, internal_token=internal_token), 201, 'M10 inactive user fixture')
    inactive_client = expect(call(AUTH, '/internal/v1/clients', 'POST', {
        'merchantId': pending['id'], 'clientName': 'M6 inactive test fixture', 'scopes': ['parcels:create'],
    }, internal_token=internal_token), 201, 'M10 inactive client fixture')
    inactive_grant = {'grant_type': 'client_credentials', 'client_id': inactive_client['clientId'],
        'client_secret': inactive_client['clientSecret']}
    expect(call(AUTH, '/api/v1/auth/token', 'POST', inactive_grant), 403, 'pending inactive account cannot obtain a token')
    rejected = expect(call(INTEGRATION, f'/api/v1/admin/merchants/{pending["id"]}/reject', 'POST',
        {'reason': '  Business details not verified  '}, admin), 200, 'M5 rejection action contract')
    check(rejected['status'] == 'REJECTED' and rejected['rejectionReason'] == 'Business details not verified',
        'rejection persists the trimmed reason')
    expect(call(INTEGRATION, f'/api/v1/admin/merchants/{pending["id"]}/approve', 'POST', {}, admin),
        409, 'rejected merchant cannot be approved')
    expect(call(AUTH, '/api/v1/auth/token', 'POST', inactive_grant), 403, 'rejected inactive account remains unable to obtain a token')
    approved_list = expect(call(INTEGRATION, '/api/v1/admin/merchants?status=APPROVED', token=admin),
        200, 'M5 approved merchant list contract')
    check({m['id'] for m in approved_list['content']} == {first_id, second_id}, 'only approved merchants appear in approved list')
    check(all('clientSecret' not in m for m in approved_list['content']), 'approved list never redisplays credentials')
    provider = os.environ.get('M6_VERIFY_PRICING_PROVIDER_URL')
    if provider:
        quote_path = '/internal/v1/pricing/quote?pickupDistrict=Colombo&dropoffDistrict=Gampaha&weightKg=1.5&size=SMALL'
        expect(call(provider, quote_path), 401, 'current pricing provider rejects unauthenticated M6 call')
        internal_quote=expect(call(provider, quote_path, internal_token=os.environ['M6_VERIFY_INTERNAL_PRICING_TOKEN']),
            200, 'real internal-service-token pricing quote')
        check(internal_quote['totalFee']==850, 'internal service mode uses the same real pricing rules')
        expect(call(provider, quote_path, internal_token='intentionally-invalid-pricing-token'),
            401, 'invalid internal pricing credential remains denied')
        expect(call(provider, quote_path, token=first_token), 403, 'ordinary merchant token cannot read internal pricing')
        pricing = expect(call(AUTH, '/api/v1/auth/token', 'POST', {
            'grant_type': 'client_credentials', 'client_id': os.environ['M6_VERIFY_PRICING_CLIENT_ID'],
            'client_secret': os.environ['M6_VERIFY_PRICING_CLIENT_SECRET'], 'scope': 'pricing:read',
        }), 200, 'real dedicated pricing service token')
        encoded = pricing['access_token'].split('.')[1]
        claims = json.loads(base64.urlsafe_b64decode(encoded + '=' * (-len(encoded) % 4)))
        check(claims.get('scope') == 'pricing:read' and claims.get('role') != 'DISPATCHER',
            'dedicated pricing scope without dispatcher elevation')
        check('swiftdrop-api' in claims.get('aud', []), 'real auth-issued pricing token targets the required API audience')
        quote=expect(call(provider, quote_path, token=pricing['access_token']), 200, 'real scoped pricing quote')
        check(quote['totalFee']==850, 'real zone, weight and size rules produce 850 LKR')
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
    # Exercise the merged M1 replay contract with M6's exact downstream identity.
    downstream_key = 'm6-' + hashlib.sha256((first_id + ':CREATE_ORDER:same-key').encode()).hexdigest()
    internal_payload = dict(payload, merchantId=first_id, fee=850)
    provider_replay = expect(call(PARCEL, '/internal/v1/parcels', 'POST', internal_payload,
        first_token, downstream_key), 201, 'real M1 downstream creation replay')
    check(provider_replay['id'] == first['parcelId'], 'M1 downstream replay preserves the original parcel')
    expect(call(PARCEL, '/internal/v1/parcels', 'POST', dict(internal_payload, recipientName='Changed'),
        first_token, downstream_key), 409, 'M1 downstream key rejects changed payload')
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
    first_orders = expect(call(INTEGRATION, path, token=first_token), 200, 'first merchant order listing')
    second_orders = expect(call(INTEGRATION, path, token=second_token), 200, 'second merchant order listing')
    check({o['orderId'] for o in first_orders['content']} == {first['orderId'], third['orderId']},
        'first merchant listing excludes the second tenant')
    check({o['orderId'] for o in second_orders['content']} == {other['orderId']},
        'second merchant listing excludes the first tenant')
    invalid = dict(payload, externalReference='invalid-input-' + run_id, weightKg=0)
    expect(call(INTEGRATION, path, 'POST', invalid, first_token, 'invalid-input'), 400, 'merchant input validation')
    expect(call(INTEGRATION, path, 'POST', payload, first_token), 400, 'idempotency key is required')
    # This value satisfies M6's merged positive-weight contract but fails M1's 0.10 kg domain minimum.
    upstream_invalid = dict(payload, externalReference='parcel-failure-' + run_id, weightKg=0.01)
    expect(call(INTEGRATION, path, 'POST', upstream_invalid, first_token, 'parcel-failure-key'),
        422, 'real M1 validation failure uses the merchant contract without an order')
    after_failure = expect(call(PARCEL, '/internal/v1/parcels?page=0&size=100'), 200, 'inspect failed parcel creation')
    check(len([p for p in after_failure['content'] if p['merchantId'] in [first_id, second_id]]) == 3,
        'failed creation produced no parcel side effect')
    after_failure_orders = expect(call(INTEGRATION, path, token=first_token), 200, 'inspect failed merchant order mapping')
    check(after_failure_orders['totalElements'] == 2, 'failed creation produced no merchant mapping')
    corrected = dict(upstream_invalid, weightKg=1.5)
    recovered = expect(call(INTEGRATION, path, 'POST', corrected, first_token, 'parcel-failure-key'),
        201, 'retry after M1 validation failure can reuse the rolled-back key')
    check(expect(call(INTEGRATION, path, 'POST', corrected, first_token, 'parcel-failure-key'),
        201, 'recovered creation replays') == recovered, 'recovered creation returns the original result')

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
    verify_bulk(first_id, first_token, second_id, second_token, admin, run_id)
    verify_recovery(first_id, first_token, admin, run_id)
    print('ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED', flush=True)


def verify_bulk(first_id, first_token, second_id, second_token, admin, run_id):
    header = 'externalReference,recipientName,recipientPhone,pickupLine1,pickupLine2,pickupCity,pickupDistrict,pickupPostalCode,dropoffLine1,dropoffLine2,dropoffCity,dropoffDistrict,dropoffPostalCode,size,weightKg'
    def row(reference, weight='1.5'):
        return f'{reference},Test Recipient,+94770000000,10 Verification Road,,Colombo,Colombo,00100,20 Verification Road,,Gampaha,Gampaha,11000,SMALL,{weight}'
    def submit(text, token, key):
        return call(INTEGRATION, '/api/v1/merchant/orders/bulk', 'POST', text, token, key, content_type='text/csv')
    before = expect(call(PARCEL, '/internal/v1/parcels?page=0&size=100'), 200, 'inspect parcels before bulk')
    csv = header + '\n' + row('bulk-a-' + run_id) + '\n' + row('bulk-b-' + run_id) + '\n' + row('bulk-invalid-' + run_id, '0.01') + '\n'
    original = expect(submit(csv, first_token, 'bulk-original'), 200, 'real bulk CSV submission')
    check(original['totalRows'] == 3 and original['acceptedRows'] == 2 and original['rejectedRows'] == 1,
        'bulk reports valid and invalid source rows separately')
    check(original['results'][2]['rowNumber'] == 4 and not original['results'][2]['accepted'], 'bulk validation reports the invalid source row')
    after = expect(call(PARCEL, '/internal/v1/parcels?page=0&size=100'), 200, 'inspect real bulk parcels')
    check(after['totalElements'] == before['totalElements'] + 2, 'invalid bulk row created no parcel')
    by_id = {p['id']: p for p in after['content']}
    accepted = [r['order'] for r in original['results'] if r['accepted']]
    check(all(o['parcelId'] in by_id and by_id[o['parcelId']]['merchantId'] == first_id
        and by_id[o['parcelId']]['fee'] == 850 and o['fee'] == 850 for o in accepted), 'bulk parcels have real M1 mappings, correct tenant and configured quote')
    replay = expect(submit(csv, first_token, 'bulk-original'), 200, 'bulk same-key replay')
    check(replay == original, 'bulk replay preserves every original row result')
    expect(submit(csv.replace('bulk-a-', 'changed-a-'), first_token, 'bulk-original'), 409, 'bulk conflicting file reuse')
    duplicate = expect(submit(csv, first_token, 'bulk-different-key'), 200, 'bulk duplicate references in a new upload')
    check(duplicate['acceptedRows'] == 0, 'bulk existing references never create another parcel')
    other = expect(submit(csv, second_token, 'bulk-original'), 200, 'bulk same key and references in a different tenant')
    check(other['acceptedRows'] == 2 and not ({r['order']['parcelId'] for r in other['results'] if r['accepted']}
        & {o['parcelId'] for o in accepted}), 'bulk tenant replay cannot expose another merchant parcel')
    expect(submit(header + '\n' + row('bulk-malformed-' + run_id) + '\n"unclosed', first_token, 'bulk-malformed'),
        400, 'malformed bulk file rejected before any row creation')
    parallel_csv = header + '\n' + row('bulk-concurrent-' + run_id) + '\n'
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(submit, parallel_csv, first_token, 'bulk-concurrent') for _ in range(2)]
        parallel = [expect(f.result(), 200, 'concurrent real bulk request') for f in futures]
    completed = expect(submit(parallel_csv, first_token, 'bulk-concurrent'), 200, 'completed concurrent bulk replay')
    check(completed['acceptedRows'] == 1 and all(not response['acceptedRows'] or response == completed for response in parallel),
        'concurrent bulk requests share the completed original parcel; an early replay may report processing')
    final = expect(call(PARCEL, '/internal/v1/parcels?page=0&size=100'), 200, 'inspect final real bulk parcel count')
    check(final['totalElements'] == before['totalElements'] + 5, 'bulk replay, duplicates and malformed files added no extra parcels')
    expect(submit(csv, admin, 'bulk-admin'), 403, 'bulk non-merchant role denied')
    expect(submit(csv, None, 'bulk-anonymous'), 401, 'bulk anonymous request denied')
    expect(submit(csv, 'invalid-token', 'bulk-invalid-token'), 401, 'bulk invalid bearer token denied')


def verify_recovery(merchant_id, token, admin, run_id):
    fault_auth=os.environ['M6_VERIFY_FAULT_AUTH_URL']
    fault_parcel=os.environ['M6_VERIFY_FAULT_PARCEL_URL']
    provider=os.environ['M6_VERIFY_PRICING_PROVIDER_URL']
    registered=expect(call(INTEGRATION,'/api/v1/merchants/register','POST',{
        'businessName':'Lost Response Verification','contactName':'Recovery Contact',
        'email':f'm6-loss-{run_id}@example.test','phoneNumber':'+94770000000'}),201,'recovery merchant registration')
    approve=f'/api/v1/admin/merchants/{registered["id"]}/approve'
    expect(call(INTEGRATION,approve,'POST',{},admin),502,'injected lost auth provisioning response after commit')
    expect(call(INTEGRATION,approve,'POST',{},admin),409,'normal retry reconciles without silently rotating credentials')
    faults=expect(call(fault_auth,'/__verification/faults'),200,'inspect isolated auth fault injection')
    check(faults['injected']==1 and len(faults['operationIds'])==1,'one real provisioning response was discarded')
    operation=faults['operationIds'][0]
    status=expect(call(AUTH,f'/internal/v1/merchant-provisioning/{operation}',internal_token=os.environ['M6_VERIFY_INTERNAL_TOKEN']),200,'real auth non-secret recovery status')
    check(status.get('clientSecret') is None and status['credentialRecoveryRequired'],'status never reads a stored secret')
    client_id=status['clientId']
    recovery=f'/api/v1/admin/merchants/{registered["id"]}/recover-credentials'
    key=str(uuid.uuid4())
    expect(call(INTEGRATION,recovery,'POST',{'confirmReplacement':False},admin,key),400,'recovery requires explicit confirmation')
    expect(call(INTEGRATION,recovery,'POST',{'confirmReplacement':True},admin,key),502,'injected lost replacement response after commit')
    expect(call(INTEGRATION,recovery,'POST',{'confirmReplacement':True},admin,key),409,'same replacement key does not rotate or reveal a secret again')
    repaired=expect(call(INTEGRATION,recovery,'POST',{'confirmReplacement':True},admin,str(uuid.uuid4())),200,'explicit new confirmation recovers credential handoff')
    check(repaired['clientId']==client_id and repaired['merchant']['status']=='APPROVED','recovery retains the original client and approves the merchant')
    grant={'grant_type':'client_credentials','client_id':client_id,'client_secret':repaired['clientSecret']}
    recovered_token=expect(call(AUTH,'/api/v1/auth/token','POST',grant),200,'recovered credential authenticates')['access_token']
    expect(call(INTEGRATION,'/api/v1/merchant/orders',token=recovered_token),200,'recovered merchant can access its own order API')
    again=expect(call(INTEGRATION,recovery,'POST',{'confirmReplacement':True},admin,str(uuid.uuid4())),200,'approved merchant lost-handoff replacement')
    check(again['clientId']==client_id,'approved recovery does not mint another client')
    expect(call(AUTH,'/api/v1/auth/token','POST',grant),401,'replaced old secret cannot obtain another token')
    expect(call(INTEGRATION,recovery,'POST',{'confirmReplacement':True},token,str(uuid.uuid4())),403,'merchant cannot invoke ADMIN recovery')
    expect(call(INTEGRATION,approve,'POST',{},admin),409,'completed approval cannot create duplicate credentials')

    header='externalReference,recipientName,recipientPhone,pickupLine1,pickupLine2,pickupCity,pickupDistrict,pickupPostalCode,dropoffLine1,dropoffLine2,dropoffCity,dropoffDistrict,dropoffPostalCode,size,weightKg'
    reference='m6-loss-bulk-'+run_id
    csv=header+'\n'+f'{reference},Recovery Recipient,+94770000000,10 Road,,Colombo,Colombo,00100,20 Road,,Gampaha,Gampaha,11000,SMALL,1.5\n'
    upload_key='lost-parcel-'+run_id
    before=expect(call(PARCEL,'/internal/v1/parcels?page=0&size=100'),200,'inspect parcels before committed-response fault')
    failed=expect(call(INTEGRATION,'/api/v1/merchant/orders/bulk','POST',csv,token,upload_key,content_type='text/csv'),200,'bulk records lost real parcel response')
    check(failed['acceptedRows']==0,'unknown parcel is not falsely reported successful')
    committed=expect(call(PARCEL,'/internal/v1/parcels?page=0&size=100'),200,'real parcel committed despite discarded response')
    check(committed['totalElements']==before['totalElements']+1,'exactly one real parcel committed before retry')
    original_id=next(p['id'] for p in committed['content'] if p['id'] not in {old['id'] for old in before['content']})
    expect(call(PARCEL,f'/internal/v1/parcels/{original_id}/status','PATCH',
        {'status':'PICKED_UP','expectedCurrentStatus':'CREATED'}),200,'advance real parcel before missing mapping is recovered')
    zones=expect(call(provider,'/api/v1/admin/zones',token=os.environ['M6_VERIFY_DISPATCHER_TOKEN']),200,'load real pricing for change between retries')
    dropoff=next(z for z in zones if 'Gampaha' in z['districts'])
    expect(call(provider,'/api/v1/admin/zones/'+dropoff['id'],'PUT',{'name':dropoff['name'],'districts':dropoff['districts'],'served':dropoff['served'],'baseFeeLkr':1000},os.environ['M6_VERIFY_DISPATCHER_TOKEN']),200,'change real pricing after ambiguous parcel commit')
    replay=expect(call(INTEGRATION,'/api/v1/merchant/orders/bulk','POST',csv,token,upload_key,content_type='text/csv'),200,'bulk reconciles real committed parcel using original quote')
    check(replay['acceptedRows']==1 and replay['results'][0]['order']['fee']==850,'recovery pins 850 LKR instead of requoting at the new price')
    check(replay['results'][0]['order']['status']=='PICKED_UP','recovery respects the provider current lifecycle state')
    final=expect(call(PARCEL,'/internal/v1/parcels?page=0&size=100'),200,'inspect parcel count after reconciliation')
    check(final['totalElements']==committed['totalElements'],'lost-response recovery created no duplicate parcel')
    check(replay['results'][0]['order']['parcelId'] in {p['id'] for p in committed['content']},'reconciled mapping points at the original real parcel')
    expect(call(INTEGRATION,'/api/v1/merchant/orders/bulk','POST',csv.replace(reference,reference+'changed'),token,upload_key,content_type='text/csv'),409,'recovery rejects changed file with the original key')
    faults=expect(call(fault_parcel,'/__verification/faults'),200,'inspect committed parcel fault injection')
    check(faults['injected']==1,'one real successful parcel response was deliberately discarded')

    # Week 7: a single-order response can be lost after the real M1 commit too.
    single = dict(payload, externalReference='m6-loss-single-' + run_id)
    single_key = 'lost-single-' + run_id
    expect(call(INTEGRATION, path, 'POST', single, token, single_key), 502, 'single-order response discarded after real commit')
    committed_single = expect(call(PARCEL, '/internal/v1/parcels?page=0&size=100'), 200, 'inspect real single-order commit')
    check(committed_single['totalElements'] == final['totalElements'] + 1, 'one single-order parcel committed before recovery')
    single_id = next(p['id'] for p in committed_single['content'] if p['id'] not in {old['id'] for old in final['content']})
    expect(call(INTEGRATION, path, 'POST', single, token, single_key + '-new'), 409, 'unknown single-order reference blocks a new key')
    conflicting_csv = header + '\n' + f'{single["externalReference"]},Recovery Recipient,+94770000000,10 Road,,Colombo,Colombo,00100,20 Road,,Gampaha,Gampaha,11000,SMALL,1.5\n'
    conflict = expect(call(INTEGRATION, path + '/bulk', 'POST', conflicting_csv, token, 'cross-path-' + run_id, content_type='text/csv'), 200, 'bulk cannot take an uncertain single-order reference')
    check(conflict['acceptedRows'] == 0, 'cross-path reservation prevents another parcel write')
    expect(call(PARCEL, f'/internal/v1/parcels/{single_id}/status', 'PATCH',
        {'status': 'PICKED_UP', 'expectedCurrentStatus': 'CREATED'}), 200, 'advance single-order parcel before recovery')
    expect(call(provider, '/api/v1/admin/zones/' + dropoff['id'], 'PUT',
        {'name': dropoff['name'], 'districts': dropoff['districts'], 'served': dropoff['served'], 'baseFeeLkr': 1500},
        os.environ['M6_VERIFY_DISPATCHER_TOKEN']), 200, 'change pricing again before single-order retry')
    recovered = expect(call(INTEGRATION, path, 'POST', single, token, single_key), 201, 'single-order reconciles committed parcel with pinned quote')
    check(recovered['parcelId'] == single_id and recovered['fee'] == 1350 and recovered['status'] == 'PICKED_UP',
        'single-order recovery preserves original fee and provider lifecycle')
    check(expect(call(INTEGRATION, path, 'POST', single, token, single_key), 201, 'recovered single-order replay') == recovered,
        'recovered single-order response is stable')
    after_single = expect(call(PARCEL, '/internal/v1/parcels?page=0&size=100'), 200, 'inspect final single-order parcel count')
    check(after_single['totalElements'] == committed_single['totalElements'], 'single-order recovery created no duplicate parcel')
    faults = expect(call(fault_parcel, '/__verification/faults'), 200, 'inspect both real committed-response faults')
    check(faults['injected'] == 2, 'one bulk and one single-order committed response were discarded')


if __name__ == '__main__':
    main()
