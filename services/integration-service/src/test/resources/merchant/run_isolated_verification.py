"""Run the M6 onboarding/order verification in disposable PostgreSQL/Kafka and real service JVMs.

Build all service JARs with Java 21 Maven verify first. Requires Docker, Python 3,
Java 21. Uses loopback ports 19181-19185, 19192, 19194, and 19125. Creates only its own containers and cleans them up.
Pricing uses a contract fixture that checks for a bearer header. The real provider is
also probed with a real auth-issued pricing:read token to expose owner blockers.
"""
import importlib.util
import json
import os
from pathlib import Path
import secrets
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
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[6]
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
DOCKER = shutil.which('docker')
RUN = 'swiftdrop-m6-week4-' + secrets.token_hex(4) + '-'
LOG_DIR = Path(tempfile.mkdtemp(prefix='swiftdrop-m6-week4-'))
processes = []
containers = []
logs = []
fixture = None
password = secrets.token_urlsafe(24)
internal_token = secrets.token_urlsafe(32)
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
            print('Waiting for ' + url.rsplit('/', 2)[0], flush=True)
            last_notice = time.monotonic()
        time.sleep(1)
    raise RuntimeError('Service readiness timed out: ' + url)


class PricingFixture(BaseHTTPRequestHandler):
    def do_GET(self):
        if not self.headers.get('Authorization', '').startswith('Bearer '):
            self.send_error(401)
            return
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        if parsed.path != '/internal/v1/pricing/quote' or set(query) != {'pickupDistrict', 'dropoffDistrict', 'weightKg', 'size'}:
            self.send_error(400)
            return
        body = json.dumps({'currency': 'LKR', 'totalFee': 850,
            'breakdown': {'dropoffZoneBaseFee': 500, 'weightBandCharge': 250, 'sizeCharge': 100},
            'pickupZone': 'Colombo fixture', 'dropoffZone': 'Gampaha fixture'}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


try:
    if not JAVA or not DOCKER:
        raise RuntimeError('Java 21 and Docker are required on PATH (or JAVA_HOME)')
    print('Private runtime logs: ' + str(LOG_DIR), flush=True)
    for port in [19181, 19182, 19183, 19184, 19185, 19192, 19194, 19125]:
        with socket.socket() as sock:
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
    docker('run', '-d', '--name', name, '--memory=128m', '-p', '127.0.0.1:19125:1025', 'axllent/mailpit:v1.32.0')
    containers.append(name)

    fixture = ThreadingHTTPServer(('127.0.0.1', 19183), PricingFixture)
    threading.Thread(target=fixture.serve_forever, daemon=True).start()
    print('Pricing uses the merged-contract fixture; the real provider is checked separately', flush=True)
    base_env = dict(os.environ,
        SPRING_PROFILES_ACTIVE='local', SPRING_DATASOURCE_USERNAME='m6verify', SPRING_DATASOURCE_PASSWORD=password,
        SPRING_KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19192', KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19192',
        SPRING_MAIL_HOST='127.0.0.1', SPRING_MAIL_PORT='19125',
        INTERNAL_MERCHANT_SERVICE_TOKEN=internal_token, INTERNAL_NOTIFICATIONS_SERVICE_TOKEN=secrets.token_urlsafe(32),
        JWKS_URL='http://127.0.0.1:19181/.well-known/jwks.json',
        SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI='http://127.0.0.1:19181/.well-known/jwks.json',
        SWIFTDROP_SECURITY_JWKS_URL='http://127.0.0.1:19181/.well-known/jwks.json',
        SERVICES_AUTH_BASE_URL='http://127.0.0.1:19181', SERVICES_PARCEL_BASE_URL='http://127.0.0.1:19182',
        SERVICES_OPERATIONS_BASE_URL='http://127.0.0.1:19183',
        SWIFTDROP_PRICING_QUOTE_URL='http://127.0.0.1:19183/internal/v1/pricing/quote',
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
            base_env.update(MERCHANT_PRICING_CLIENT_ID=pricing_client['clientId'],
                MERCHANT_PRICING_CLIENT_SECRET=pricing_client['clientSecret'])

    request = urllib.request.Request('http://127.0.0.1:19181/internal/v1/users', method='POST',
        headers={'Content-Type': 'application/json', 'X-Internal-Service-Token': internal_token},
        data=json.dumps({'email': 'm6-admin@example.test', 'password': admin_password,
            'fullName': 'M6 Verification Admin', 'phoneNumber': '+94770000000', 'role': 'ADMIN', 'enabled': True}).encode())
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 201:
            raise RuntimeError('Admin fixture provisioning failed')
    os.environ.update(M6_VERIFY_AUTH_URL='http://127.0.0.1:19181', M6_VERIFY_INTEGRATION_URL='http://127.0.0.1:19184',
        M6_VERIFY_PARCEL_URL='http://127.0.0.1:19182', M6_VERIFY_PRICING_PROVIDER_URL='http://127.0.0.1:19185',
        M6_VERIFY_ADMIN_EMAIL='m6-admin@example.test', M6_VERIFY_ADMIN_PASSWORD=admin_password,
        M6_VERIFY_PRICING_CLIENT_ID=pricing_client['clientId'],
        M6_VERIFY_PRICING_CLIENT_SECRET=pricing_client['clientSecret'],
        M6_VERIFY_INTERNAL_TOKEN=internal_token)
    path = ROOT / 'services/integration-service/src/test/resources/merchant/verify_real_parcel.py'
    spec = importlib.util.spec_from_file_location('m6_verifier', path)
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    verifier.main()
finally:
    for proc in reversed(processes):
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=10)
    if fixture:
        fixture.shutdown()
        fixture.server_close()
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
