# -*- coding: utf-8 -*-
"""
离线训练。在 text_analyze 目录执行：python train.py

流程：读练习数据 → 分词 → 拟合TF-IDF和LDA → 打印主题词和整库高权重词
→ 对照打印结果改 STOP_WORDS/TAG_LIST → 再跑一遍，直到词比较干净。
写出的 text_model.joblib 给 house_nlp.py 识别时加载。

observe_keywords.json 只是观察用的参考，线上抽词不靠翻这张表。
"""
import os
import csv
import json
import glob
from collections import Counter

import joblib
from sklearn.feature_extraction.text import CountVectorizer, TfidfVectorizer
from sklearn.decomposition import LatentDirichletAllocation

from house_nlp import join_house_text, cut_words, to_doc, TAG_LIST, match_tags

CSV_DIR = r"D:\中南大学\学习\Web开发技术\my_data\my_data"
DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
MODEL_FILE = os.path.join(DATA_DIR, "text_model.joblib")
OBSERVE_FILE = os.path.join(DATA_DIR, "observe_keywords.json")


def read_all_houses():
    files = glob.glob(os.path.join(CSV_DIR, "*.csv"))
    houses = []
    for path in files:
        with open(path, "r", encoding="gb18030", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                text = join_house_text(
                    row.get("核心卖点"),
                    row.get("小区介绍"),
                    row.get("周边配套")
                )
                if text:
                    houses.append({"text": text})
    print("共读入有文案的房源：", len(houses))
    return houses


def print_raw_freq(houses, n=30):
    """先看切出来的生词，决定还要不要加停用词。"""
    cnt = Counter()
    for h in houses:
        cnt.update(cut_words(h["text"]))
    print("\n----- 分词后出现次数最多的%d个（观察用）-----" % n)
    for w, c in cnt.most_common(n):
        print(c, "\t", w)


def train_and_save(houses, n_topics=6):
    docs = [to_doc(h["text"]) for h in houses]
    print("\n----- 拟合 TF-IDF -----")
    tfidf = TfidfVectorizer(max_features=1000, min_df=15)
    x_tfidf = tfidf.fit_transform(docs)

    # 整库平均 TF-IDF，用来观察「数据里真正突出的词」
    import numpy as np
    mean_w = np.asarray(x_tfidf.mean(axis=0)).ravel()
    names = tfidf.get_feature_names_out()
    order = mean_w.argsort()[::-1]
    observe = []
    print("\n----- 整库 TF-IDF 较高的词（用于观察、对照）-----")
    for i in order[:40]:
        observe.append({"name": str(names[i]), "score": round(float(mean_w[i]), 5)})
        print("%.5f\t%s" % (mean_w[i], names[i]))

    print("\n----- 拟合 LDA（耗时一至两分钟）-----")
    count_vec = CountVectorizer(max_features=1000, min_df=15)
    x_count = count_vec.fit_transform(docs)
    lda = LatentDirichletAllocation(
        n_components=n_topics,
        random_state=0,
        max_iter=10,
        learning_method="batch"
    )
    lda.fit(x_count)
    cnames = count_vec.get_feature_names_out()
    topic_words = []
    for i, topic in enumerate(lda.components_):
        idx = topic.argsort()[-10:][::-1]
        words = [str(cnames[j]) for j in idx]
        topic_words.append(words)
        print("主题", i + 1, ":", "、".join(words))

    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)

    joblib.dump({
        "tfidf": tfidf,
        "count": count_vec,
        "lda": lda,
        "topic_words": topic_words
    }, MODEL_FILE)
    print("\n模型已写入：", MODEL_FILE)

    with open(OBSERVE_FILE, "w", encoding="utf-8") as f:
        json.dump({
            "note": "训练观察结果，不是线上查询表。识别走模型打分。",
            "tfidf_top": observe,
            "lda_topics": topic_words
        }, f, ensure_ascii=False, indent=2)
    print("观察结果：", OBSERVE_FILE)

    # 仍统计一遍展示标签出现次数，只作对照
    counter = Counter()
    for h in houses:
        for t in match_tags(h["text"], TAG_LIST):
            counter[t] += 1
    print("\n----- 当前展示标签在数据里出现的次数（对照用）-----")
    for name, c in counter.most_common():
        print(c, "\t", name)


if __name__ == "__main__":
    houses = read_all_houses()
    print_raw_freq(houses)
    train_and_save(houses)
    print("\n训练结束。改完停用词或标签后可以再跑一次本脚本。")
    print("对外服务：python app.py")
