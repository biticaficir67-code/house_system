"""Start standalone price API from any working directory."""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'vendor'))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='杭州历史房源辅助定价服务')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8001)
    args = parser.parse_args()
    import uvicorn
    uvicorn.run('api:app', host=args.host, port=args.port)
