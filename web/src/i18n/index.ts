import { createI18n } from 'vue-i18n'

import enUS from './messages/en-US'
import zhCN from './messages/zh-CN'

export default createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})
