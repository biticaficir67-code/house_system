<template>
  <div class="page-wrap pricing-page">
    <NavMain></NavMain>
    <div class="page-head">
      <div><h2>价格建议</h2><p class="sub">{{ house ? house.communityName : '房源 ' + houseId }}</p></div>
      <el-button :icon="ArrowLeft" @click="back">返回房源</el-button>
    </div>
    <main class="pricing-content" v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-alert title="基于历史挂牌样本，不代表当前市场价。" type="warning" show-icon :closable="false" />
      <section v-if="house" class="pricing-house">
        <div><span class="pricing-label">房源信息</span><h3>{{ house.communityName }}</h3><p>{{ house.city }} · {{ house.district }} · {{ house.buildingAreaSqm }} m² · {{ house.rooms }}室{{ house.halls }}厅</p></div>
        <div><span class="pricing-label">当前挂牌总价</span><strong>{{ wan(house.listingPriceYuan) }} <small>万元</small></strong></div>
        <el-button type="primary" :icon="MagicStick" :loading="predicting" :disabled="loading || predicting" @click="predict">{{ predicting ? '正在分析房源…' : '生成价格参考' }}</el-button>
      </section>
      <div class="pricing-grid">
        <section class="pricing-result">
          <header><h3>参考结果</h3><el-tag v-if="result" :type="result.status === 'DEMO_ONLY' ? 'warning' : 'info'">{{ priceStatus(result.status) }}</el-tag></header>
          <template v-if="result">
            <el-alert v-if="result.stale" title="房源特征已变化，此记录基于旧资料，请重新生成参考。" type="warning" :closable="false" show-icon />
            <template v-if="result.status === 'DEMO_ONLY'">
              <div v-if="comparison" class="price-comparison">
                <div><span>当前挂牌价</span><strong>{{ wan(comparison.listing) }} <small>万元</small></strong></div>
                <div class="reference"><span>历史参考价</span><strong>{{ wan(result.total) }} <small>万元</small></strong></div>
                <div class="difference"><span>挂牌价 − 参考价</span><strong>{{ differenceText }} <small>万元</small></strong><p>{{ differencePercent }}</p></div>
              </div>
              <p class="pricing-caption">参考单价 {{ result.unit.toLocaleString('zh-CN', {maximumFractionDigits: 2}) }} 元/m²</p>
              <div v-if="comparison" class="comparison-interval">
                <div class="comparison-heading"><span><i></i>历史参考区间</span><b>{{ wan(result.lower) }} — {{ wan(result.upper) }} 万元</b></div>
                <div class="comparison-plot" role="img" :aria-label="comparisonDescription">
                  <div class="comparison-axis"></div>
                  <div class="comparison-band" :style="{left: comparison.lower + '%', width: (comparison.upper - comparison.lower) + '%'}"></div>
                  <template v-if="comparison.listing !== null">
                    <div class="comparison-marker listing" :style="{left: comparison.listingPosition + '%'}"><i></i></div>
                    <span class="comparison-label listing" :style="markerLabel(comparison.listingPosition)">挂牌价</span>
                  </template>
                  <div class="comparison-marker reference" :style="{left: comparison.reference + '%'}"><i></i></div>
                  <span class="comparison-label reference" :style="markerLabel(comparison.reference)">历史参考价</span>
                </div>
                <div class="comparison-scale"><span>{{ wan(comparison.start) }} 万</span><span>{{ wan(comparison.end) }} 万</span></div>
              </div>
              <p class="pricing-caption">{{ result.coverage ? '历史区间覆盖目标 ' + (result.coverage * 100) + '%' : '未提供区间覆盖目标' }}，非成交概率。</p>
            </template>
            <div v-else class="pricing-empty"><strong>{{ result.status === 'FAILED' ? '估价服务暂不可用' : result.status === 'LEGACY' ? '旧版估价记录' : '当前房源无法估价' }}</strong><p>{{ result.message || '请人工核价。' }}</p></div>
            <div class="pricing-facts"><div><span>参考层级</span><b>{{ levelText }}</b></div><div><span>训练参考样本</span><b>{{ result.sampleCount ?? '—' }} 条</b></div><div><span>生成时间</span><b>{{ timeText(result.generatedAt) }}</b></div><div><span>价格观测时间</span><b>{{ timeText(result.dataObservedAt) }}</b></div></div>
            <section v-if="result.flags.length" class="pricing-notes"><h4>参考依据</h4><ul><li v-for="(flag,index) in result.flags" :key="'flag'+index">{{ reasonFor(flag) }}</li></ul></section>
            <details class="pricing-snapshot"><summary>数据说明</summary><ul v-if="result.explanations.length"><li v-for="(line,index) in result.explanations" :key="index">{{ line }}</li></ul><p>{{ result.notice || '历史样本估价仅供参考，不属于房地产评估报告。' }}</p></details>
            <details class="pricing-snapshot"><summary>模型版本与输入快照</summary><dl><dt>模型版本</dt><dd>{{ result.modelVersion || '—' }}</dd><dt>数据版本</dt><dd>{{ result.dataVersion || '—' }}</dd><dt>请求编号</dt><dd>{{ result.requestId || '—' }}</dd></dl><pre>{{ JSON.stringify(result.snapshot || {}, null, 2) }}</pre></details>
          </template>
          <el-empty v-else description="暂无估价结果" :image-size="85" />
        </section>
        <aside class="pricing-history">
          <header><h3>预测记录</h3><el-button text :icon="Refresh" :loading="recordsLoading" :disabled="loading || predicting" @click="refreshRecords()">刷新</el-button></header>
          <el-tabs v-model="recordsTab">
            <el-tab-pane label="结果历史" name="history">
              <el-alert v-if="historyError" :title="historyError" type="error" :closable="false" />
              <p class="pricing-caption">最近 {{ history.length }} 条 / 最多 100 条</p>
              <el-empty v-if="!history.length && !historyError" description="暂无历史结果" :image-size="60" />
              <button v-for="(item,index) in history" :key="item.id || index" class="pricing-history-item" :class="{selected: result?.id === item.id}" :disabled="predicting" @click="result = item"><span>{{ timeText(item.generatedAt) }}</span><strong>{{ item.status === 'DEMO_ONLY' ? wan(item.total) + ' 万元' : priceStatus(item.status) }}</strong><small>{{ item.stale ? '资料已变化' : item.modelVersion || '旧记录' }}</small></button>
            </el-tab-pane>
            <el-tab-pane label="调用记录" name="requests">
              <el-alert v-if="requestsError" :title="requestsError" type="error" :closable="false" />
              <el-empty v-if="!requests.length && !requestsError" description="暂无价格调用记录" :image-size="60" />
              <article v-for="item in requests" :key="item.id" class="pricing-request">
                <div><time>{{ timeText(item.createdAt) }}</time><el-tag size="small" :type="item.status === 'FAILED' ? 'danger' : 'info'">{{ requestStatus(item.status) }}</el-tag></div>
                <p v-if="item.errorMessage">{{ item.errorMessage }}</p><code>{{ item.requestId }}</code>
              </article>
            </el-tab-pane>
          </el-tabs>
        </aside>
      </div>
    </main>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
import { normalizePrediction, priceStatus, reasonFor, wan, timeText, comparePrices } from '@/reports/pricing.mjs'
import { ArrowLeft, MagicStick, Refresh } from '@element-plus/icons-vue'
export default {
  name: 'PriceAdvice',
  components: { NavMain },
  data() {
    return {
      house: null, history: [], requests: [], result: null, loading: false, predicting: false, error: '', sequence: 0,
      historyError: '', requestsError: '', recordsTab: 'history', recordsLoading: false,
      ArrowLeft, MagicStick, Refresh
    }
  },
  computed: {
    houseId() { return String(this.$route.params.houseId || '') },
    levelText() { return ({ COMMUNITY: '同小区', BUSINESS_AREA: '同板块', NONE: '样本不足' })[this.result?.referenceLevel] || '—' },
    comparison() { return comparePrices(this.result, this.house?.listingPriceYuan) },
    differenceText() { const diff = this.comparison?.difference; return diff == null ? '—' : (diff > 0 ? '+' : diff < 0 ? '-' : '') + wan(Math.abs(diff)) },
    differencePercent() { const pct = this.comparison?.percent; return pct == null ? '未填写挂牌价' : Math.abs(pct) < 0.005 ? '与历史参考价持平' : (pct > 0 ? '高于' : '低于') + '历史参考价 ' + Math.abs(pct).toLocaleString('zh-CN', {maximumFractionDigits: 2}) + '%' },
    comparisonDescription() { const c = this.comparison; return c ? `历史参考区间 ${wan(this.result.lower)} 至 ${wan(this.result.upper)} 万元，历史参考价 ${wan(this.result.total)} 万元，${c.listing === null ? '未填写挂牌价' : '当前挂牌价 ' + wan(c.listing) + ' 万元'}。` : '' }
  },
  watch: { houseId: { immediate: true, handler() { this.load() } } },
  beforeUnmount() { this.sequence++ },
  methods: {
    priceStatus, reasonFor, wan, timeText,
    markerLabel(position) { return { left: `clamp(0px, calc(${position}% - 44px), calc(100% - 88px))` } },
    requestStatus(status) { return ({ PENDING: '处理中', STALE: '资料已变化', SUCCESS: '已完成' })[status] || priceStatus(status) },
    async getData(path, method = 'get') {
      const response = await this.$axios({ url: path, method, timeout: 35000 })
      if (response.data.code !== 0) throw new Error(response.data.message || '请求失败')
      return response.data.data
    },
    message(error) { return error.response?.data?.message || error.message || '服务暂不可用' },
    async load() {
      // 路由切换后，不再接收上一套房源的请求结果。
      const seq = ++this.sequence
      this.loading = true; this.predicting = false; this.recordsLoading = false; this.error = ''; this.house = null; this.history = []; this.requests = []; this.result = null
      this.historyError = ''; this.requestsError = ''; this.recordsTab = 'history'
      try {
        const house = await this.getData('/houses/' + encodeURIComponent(this.houseId))
        if (seq !== this.sequence) return
        this.house = house
        await this.refreshRecords(true)
      } catch (error) { if (seq === this.sequence) this.error = this.message(error) }
      finally { if (seq === this.sequence) this.loading = false }
    },
    async refreshRecords(selectLatest = false) {
      const seq = this.sequence
      const base = '/ai/houses/' + encodeURIComponent(this.houseId)
      this.recordsLoading = true
      // 历史结果与调用日志独立加载，一项失败不清空另一项。
      const results = await Promise.allSettled([this.getData(base + '/price-predictions'), this.getData(base + '/requests?kind=PRICE')])
      if (seq !== this.sequence) return
      this.historyError = ''; this.requestsError = ''; this.recordsLoading = false
      if (results[0].status === 'fulfilled') {
        try {
          this.history = results[0].value.map(normalizePrediction)
          if (selectLatest) this.result = this.history[0] || null
        } catch (error) { this.historyError = this.message(error) }
      } else this.historyError = this.message(results[0].reason)
      if (results[1].status === 'fulfilled') this.requests = results[1].value.filter(item => item.kind === 'PRICE')
      else this.requestsError = this.message(results[1].reason)
    },
    async predict() {
      if (!this.house || this.predicting) return
      const seq = this.sequence
      this.predicting = true; this.error = ''
      try {
        const raw = await this.getData('/ai/houses/' + encodeURIComponent(this.houseId) + '/price-predictions', 'post')
        if (seq !== this.sequence) return
        this.result = normalizePrediction(raw)
        await this.refreshRecords()
      } catch (error) { if (seq === this.sequence) this.error = this.message(error) }
      finally { if (seq === this.sequence) this.predicting = false }
    },
    back() {
      this.$router.push('/house')
    }
  }
}
</script>
<style scoped>
.price-comparison{display:grid;grid-template-columns:1fr 1fr;gap:20px;padding:24px 0 0}.price-comparison>div{min-width:0}.price-comparison span{font-size:12px;color:#788397}.price-comparison strong{display:block;margin:8px 0;font-size:28px;font-weight:600;overflow-wrap:anywhere;color:#53627d}.price-comparison small{font-size:12px;font-weight:400}.price-comparison .reference strong{color:var(--c-main)}.price-comparison .difference{grid-column:1/-1;display:flex;align-items:baseline;gap:12px;flex-wrap:wrap;border-top:1px solid #eef0f3;padding-top:12px}.price-comparison .difference strong{font-size:20px;color:var(--c-text);margin:0}.price-comparison .difference p{font-size:12px;color:#788397;margin:0}.comparison-interval{margin-top:18px;padding:16px 18px;background:#f8f9fb;border-radius:4px}.comparison-heading{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;font-size:12px;color:#69758a}.comparison-heading>span{display:flex;align-items:center;gap:6px}.comparison-heading i{width:12px;height:7px;background:#fae493;display:inline-block}.comparison-heading b{font-weight:500}.comparison-plot{height:124px;position:relative;margin:8px 0 0}.comparison-axis{position:absolute;left:0;right:0;top:62px;height:2px;background:#d8dde5}.comparison-band{position:absolute;top:57px;height:12px;background:#fae493;border-left:1px solid #d8ab44;border-right:1px solid #d8ab44;min-width:1px}.comparison-marker{position:absolute;width:0}.comparison-marker i{position:absolute;left:-5px;width:10px;height:10px;z-index:1}.comparison-marker::after{content:'';position:absolute;left:0;width:0;border-left:1px dashed currentColor}.comparison-marker.listing{top:27px;color:#53627d}.comparison-marker.listing i{background:#53627d;transform:rotate(45deg);border:1px solid #fff}.comparison-marker.listing::after{top:10px;height:26px}.comparison-marker.reference{top:90px;color:var(--c-main)}.comparison-marker.reference i{background:var(--c-main);border-radius:50%;border:1px solid #fff}.comparison-marker.reference::after{bottom:0;height:27px}.comparison-label{position:absolute;width:88px;text-align:center;font-size:11px;line-height:16px}.comparison-label.listing{top:4px;color:#53627d}.comparison-label.reference{top:107px;color:var(--c-main)}.comparison-scale{display:flex;justify-content:space-between;gap:10px;color:#788397;font-size:10px;margin-top:8px}@media(max-width:480px){.price-comparison{gap:16px 10px}.price-comparison strong{font-size:23px}.price-comparison .difference{gap:6px 10px}.price-comparison .difference p{flex-basis:100%}.comparison-interval{padding:14px 10px}}
.pricing-snapshot p,.pricing-snapshot li{line-height:1.8;color:#788397}.pricing-snapshot ul{padding-left:18px}
.pricing-request{border-top:1px solid #eef0f3;padding:14px 0;min-width:0}.pricing-request>div{display:flex;justify-content:space-between;gap:8px;align-items:center;flex-wrap:wrap}.pricing-request time{font-size:11px;color:var(--c-mute)}.pricing-request p{font-size:12px;line-height:1.7;color:#b54c40}.pricing-request code{display:block;margin-top:10px;font-size:10px;overflow-wrap:anywhere;color:#788397}
.pricing-content{padding:12px 24px 30px;max-width:1500px;margin:auto}.pricing-content>.el-alert{margin-bottom:14px}.page-head .sub{font-size:12px;color:var(--c-mute);margin:8px 0 0}.pricing-house{display:flex;align-items:center;gap:30px;flex-wrap:wrap;background:#fff;border:1px solid #e4e6ea;padding:20px 24px;margin:20px 0}.pricing-house h3{margin:4px 0;font-size:17px}.pricing-house p{margin:5px 0;color:var(--c-mute);font-size:12px}.pricing-house strong{display:block;font-size:23px;margin-top:4px;color:var(--c-main)}.pricing-house small{font-size:12px;font-weight:400}.pricing-house>.el-button{margin-left:auto}.pricing-label{color:var(--c-mute);font-size:11px}.pricing-grid{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:20px;align-items:start}.pricing-result,.pricing-history{background:#fff;border:1px solid #e4e6ea;border-radius:4px;padding:22px;min-width:0}.pricing-result>header,.pricing-history>header{display:flex;align-items:center;justify-content:space-between;gap:10px}.pricing-grid h3{margin:0;font-size:15px}.pricing-total{padding:28px 0 18px}.pricing-total>span{font-size:12px;color:var(--c-mute)}.pricing-total strong{display:block;font-size:36px;color:var(--c-main);font-weight:600;margin:7px 0}.pricing-total strong small{font-size:14px;font-weight:400}.pricing-total p{font-size:13px;color:#6d7689;margin:0}.pricing-interval{padding:18px;background:#f8f9fb;border-radius:4px}.pricing-interval>div{display:flex;justify-content:space-between;align-items:center;gap:12px;font-size:11px;flex-wrap:wrap}.pricing-interval b{font-weight:500;color:#767f91}.pricing-range{height:8px;background:#fae493;margin:24px 0 16px;position:relative;border-radius:4px}.pricing-range i{position:absolute;top:-5px;width:3px;height:18px;background:var(--c-main);border-radius:2px}.pricing-caption{font-size:11px;line-height:1.8;color:var(--c-mute);overflow-wrap:anywhere}.pricing-facts{display:grid;grid-template-columns:1fr 1fr;gap:18px;padding:22px 0;border-bottom:1px solid #e4e6ea}.pricing-facts span{display:block;color:var(--c-mute);font-size:11px}.pricing-facts b{font-size:12px;font-weight:400;display:block;margin-top:5px}.pricing-notes h4{font-size:13px;font-weight:500;margin:20px 0 12px}.pricing-notes ul{padding-left:18px;color:#788397;font-size:12px;line-height:1.9}.pricing-notes p{font-size:12px;color:var(--c-mute)}.pricing-empty{padding:40px 0 25px;text-align:center;color:#737e92}.pricing-empty strong{font-size:18px;font-weight:500}.pricing-empty p{font-size:12px;line-height:1.8}.pricing-snapshot{font-size:11px;border-top:1px solid #e4e6ea;margin-top:20px;padding-top:14px}.pricing-snapshot summary{cursor:pointer;color:var(--c-main)}.pricing-snapshot dl{display:grid;grid-template-columns:80px minmax(0,1fr);gap:8px}.pricing-snapshot dd{margin:0;overflow-wrap:anywhere;color:var(--c-mute)}.pricing-snapshot pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f8f9fb;padding:14px;max-height:260px;overflow:auto;color:#737e92}.pricing-history-item{display:flex;flex-direction:column;width:100%;text-align:left;background:#fff;border:0;border-top:1px solid #eef0f3;padding:14px 8px;gap:5px;cursor:pointer;color:#616773}.pricing-history-item.selected{background:#faf3e4}.pricing-history-item>span,.pricing-history-item small{font-size:10px;color:var(--c-mute)}.pricing-history-item strong{font-size:14px;font-weight:500}.pricing-history-item:disabled{cursor:wait;opacity:.6}.pricing-page :deep(.nav-wrap .el-menu-demo){min-width:0}@media(max-width:800px){.pricing-grid{grid-template-columns:1fr}.pricing-house{padding:18px;gap:18px}.pricing-house>.el-button{margin-left:0}.pricing-result,.pricing-history{padding:18px}.pricing-content{padding:10px 14px 24px}.pricing-total strong{font-size:30px}.pricing-facts{grid-template-columns:1fr 1fr}.pricing-house>div:first-child{flex-basis:100%}}
</style>
