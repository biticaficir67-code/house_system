import assert from 'node:assert/strict'
import { normalizePrediction, wan, reasonFor, comparePrices } from './pricing.mjs'
const raw={status:'DEMO_ONLY',estimatedTotalPriceYuan:'3000000',estimatedUnitPriceYuanSqm:30000,priceInterval:{lower_total_price_yuan:2500000,upper_total_price_yuan:3500000,nominal_coverage:.9},qualityFlags:['HISTORICAL_DATA'],inputSnapshot:{building_area_sqm:100},stale:true}
const result=normalizePrediction(raw)
assert.equal(result.total,3000000)
assert.equal(result.lower,2500000)
assert.equal(result.stale,true)
assert.equal(result.snapshot.building_area_sqm,100)
assert.equal(wan(result.total),'300.00')
assert.equal(wan(null),'—')
for(const status of ['UNAVAILABLE','FAILED']){const missing=normalizePrediction({...raw,status});assert.equal(missing.total,null);assert.equal(missing.lower,null);assert.equal(missing.unit,null)}
assert.throws(()=>normalizePrediction({...raw,estimatedTotalPriceYuan:4000000}),/区间无效/)
assert.throws(()=>normalizePrediction({...raw,estimatedUnitPriceYuanSqm:null}),/区间无效/)
assert.equal(normalizePrediction({status:'OLD',predPrice:123}).total,null)
assert.match(reasonFor('OUT_OF_TRAINING_RANGE:rooms'),/rooms/)
const malformed = normalizePrediction({status:'FAILED',qualityFlags:'invalid',explanations:[null,'服务暂不可用'],request_id:'request-test'})
assert.deepEqual(malformed.flags,[])
assert.deepEqual(malformed.explanations,['服务暂不可用'])
assert.equal(malformed.requestId,'request-test')
const inside = comparePrices(result, '3300000')
assert.equal(inside.difference,300000)
assert.equal(inside.percent,10)
assert.ok(inside.listingPosition > inside.reference)
for (const listing of [100000, 10000000, 3000000]) {
  const c = comparePrices(result, listing)
  assert.ok(c.listingPosition > 0 && c.listingPosition < 100)
  assert.ok(c.reference >= c.lower && c.reference <= c.upper)
  assert.ok(c.start < Math.min(listing,result.lower))
  assert.ok(c.end > Math.max(listing,result.upper))
}
assert.equal(comparePrices(result,3000000).listingPosition,comparePrices(result,3000000).reference)
for (const listing of [null,undefined,0,-1,'',NaN,Infinity]) {
  assert.equal(comparePrices(result,listing).listingPosition,null)
  assert.equal(comparePrices(result,listing).difference,null)
}
assert.equal(comparePrices({...result,status:'UNAVAILABLE'},3000000),null)
assert.equal(comparePrices({...result,lower:4000000},3000000),null)
const flat = comparePrices({...result,lower:3000000,upper:3000000},3000000)
assert.ok(Number.isFinite(flat.reference))
console.log('PASS: pricing contract, nulls, interval bounds, historical records, stale snapshots')
