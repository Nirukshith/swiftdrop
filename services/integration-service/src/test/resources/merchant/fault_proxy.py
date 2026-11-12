"""Loopback-only verification proxy: discard one successful response AFTER provider commit."""
import json
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class FaultProxy(BaseHTTPRequestHandler):
    def forward(self):
        if self.path == '/__verification/faults':
            self.respond(200, json.dumps({'injected': len(self.server.dropped), 'operationIds': list(self.server.auth_operations)}).encode(), 'application/json')
            return
        data = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        headers = {key: value for key, value in self.headers.items()
                   if key.lower() not in {'host', 'connection', 'content-length', 'transfer-encoding'}}
        request = urllib.request.Request(self.server.upstream + self.path, method=self.command,
                                         headers=headers, data=data if data else None)
        try:
            response = urllib.request.urlopen(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read()
            status = response.status
            content_type = response.headers.get('Content-Type', 'application/json')
        fault = None
        payload = json.loads(data) if data and self.headers.get('Content-Type', '').startswith('application/json') else {}
        with self.server.guard:
            if self.server.kind == 'parcel' and self.command == 'POST' and self.path == '/internal/v1/parcels':
                if payload.get('externalReference', '').startswith('m6-loss-bulk-'):
                    fault = ('parcel', self.headers.get('Idempotency-Key'))
            elif self.server.kind == 'auth' and self.command == 'PUT' and '/merchant-provisioning/' in self.path:
                operation = self.path.rsplit('/', 1)[-1]
                if payload.get('email', '').startswith('m6-loss-'):
                    self.server.auth_operations.add(operation)
                    fault = ('provisioning', operation)
            elif self.server.kind == 'auth' and self.command == 'POST' and self.path.endswith('/credentials'):
                operation = self.path.split('/')[-2]
                if operation in self.server.auth_operations:
                    fault = ('replacement', operation)
            if status in (200, 201) and fault and fault not in self.server.dropped:
                self.server.dropped.add(fault)
                # The real provider has already replied successfully. Its body is discarded;
                # M6 receives a gateway failure with an ambiguous committed outcome.
                self.respond(502, b'{"code":"VERIFICATION_RESPONSE_LOST","message":"Committed provider response discarded by isolated fault fixture"}', 'application/json')
                return
        self.respond(status, body, content_type)

    def respond(self, status, body, content_type):
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    do_GET = forward
    do_POST = forward
    do_PUT = forward
    do_PATCH = forward

    def log_message(self, *_):
        pass


def start_proxy(port, upstream, kind):
    server = ThreadingHTTPServer(('127.0.0.1', port), FaultProxy)
    server.upstream = upstream
    server.kind = kind
    server.dropped = set()
    server.auth_operations = set()
    server.guard = threading.Lock()
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server
