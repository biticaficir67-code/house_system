<template>
  <el-dialog :model-value="modelValue" title="表格导入" width="min(1000px,95vw)" :before-close="beforeClose" @update:model-value="updateVisible" destroy-on-close>
    <div class="import-box">
      <el-form :inline="true" label-position="top">
        <el-form-item label="数据类型"><el-input :model-value="{HOUSE:'房源',CUSTOMER:'客户与需求',OWNER:'业主'}[kind]" disabled style="width:160px" /></el-form-item>
        <el-form-item label="所属门店"><el-select v-model="storeId" :disabled="!isAdmin || busy || committing" @change="storeChanged"><el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
        <el-form-item label="负责经纪人"><el-select v-model="brokerId" :disabled="!isAdmin || busy || committing" placeholder="请选择负责人" @change="reset"><el-option v-for="b in brokers" :key="b.id" :label="b.brokerName || b.name" :value="b.id" /></el-select></el-form-item>
      </el-form>
      <div class="import-tools"><el-button @click="download('xlsx')">下载Excel模板</el-button><el-button @click="download('csv')">下载CSV模板</el-button></div>
      <div class="drop-area" @dragover.prevent @drop.prevent="dropped">
        <p>{{ file ? file.name : '选择或拖入Excel / CSV文件' }}</p><small>支持.xlsx、.xls和UTF-8 / GB18030 CSV；每批最多1000条、5MB。归属以上方选择为准。</small>
        <div><el-button @click="$refs.filePicker.click()" :disabled="busy || committing">选择文件</el-button><el-button type="primary" @click="previewFile" :loading="busy" :disabled="committing || !file || !storeId || !brokerId">校验并预览</el-button></div>
        <input ref="filePicker" hidden type="file" accept=".xlsx,.xls,.csv" @change="picked" />
      </div>
      <el-alert v-if="kind === 'HOUSE'" title="房源导入后为下架、待核验。上传图片并核验后即可上架；AI分析可在详情按需生成。" type="info" :closable="false" />
      <template v-if="preview">
        <div class="import-summary"><div>文件记录<strong>{{ preview.total }}</strong></div><div>有效新数据<strong>{{ preview.validCount }}</strong></div><div>重复跳过<strong>{{ preview.duplicateCount }}</strong></div><div>需修正<strong>{{ preview.errorCount }}</strong></div></div>
        <el-alert v-for="message in preview.messages" :key="message" :title="message" type="info" :closable="false" class="import-message" />
        <div class="preview-actions"><span>预览有效15分钟；错误和明确重复的行不会写入。</span><el-button @click="exportIssues">下载校验结果</el-button><el-button type="primary" :loading="committing" :disabled="preview.validCount === 0 || !!result" @click="commit">确认导入 {{ preview.validCount }} 条</el-button></div>
        <el-table :data="previewRows" border stripe max-height="430">
          <el-table-column prop="rowNumber" label="原表行号" width="100" />
          <el-table-column label="记录" min-width="160"><template #default="{row}">{{ row.values.communityName || row.values.name || '待修正记录' }}</template></el-table-column>
          <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="row.status === 'ERROR' ? 'danger' : row.status === 'DUPLICATE' ? 'info' : 'success'">{{ {VALID:'可导入',DUPLICATE:'重复跳过',ERROR:'需修正'}[row.status] }}</el-tag></template></el-table-column>
          <el-table-column label="校验说明" min-width="300"><template #default="{row}">{{ [...row.errors, ...row.warnings].join('；') || '字段校验通过' }}</template></el-table-column>
        </el-table>
        <el-pagination layout="prev,pager,next,total" :total="preview.total" :page-size="20" v-model:current-page="previewPage" class="preview-pages" />
      </template>
      <el-result v-if="result" icon="success" :title="`成功导入 ${result.importedCount} 条`" :sub-title="`重复跳过 ${result.skippedDuplicateCount} 条，错误跳过 ${result.skippedErrorCount} 条。原数据未覆盖。`">
        <template #extra><el-button type="primary" @click="updateVisible(false)">完成并关闭</el-button></template>
      </el-result>
    </div>
    <div v-if="isAdmin" class="box1 quality-box"><div class="quality-heading"><h3>数据质量提醒</h3><el-button size="small" @click="loadQuality">刷新</el-button></div><p>只提示需要核对的数据，不自动修改历史日期或状态。</p><div class="quality-grid"><article v-for="item in quality" :key="item.key"><span>{{ item.label }}</span><strong>{{ item.count }}</strong><small>{{ item.advice }}</small></article></div><el-alert v-if="qualityError" :title="qualityError" type="info" :closable="false" /></div>
  </el-dialog>
</template>

<script>
import { errorText, saveBlob, csvCell } from '@/business'
export default {
  props: { modelValue: Boolean, initialKind: { type: String, default: 'HOUSE' } },
  emits: ['update:modelValue', 'imported'],
  data() { return { kind: ['HOUSE','CUSTOMER','OWNER'].includes(this.initialKind) ? this.initialKind : 'HOUSE', storeId: '', brokerId: '', stores: [], brokers: [], file: null, preview: null, previewPage: 1, result: null, busy: false, committing: false, quality: [], qualityError: '' } },
  computed: { isAdmin() { return this.$store.state.role === 'ADMIN' }, previewRows() { return this.preview?.rows.slice((this.previewPage-1)*20, this.previewPage*20) || [] } },
  async created() {
    try { const response = await this.$axios.get('/stores'); this.stores = (response.data.data || []).filter(s => s.enabled); this.storeId = this.isAdmin ? this.stores[0]?.id || '' : this.$store.state.storeId; await this.loadBrokers(); if (this.isAdmin) this.loadQuality() }
    catch(error) { this.$message.error(errorText(error)) }
  },
  methods: {
    beforeClose(done) {
      if (this.busy || this.committing) return this.$message.info('正在处理文件，请等待结果后关闭')
      done()
    },
    updateVisible(value) {
      if (!value && this.$route.query.import === '1') {
        const query = { ...this.$route.query }; delete query.import
        this.$router.replace({ query })
      }
      this.$emit('update:modelValue', value)
    },
    reset() { this.preview = null; this.result = null; this.previewPage = 1 },
    async storeChanged() { this.reset(); this.brokerId = ''; await this.loadBrokers() },
    async loadBrokers() { if (!this.storeId) return; const response = await this.$axios.get('/brokers', { params: { storeId: this.storeId } }); this.brokers = response.data.data || []; this.brokerId = this.isAdmin ? '' : this.$store.state.brokerId },
    choose(file) { if (!file) return; if (file.size > 5*1024*1024 || !/\.(xlsx|xls|csv)$/i.test(file.name)) return this.$message.warning('请选择5MB以内的Excel或CSV文件'); this.file = file; this.reset() },
    picked(event) { this.choose(event.target.files[0]); event.target.value = '' },
    dropped(event) { if (!this.busy && !this.committing) this.choose(event.dataTransfer.files[0]) },
    async download(format) { try { const r = await this.$axios.get(`/imports/${this.kind}/template`, { params: {format}, responseType: 'blob' }); saveBlob(r.data, `导入模板-${{HOUSE:'房源',CUSTOMER:'客户',OWNER:'业主'}[this.kind]}.${format}`) } catch(error) { this.$message.error(errorText(error)) } },
    async previewFile() {
      this.busy = true; this.reset()
      try { const body = new FormData(); body.append('file', this.file); body.append('storeId', this.storeId); body.append('brokerId', this.brokerId); const r = await this.$axios.post(`/imports/${this.kind}/preview`, body, { timeout: 60000 }); this.preview = r.data.data }
      catch(error) { this.$message.error(errorText(error)) } finally { this.busy = false }
    },
    async commit() {
      if (!this.preview || this.committing) return
      this.committing = true
      try { const r = await this.$axios.post(`/imports/${this.kind}/commit`, { previewToken: this.preview.previewToken }, { timeout: 120000 }); this.result = r.data.data; this.$emit('imported'); if (this.isAdmin) this.loadQuality() }
      catch(error) { this.$message.error(errorText(error)) } finally { this.committing = false }
    },
    exportIssues() { const rows = [['行号','记录','状态','校验说明'], ...this.preview.rows.map(r=>[r.rowNumber,r.values.communityName || r.values.name || '',r.status,[...r.errors,...r.warnings].join('；')])]; saveBlob(new Blob(['\ufeff'+rows.map(r=>r.map(csvCell).join(',')).join('\r\n')], {type:'text/csv;charset=utf-8'}),'导入校验结果.csv') },
    async loadQuality() { try { const r = await this.$axios.get('/imports/quality'); this.quality = r.data.data; this.qualityError = '' } catch(error) { this.qualityError = errorText(error) } }
  }
}
</script>

<style scoped>
.import-box{max-width:1250px}.import-tools{display:flex;gap:8px;margin-bottom:16px}.import-tools .el-button{margin-left:0}.drop-area{border:1px dashed #c4c9d1;background:#f8f9fa;border-radius:9px;padding:24px;margin-bottom:18px;text-align:center}.drop-area p{margin:0 0 8px;font-weight:600}.drop-area small{display:block;color:#78818e;line-height:1.7;margin-bottom:16px}.import-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:22px 0}.import-summary>div{border:1px solid #e4e7eb;border-radius:8px;padding:16px;color:#737d89;font-size:13px}.import-summary strong{display:block;color:#303640;font-size:27px;margin-top:8px}.preview-actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin:20px 0 12px}.preview-actions>span{flex:1;font-size:12px;color:#7c8590}.import-message{margin:8px 0}.preview-pages{margin-top:16px}.quality-box{margin-top:20px}.quality-heading{display:flex;align-items:center;justify-content:space-between}.quality-box h3{font-size:17px;margin:0}.quality-box>p{color:#7c8590;font-size:12px}.quality-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:15px}.quality-grid article{padding:16px;border:1px solid #e4e7eb;border-radius:8px}.quality-grid span,.quality-grid small{display:block;color:#78818e;font-size:12px;line-height:1.6}.quality-grid strong{display:block;font-size:25px;color:#936f38;margin:10px 0}@media(max-width:780px){.import-summary,.quality-grid{grid-template-columns:repeat(2,1fr)}}
</style>
