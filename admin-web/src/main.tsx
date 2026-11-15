// React 19 兼容补丁需在其他 antd 相关导入前加载
import '@ant-design/v5-patch-for-react-19'
import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './styles.css'

/** React Query 全局客户端：失败不自动重试，窗口聚焦不自动刷新，避免后台频繁请求 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false },
  },
})

// 应用入口：ConfigProvider 提供 antd 中文语言包与主题令牌，QueryClientProvider 提供数据缓存
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#0E4F3C',
          borderRadius: 8,
          fontFamily: "'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif",
        },
      }}
    >
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </ConfigProvider>
  </React.StrictMode>,
)
