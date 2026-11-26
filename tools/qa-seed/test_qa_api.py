import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from qa_api import request


class QaApiSafetyTests(unittest.TestCase):
    def testRedirectDoesNotForwardQaAccessToken(self):
        received=[]
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                received.append(self.path)
                self.send_response(302)
                self.send_header('Location','/other-origin-simulation')
                self.end_headers()
            def log_message(self,*args):pass
        server=HTTPServer(('127.0.0.1',0),Handler)
        worker=threading.Thread(target=server.serve_forever,daemon=True);worker.start()
        try:
            with self.assertRaisesRegex(RuntimeError,'리다이렉트'):
                request('http://127.0.0.1:'+str(server.server_port),'/source',token='synthetic-qa-token')
            self.assertEqual(received,['/source'])
        finally:server.shutdown();server.server_close();worker.join()

    def testHttpErrorDoesNotEchoResponseMessageOrToken(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(401);self.end_headers();self.wfile.write(b'{"code":"C002","message":"do-not-echo-body"}')
            def log_message(self,*args):pass
        server=HTTPServer(('127.0.0.1',0),Handler)
        worker=threading.Thread(target=server.serve_forever,daemon=True);worker.start()
        try:
            with self.assertRaisesRegex(RuntimeError,'HTTP 401 / C002') as result:
                request('http://127.0.0.1:'+str(server.server_port),'/source',token='synthetic-qa-token')
            self.assertNotIn('do-not-echo-body',str(result.exception))
        finally:server.shutdown();server.server_close();worker.join()


if __name__=='__main__':unittest.main()
