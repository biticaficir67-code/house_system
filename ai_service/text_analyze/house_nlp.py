# -*- coding: utf-8 -*-
"""
房源文案智能分析。

卖点词的产生：先在练习数据上训练 TF-IDF 和 LDA（见 train.py），
识别时用训练好的模型给这一篇文案打分，按权重取出关键词。
没有模型文件时，用TextRank只对这一篇构图取词，不依赖卖点统计表。

TAG_LIST是看过训练结果之后整理的展示用标签，方便页面打勾，
不是识别的唯一来源。
"""
import os
import re
import jieba
import jieba.analyse

#其中一些词汇室房产里的固定说法，如果此处不强调成词的话，像“满五唯一”这样的术语会被切开
for _w in ["满五唯一", "满五年", "满两年", "近地铁", "学区房", "南北通透",
           "人车分流", "品牌物业", "无抵押", "有电梯", "精装修", "次新房",
           "拎包入住", "农贸市场", "地铁口", "地铁站", "商圈", "购物中心",
           "步行街", "美食街", "商业街", "印象城"]:
    jieba.add_word(_w)

#去除虚词、套话、地名，以及单独拿出来没有卖点意义的词（号线、广场、家园、板块）
STOP_WORDS = set("""
的 了 是 在 有 和 与 及 到 为 中 等 这个 就是 可以 以及 或者 如果 因为 所以
但是 而且 还 也 都 很 对 上 于 把 被 从 就 还 并 或 等 比较 非常 一个 我们
进行 还有 位于 周边 配套 介绍 房子 房源 业主 目前 左右 距离 大概 此房 小区
方便 生活 出行 居住 适合 齐全 完善 项目 建筑 位置 面积 公里 米左右 百度
地图 有限公司 自己 出门 附近 达到 分钟 左右 门口 便利 交通 环境 规划 开发
设施 楼盘 住宅 房屋 房间 直达 快速 高架 路口 大道 东路 南路 北路 西路
号线 广场 家园 板块 社区 花园 新村 公寓 花苑 名苑 楼栋 物业
杭州 杭州市 滨江 西湖 余杭 萧山 拱墅 上城 下城 江干 临平 富阳 临安 钱塘
滨江区 西湖区 余杭区 萧山区 拱墅区 上城区 钱塘区 临平区 钱塘江
西溪 良渚 闲林 天目山 绿城 联华 开发商 三墩 三吨
""".split())

#调试之后，发现楼盘名、广场名常常带这些尾巴，整段丢掉
NOISE_SUFFIX = ("家园", "花园", "广场", "公寓", "新村", "花苑", "名苑", "别苑",
                "板块", "小区", "府邸", "公馆", "壹号")

#能用来对上标签、但单独展示没有参考意义的词
WEAK_KEYS = set(["号线", "广场"])

# 看过TF-IDF/LDA主题词之后整理的展示标签，页面上可以使用这些标签
TAG_LIST = [
    {"name": "近地铁", "keys": ["地铁", "地铁口", "地铁站", "号线"]},
    {"name": "公交便利", "keys": ["公交", "公交站", "公交车", "公交车站"]},
    {"name": "近医院", "keys": ["医院", "卫生院", "诊所"]},
    {"name": "近学校", "keys": ["学校", "小学", "中学", "幼儿园", "学区"]},
    {"name": "近商圈", "keys": ["商圈", "步行街", "美食街", "商业街", "购物中心",
                              "印象城", "万达", "银泰", "吾悦", "天街", "大悦城", "奥特莱斯"]},
    {"name": "商业配套", "keys": ["商场", "购物", "综合体", "超市"]},
    {"name": "近菜市场", "keys": ["菜场", "菜市场", "农贸市场"]},
    {"name": "近公园", "keys": ["公园", "湿地", "绿化"]},
    {"name": "江景河景", "keys": ["江景", "河景", "湖景", "沿江", "沿河"]},
    {"name": "精装修", "keys": ["精装", "精装修", "豪装"]},
    {"name": "拎包入住", "keys": ["拎包", "拎包入住"]},
    {"name": "采光好", "keys": ["采光", "通透", "朝南"]},
    {"name": "南北通透", "keys": ["南北通", "南北通透"]},
    {"name": "户型方正", "keys": ["方正", "全明", "格局方正"]},
    {"name": "有电梯", "keys": ["电梯", "有电梯"]},
    {"name": "高楼层景观", "keys": ["高层", "视野", "景观房"]},
    {"name": "人车分流", "keys": ["人车分流"]},
    {"name": "品牌物业", "keys": ["品牌物业", "物业好", "物业服务"]},
    {"name": "车位充足", "keys": ["车位", "车库", "停车"]},
    {"name": "环境安静", "keys": ["安静", "不临街", "内街"]},
    {"name": "满五唯一", "keys": ["满五唯一", "满五", "满五年"]},
    {"name": "满两年", "keys": ["满两年", "满二"]},
    {"name": "无抵押", "keys": ["无抵押", "没有抵押"]},
    {"name": "税费较低", "keys": ["税费少", "税费低", "个税免", "增值税免"]},
    {"name": "次新房", "keys": ["次新", "次新房"]},
    {"name": "近银行", "keys": ["银行"]},
    {"name": "口碑较好", "keys": ["口碑很好", "评价很好", "五星好评", "超赞"]},
]

BAD_LIST = [
    {"name": "有抵押", "keys": ["有抵押", "抵押中", "抵押贷"]},
    {"name": "无电梯", "keys": ["无电梯", "没有电梯", "步梯"]},
    {"name": "临街吵闹", "keys": ["临街", "噪音", "吵"]},
    {"name": "不满五", "keys": ["不满五", "未满五"]},
]

#文案里常见的形近、音近写错，先改成标准说法再识别
TYPO_PAIRS = [
    ("经装修", "精装修"), ("菁装修", "精装修"), ("晶装修", "精装修"),
    ("睛装修", "精装修"), ("净装修", "精装修"), ("精装璜", "精装修"),
    ("邻包入住", "拎包入住"), ("领包入住", "拎包入住"),
    ("邻包", "拎包"), ("领包", "拎包"),
    ("彩光", "采光"), ("採光", "采光"),
    ("户形", "户型"), ("户形方正", "户型方正"),
    ("电悌", "电梯"), ("地跌", "地铁"),
    ("满伍唯一", "满五唯一"), ("满伍", "满五"),
    ("无底押", "无抵押"), ("无抵压", "无抵押"),
    ("人车分留", "人车分流"), ("南北桶透", "南北通透"),
    ("开电梯", "有电梯"),
]

#表情先换成对应的词，后面按普通文案处理
EMOJI_TO_TEXT = {
    "🚇": "地铁", "🚉": "地铁", "🚌": "公交", "🚍": "公交",
    "🏫": "学校", "🏥": "医院", "🏦": "银行",
    "🌳": "公园绿化", "🌲": "绿化", "🛒": "超市",
    "🏬": "商场", "🛍️": "购物", "🏪": "超市",
    "⭐": "五星", "🌟": "五星", "✨": "口碑很好",
    "👍": "口碑很好", "💯": "口碑很好",
    "😊": "口碑很好", "😄": "口碑很好",
    "❤️": "口碑很好", "❤": "口碑很好",
    "🔑": "拎包入住",
}

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
MODEL_FILE = os.path.join(DATA_DIR, "text_model.joblib")

_model = None
_model_checked = False


def normalize_text(text):
    """
    识别前先把文案理顺：表情换成词，常见错别字改掉，
    打分、口头夸赞理解成“口碑很好”。
    """
    if not text:
        return ""
    text = str(text)
    for emo, word in EMOJI_TO_TEXT.items():
        if emo in text:
            text = text.replace(emo, word)
    for wrong, right in TYPO_PAIRS:
        if wrong in text:
            text = text.replace(wrong, right)
    text = re.sub(r"[经菁晶睛净]装修", "精装修", text)
    text = re.sub(r"(\d+)分钟", r"\1_MIN_", text)
    text = re.sub(r"(打|给|评)?(十|10)分(?!钟)", "口碑很好", text)
    text = re.sub(r"(?<!\d)(9|9\.5|10|10\.0)\s*分", "口碑很好", text)
    text = re.sub(r"满分|五星好评|五星推荐|五星|超赞|yyds|YYDS|绝绝子", "口碑很好", text)
    text = re.sub(r"十分满意", "口碑很好", text)
    text = text.replace("_MIN_", "分钟")
    return text


def join_house_text(core_selling, community, surrounding):
    """房源录入后的三段文字拼成一篇文本，再做分词和模型打分。"""
    parts = []
    for x in (core_selling, community, surrounding):
        if x:
            parts.append(normalize_text(str(x).strip()))
    return " ".join(parts)


def is_noise_word(w):
    # 停用词、纯数字、楼盘名尾巴都不进关键词
    if w in STOP_WORDS or w.isdigit() or len(w) < 2:
        return True
    for s in NOISE_SUFFIX:
        if w == s or w.endswith(s):
            return True
    return False


_LEX = None


def selling_lexicon():
    """页面上真正要出现的词是卖点标签以及能说明卖点的词。"""
    global _LEX
    if _LEX is not None:
        return _LEX
    words = set()
    for item in TAG_LIST:
        words.add(item["name"])
        for k in item["keys"]:
            if k not in WEAK_KEYS:
                words.add(k)
    extra = """
        户型 学区房 精装 物业服务 停车 车库 车位 绿化 湿地
        通透 朝南 全明 视野 景观 电梯 满五 满二 个税 增值税
        农贸 菜场 地铁口 地铁站 公交站 幼儿园 小学 中学
        商圈 步行街 美食街 商业街 购物中心 商场 综合体 口碑很好
    """.split()
    words.update(extra)
    _LEX = words
    return _LEX


def is_selling_word(w):
    # 只保留能对上卖点标签或说明卖点的词
    if is_noise_word(w) or w in WEAK_KEYS:
        return False
    lex = selling_lexicon()
    if w in lex:
        return True
    for k in lex:
        if len(k) >= 2 and k in w:
            return True
    return False


def filter_selling_items(items):
    # 去掉重复和没有卖点意义的词
    seen = set()
    data = []
    for item in items:
        w = item["name"]
        if w in seen or not is_selling_word(w):
            continue
        seen.add(w)
        data.append(item)
    return data


def cut_words(text):
    # jieba 切词后丢掉噪声
    result = []
    for w in jieba.lcut(text or ""):
        w = w.strip()
        if is_noise_word(w):
            continue
        result.append(w)
    return result


def to_doc(text):
    """先切词再空格拼起来，给 sklearn 用。"""
    return " ".join(cut_words(text))


def match_tags(text, rule_list):
    # 文案里出现标签关键词就算命中
    hit = []
    for item in rule_list:
        for key in item["keys"]:
            if key in text:
                hit.append(item["name"])
                break
    return hit


#出门就是某某广场/周边配套里写有商业内容，视为临近商圈
MALL_NEAR = re.compile(
    r"(出门就是|出门即是|出门便是|出门就到|紧邻|旁边就是|楼下就是|步行可达|步行至)"
    r".{0,20}(广场|商场|商圈|步行街|购物中心|综合体)"
)


def has_mall_signal(core, community, surrounding):
    """
    文案里写「出门就是XX广场」或周边配套点名广场、步行街，当作近商圈。
    小区介绍里单独一个楼盘名带「广场」的，不按商圈算。
    """
    core = normalize_text(core or "")
    surrounding = normalize_text(surrounding or "")
    community = normalize_text(community or "")
    text = join_house_text(core, community, surrounding)
    if MALL_NEAR.search(text):
        return True
    blob = core + " " + surrounding
    if "广场" in blob:
        return True
    return False


def insert_tag(tags, name, before="商业配套"):
    # 近商圈插到商业配套前面，避免两个标签挤在一起不好看
    if name in tags:
        return tags
    if before in tags:
        i = tags.index(before)
        return tags[:i] + [name] + tags[i:]
    return tags + [name]


def load_model():
    """加载train.py写出的TF-IDF和LDA。文件不存在就返回None。"""
    global _model, _model_checked
    if _model_checked:
        return _model
    _model_checked = True
    if not os.path.exists(MODEL_FILE):
        _model = None
        return None
    try:
        import joblib
        _model = joblib.load(MODEL_FILE)
    except Exception:
        _model = None
    return _model


def extract_by_textrank(text, topn=15):
    """
    TextRank：把这一篇里的词看成图上的点，按相邻关系打分。
    不需要事先训练，单篇文案也能抽词。
    """
    if not text.strip():
        return []
    pairs = jieba.analyse.textrank(text, topK=topn * 3, withWeight=True)
    data = []
    for name, score in pairs:
        if not is_selling_word(name):
            continue
        data.append({"name": name, "value": round(float(score) * 100, 2)})
        if len(data) >= topn:
            break
    return data


def extract_by_tfidf(text, topn=15):
    """用训练好的 TF-IDF：在整库背景下，看这篇里哪些词更突出。"""
    model = load_model()
    if not model:
        return []
    vec = model["tfidf"]
    x = vec.transform([to_doc(text)])
    names = vec.get_feature_names_out()
    row = x.toarray()[0]
    order = row.argsort()[::-1]
    data = []
    for i in order:
        if row[i] <= 0:
            break
        w = names[i]
        if not is_selling_word(w):
            continue
        data.append({"name": w, "value": round(float(row[i]) * 100, 2)})
        if len(data) >= topn:
            break
    return data


def infer_topic(text):
    """LDA判断这篇更接近训练时的哪一类主题，并带上该类的代表词。"""
    model = load_model()
    if not model:
        return None
    count_vec = model["count"]
    lda = model["lda"]
    topic_words = model.get("topic_words") or []
    x = count_vec.transform([to_doc(text)])
    dist = lda.transform(x)[0]
    k = int(dist.argmax())
    raw = topic_words[k] if k < len(topic_words) else []
    words = [w for w in raw if is_selling_word(w)]
    return {
        "id": k + 1,
        "score": round(float(dist[k]), 3),
        "words": words
    }


def tags_from_fields(core, community, surrounding):
    # 三段文案对上展示标签，出门就是广场再补近商圈
    text = join_house_text(core, community, surrounding)
    tags = match_tags(text, TAG_LIST)
    if has_mall_signal(core, community, surrounding):
        tags = insert_tag(tags, "近商圈")
    return tags


def make_summary(advantages, disadvantages):
    # 详情页用的一两句归纳
    if not advantages and not disadvantages:
        return "文案信息较少，暂无法归纳明显卖点。"
    bits = []
    if advantages:
        bits.append("该房源突出" + "、".join(advantages[:6]))
    if disadvantages:
        bits.append("需注意" + "、".join(disadvantages[:3]))
    return "；".join(bits) + "。"


def analyze_house(core_selling, community, surrounding, district=""):
    """
    接口入口。
    关键词来自TF-IDF（有模型时）或 TextRank（没有模型时）。
    """
    text = join_house_text(core_selling, community, surrounding)
    tfidf_words = extract_by_tfidf(text)
    rank_words = extract_by_textrank(text)
    if tfidf_words:
        keywords = filter_selling_items(tfidf_words + rank_words)[:12]
        method = "训练模型 TF-IDF + TextRank"
    else:
        keywords = filter_selling_items(rank_words)[:12]
        method = "单篇 TextRank（未加载模型）"

    topic = infer_topic(text)
    advantages = tags_from_fields(core_selling, community, surrounding)
    disadvantages = match_tags(text, BAD_LIST)
    if "近商圈" in advantages:
        names = set(x["name"] for x in keywords)
        if not names.intersection(["商圈", "步行街", "商场", "购物中心", "综合体"]):
            keywords.insert(0, {"name": "商圈", "value": 40})

    # 同类主题里的卖点词，这篇没写到的，仅作对照
    if topic and topic.get("words"):
        have = set([x["name"] for x in keywords])
        have.update(cut_words(text))
        extra = []
        for w in topic["words"]:
            if not is_selling_word(w):
                continue
            if w not in have and w not in text:
                extra.append("同类主题较常见、本文未突出：" + w)
        disadvantages = disadvantages + extra[:3]

    return {
        "summary": make_summary(advantages, disadvantages),
        "wordFreq": keywords,
        "keywords": keywords,
        "advantages": advantages,
        "disadvantages": disadvantages,
        "topic": topic,
        "method": method
    }
