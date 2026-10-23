# -*- coding: utf-8 -*-
"""
文案识别服务的对外入口，放在 ai_service/text_analyze。

启动（先进入 text_analyze 目录）：
    python app.py

给 Java 后端调用：
    POST http://127.0.0.1:5001/ai/analyze
    POST http://127.0.0.1:5001/ai/extract-tags
两个地址用同一套处理。端口用 5001，避免和别人的5000冲突。
"""
from flask import Flask, request, jsonify
from house_nlp import analyze_house

app = Flask(__name__)


@app.after_request
def allow_cors(resp):
    #Java后端和vue前端可能跨域访问
    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Access-Control-Allow-Headers"] = "Content-Type"
    resp.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    return resp


def read_body():
    """json和表单都能接收，对接时格式差一点也能读到。"""
    data = request.get_json(silent=True)
    if data is None:
        data = request.form.to_dict()
    if not data:
        data = {}
    return data


@app.route("/ai/analyze", methods=["POST", "OPTIONS"])
@app.route("/ai/extract-tags", methods=["POST", "OPTIONS"])
def analyze():
    # 预检请求直接放行，正文再拼卖点、小区、配套
    if request.method == "OPTIONS":
        return jsonify({"code": 0})
    data = read_body()
    core = data.get("核心卖点") or data.get("coreSelling") or ""
    community = data.get("小区介绍") or data.get("communityIntro") or ""
    surrounding = data.get("周边配套") or data.get("surrounding") or ""
    district = data.get("区域位置") or data.get("district") or ""

    result = analyze_house(core, community, surrounding, district)
    return jsonify({
        "code": 0,
        "msg": "ok",
        "data": result
    })


@app.route("/ai/health", methods=["GET"])
def health():
    """测试确认服务是否开启，正式页面不会涉及。"""
    return jsonify({"code": 0, "msg": "text_analyze is running"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)
