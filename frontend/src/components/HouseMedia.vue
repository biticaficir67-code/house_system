<template>
  <section class="media-section">
    <div class="media-header">
      <div><h3>房源图片</h3><p>支持 JPG、PNG、WebP，每张不超过50MB，最多20张。图片变更后需要重新核验。</p></div>
      <el-button type="primary" :disabled="readonly || busy" :loading="busy" @click="$refs.picker.click()">上传图片</el-button>
      <input ref="picker" type="file" accept=".jpg,.jpeg,.png,.webp" multiple hidden @change="uploadFiles" />
    </div>
    <el-progress v-if="busy && progress > 0" :percentage="progress" :stroke-width="6" />
    <el-empty v-if="!media.length" :image-size="55" description="还没有图片，上传后可设置封面" />
    <div v-else class="media-grid">
      <article v-for="(item, index) in media" :key="item.id" class="media-card">
        <el-image v-if="item.mediaType === 'IMAGE'" :src="urls[item.id] || ''" fit="cover" :preview-src-list="previewImages" preview-teleported>
          <template #error><div class="media-placeholder">{{ failed[item.id] ? '图片文件暂不可用' : '正在加载' }}</div></template>
        </el-image>
        <video v-else :src="urls[item.id]" controls preload="metadata" />
        <div class="media-caption"><span :title="item.originalName">{{ item.originalName }}</span><el-tag v-if="item.isCover" size="small" type="warning">封面</el-tag></div>
        <div v-if="!readonly" class="media-actions">
          <el-button v-if="item.mediaType === 'IMAGE'" size="small" :disabled="busy || !!item.isCover" @click="cover(item)">设为封面</el-button>
          <el-button size="small" :disabled="busy || index === 0" @click="move(index, -1)" title="向前排序">↑</el-button>
          <el-button size="small" :disabled="busy || index === media.length - 1" @click="move(index, 1)" title="向后排序">↓</el-button>
          <el-button size="small" type="danger" plain :disabled="busy" @click="remove(item)">移除</el-button>
        </div>
      </article>
    </div>
    <p v-if="readonly" class="media-note">已成交房源保留历史图片，不再调整媒体。</p>
  </section>
</template>

<script>
import { errorText } from '@/business'
export default {
  props: { houseId: [String, Number], version: [String, Number], media: { type: Array, default: () => [] }, readonly: Boolean, refresh: Function },
  data() { return { urls: {}, failed: {}, busy: false, progress: 0, generation: 0 } },
  computed: { previewImages() { return this.media.filter(m => m.mediaType === 'IMAGE' && this.urls[m.id]).map(m => this.urls[m.id]) } },
  watch: {
    houseId() { this.clearUrls(); this.load() },
    media: { handler() { this.load() }, deep: true, immediate: true }
  },
  beforeUnmount() { this.generation++; this.clearUrls() },
  methods: {
    clearUrls() { Object.values(this.urls).forEach(url => URL.revokeObjectURL(url)); this.urls = {}; this.failed = {} },
    async load() {
      const generation = ++this.generation
      const ids = new Set(this.media.map(m => String(m.id)))
      for (const id of Object.keys(this.urls)) if (!ids.has(String(id))) { URL.revokeObjectURL(this.urls[id]); delete this.urls[id] }
      await Promise.all(this.media.filter(m => !this.urls[m.id]).map(async item => {
        try {
          const response = await this.$axios.get(`/houses/${this.houseId}/media/${item.id}/content`, { responseType: 'blob' })
          if (generation === this.generation) { this.urls[item.id] = URL.createObjectURL(response.data); this.failed[item.id] = false }
        } catch { if (generation === this.generation) this.failed[item.id] = true }
      }))
    },
    async uploadFiles(event) {
      const files = [...event.target.files]; event.target.value = ''
      if (!files.length) return
      if (this.media.filter(m => m.mediaType === 'IMAGE').length + files.length > 20) return this.$message.warning('每套房源最多20张图片，请减少本次选择数量')
      if (files.some(f => !/\.(jpg|jpeg|png|webp)$/i.test(f.name) || f.size === 0 || f.size > 50 * 1024 * 1024)) return this.$message.warning('请选择50MB以内的JPG、PNG或WebP图片')
      this.busy = true; let done = 0; const errors = []
      try {
        for (const file of files) {
          const body = new FormData(); body.append('file', file)
          try { await this.$axios.post(`/houses/${this.houseId}/media`, body, { onUploadProgress: e => { this.progress = Math.min(99, Math.round(((done + (e.total ? e.loaded / e.total : 0)) / files.length) * 100)) } }); done++ }
          catch (error) { errors.push(`${file.name}：${errorText(error)}`) }
        }
        if (this.refresh) await this.refresh()
        if (errors.length) this.$alert(`成功上传${done}张；${errors.join('；')}`, '上传结果')
        else this.$message.success(`已上传${done}张图片，请核验后上架`)
      } catch (error) { this.$message.error(errorText(error)) }
      finally { this.busy = false; this.progress = 0 }
    },
    async perform(action) {
      this.busy = true
      try { await action(); if (this.refresh) await this.refresh() }
      catch (error) { this.$message.error(errorText(error)); if (error?.response?.status === 409 && this.refresh) { try { await this.refresh() } catch (refreshError) { this.$message.error(errorText(refreshError)) } } }
      finally { this.busy = false }
    },
    cover(item) { return this.perform(() => this.$axios.put(`/houses/${this.houseId}/media/${item.id}/cover`, { version: this.version })) },
    move(index, offset) {
      const ids = this.media.map(m => m.id); [ids[index], ids[index + offset]] = [ids[index + offset], ids[index]]
      return this.perform(() => this.$axios.put(`/houses/${this.houseId}/media/order`, { mediaIds: ids, version: this.version }))
    },
    async remove(item) {
      try { await this.$confirm('移除后会下架并重新核验该房源，确认移除这张图片？', '移除图片', { type: 'warning' }) }
      catch { return }
      return this.perform(() => this.$axios.delete(`/houses/${this.houseId}/media/${item.id}`))
    }
  }
}
</script>

<style scoped>
.media-section{margin:22px 0;padding-top:18px;border-top:1px solid #e7e8eb}.media-header{display:flex;align-items:center;justify-content:space-between;gap:16px}.media-header h3{margin:0 0 6px;font-size:16px}.media-header p,.media-note{font-size:12px;color:#78818e;line-height:1.6}.media-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(215px,1fr));gap:14px;margin-top:15px}.media-card{border:1px solid #e2e5e9;border-radius:8px;overflow:hidden;background:white}.media-card .el-image,.media-card video{display:block;width:100%;height:155px;background:#f1f3f5}.media-placeholder{display:flex;align-items:center;justify-content:center;height:155px;color:#8a9099;font-size:12px}.media-caption{display:flex;justify-content:space-between;gap:8px;padding:9px;font-size:12px}.media-caption>span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.media-actions{display:flex;gap:4px;padding:0 9px 10px;flex-wrap:wrap}.media-actions .el-button{margin-left:0}
</style>
