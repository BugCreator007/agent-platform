export type ModuleKey =
  | 'overview'
  | 'agents'
  | 'sessions'
  | 'debug'
  | 'workflow'
  | 'models'
  | 'release'
  | 'eval'
  | 'tools'
  | 'knowledge'
  | 'alerts'
  | 'cost'
  | 'settings'

export interface ModuleDefinition {
  key: ModuleKey
  label: string
  path: string
  description: string
  group: '工作台' | '构建' | '运维'
}

export const modules: ModuleDefinition[] = [
  { key: 'overview', label: '运营概览', path: '/overview', group: '工作台', description: '全局健康度、核心指标、告警与待办。' },
  { key: 'agents', label: '智能体', path: '/agents', group: '工作台', description: '智能体定义、提示词、工具与知识绑定。' },
  { key: 'sessions', label: '运行追踪', path: '/sessions', group: '工作台', description: '会话、调用链、失败定位与重放。' },
  { key: 'debug', label: '会话调试', path: '/debug', group: '工作台', description: '选择智能体与模型绑定，进行流式对话调试。' },
  { key: 'workflow', label: '流程编排', path: '/workflow', group: '构建', description: '可视化 DAG、节点配置、测试与发布。' },
  { key: 'models', label: '模型厂商', path: '/models', group: '构建', description: '厂商、密钥、额度、模型目录与路由。' },
  { key: 'release', label: '发布与版本', path: '/release', group: '构建', description: '评测门禁、审批、灰度与回滚。' },
  { key: 'eval', label: '评测中心', path: '/eval', group: '构建', description: '评测集、回归运行与版本对比。' },
  { key: 'tools', label: '工具与 MCP', path: '/tools', group: '构建', description: '工具注册、健康检查与 MCP 接入。' },
  { key: 'knowledge', label: '知识库', path: '/knowledge', group: '构建', description: '数据集、文档、索引、检索与授权。' },
  { key: 'alerts', label: '监控告警', path: '/alerts', group: '运维', description: '告警记录、规则、认领与升级。' },
  { key: 'cost', label: '成本与用量', path: '/cost', group: '运维', description: '成本拆解、预算与节流建议。' },
  { key: 'settings', label: '平台设置', path: '/settings', group: '运维', description: '成员、密钥、安全护栏与审计。' },
]
