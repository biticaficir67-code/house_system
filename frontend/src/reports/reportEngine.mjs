import * as echarts from 'echarts'

const reportEngine = (() => {
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const colors = ['#b57920','#f7b500','#96a3bf','#c6c6c7','#c5966c','#8b98ae'];
  const pretty = n => Number(n || 0).toLocaleString('zh-CN', {maximumFractionDigits:1});
  const sum = (rows,key) => rows.reduce((n,r)=>n+Number(r[key]||0),0);
  const dateString = date => date.toISOString().slice(0,10);
  const anchor = new Date('2026-10-22T12:00:00Z');
  let active = null;
  let currentMode = null;
  const state = { tab:'overview',from:'2026-05-01',to:'2026-10-22',storeId:'',preset:'six',types:{},focus:null,compact:false,explorer:{source:'prices',type:'scatter',metric:1,sort:'desc'} };
  const tabs = { overview:'经营总览',inventory:'房源结构',team:'团队效能',explore:'多维探索' };
  const definitions = {
    trend: {title:'月度带看与成交',sub:'已完成带看 / 已核实成交 · 按自然月',keys:['月份','实际带看（次）','成交（笔）']},
    stock: {title:'在售房源区域分布',sub:'各区域在售房源数量与占比',endpoint:'house-stock',params:'groupType=district',fields:['groupName','houseCount'],keys:['区域','在售房源（套）']},
    stores: {title:'门店业务对比',sub:'在售房源 / 实际带看 / 核实成交',endpoint:'store-performance',fields:['storeName','onSaleCount','showCount','dealCount'],keys:['门店','在售（套）','带看（次）','成交（笔）']},
    prices: {title:'区域挂牌均价',sub:'挂牌单价均值 · 不代表真实成交价',keys:['区域','挂牌均价（元/m²）','样本（套）']},
    rooms: {title:'户型结构',sub:'在售房源 · 按居室数量',endpoint:'house-room-type',fields:['roomType','houseCount'],keys:['户型','房源（套）']},
    age: {title:'房源库龄分布',sub:'在售房源 · 相对所选样本最新挂牌日',endpoint:'house-age-distribution',fields:['ageBucket','houseCount'],keys:['库龄','房源（套）']},
    decoration: {title:'装修状态分布',sub:'在售房源 · 按装修类型',endpoint:'house-decoration',fields:['decorationType','houseCount'],keys:['装修','房源（套）']},
    roomprice: {title:'各户型挂牌总价',sub:'挂牌总价 · 均值、最低值与最高值',endpoint:'house-price-range',fields:['roomType','avgPriceWan','minPriceWan','maxPriceWan'],keys:['户型','均价（万元）','最低（万元）','最高（万元）']},
    ranking: {title:'经纪人成交排行',sub:'按核实成交笔数 · TOP 10',endpoint:'broker-ranking',fields:['brokerName','dealCount','showCount','avgDealPriceWan'],keys:['经纪人','成交（笔）','带看（次）','平均成交价（万元）']},
    performance: {title:'经纪人业务对比',sub:'实际带看与核实成交 · 按经纪人',endpoint:'broker-performance',fields:['brokerName','showCount','dealCount'],keys:['经纪人','带看（次）','成交（笔）']},
    revisit: {title:'客户复看分布',sub:'已完成带看的客户 · 按带看次数分层',endpoint:'customer-revisit',fields:['revisitType','customerCount'],keys:['带看次数','客户（位）']},
    cycle: {title:'成交周期分布',sub:'挂牌至已核实成交的天数',endpoint:'deal-cycle',fields:['cycleBucket','dealCount','avgCycleDays'],keys:['成交周期','成交（笔）','平均周期（天）']}
  };
  Object.assign(definitions,{
    matrix:{...definitions.trend,title:'月度业务热力矩阵',sub:'每项指标最大值 = 100 · 单元格显示原始数量',source:'trend'},
    pricecloud:{...definitions.prices,title:'区域价格与供给象限',sub:'横轴：挂牌单价 · 纵轴：样本房源数',source:'prices'},
    radar:{...definitions.stores,title:'门店业务结构雷达',sub:'各指标按门店最大值 = 100 标准化',source:'stores'},
    brokerbubble:{...definitions.ranking,title:'经纪人产能象限',sub:'横轴：实际带看 · 纵轴：成交 · 气泡：平均成交总价',source:'ranking'},
    explorer:{...definitions.prices,title:'区域挂牌均价',sub:'自定义分析视图',source:'prices'}
  });
  const tabCharts = {overview:['trend','stock','matrix','pricecloud','stores','radar'],inventory:['stock','rooms','age','decoration','roomprice','prices'],team:['ranking','performance','brokerbubble','radar','revisit','cycle']};
  tabCharts.explore=['explorer'];
  const chartKinds={bar:'柱状图',horizontal:'条形图',line:'折线图',area:'面积图',stacked:'堆叠图',donut:'环形图',rose:'玫瑰图',treemap:'矩形树图',scatter:'散点图',heatmap:'热力图',radar:'雷达图',range:'区间图'};
  const defaults={trend:'area',stock:'treemap',stores:'bar',prices:'horizontal',rooms:'rose',age:'bar',decoration:'donut',roomprice:'range',ranking:'horizontal',performance:'stacked',revisit:'donut',cycle:'bar',matrix:'heatmap',pricecloud:'scatter',radar:'radar',brokerbubble:'scatter'};
  const sourceOf=id=>id==='explorer'?state.explorer.source:definitions[id].source||id;
  const chartsFor=()=>state.tab==='explore'?['explorer']:tabCharts[state.tab];
  const typesFor=id=>{const source=sourceOf(id),single=['stock','rooms','age','decoration','revisit','cycle'].includes(source);return [...['bar','horizontal','line','area'],...(single?['donut','rose','treemap']:['stacked','scatter','heatmap',...(definitions[source].keys.length>=4?['radar']:[])]),...(source==='roomprice'?['range']:[])];};
  const typeOf=id=>id==='explorer'?state.explorer.type:state.types[id]||defaults[id]||'bar';
  const statuses = { FINE:'精装',SIMPLE:'简装',ROUGH:'毛坯',OTHER:'其他',UNKNOWN:'未知' };
  function grouped(rows,key,measure = () => 1) {
    const groups = new Map();
    for(const row of rows){const name=key(row);groups.set(name,(groups.get(name)||0)+measure(row));}
    return [...groups].map(([name,value])=>[name,value]);
  }
  // 固定样本仅供预览页使用，不作为业务接口失败时的回退。
  function demo(filters) {
    const districts=['滨江区','上城区','西湖区','拱墅区','萧山区'];
    const brokers=['陈晓','李明','王悦','赵宁','林子涵','周辰'];
    const stores=['城市中心店','滨江旗舰店','西溪生活店'];
    const random=(i,salt=0)=>{let n=Math.imul(i+1+salt,0x45d9f3b);n=Math.imul(n^(n>>>16),0x45d9f3b);return ((n^(n>>>16))>>>0)/4294967296;};
    const houses=Array.from({length:264},(_,i)=>{const st=Math.floor(random(i,19)*3),region=Math.floor(random(i,37)*5);return {id:i,storeId:String(st+1),store:stores[st],broker:brokers[Math.floor(random(i,81)*6)],district:districts[region],rooms:1+Math.floor(random(i,41)*4),decoration:['FINE','SIMPLE','ROUGH'][Math.floor(random(i,62)*3)],price:180+i*1.7+region*18,area:65+i%100,date:dateString(new Date(+anchor-Math.floor(random(i,51)*180)*86400000)),status:i%7===0?'OFF':'ON_SALE'};});
    const showings=Array.from({length:720},(_,i)=>({ ...houses[Math.floor(random(i,71)*264)],date:dateString(new Date(+anchor-Math.floor(175*Math.pow(random(i,83),1.5))*86400000)),customer:Math.floor(random(i,17)*190) }));
    const deals=Array.from({length:104},(_,i)=>({...houses[Math.floor(random(i,29)*264)],date:dateString(new Date(+anchor-Math.floor(165*Math.pow(random(i,67),1.7))*86400000)),cycle:10+Math.floor(random(i,103)*140)}));
    const inRange=r=>(!filters.storeId||r.storeId===filters.storeId)&&r.date>=filters.from&&r.date<=filters.to;
    const ai=Array.from({length:58},(_,i)=>({storeId:String(i%3+1),date:dateString(new Date(+anchor-(i*17%170)*86400000))})).filter(inRange);
    const h=houses.filter(inRange),s=showings.filter(inRange),d=deals.filter(inRange),sale=h.filter(x=>x.status==='ON_SALE');
    const monthly=(rows)=>grouped(rows,r=>r.date.slice(0,7)).sort((a,b)=>a[0].localeCompare(b[0]));
    const latest=h.length?Math.max(...h.map(x=>Date.parse(x.date))):0;
    const bucket=(age,tail)=>age<=30?'0–30天'+tail:age<=60?'31–60天'+tail:age<=90?'61–90天'+tail:'90天以上'+tail;
    const roomName=r=>r.rooms>=4?'4室及以上':r.rooms+'室';
    return {
      overview:{inventory:h.length,showings:s.length,deals:d.length,ai:ai.length},
      trend: mergeMonths(monthly(s),monthly(d)),
      stock:grouped(sale,r=>r.district).sort((a,b)=>b[1]-a[1]),
      stores:stores.filter((_,i)=>!filters.storeId||String(i+1)===filters.storeId).map(name=>[name,sale.filter(x=>x.store===name).length,s.filter(x=>x.store===name).length,d.filter(x=>x.store===name).length]),
      prices:grouped(h,r=>r.district).map(([name,count])=>[name,h.filter(x=>x.district===name).reduce((n,r)=>n+r.price*10000/r.area,0)/count,count]),
      rooms:grouped(sale,roomName),
      age:grouped(sale,r=>bucket((latest-Date.parse(r.date))/86400000,'')),
      decoration:grouped(sale,r=>statuses[r.decoration]||r.decoration),
      roomprice:grouped(sale,roomName).map(([name,count])=>{const p=sale.filter(r=>roomName(r)===name).map(r=>r.price);return [name,p.reduce((a,b)=>a+b,0)/count,Math.min(...p),Math.max(...p)];}),
      ranking:brokers.map(name=>{const rows=d.filter(r=>r.broker===name);return [name,rows.length,s.filter(r=>r.broker===name).length,rows.length?sum(rows,'price')/rows.length:0];}).filter(r=>r[1]||r[2]).sort((a,b)=>b[1]-a[1]),
      performance:brokers.map(name=>[name,s.filter(r=>r.broker===name).length,d.filter(r=>r.broker===name).length]).filter(r=>r[1]||r[2]),
      revisit:grouped(grouped(s,r=>r.customer).map(([,count])=>({count})),r=>r.count===1?'只看1次':r.count===2?'看2次':'看3次及以上'),
      cycle:grouped(d,r=>bucket(r.cycle,'成交')).map(([name,count])=>[name,count,sum(d.filter(r=>bucket(r.cycle,'成交')===name),'cycle')/count])
    };
  }
  function mergeMonths(showings,deals){const s=new Map(showings),d=new Map(deals);return [...new Set([...s.keys(),...d.keys()])].sort().map(month=>[month,s.get(month)||0,d.get(month)||0]);}
  function clean(row){return Object.fromEntries(Object.entries(row).map(([k,v])=>[k.replace(/_([a-z])/g,(_,c)=>c.toUpperCase()),v]));}
  function rowsFrom(result,fields){const rows=Array.isArray(result)?result:result?.records;if(!Array.isArray(rows))throw Error('报表数据格式不符合接口约定');return rows.map(clean).map(r=>fields.map((f,i)=>i===0?String(statuses[r[f]]||r[f]||'未知'):r[f]==null?null:Number(r[f])));}
  function dispose(){if(active){active.abort=true;active.observer?.disconnect();active.charts.forEach(c=>c.dispose());document.body.classList.remove('bi-lock','bi-present');active=null;}}
  function mount(root,options){
    if(currentMode!==options.real){
      state.from=options.real?'2000-01-01':'2026-05-01';
      state.to=options.real?new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()):'2026-10-22';
      state.preset=options.real?'all':'six';state.storeId='';state.focus=null;currentMode=options.real;
    }
    dispose();const ctx={root,...options,charts:[],abort:false,results:{},errors:{},stores:[],loading:false,sequence:0};active=ctx;draw(ctx);refresh(ctx);
  }
  function card(id){const def=definitions[id];return `<section class="report-chart-panel ${id==='trend'||id==='ranking'?'report-wide':''}" data-panel="${id}"><header><div><h2>${def.title}</h2><p>${def.sub}</p></div><div class="bi-chart-actions"><select data-chart-type="${id}" aria-label="${def.title}图表类型">${typesFor(id).map(type=>`<option value="${type}" ${typeOf(id)===type?'selected':''}>${chartKinds[type]}</option>`).join('')}</select><button class="icon ghost" data-expand="${id}" title="放大图表" aria-label="放大${def.title}">⛶</button><button class="icon ghost" data-png="${id}" title="下载图表 PNG" aria-label="下载${def.title}">↓</button></div></header><div class="report-chart" id="chart-${id}" role="img" aria-label="${def.title}"></div><details class="report-detail"><summary>数据明细 <span class="bi-row-count" id="count-${id}"></span></summary><div class="table-wrap" id="detail-${id}"></div></details></section>`;}
  function draw(ctx){
    document.body.classList.remove('bi-lock');
    if(state.tab==='explore'){
      const source=definitions[state.explorer.source],multi=['scatter','radar','heatmap','range'].includes(state.explorer.type);
      definitions.explorer={...source,source:state.explorer.source,title:multi?source.title:source.keys[0]+' · '+source.keys[state.explorer.metric],sub:source.sub+(state.explorer.type==='heatmap'?' · 色阶按指标标准化':state.explorer.type==='radar'?' · 最大值 = 100 标准化':'')};
    }
    ctx.charts.forEach(c=>c.dispose());ctx.charts=[];ctx.observer?.disconnect();
    const r=ctx.root;
    r.innerHTML=`<div class="heading"><div><h1>报表中心</h1></div><div class="actions"><button data-report-export title="导出当前视图数据">↓ 导出报表</button><button class="primary" data-report-refresh>↻ 更新数据</button></div></div><div class="report-tabs" role="tablist" aria-label="报表分类">${Object.entries(tabs).map(([key,name])=>`<button role="tab" aria-selected="${state.tab===key}" class="${state.tab===key?'active':''}" data-report-tab="${key}">${name}</button>`).join('')}<span class="report-source">${ctx.real?'':'示例数据'}</span></div><form class="report-filters"><div class="report-presets" role="group" aria-label="快捷日期">${[['month','本月'],['thirty','近30天'],['six','近6个月']].map(([id,label])=>`<button type="button" class="${state.preset===id?'selected':''}" data-period="${id}">${label}</button>`).join('')}</div><div class="report-date"><label>开始日期<input type="date" name="from" value="${state.from}" required min="2000-01-01" max="2099-12-31"></label><span>—</span><label>结束日期<input type="date" name="to" value="${state.to}" required min="2000-01-01" max="2099-12-31"></label></div><label class="report-store">门店<select name="storeId" aria-label="筛选门店"><option value="">${ctx.real?'可见范围内全部门店':'全部门店'}</option>${(ctx.real?ctx.stores:[{id:'1',name:'城市中心店'},{id:'2',name:'滨江旗舰店'},{id:'3',name:'西溪生活店'}]).map(s=>`<option value="${escape(s.id)}" ${state.storeId===String(s.id)?'selected':''}>${escape(s.name)}</option>`).join('')}</select></label><button type="submit">应用筛选</button></form><div class="report-range"><span>${state.from} — ${state.to} · ${tabs[state.tab]}</span><span id="report-update" role="status">正在加载</span></div><div class="stats report-kpis" id="report-kpis"></div><div class="report-grid">${tabCharts[state.tab].map(card).join('')}</div><details class="report-basis"><summary>统计口径</summary><p>房源按挂牌日期统计，结构图仅统计在售房源。带看不含历史演示记录，成交仅统计已核实的有效记录。各项指标独立统计。</p></details>`;
    r.querySelectorAll('[data-report-tab]').forEach(b=>b.onclick=()=>{state.tab=b.dataset.reportTab;draw(ctx);refresh(ctx);});
    r.querySelectorAll('[data-period]').forEach(b=>b.onclick=()=>{state.preset=b.dataset.period;const today=ctx.real?new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()):'2026-10-22';state.to=today;const date=new Date(today+'T12:00:00Z');if(state.preset==='month')state.from=today.slice(0,7)+'-01';if(state.preset==='thirty')state.from=dateString(new Date(+date-29*86400000));if(state.preset==='six'){date.setUTCMonth(date.getUTCMonth()-5,1);state.from=dateString(date);}draw(ctx);refresh(ctx);});
    r.querySelector('form').onsubmit=e=>{e.preventDefault();const f=new FormData(e.target);if(f.get('from')>f.get('to')){ctx.toast('开始日期不能晚于结束日期');return;}state.from=String(f.get('from'));state.to=String(f.get('to'));state.storeId=String(f.get('storeId'));state.preset='custom';draw(ctx);refresh(ctx);};
    r.querySelector('[data-report-refresh]').onclick=()=>refresh(ctx);
    r.querySelector('[data-report-export]').onclick=()=>exportReport(ctx);
    r.querySelectorAll('[data-png]').forEach(b=>b.onclick=()=>{const chart=ctx.charts.find(c=>c.getDom().id==='chart-'+b.dataset.png);if(!chart){ctx.toast('当前图表没有可下载的数据');return;}download(chart.getDataURL({type:'png',pixelRatio:2,backgroundColor:'#ffffff'}),definitions[b.dataset.png].title+'.png');});
    decorate(ctx);
  }
  async function refresh(ctx){
    const sequence=++ctx.sequence;ctx.loading=true;ctx.results={};ctx.errors={};
    ctx.root.querySelector('#report-update').textContent='正在更新…';ctx.root.querySelector('[data-report-export]').disabled=true;
    const ids=tabCharts[state.tab];ctx.charts.forEach(c=>c.dispose());ctx.charts=[];
    ids.forEach(id=>{ctx.root.querySelector('#chart-'+id).innerHTML='<div class="report-placeholder">正在加载报表…</div>';ctx.root.querySelector('#detail-'+id).innerHTML='';});
    ctx.root.querySelector('#report-kpis').innerHTML=Array.from({length:4},()=>'<div class="stat"><div class="report-skeleton"></div><strong>—</strong><span class="muted">正在统计</span></div>').join('');
    const params=new URLSearchParams({from:state.from,to:state.to});if(state.storeId)params.set('storeId',state.storeId);
    // 切换筛选或离开页面后，忽略先前请求的返回值。
    const current=()=>!ctx.abort&&sequence===ctx.sequence;
    if(!ctx.real){const result=demo(state);if(!current())return;ctx.results=result;}
    else {
      const sourceIds=[...new Set(ids.map(sourceOf))];
      const jobs=[['overview',()=>ctx.api('/reports/overview?'+params)],...sourceIds.map(id=>[id,async()=>{
        if(id==='trend'){const [s,d]=await Promise.all([ctx.api('/report/custom/show-trend?'+params),ctx.api('/report/custom/deal-trend?'+params)]);return mergeMonths(rowsFrom(s,['showMonth','showCount']),rowsFrom(d,['dealMonth','dealCount']));}
        if(id==='prices'){const result=await ctx.api('/reports/prices?'+params+'&groupBy=DISTRICT');return rowsFrom(result,['groupKey','averageListingUnitPriceYuanSqm','sampleCount']);}
        const def=definitions[id];return rowsFrom(await ctx.api('/report/custom/'+def.endpoint+'?'+params+(def.params?'&'+def.params:'')),def.fields);
      }])];
      await Promise.all(jobs.map(async([key,task])=>{try{const value=await task();if(current())ctx.results[key]=value;}catch(e){if(current())ctx.errors[key]=e.message;}}));
      if(!ctx.stores.length){try{const result=await ctx.api('/stores');if(current()){ctx.stores=Array.isArray(result)?result:result.records||[];const select=ctx.root.querySelector('[name=storeId]');select.innerHTML='<option value="">可见范围内全部门店</option>'+ctx.stores.map(s=>`<option value="${escape(s.id)}" ${state.storeId===String(s.id)?'selected':''}>${escape(s.name)}</option>`).join('');}}catch{/* 门店列表失败不阻断报表。 */}}
    }
    if(!current())return;ctx.loading=false;
    ids.forEach(id=>{const source=sourceOf(id);if(source!==id){ctx.results[id]=ctx.results[source];if(ctx.errors[source])ctx.errors[id]=ctx.errors[source];}});
    const o=ctx.results.overview;
    ctx.root.querySelector('#report-kpis').innerHTML=[['房源档案',o?.inventory,'套','按挂牌日期纳入统计'],['实际带看',o?.showings,'次','仅统计已完成的新业务'],['核实成交',o?.deals,'笔','仅统计有效且已核实成交'],['AI 服务调用',o?.ai,'次','包含失败与不可估价记录']].map(([name,value,unit,note])=>`<div class="stat"><div class="stat-top">${name}<span class="report-kpi-mark"></span></div><strong>${value==null?'—':pretty(value)}<small>${unit}</small></strong><div class="stat-bottom">${o?note:escape(ctx.errors.overview||'暂无统计')}</div></div>`).join('');
    for(const id of ids)renderChart(ctx,id);
    const failures=['overview',...ids].filter(id=>ctx.errors[id]).length;
    ctx.root.querySelector('#report-update').textContent=failures?`${failures} 项加载失败，可重试`:(ctx.real?'已更新':'示例数据');
    ctx.root.querySelector('[data-report-export]').disabled=false;
    ctx.observer?.disconnect();ctx.observer=new ResizeObserver(()=>ctx.charts.forEach(c=>c.resize()));ctx.charts.forEach(c=>ctx.observer.observe(c.getDom()));
    updateFocus(ctx);
  }
  function renderChart(ctx,id){
    const el=ctx.root.querySelector('#chart-'+id),def=definitions[id];
    const previous=ctx.charts.find(c=>c.getDom()===el);if(previous){previous.dispose();ctx.charts=ctx.charts.filter(c=>c!==previous);}
    let rows=(ctx.results[id]||[]).map(r=>[...r]);
    if(state.focus?.dimension===def.keys[0])rows=rows.filter(r=>r[0]===state.focus.value);
    if(id==='explorer'&&state.explorer.sort!=='source'){const metric=state.explorer.metric;rows.sort((a,b)=>(Number(a[metric]||0)-Number(b[metric]||0))*(state.explorer.sort==='asc'?1:-1));}
    ctx.root.querySelector('#count-'+id).textContent=`${rows.length} 行`;
    if(ctx.errors[id]){el.innerHTML=`<div class="report-placeholder report-failure">${escape(ctx.errors[id])}<button data-chart-retry>重新加载</button></div>`;el.querySelector('button').onclick=()=>refresh(ctx);return;}
    if(!rows?.length||rows.every(r=>r.slice(1).every(v=>!v))){el.innerHTML='<div class="report-placeholder">当前筛选范围内暂无统计记录</div>';return;}
    ctx.root.querySelector('#detail-'+id).innerHTML=`<table><thead><tr>${def.keys.map(k=>`<th>${k}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${row.map((v,i)=>`<td>${i===0?escape(v):v==null?'—':pretty(v)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
    el.innerHTML='';const chart=echarts.init(el,null,{renderer:'canvas'});ctx.charts.push(chart);
    const kind=typeOf(id);
    const option=makeOption(rows,def,kind,{source:sourceOf(id),metric:id==='explorer'?Number(state.explorer.metric):1,single:id==='explorer'&&!['scatter','heatmap','radar','range'].includes(kind)});
    chart.setOption(option);
    chart.on('click',params=>{let value=params.name;if(kind==='heatmap')value=rows[params.value?.[0]]?.[0];if(!value||!rows.some(r=>r[0]===value))return;state.focus=state.focus?.dimension===def.keys[0]&&state.focus.value===value?null:{dimension:def.keys[0],value};repaint(ctx);});
  }
  function makeOption(rows,def,kind,{source='prices',metric=1,single=false}={}){
    const names=rows.map(r=>r[0]),unit=def.keys[metric]?.match(/[（(](.*?)[）)]/)?.[1]||'数量';
    const indexes=single?[metric]:source==='ranking'?[1]:source==='prices'?[1]:source==='cycle'?[1]:def.keys.slice(1).map((_,i)=>i+1);
    const splitLine={lineStyle:{color:'#e4e6ea',type:'dashed'}};
    const option={color:colors,animationDuration:350,textStyle:{fontFamily:'Microsoft YaHei, sans-serif',fontSize:11,color:'#96a3bf'},tooltip:{trigger:'axis',confine:true,valueFormatter:v=>pretty(v)},grid:{top:54,left:16,right:30,bottom:24,containLabel:true},legend:{top:5,itemWidth:10,itemHeight:7,textStyle:{fontSize:10,color:'#96a3bf'},type:'scroll'},aria:{enabled:true},xAxis:{type:'category',name:def.keys[0],nameLocation:'middle',nameGap:27,data:names,axisLine:{lineStyle:{color:'#e4e6ea'}},axisTick:{show:false},axisLabel:{fontSize:10,interval:0,overflow:'truncate',width:75}},yAxis:{type:'value',name:unit,nameTextStyle:{fontSize:10},splitLine,axisLabel:{fontSize:10}},series:[]};
    if(['套','次','笔','位'].includes(unit))option.yAxis.minInterval=1;
    const series=(index,type='bar')=>({name:def.keys[index],type,data:rows.map(r=>r[index]),barMaxWidth:27,itemStyle:{borderRadius:[3,3,0,0]},emphasis:{focus:'series'},...(type==='line'?{symbolSize:5,lineStyle:{width:2.5}}:{})});
    if(['bar','line','area','stacked','horizontal'].includes(kind)){
      option.series=indexes.map(i=>series(i,['line','area'].includes(kind)?'line':'bar'));
      if(kind==='area')option.series.forEach(s=>s.areaStyle={opacity:.12});
      if(kind==='stacked')option.series.forEach(s=>{s.stack='total';s.itemStyle.borderRadius=0;});
      if(kind==='horizontal'){option.xAxis={type:'value',name:unit,splitLine};option.yAxis={type:'category',inverse:true,data:names,axisLine:{show:false},axisTick:{show:false},axisLabel:{fontSize:10}};option.series.forEach(s=>{s.label={show:true,position:'right',fontSize:10,formatter:p=>pretty(p.value)};s.itemStyle.borderRadius=[0,3,3,0];});option.grid.right=55;}
      if(rows.length>12){option.dataZoom=[{type:'inside'},{type:'slider',height:12,bottom:2}];option.grid.bottom=45;}
    }else if(['donut','rose','treemap'].includes(kind)){
      delete option.xAxis;delete option.yAxis;option.tooltip={trigger:'item',confine:true,valueFormatter:v=>pretty(v)};
      const data=rows.map(r=>({name:r[0],value:Number(r[metric]||0)}));
      if(kind==='treemap'){option.legend.show=false;option.series=[{type:'treemap',left:10,right:10,top:12,bottom:12,roam:false,nodeClick:false,breadcrumb:{show:false},label:{show:true,fontSize:12,formatter:p=>`${p.name}\n${pretty(p.value)}`},itemStyle:{borderWidth:3,borderColor:'#fff',gapWidth:3},data}];}
      else{option.legend={bottom:2,left:'center',type:'scroll',textStyle:{fontSize:10,color:'#96a3bf'},itemWidth:8,itemHeight:8};option.series=[{type:'pie',radius:kind==='rose'?['15%','69%']:['45%','66%'],roseType:kind==='rose'?'area':false,center:['50%','43%'],label:{show:kind==='rose',fontSize:10,formatter:'{b}'},labelLine:{length:7,length2:4},itemStyle:{borderWidth:3,borderColor:'#fff',borderRadius:4},data}];if(kind==='donut')option.title={text:pretty(data.reduce((n,r)=>n+r.value,0)),subtext:unit,left:'center',top:'31%',textStyle:{fontSize:28,color:'#616773',fontWeight:600},subtextStyle:{fontSize:11,color:'#96a3bf'}};}
    }else if(kind==='heatmap'){
      const measures=def.keys.slice(1),maxima=measures.map((_,i)=>Math.max(1,...rows.map(r=>Number(r[i+1]||0)))),data=rows.flatMap((r,x)=>measures.map((_,y)=>[x,y,r[y+1]==null?null:Number(r[y+1])/maxima[y]*100,r[y+1]]));
      option.grid={top:18,left:18,right:24,bottom:65,containLabel:true};option.legend.show=false;option.xAxis.name='';option.xAxis.splitArea={show:true};option.yAxis={type:'category',data:measures,axisTick:{show:false},axisLine:{show:false},axisLabel:{fontSize:10,width:105,overflow:'truncate'}};
      option.visualMap={min:0,max:100,dimension:2,orient:'horizontal',left:'center',bottom:1,itemHeight:100,itemWidth:8,inRange:{color:['#f4f5f7','#96a3bf','#b57920']},text:['指标内最高','0'],textStyle:{fontSize:9,color:'#96a3bf'},calculable:true};
      option.tooltip={confine:true,formatter:p=>`${escape(names[p.value[0]])}<br>${escape(measures[p.value[1]])}：${p.value[3]==null?'—':pretty(p.value[3])}<br>指标内最大值 = 100，当前 ${pretty(p.value[2])}`};option.series=[{type:'heatmap',data,label:{show:true,color:'#616773',fontSize:11,formatter:p=>p.value[3]==null?'—':pretty(p.value[3])},itemStyle:{borderColor:'#fff',borderWidth:4,borderRadius:5}}];
    }else if(kind==='scatter'){
      const broker=source==='ranking',x=broker?2:1,y=broker?1:2,size=broker?3:2,maxSize=Math.max(1,...rows.map(r=>Number(r[size]||0)));
      option.grid={top:30,left:20,right:28,bottom:47,containLabel:true};option.legend.show=false;option.xAxis={type:'value',name:def.keys[x],nameLocation:'middle',nameGap:32,splitLine,scale:true,axisLabel:{fontSize:10}};option.yAxis={type:'value',name:def.keys[y],nameTextStyle:{fontSize:10},splitLine,scale:true};
      option.tooltip={trigger:'item',confine:true,formatter:p=>`${escape(p.name)}<br>${escape(def.keys[x])}：${pretty(p.value[0])}<br>${escape(def.keys[y])}：${pretty(p.value[1])}${broker?`<br>平均成交总价（万元）：${pretty(p.value[2])}`:''}`};
      option.series=[{type:'scatter',data:rows.map((r,i)=>({name:r[0],value:[r[x],r[y],r[size]],itemStyle:{color:colors[i%colors.length]}})),symbolSize:v=>14+Math.sqrt(Math.max(0,Number(v[2]||0))/maxSize)*34,label:{show:true,position:'top',fontSize:10,formatter:'{b}'},itemStyle:{opacity:.82,borderWidth:2,borderColor:'#fff'},markLine:{silent:true,symbol:'none',lineStyle:{color:'#96a3bf',type:'dashed'},label:{fontSize:9},data:[{type:'average',name:'均值'}]}}];
    }else if(kind==='radar'){
      delete option.xAxis;delete option.yAxis;const cols=def.keys.slice(1),max=cols.map((_,i)=>Math.max(1,...rows.map(r=>Number(r[i+1]||0))));option.legend={bottom:0,type:'scroll',textStyle:{fontSize:10},itemWidth:10,itemHeight:7};option.tooltip={trigger:'item',confine:true,formatter:p=>`${escape(p.name)}<br>${cols.map((c,i)=>`${escape(c)}：${pretty(rows.find(r=>r[0]===p.name)?.[i+1])}`).join('<br>')}<br>图形按各指标最大值标准化`};option.radar={center:['50%','45%'],radius:'59%',indicator:cols.map(name=>({name,max:100})),axisName:{fontSize:9,color:'#96a3bf'},splitNumber:4,splitArea:{areaStyle:{color:['#fff','#f4f5f7']}},splitLine:{lineStyle:{color:'#e4e6ea'}},axisLine:{lineStyle:{color:'#e4e6ea'}}};option.series=[{type:'radar',data:rows.map(r=>({name:r[0],value:cols.map((_,i)=>Number(r[i+1]||0)/max[i]*100)})),symbolSize:4,lineStyle:{width:1.7},areaStyle:{opacity:.06}}];
    }else if(kind==='range'){
      option.legend={top:3,data:['挂牌区间','挂牌均价'],textStyle:{fontSize:10}};option.xAxis={type:'value',name:'万元',splitLine};option.yAxis={type:'category',data:names,axisTick:{show:false},axisLine:{show:false}};
      option.tooltip={trigger:'axis',confine:true,formatter:ps=>{const r=rows[ps[0].dataIndex];return `${escape(r[0])}<br>最低：${pretty(r[2])} 万元<br>均价：${pretty(r[1])} 万元<br>最高：${pretty(r[3])} 万元`;}};
      option.series=[{type:'bar',stack:'range',silent:true,itemStyle:{color:'transparent'},data:rows.map(r=>r[2])},{name:'挂牌区间',type:'bar',stack:'range',barWidth:15,itemStyle:{color:'#96a3bf',borderRadius:3},data:rows.map(r=>r[3]-r[2])},{name:'挂牌均价',type:'scatter',symbol:'diamond',symbolSize:12,itemStyle:{color:'#cb9253'},data:rows.map(r=>[r[1],r[0]])}];
    }
    option.aria={enabled:true,label:{description:`${def.title||'统计图'}。${def.keys.join('、')}。${rows.slice(0,30).map(r=>r.map((v,i)=>i===0?v:v==null?'暂无':pretty(v)).join('，')).join('；')}`}};
    return option;
  }
  function repaint(ctx){
    if(ctx.loading)return;
    for(const id of chartsFor())renderChart(ctx,id);
    ctx.observer?.disconnect();ctx.charts.forEach(c=>ctx.observer?.observe(c.getDom()));updateFocus(ctx);
  }
  function updateFocus(ctx){
    const strip=ctx.root.querySelector('.bi-focus');if(!strip)return;
    strip.innerHTML=state.focus?`<span class="bi-focus-label">图表聚焦</span><span class="bi-focus-chip">${escape(state.focus.dimension)}：${escape(state.focus.value)} <button data-clear-focus title="清除聚焦" aria-label="清除聚焦">×</button></span><span class="muted">总指标不随聚焦变化</span>`:'<span class="bi-live-dot"></span><span>'+(ctx.real?'当前范围':'示例数据')+'</span>';
    strip.querySelector('[data-clear-focus]')?.addEventListener('click',()=>{state.focus=null;repaint(ctx);});
  }
  function storedViews(ctx){try{const list=JSON.parse(localStorage.getItem('qiju-bi-views-'+(ctx.real?'live':'demo'))||'[]');return Array.isArray(list)?list.filter(v=>v&&typeof v.name==='string'&&v.state&&tabs[v.state.tab]):[];}catch{return [];}}
  function saveView(ctx){
    const modal=document.createElement('dialog');modal.className='bi-save-modal';
    modal.innerHTML='<form><div class="modal-head"><h2>保存分析视图</h2><button type="button" class="icon ghost" aria-label="关闭">×</button></div><div class="modal-body"><label>视图名称<input name="name" maxlength="40" required placeholder="例如：滨江门店 · 月度复盘" autocomplete="off"></label><div class="modal-actions"><button type="submit" class="primary">保存视图</button></div></div></form>';
    ctx.root.append(modal);modal.querySelector('button[type=button]').onclick=()=>modal.close();modal.onclose=()=>modal.remove();
    modal.querySelector('form').onsubmit=e=>{e.preventDefault();const name=String(new FormData(e.target).get('name')).trim();if(!name)return;const views=storedViews(ctx);const saved={name,state:structuredClone(state),id:String(Date.now())};const next=[saved,...views.filter(v=>v.name!==name)].slice(0,12);try{localStorage.setItem('qiju-bi-views-'+(ctx.real?'live':'demo'),JSON.stringify(next));modal.close();draw(ctx);refresh(ctx);ctx.toast('分析视图已保存到当前浏览器');}catch{ctx.toast('浏览器存储不可用，未能保存视图');}};
    modal.showModal();modal.querySelector('input').focus();
  }
  function decorate(ctx){
    const r=ctx.root,views=storedViews(ctx);
    const allDates=document.createElement('button');allDates.type='button';allDates.textContent='全部时间';allDates.className=state.preset==='all'?'selected':'';
    allDates.onclick=()=>{state.from='2000-01-01';state.to=ctx.real?new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()):'2026-10-22';state.preset='all';draw(ctx);refresh(ctx);};
    r.querySelector('.report-presets').append(allDates);
    r.onkeydown=e=>{if(e.key==='Escape'){const expanded=r.querySelector('.bi-expanded');if(expanded){expanded.querySelector('[data-expand]').click();e.preventDefault();}}};
    r.classList.toggle('bi-compact',state.compact);
    const toolbar=document.createElement('div');toolbar.className='bi-toolbar';
    toolbar.innerHTML=`<div class="bi-workbook"><span class="bi-workbook-icon">▥</span><span>经营分析</span></div><div class="bi-view-tools"><select aria-label="已保存的分析视图" data-saved-view><option value="">我的分析视图</option>${views.map(v=>`<option value="${escape(v.id)}">${escape(v.name)}</option>`).join('')}</select><button data-save-view title="保存当前分析配置">＋ 保存视图</button><label class="bi-density"><input type="checkbox" ${state.compact?'checked':''} data-density>紧凑</label><button class="icon ghost" data-presentation title="切换专注看板" aria-label="切换专注看板">⛶</button></div>`;
    r.querySelector('.report-tabs').before(toolbar);
    const focus=document.createElement('div');focus.className='bi-focus';r.querySelector('.report-grid').before(focus);updateFocus(ctx);
    toolbar.querySelector('[data-save-view]').onclick=()=>saveView(ctx);
    toolbar.querySelector('[data-saved-view]').onchange=e=>{const view=views.find(v=>v.id===e.target.value);if(!view)return;const saved=view.state;state.tab=saved.tab;state.from=/^\d{4}-\d{2}-\d{2}$/.test(saved.from)?saved.from:state.from;state.to=/^\d{4}-\d{2}-\d{2}$/.test(saved.to)?saved.to:state.to;state.storeId=String(saved.storeId||'');state.preset=saved.preset;state.focus=null;state.compact=!!saved.compact;state.types=Object.fromEntries(Object.entries(saved.types||{}).filter(([id,type])=>definitions[id]&&chartKinds[type]));if(saved.explorer&&definitions[saved.explorer.source]&&!definitions[saved.explorer.source].source){state.explorer={...saved.explorer,metric:Number(saved.explorer.metric)||1};if(!chartKinds[state.explorer.type])state.explorer.type='bar';}draw(ctx);refresh(ctx);};
    toolbar.querySelector('[data-density]').onchange=e=>{state.compact=e.target.checked;r.classList.toggle('bi-compact',state.compact);ctx.charts.forEach(c=>c.resize());};
    toolbar.querySelector('[data-presentation]').onclick=()=>{document.body.classList.toggle('bi-present');toolbar.querySelector('[data-presentation]').setAttribute('aria-pressed',String(document.body.classList.contains('bi-present')));requestAnimationFrame(()=>ctx.charts.forEach(c=>c.resize()));};
    r.querySelectorAll('[data-chart-type]').forEach(s=>s.onchange=()=>{if(s.dataset.chartType==='explorer'){state.explorer.type=s.value;draw(ctx);refresh(ctx);return;}state.types[s.dataset.chartType]=s.value;renderChart(ctx,s.dataset.chartType);const chart=ctx.charts.find(c=>c.getDom().id==='chart-'+s.dataset.chartType);if(chart)ctx.observer?.observe(chart.getDom());});
    r.querySelectorAll('[data-expand]').forEach(b=>b.onclick=()=>{const panel=b.closest('.report-chart-panel'),expanded=panel.classList.toggle('bi-expanded');document.body.classList.toggle('bi-lock',expanded);b.textContent=expanded?'×':'⛶';b.title=expanded?'还原图表':'放大图表';b.setAttribute('aria-label',expanded?'还原图表':'放大'+definitions[b.dataset.expand].title);requestAnimationFrame(()=>ctx.charts.forEach(c=>c.resize()));});
    if(state.tab==='explore'){
      const source=state.explorer.source,def=definitions[source];
      const workspace=document.createElement('div');workspace.className='bi-explore';
      const shelf=document.createElement('aside');shelf.className='bi-shelf';
      const sources=['prices','stock','trend','stores','rooms','age','decoration','roomprice','ranking','performance','revisit','cycle'];
      shelf.innerHTML=`<div class="bi-shelf-heading"><strong>数据</strong><span class="badge">${ctx.real?'业务接口':'示例数据集'}</span></div><label>数据集<select data-dataset aria-label="探索数据集">${sources.map(id=>`<option value="${id}" ${source===id?'selected':''}>${definitions[id].title}</option>`).join('')}</select></label><div class="bi-field-heading">维度</div><div class="bi-dimension-pill"><span>Abc</span>${escape(def.keys[0])}</div><div class="bi-field-heading">度量</div>${def.keys.slice(1).map((key,i)=>`<label class="bi-measure"><input type="radio" name="measure" value="${i+1}" ${Number(state.explorer.metric)===i+1?'checked':''}><span>#</span>${escape(key)}</label>`).join('')}<div class="bi-field-heading">排序</div><select aria-label="探索数据排序" data-sort><option value="desc" ${state.explorer.sort==='desc'?'selected':''}>度量 · 降序</option><option value="asc" ${state.explorer.sort==='asc'?'selected':''}>度量 · 升序</option><option value="source" ${state.explorer.sort==='source'?'selected':''}>数据源顺序</option></select><div class="bi-source-caption">${escape(def.sub)}</div>`;
      const grid=r.querySelector('.report-grid');grid.before(workspace);workspace.append(shelf,grid);grid.classList.add('bi-explore-grid');
      const gallery=document.createElement('div');gallery.className='bi-showme';gallery.innerHTML=`<div class="bi-field-heading">图表类型</div><div class="bi-chart-gallery">${typesFor('explorer').map((type,i)=>`<button class="${typeOf('explorer')===type?'active':''}" data-showme="${type}" title="${chartKinds[type]}"><span aria-hidden="true">${({bar:'▥',horizontal:'☰',line:'⌁',area:'◩',stacked:'▤',donut:'◉',rose:'✺',treemap:'▦',scatter:'⁙',heatmap:'▦',radar:'⬡',range:'↔'})[type]}</span><small>${chartKinds[type]}</small></button>`).join('')}</div><div class="bi-source-caption">${['scatter','radar','heatmap','range'].includes(state.explorer.type)?'多度量视图 · 使用各指标原始值':'单度量视图 · '+escape(def.keys[state.explorer.metric])}</div>`;
      shelf.append(gallery);
      shelf.querySelector('[data-dataset]').onchange=e=>{state.explorer.source=e.target.value;state.explorer.metric=1;state.focus=null;if(!typesFor('explorer').includes(state.explorer.type))state.explorer.type=typesFor('explorer')[0];draw(ctx);refresh(ctx);};
      shelf.querySelectorAll('[name=measure]').forEach(input=>input.onchange=e=>{state.explorer.metric=Number(e.target.value);draw(ctx);refresh(ctx);});
      shelf.querySelector('[data-sort]').onchange=e=>{state.explorer.sort=e.target.value;repaint(ctx);};
      shelf.querySelectorAll('[data-showme]').forEach(button=>button.onclick=()=>{state.explorer.type=button.dataset.showme;draw(ctx);refresh(ctx);});
    }
  }
  function download(href,name){const a=document.createElement('a');a.href=href;a.download=name;a.click();}
  function exportReport(ctx){
    if(ctx.loading){ctx.toast('请等待报表加载完成');return;}
    const lines=[['报表中心',tabs[state.tab]],['数据来源',ctx.real?'业务报表接口':'独立报表示例，非实际经营数据'],['开始日期',state.from],['结束日期',state.to],['门店',state.storeId||'全部可见门店'],['图表聚焦',state.focus?`${state.focus.dimension}：${state.focus.value}`:'无'],['总指标口径','日期和门店全局口径，不受图表聚焦影响']];
    if(ctx.results.overview)lines.push([],['指标','数量'],...Object.entries({房源档案:ctx.results.overview.inventory,实际带看:ctx.results.overview.showings,核实成交:ctx.results.overview.deals,AI调用:ctx.results.overview.ai}));
    for(const id of chartsFor()){const def=definitions[id];let rows=ctx.results[id]||[];if(state.focus?.dimension===def.keys[0])rows=rows.filter(r=>r[0]===state.focus.value);lines.push([],[def.title],[def.sub],['图形',chartKinds[typeOf(id)]]);if(ctx.errors[id])lines.push(['加载失败',ctx.errors[id]]);else lines.push(def.keys,...rows);}
    const csv='\uFEFF'+lines.map(row=>row.map(v=>'"'+String(v??'').replace(/^\s*[=+@-]/,"'$&").replace(/"/g,'""')+'"').join(',')).join('\r\n');
    const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));download(url,`报表-${tabs[state.tab]}-${state.from}-${state.to}.csv`);setTimeout(()=>URL.revokeObjectURL(url),1000);ctx.toast('报表已导出');
  }
  return {mount,dispose,demo,mergeMonths,rowsFrom,makeOption};
})();

export default reportEngine
