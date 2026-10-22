<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <br>
    <div class="box">
      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="小区名称" prop="community">
          <el-input v-model="form.community"></el-input>
        </el-form-item>
        <el-form-item label="区域" prop="district">
          <el-input v-model="form.district"></el-input>
        </el-form-item>
        <el-form-item label="户型" prop="layout">
          <el-input v-model="form.layout"></el-input>
        </el-form-item>
        <el-form-item label="面积" prop="area">
          <el-input v-model="form.area"></el-input>
        </el-form-item>
        <el-form-item label="挂牌总价(万)" prop="price">
          <el-input v-model="form.price"></el-input>
        </el-form-item>
        <el-form-item label="核心卖点" prop="core">
          <el-input type="textarea" v-model="form.core" rows="3"></el-input>
        </el-form-item>
        <el-form-item label="小区介绍">
          <el-input type="textarea" v-model="form.intro" rows="3"></el-input>
        </el-form-item>
        <el-form-item label="周边配套">
          <el-input type="textarea" v-model="form.around" rows="3"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSubmit">确定</el-button>
          <el-button @click="cancel">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
export default {
  name: 'AddHouse',
  components: { NavMain },
  data() {
    return {
      form: {},
      rules: {
        community: [{ required: true, message: '请输入小区名称', trigger: 'blur' }],
        district: [{ required: true, message: '请输入区域', trigger: 'blur' }],
        layout: [{ required: true, message: '请输入户型', trigger: 'blur' }],
        area: [{ required: true, message: '请输入面积', trigger: 'blur' }],
        price: [{ required: true, message: '请输入挂牌总价', trigger: 'blur' }],
        core: [{ required: true, message: '请输入核心卖点', trigger: 'blur' }]
      }
    }
  },
  methods: {
    onSubmit() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          // 后端接好后改为 this.$axios.post('/addHouse', this.form)
          this.$alert('添加成功（目前为页面效果，未写入数据库）', { confirmButtonText: '确定' })
          this.$router.push('/house')
        } else {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
      })
    },
    cancel() {
      this.$refs['form'].resetFields()
    }
  }
}
</script>
<style>
</style>
