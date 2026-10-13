"""Historical reference intervals and training-only support guards."""
import json
import math
from pathlib import Path
from datetime import datetime, timezone, timedelta
import numpy as np
import pandas as pd
from jsonschema import Draft202012Validator, FormatChecker
from features import BASE_NUMERIC, BASE_CATEGORICAL, community

ROOT = Path(__file__).parent
NOTICE = '基于历史挂牌样本的辅助定价演示；价格口径未核实，不代表当前市场价、未来价格或房地产评估报告。区间不保证覆盖实际成交价。'
RANGE_FIELDS = ['building_area_sqm', 'rooms', 'halls', 'bathrooms', 'total_floors']

def conformal_radius(scores, coverage=.9):
    values = np.asarray(scores, dtype=float)
    if not 0 < coverage < 1 or not len(values) or not np.isfinite(values).all() or (values < 0).any():
        raise ValueError('Invalid calibration residuals or coverage')
    rank = math.ceil((len(values) + 1) * coverage)
    if rank > len(values):
        raise ValueError('Insufficient calibration rows for finite interval')
    return float(np.partition(values, rank - 1)[rank - 1])

def support_metadata(train):
    def counts(fields):
        return {'::'.join(key): int(len(group)) for key, group in train.groupby(fields)}
    return {'communities': counts(['district', 'community_name']),
            'business_areas': counts(['district', 'business_area']),
            'ranges': {k: [float(train[k].min()), float(train[k].max())] for k in RANGE_FIELDS},
            'districts': sorted(train.district.unique().tolist())}

def guard(row, support):
    flags = []
    if row['district'] not in support['districts']:
        flags.append('DISTRICT_NOT_SUPPORTED')
    for key, (low, high) in support['ranges'].items():
        if not np.isfinite(row[key]) or not low <= row[key] <= high:
            flags.append('OUT_OF_TRAINING_RANGE:' + key)
    community_count = support['communities'].get(row['district'] + '::' + row['community_name'], 0)
    area_count = support['business_areas'].get(row['district'] + '::' + row['business_area'], 0)
    if community_count >= 5:
        level, count = 'COMMUNITY', community_count
    elif row['business_area'] != 'UNKNOWN' and area_count >= 20:
        level, count = 'BUSINESS_AREA', area_count
    else:
        level, count = 'NONE', community_count
        flags.append('INSUFFICIENT_REFERENCE_SAMPLES')
    group = 'seen' if community_count else 'unseen'
    return flags, level, count, group

def validate_request(request):
    schema = json.loads((ROOT / 'schemas/price-request.schema.json').read_text(encoding='utf-8'))
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(request)
    if 'UNKNOWN' in request['orientation'] and len(request['orientation']) != 1:
        raise ValueError('UNKNOWN 朝向只能单独填写')
    if (request.get('longitude') is None) != (request.get('latitude') is None):
        raise ValueError('经纬度必须同时填写或同时为空')
    if request.get('internal_area_sqm') is not None and request['internal_area_sqm'] > request['building_area_sqm']:
        raise ValueError('套内面积不能大于建筑面积')
    for key in ('city', 'district', 'community_name'):
        if not request[key].strip():
            raise ValueError(key + ' 不能为空白')
    if any(isinstance(v, float) and not math.isfinite(v) for v in request.values()):
        raise ValueError('数值必须有限')

def request_features(request):
    row = {k: request.get(k) or 'UNKNOWN' for k in BASE_CATEGORICAL}
    row['district'] = request['district'].strip().removesuffix('区')
    row['business_area'] = (request.get('business_area') or 'UNKNOWN').strip()
    row['community_name'] = community(request['community_name'])
    row.update({k: float(request[k]) for k in RANGE_FIELDS})
    row.update({k: float(request[k]) if request.get(k) is not None else np.nan for k in ('longitude', 'latitude', 'has_elevator')})
    row.update({'orientation_' + d: float(d in request['orientation']) for d in ('E','S','W','N','NE','NW','SE','SW','UNKNOWN')})
    return row

class PriceReference:
    def __init__(self, bundle):
        self.bundle = bundle

    def predict(self, request):
        validate_request(request)
        row = request_features(request)
        b = self.bundle
        flags, level, count, group = guard(row, b['support'])
        if request['city'].strip() not in ('杭州', '杭州市'):
            flags.append('CITY_NOT_SUPPORTED')
        if request['property_usage'] != 'RESIDENTIAL':
            flags.append('PROPERTY_USAGE_NOT_SUPPORTED')
        if group not in b['radii']:
            flags.append('INSUFFICIENT_CALIBRATION_SAMPLES')
        data = dict(status='UNAVAILABLE' if flags else 'DEMO_ONLY', usage='HISTORICAL_DEMO',
            price_basis='LISTING_UNVERIFIED', estimated_unit_price_yuan_sqm=None,
            estimated_total_price_yuan=None, price_interval=None, model_version=b['model_version'],
            data_version=b['data_version'], data_observed_at=None,
            sample_listing_date_min=b['listing_date_min'], sample_listing_date_max=b['listing_date_max'],
            comparable_sample_count=count, reference_level=level, quality_flags=flags,
            explanations=[], generated_at=datetime.now(timezone(timedelta(hours=8))).isoformat(), notice=NOTICE)
        if not flags:
            pred = float(b['pipeline'].predict(pd.DataFrame([row])[BASE_NUMERIC + BASE_CATEGORICAL])[0])
            if not math.isfinite(pred) or pred <= 0:
                data['status'] = 'UNAVAILABLE'
                data['quality_flags'].append('INVALID_MODEL_OUTPUT')
            else:
                radius = b['radii'][group]
                area = row['building_area_sqm']
                data.update(estimated_unit_price_yuan_sqm=round(pred, 2), estimated_total_price_yuan=round(pred * area, 2),
                    price_interval=dict(lower_total_price_yuan=round(pred * math.exp(-radius) * area, 2),
                        upper_total_price_yuan=round(pred * math.exp(radius) * area, 2),
                        nominal_coverage=b['coverage'], method='SPLIT_CONFORMAL_LOG_UNIT_BY_COMMUNITY_SEEN',
                        calibration_group=group, calibration_sample_count=b['calibration_counts'][group]))
                data['explanations'] = ['参考样本数是训练集中同小区或同板块记录数，并非严格匹配的相似成交房源。']
                data['quality_flags'] = ['HISTORICAL_DATA', 'PRICE_BASIS_UNVERIFIED']
                if level == 'BUSINESS_AREA':
                    data['quality_flags'].append('BUSINESS_AREA_FALLBACK')
                if any(row[k] == 'UNKNOWN' for k in BASE_CATEGORICAL) or not np.isfinite(row['has_elevator']):
                    data['quality_flags'].append('MISSING_OPTIONAL_FEATURES')
        return dict(code=0, msg='样本或输入覆盖不足，请人工核验' if data['status']=='UNAVAILABLE' else 'ok', request_id=request['request_id'], data=data)
