import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

// 全局挂 Element Plus，界面用中文
export default (app) => {
  app.use(ElementPlus, { locale: zhCn })
}
