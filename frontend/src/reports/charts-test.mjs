import assert from 'node:assert/strict';
import * as echarts from 'echarts';
import reportEngine from './reportEngine.mjs';

const {demo,makeOption}=reportEngine;
const sample=demo({from:'2026-05-01',to:'2026-10-22',storeId:''});
const def={keys:['门店','在售（套）','带看（次）','成交（笔）']};
for(const kind of ['bar','horizontal','line','area','stacked','donut','rose','treemap','scatter','heatmap','radar']){
  const chart=echarts.init(null,null,{renderer:'svg',ssr:true,width:880,height:400});
  const option=makeOption(sample.stores,def,kind,{source:'stores'});
  chart.setOption(option);
  const svg=chart.renderToSVGString();
  assert.match(svg,/<path/,'Blank chart: '+kind);
  assert.ok(svg.length>1500,'Insufficient chart content: '+kind);
  if(kind==='heatmap'){
    assert.ok(option.series[0].data.every(p=>p[2]>=0&&p[2]<=100));
    assert.equal(option.series[0].data[0][3],sample.stores[0][1]);
  }
  if(kind==='radar')assert.ok(option.series[0].data.every(r=>r.value.every(v=>v>=0&&v<=100)));
  chart.dispose();
}
const range=echarts.init(null,null,{renderer:'svg',ssr:true,width:800,height:400});
range.setOption(makeOption(sample.roomprice,{keys:['户型','均价（万元）','最低（万元）','最高（万元）']},'range',{source:'roomprice'}));
assert.match(range.renderToSVGString(),/挂牌均价/);
range.dispose();
const selected=makeOption(sample.prices,{keys:['区域','均价（元/m²）','样本（套）']},'bar',{source:'prices',metric:2,single:true});
assert.deepEqual(selected.series[0].data,sample.prices.map(r=>r[2]));
console.log('PASS: 12 chart renderers, heatmap/radar normalization, selected measure');
