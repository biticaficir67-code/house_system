"""Verify the delivered model, schema copies, and actual server startup after relocation."""
import hashlib
import json
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class DeliveryTests(unittest.TestCase):
    def test_model_checksum_and_schema(self):
        manifest = json.loads((ROOT/'models/manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(hashlib.sha256((ROOT/'models/reference.joblib').read_bytes()).hexdigest(),manifest['model_sha256'])
        runtime=json.loads((ROOT/'schemas/price-request.schema.json').read_text(encoding='utf-8'))
        documented=json.loads((ROOT.parents[1]/'docs/ai/price-request.schema.json').read_text(encoding='utf-8'))
        self.assertEqual(runtime,documented)

    def test_start_from_other_directory(self):
        with socket.socket() as socket_handle:
            socket_handle.bind(('127.0.0.1',0))
            port=socket_handle.getsockname()[1]
        with tempfile.TemporaryDirectory() as directory:
            process=subprocess.Popen([sys.executable,str(ROOT/'start_service.py'),'--port',str(port)],
                cwd=directory,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
                creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
            try:
                deadline=time.monotonic()+15
                ready=False
                while time.monotonic()<deadline and process.poll() is None:
                    try:
                        with urllib.request.urlopen(f'http://127.0.0.1:{port}/ready',timeout=1) as response:
                            ready=json.load(response)['ready']
                        if ready:
                            break
                    except OSError:
                        time.sleep(.1)
                self.assertTrue(ready,'Server failed to become ready after relocation')
                payload=(ROOT/'examples/price-request.json').read_bytes()
                request=urllib.request.Request(f'http://127.0.0.1:{port}/ai/predict/price',data=payload,headers={'Content-Type':'application/json'},method='POST')
                with urllib.request.urlopen(request,timeout=5) as response:
                    result=json.load(response)
                self.assertEqual(result['data']['status'],'DEMO_ONLY')
                recorded=json.loads((ROOT/'examples/price-response.json').read_text(encoding='utf-8'))
                self.assertEqual(result['data']['estimated_total_price_yuan'],recorded['data']['estimated_total_price_yuan'])
            finally:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)

if __name__=='__main__':
    unittest.main()
