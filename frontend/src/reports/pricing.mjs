const positive = value => value !== null && value !== undefined && Number.isFinite(Number(value)) && Number(value) > 0 ? Number(value) : null
export const priceStatus = status => ({ DEMO_ONLY: '历史参考', UNAVAILABLE: '无法估价', FAILED: '服务调用失败', LEGACY: '历史导入记录' }[status] || '未知状态')
export const reasonFor = value => ({
  CITY_NOT_SUPPORTED: '当前模型仅支持杭州历史样本', PROPERTY_USAGE_NOT_SUPPORTED: '当前模型仅支持普通住宅',
  DISTRICT_NOT_SUPPORTED: '行政区不在模型覆盖范围内', INSUFFICIENT_REFERENCE_SAMPLES: '同小区或同板块参考样本不足',
  INSUFFICIENT_CALIBRATION_SAMPLES: '校准样本不足，无法给出可靠区间', INVALID_MODEL_OUTPUT: '模型结果无效，请人工核验',
  HISTORICAL_DATA: '采用历史样本，不代表当前市场价格', PRICE_BASIS_UNVERIFIED: '样本价格口径尚未核实',
  BUSINESS_AREA_FALLBACK: '小区样本不足，使用同板块参考', MISSING_OPTIONAL_FEATURES: '部分房源属性未知，可能影响参考结果'
}[value] || (String(value).startsWith('OUT_OF_TRAINING_RANGE:') ? '房源属性超出训练范围：' + String(value).split(':')[1] : String(value)))
export function normalizePrediction(raw) {
  // Java 外层字段为 camelCase，模型区间内字段沿用 snake_case。
  const status = ['DEMO_ONLY', 'UNAVAILABLE', 'FAILED'].includes(raw.status) ? raw.status : 'LEGACY'
  const interval = raw.priceInterval ?? raw.price_interval
  const total = positive(raw.estimatedTotalPriceYuan ?? raw.estimated_total_price_yuan)
  const unit = positive(raw.estimatedUnitPriceYuanSqm ?? raw.estimated_unit_price_yuan_sqm)
  const lower = positive(interval?.lower_total_price_yuan)
  const upper = positive(interval?.upper_total_price_yuan)
  if (status === 'DEMO_ONLY' && (!total || !unit || !lower || !upper || lower > total || upper < total)) throw new Error('服务返回的价格或区间无效，请人工核验')
  return { ...raw, status, total: status === 'DEMO_ONLY' ? total : null, unit: status === 'DEMO_ONLY' ? unit : null,
    lower: status === 'DEMO_ONLY' ? lower : null, upper: status === 'DEMO_ONLY' ? upper : null,
    coverage: Number(interval?.nominal_coverage) > 0 && Number(interval?.nominal_coverage) < 1 ? Number(interval.nominal_coverage) : null, calibrationCount: interval?.calibration_sample_count,
    flags: Array.isArray(raw.qualityFlags ?? raw.quality_flags) ? (raw.qualityFlags ?? raw.quality_flags).filter(value => typeof value === 'string') : [],
    explanations: Array.isArray(raw.explanations) ? raw.explanations.filter(value => typeof value === 'string') : [],
    requestId: raw.requestId ?? raw.request_id,
    generatedAt: raw.generatedAt || raw.generated_at || raw.createdAt, modelVersion: raw.modelVersion || raw.model_version,
    dataVersion: raw.dataVersion || raw.data_version, referenceLevel: raw.referenceLevel || raw.reference_level,
    sampleCount: raw.comparableSampleCount ?? raw.comparable_sample_count, snapshot: raw.inputSnapshot || raw.input_snapshot,
    dataObservedAt: raw.dataObservedAt || raw.data_observed_at }
}
export const wan = value => value === null || value === undefined ? '—' : (Number(value) / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const timeText = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—'

export function comparePrices(result, listingPrice) {
  if (result?.status !== 'DEMO_ONLY') return null
  const total = positive(result.total), lower = positive(result.lower), upper = positive(result.upper)
  if (!total || !lower || !upper || lower > total || upper < total) return null
  const listing = positive(listingPrice)
  // 坐标范围包含挂牌价，超出参考区间时不截断或挪动价格标记。
  const min = Math.min(lower, listing ?? lower), max = Math.max(upper, listing ?? upper)
  const padding = Math.max((max - min) * 0.12, max * 0.01, 1)
  const start = Math.max(0, min - padding), end = max + padding
  const position = value => (value - start) / (end - start) * 100
  return {
    listing, difference: listing === null ? null : listing - total,
    percent: listing === null ? null : (listing - total) / total * 100,
    start, end, lower: position(lower), upper: position(upper),
    reference: position(total), listingPosition: listing === null ? null : position(listing)
  }
}
