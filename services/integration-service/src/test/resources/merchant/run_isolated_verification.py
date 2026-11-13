"""Run the M6 onboarding/order verification in disposable PostgreSQL/Kafka and real service JVMs.

Build all service JARs with Java 21 Maven verify first. Requires Docker, Python 3,
Java 21. Uses loopback service/fault ports 19181, 19182, 19184-19187, plus
19192, 19194, 19125 and 19126; optional Angular uses 19180. Creates and cleans its own fixtures.
Pricing uses the real operations service. Loopback fault proxies discard selected
successful auth/parcel responses after their commits to verify recovery.
"""
import importlib.util
import json
import os
from pathlib import Path
import secrets
import signal
import shutil
import tempfile
import socket
import sys
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from fault_proxy import start_proxy

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[6]
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
DOCKER = shutil.which('docker')
RUN = 'swiftdrop-m6-demo-' + secrets.token_hex(4) + '-'
LOG_DIR = Path(tempfile.mkdtemp(prefix='swiftdrop-m6-demo-'))
WITH_FRONTEND = os.environ.get('M6_VERIFY_FRONTEND') == '1'
frontend_process = None
demo_accounts = None
HOLD_SECONDS = int(os.environ.get('M6_DEMO_HOLD_SECONDS', '0'))
processes = []
containers = []
logs = []
proxies = []
password = secrets.token_urlsafe(24)
internal_token = secrets.token_urlsafe(32)
pricing_internal_token = secrets.token_urlsafe(32)
admin_password = secrets.token_urlsafe(24) + 'A1!'


def docker(*args):
    result = subprocess.run([DOCKER, *args], capture_output=True, text=True, timeout=120)
    if result.returncode:
        raise RuntimeError('Docker operation failed: ' + args[0] + ': ' + result.stderr[:500])
    return result.stdout.strip()


def wait_http(url, process=None):
    until = time.monotonic() + 150
    last_notice = 0
    while time.monotonic() < until:
        if process and process.poll() is not None:
            raise RuntimeError('Service exited before readiness: ' + url)
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                if response.status == 200:
                    return
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        if time.monotonic() - last_notice > 20:
            origin = urllib.parse.urlsplit(url)
            print('Waiting for ' + origin.scheme + '://' + origin.netloc, flush=True)
            last_notice = time.monotonic()
        time.sleep(1)
    raise RuntimeError('Service readiness timed out: ' + url)




try:
    if not 0 <= HOLD_SECONDS <= 1800 or HOLD_SECONDS and not WITH_FRONTEND:
        raise ValueError('Demo hold must be 0–1800 seconds; a nonzero hold requires M6_VERIFY_FRONTEND=1')
    if not JAVA or not DOCKER:
        raise RuntimeError('Java 21 and Docker are required on PATH (or JAVA_HOME)')
    if WITH_FRONTEND:
        node = subprocess.run(['node', '--version'], capture_output=True, text=True, check=True)
        if not node.stdout.startswith('v22.') or not (ROOT / 'frontend/node_modules').is_dir():
            raise RuntimeError('Frontend verification requires Node 22 and npm ci in frontend')
    print('Private runtime logs: ' + str(LOG_DIR), flush=True)
    ports = [19181, 19182, 19184, 19185, 19186, 19187, 19192, 19194, 19125, 19126]
    if WITH_FRONTEND:
        ports.append(19180)
    for port in ports:
        with socket.socket() as sock:
            # A just-cleaned verification run can leave TCP TIME_WAIT sockets.
            # Match the HTTP servers' reuse setting without replacing an active listener.
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(('127.0.0.1', port))
    for module in ['auth', 'parcel', 'operations', 'integration']:
        jar = ROOT / f'services/{module}-service/target/{module}-service-0.1.0-SNAPSHOT.jar'
        if not jar.exists():
            raise RuntimeError('Missing verified service JAR: ' + module)

    print('Starting isolated PostgreSQL and Kafka fixtures', flush=True)
    name = RUN + 'postgres'
    docker('run', '-d', '--name', name, '--memory=300m', '-p', '127.0.0.1:19194:5432',
        '-e', 'POSTGRES_USER=m6verify', '-e', 'POSTGRES_PASSWORD=' + password, 'postgres:16-alpine')
    containers.append(name)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        # The image's temporary initialization server accepts Unix-socket probes
        # before restarting. Wait for the final TCP listener before creating DBs.
        check = subprocess.run([DOCKER, 'exec', name, 'pg_isready', '-h', '127.0.0.1', '-U', 'm6verify'], capture_output=True)
        if check.returncode == 0:
            break
        time.sleep(1)
    else:
        raise RuntimeError('Isolated PostgreSQL did not become ready')
    for database in ['auth_db', 'parcel_db', 'ops_db', 'integration_db']:
        docker('exec', name, 'psql', '-U', 'm6verify', '-d', 'postgres', '-c', 'CREATE DATABASE ' + database)
    name = RUN + 'kafka'
    kafka_env = {
        'KAFKA_NODE_ID': '1', 'KAFKA_PROCESS_ROLES': 'broker,controller',
        'KAFKA_LISTENERS': 'PLAINTEXT://:9092,CONTROLLER://:9093',
        'KAFKA_ADVERTISED_LISTENERS': 'PLAINTEXT://127.0.0.1:19192',
        'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP': 'PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT',
        'KAFKA_CONTROLLER_LISTENER_NAMES': 'CONTROLLER', 'KAFKA_CONTROLLER_QUORUM_VOTERS': '1@localhost:9093',
        'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR': '1', 'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR': '1',
        'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR': '1', 'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS': '0',
        'KAFKA_HEAP_OPTS': '-Xms128m -Xmx384m',
    }
    args = ['run', '-d', '--name', name, '--memory=700m', '-p', '127.0.0.1:19192:9092']
    for key, value in kafka_env.items():
        args += ['-e', key + '=' + value]
    docker(*args, 'apache/kafka:3.8.1')
    containers.append(name)
    time.sleep(8)
    name = RUN + 'mailpit'
    docker('run', '-d', '--name', name, '--memory=128m', '-p', '127.0.0.1:19125:1025',
        '-p', '127.0.0.1:19126:8025', 'axllent/mailpit:v1.32.0')
    containers.append(name)
    wait_http('http://127.0.0.1:19126/api/v1/messages')

    proxies.append(start_proxy(19186, 'http://127.0.0.1:19182', 'parcel'))
    proxies.append(start_proxy(19187, 'http://127.0.0.1:19181', 'auth'))
    print('Pricing uses real operations; isolated proxies inject committed-response loss only', flush=True)
    base_env = dict(os.environ,
        SPRING_PROFILES_ACTIVE='local', SPRING_DATASOURCE_USERNAME='m6verify', SPRING_DATASOURCE_PASSWORD=password,
        SPRING_KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19192', KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19192',
        SPRING_MAIL_HOST='127.0.0.1', SPRING_MAIL_PORT='19125',
        FRONTEND_BASE_URL='http://127.0.0.1:19180' if WITH_FRONTEND else 'http://localhost:4200',
        INTERNAL_MERCHANT_SERVICE_TOKEN=internal_token, INTERNAL_NOTIFICATIONS_SERVICE_TOKEN=secrets.token_urlsafe(32),
        INTERNAL_PRICING_SERVICE_TOKEN=pricing_internal_token,
        JWKS_URL='http://127.0.0.1:19181/.well-known/jwks.json',
        SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI='http://127.0.0.1:19181/.well-known/jwks.json',
        SWIFTDROP_SECURITY_JWKS_URL='http://127.0.0.1:19181/.well-known/jwks.json',
        SERVICES_AUTH_BASE_URL='http://127.0.0.1:19187', SERVICES_PARCEL_BASE_URL='http://127.0.0.1:19186',
        SERVICES_OPERATIONS_BASE_URL='http://127.0.0.1:19185',
        SWIFTDROP_PRICING_QUOTE_URL='http://127.0.0.1:19185/internal/v1/pricing/quote',
        LOGGING_LEVEL_ROOT='WARN', LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB='WARN', LOGGING_LEVEL_ORG_APACHE_KAFKA='ERROR')
    for module, port, db in [('auth',19181,'auth_db'), ('parcel',19182,'parcel_db'),
                             ('operations',19185,'ops_db'), ('integration',19184,'integration_db')]:
        env = dict(base_env, SERVER_PORT=str(port), SERVER_ADDRESS='127.0.0.1',
            SPRING_DATASOURCE_URL=f'jdbc:postgresql://127.0.0.1:19194/{db}')
        log = open(LOG_DIR / (module + '.log'), 'w')
        os.chmod(log.name, 0o600)
        logs.append(log)
        jar = ROOT / f'services/{module}-service/target/{module}-service-0.1.0-SNAPSHOT.jar'
        proc = subprocess.Popen([JAVA, '-Xms96m', '-Xmx384m', '-jar', str(jar)], env=env, stdout=log, stderr=subprocess.STDOUT)
        processes.append(proc)
        wait_http(f'http://127.0.0.1:{port}/actuator/health', proc)
        print('Ready: real ' + module + '-service', flush=True)
        if module == 'auth':
            def provision(path, body):
                request = urllib.request.Request('http://127.0.0.1:19181' + path, method='POST',
                    headers={'Content-Type': 'application/json', 'X-Internal-Service-Token': internal_token},
                    data=json.dumps(body).encode())
                with urllib.request.urlopen(request, timeout=20) as response:
                    return json.load(response)
            pricing_identity = secrets.token_hex(16)
            provision('/internal/v1/users', {'email': 'm6-pricing@example.test',
                'fullName': 'Isolated Pricing Client', 'phoneNumber': '+94770000000',
                'role': 'MERCHANT', 'merchantId': pricing_identity, 'enabled': True})
            pricing_client = provision('/internal/v1/clients', {'merchantId': pricing_identity,
                'clientName': 'Isolated M6 pricing service', 'scopes': ['pricing:read']})
            # Private fixture credentials configure only M6; no human role is elevated.
            if os.environ.get('M6_VERIFY_PRICING_MODE', 'oauth') == 'internal':
                base_env['MERCHANT_PRICING_CLIENT_ID'] = ''
                base_env['MERCHANT_PRICING_CLIENT_SECRET'] = ''
            else:
                base_env.update(MERCHANT_PRICING_CLIENT_ID=pricing_client['clientId'],
                    MERCHANT_PRICING_CLIENT_SECRET=pricing_client['clientSecret'])

    request = urllib.request.Request('http://127.0.0.1:19181/internal/v1/users', method='POST',
        headers={'Content-Type': 'application/json', 'X-Internal-Service-Token': internal_token},
        data=json.dumps({'email': 'm6-admin@example.test', 'password': admin_password,
            'fullName': 'M6 Verification Admin', 'phoneNumber': '+94770000000', 'role': 'ADMIN', 'enabled': True}).encode())
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 201:
            raise RuntimeError('Admin fixture provisioning failed')
    # Configure disposable real pricing via the existing authorized APIs.
    dispatcher_password = secrets.token_urlsafe(24) + 'A1!'
    provision('/internal/v1/users', {'email':'m6-dispatch@example.test','password':dispatcher_password,
        'fullName':'Isolated Pricing Dispatcher','phoneNumber':'+94770000000','role':'DISPATCHER','enabled':True})
    def public_call(base, path, body, token=None):
        headers={'Content-Type':'application/json'}
        if token: headers['Authorization']='Bearer '+token
        request=urllib.request.Request(base+path, method='POST',headers=headers,data=json.dumps(body).encode())
        with urllib.request.urlopen(request,timeout=20) as response: return json.load(response)
    dispatcher=public_call('http://127.0.0.1:19181','/api/v1/auth/login',
        {'email':'m6-dispatch@example.test','password':dispatcher_password})['accessToken']
    for district,fee in [('Colombo',0),('Gampaha',500)]:
        public_call('http://127.0.0.1:19185','/api/v1/admin/zones',
            {'name':'M6 verification '+district,'districts':[district],'served':True,'baseFeeLkr':fee},dispatcher)
    for kind,key,low,high,charge in [('WEIGHT_BAND','verification-0-50',0,50,250),('SIZE','SMALL',None,None,100),('SIZE','MEDIUM',None,None,100),('SIZE','LARGE',None,None,100)]:
        public_call('http://127.0.0.1:19185','/api/v1/admin/pricing-rules',
            {'kind':kind,'key':key,'minimumWeightKg':low,'maximumWeightKg':high,'chargeLkr':charge,'active':True},dispatcher)
    print('Real pricing configured through DISPATCHER APIs',flush=True)
    os.environ.update(M6_VERIFY_AUTH_URL='http://127.0.0.1:19181', M6_VERIFY_INTEGRATION_URL='http://127.0.0.1:19184',
        M6_VERIFY_AUTH_INTERNAL_URL='http://127.0.0.1:19181', M6_VERIFY_MAILPIT_URL='http://127.0.0.1:19126',
        M6_VERIFY_PARCEL_URL='http://127.0.0.1:19182', M6_VERIFY_PRICING_PROVIDER_URL='http://127.0.0.1:19185',
        M6_VERIFY_ADMIN_EMAIL='m6-admin@example.test', M6_VERIFY_ADMIN_PASSWORD=admin_password,
        M6_VERIFY_DISPATCHER_TOKEN=dispatcher, M6_VERIFY_FAULT_PARCEL_URL='http://127.0.0.1:19186',
        M6_VERIFY_FAULT_AUTH_URL='http://127.0.0.1:19187',
        M6_VERIFY_PRICING_CLIENT_ID=pricing_client['clientId'],
        M6_VERIFY_PRICING_CLIENT_SECRET=pricing_client['clientSecret'],
        M6_VERIFY_INTERNAL_PRICING_TOKEN=pricing_internal_token,
        M6_VERIFY_INTERNAL_TOKEN=internal_token)
    if WITH_FRONTEND:
        proxy = json.loads((ROOT / 'docs/m6-demo/proxy.conf.json').read_text())
        for context, config in proxy.items():
            config['target'] = 'http://127.0.0.1:' + ('19181' if context == '/api/v1/auth' else '19184')
        proxy_path = LOG_DIR / 'proxy.conf.json'
        proxy_path.write_text(json.dumps(proxy))
        os.chmod(proxy_path, 0o600)
        log = open(LOG_DIR / 'frontend.log', 'w')
        os.chmod(log.name, 0o600)
        logs.append(log)
        frontend_process = subprocess.Popen(['npm', 'start', '--', '--host', '127.0.0.1',
            '--port', '19180', '--proxy-config', str(proxy_path)], cwd=ROOT / 'frontend',
            stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        processes.append(frontend_process)
        wait_http('http://127.0.0.1:19180/', frontend_process)
        with urllib.request.urlopen('http://127.0.0.1:19180/merchant/bulk', timeout=20) as response:
            if response.status != 200 or '<app-root' not in response.read().decode():
                raise RuntimeError('Merchant deep link did not serve the Angular application')
        print('PASS Angular serves merchant deep link without API proxy interception', flush=True)
        os.environ.update(M6_VERIFY_AUTH_URL='http://127.0.0.1:19180', M6_VERIFY_INTEGRATION_URL='http://127.0.0.1:19180',
            M6_VERIFY_FRONTEND_URL='http://127.0.0.1:19180')
        print('Public merchant workflow uses the real Angular development proxy', flush=True)
    os.environ.pop('M6_DEMO_ACCOUNTS_FILE', None)
    if HOLD_SECONDS:
        demo_accounts = LOG_DIR / 'demo-accounts.json'
        with os.fdopen(os.open(demo_accounts, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), 'w') as private:
            json.dump({'admin': {'email': 'm6-admin@example.test', 'password': admin_password}}, private)
        os.environ['M6_DEMO_ACCOUNTS_FILE'] = str(demo_accounts)
    path = ROOT / 'services/integration-service/src/test/resources/merchant/verify_real_parcel.py'
    spec = importlib.util.spec_from_file_location('m6_verifier', path)
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    verifier.main()
    if HOLD_SECONDS:
        if demo_accounts.stat().st_mode & 0o777 != 0o600 or LOG_DIR.stat().st_mode & 0o777 != 0o700:
            raise RuntimeError('Demo login file permissions are not private')
        print('PASS live demo login file and directory have private permissions', flush=True)
        for role, login in json.loads(demo_accounts.read_text()).items():
            signed_in = public_call(os.environ['M6_VERIFY_AUTH_URL'], '/api/v1/auth/login', login)
            if signed_in['user']['role'] != role.upper():
                raise RuntimeError('Live demo login role does not match the intended account')
            print('PASS private live demo ' + role + ' login authenticates through Angular', flush=True)
        print('Demo UI: http://127.0.0.1:19180 ; Mailpit: http://127.0.0.1:19126', flush=True)
        print('Private demo logins (do not record/share): ' + str(demo_accounts), flush=True)
        print('Demo remains running for ' + str(HOLD_SECONDS) + ' seconds; Ctrl+C also cleans fixtures.', flush=True)
        time.sleep(HOLD_SECONDS)
finally:
    if demo_accounts:
        demo_accounts.unlink(missing_ok=True)
        print('Private demo login file removed', flush=True)
    if frontend_process:
        try:
            os.killpg(frontend_process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    for proc in reversed(processes):
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                if proc is frontend_process:
                    try:
                        os.killpg(proc.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                else:
                    proc.kill()
                proc.wait(timeout=10)
    for proxy in proxies:
        proxy.shutdown()
        proxy.server_close()
    for log in logs:
        log.close()
    incomplete = []
    for name in reversed(containers):
        result = subprocess.run([DOCKER, 'rm', '-f', '-v', name], capture_output=True, timeout=30)
        if result.returncode:
            incomplete.append(name)
    if incomplete:
        raise RuntimeError('Container cleanup incomplete: ' + ', '.join(incomplete))
    print('Isolated runtime fixtures cleaned up', flush=True)
