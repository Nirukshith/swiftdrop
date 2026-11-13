"""Real HTTP framing checks for the isolated verification fixture."""
import http.client
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from fault_proxy import start_proxy


class Provider(BaseHTTPRequestHandler):
    def do_PUT(self):
        body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        self.server.received.append(body)
        data=json.dumps({'clientId':'same-client','clientSecret':'test-only-response'}).encode()
        self.send_response(200)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self,*_): pass


class FaultProxyTest(unittest.TestCase):
    def setUp(self):
        self.provider=ThreadingHTTPServer(('127.0.0.1',0),Provider)
        self.provider.received=[]
        threading.Thread(target=self.provider.serve_forever,daemon=True).start()
        self.proxy=start_proxy(0,'http://127.0.0.1:'+str(self.provider.server_port),'auth')

    def tearDown(self):
        for server in (self.proxy,self.provider):
            server.shutdown(); server.server_close()

    def send(self,email):
        connection=http.client.HTTPConnection('127.0.0.1',self.proxy.server_port)
        data=json.dumps({'email':email}).encode()
        connection.request('PUT','/internal/v1/merchant-provisioning/test-operation',
            body=iter([data[:8],data[8:]]),headers={'Content-Type':'application/json'},encode_chunked=True)
        response=connection.getresponse()
        result=(response.status,response.read())
        connection.close()
        return result

    def test_chunked_java_style_body_reaches_the_real_provider(self):
        status,_=self.send('normal@example.test')
        self.assertEqual(status,200)
        self.assertEqual(self.provider.received,[{'email':'normal@example.test'}])

    def test_fault_discards_only_one_response_after_provider_received_the_body(self):
        first=self.send('m6-loss-test@example.test')
        second=self.send('m6-loss-test@example.test')
        self.assertEqual(first[0],502)
        self.assertNotIn(b'test-only-response',first[1])
        self.assertEqual(second[0],200)
        self.assertEqual(len(self.provider.received),2)
        self.assertEqual(len(self.proxy.dropped),1)


if __name__=='__main__': unittest.main()
