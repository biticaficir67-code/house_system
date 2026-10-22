<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>价格建议</h2>
      <el-button @click="back">返回</el-button>
    </div>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
export default {
  name: 'PriceAdvice',
  components: { NavMain },
  data() {
    return {
      houseId: this.$route.params.houseId
    }
  },
  methods: {
    back() {
      this.$router.push('/house')
    }
  }
}
</script>
<style>
</style>
