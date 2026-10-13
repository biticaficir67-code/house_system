"""Local/internal historical auxiliary pricing API, port 8001."""
import sys
import os
from pathlib import Path
ROOT = Path(__file__).parent
sys.path.insert(0, str(ROOT/'vendor'))
import joblib
import logging
from fastapi import FastAPI, Body
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from jsonschema.exceptions import ValidationError
from price_reference import PriceReference

def create_app(service=None):
    app = FastAPI(title='历史房源辅助定价', version='1.0')
    app.state.service = service
    if service is None:
        path = Path(os.environ.get('PRICE_MODEL_PATH', str(ROOT/'models/reference.joblib')))
        if path.exists():
            app.state.service = PriceReference(joblib.load(path))

    @app.exception_handler(RequestValidationError)
    async def invalid_body(request, error):
        return JSONResponse({'code':42201,'msg':'请求必须为合法 JSON 对象','data':None},status_code=422)

    @app.get('/health')
    def health():
        return {'status': 'ok'}

    @app.get('/ready')
    def ready():
        return JSONResponse({'ready': app.state.service is not None}, status_code=200 if app.state.service else 503)

    @app.post('/ai/predict/price')
    def predict(request: dict = Body(...)):
        if app.state.service is None:
            return JSONResponse({'code':50301,'msg':'模型尚未就绪','data':None},status_code=503)
        try:
            return app.state.service.predict(request)
        except (ValidationError, ValueError) as error:
            message = error.message if isinstance(error, ValidationError) else str(error)
            return JSONResponse({'code':42201,'msg':message,'data':None},status_code=422)
        except Exception:
            logging.exception('Historical price inference failed')
            return JSONResponse({'code':50001,'msg':'估价服务内部错误','data':None},status_code=500)
    return app

app = create_app()
