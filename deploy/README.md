# 腾讯云部署

## 目标架构

```text
CLB / WAF
   │
   ├── agent-frontend (TKE, Nginx, 静态资源)
   └── agent-backend  (TKE, Spring Boot, 3+ 副本)
          ├── CDB MySQL
          ├── Redis
          ├── TDMQ RocketMQ / 事件总线（预留）
          ├── Tencent Cloud Elasticsearch
          ├── Milvus on TKE
          ├── COS（文档对象存储）
          ├── KMS / SSM（密钥）
          └── CLS / APM / TMP（日志、链路、监控）
```

## 镜像构建

```bash
# 后端
docker build -t ccr.ccs.tencentyun.com/agent-platform/backend:latest backend

# 前端
docker build -t ccr.ccs.tencentyun.com/agent-platform/frontend:latest frontend

docker push ccr.ccs.tencentyun.com/agent-platform/backend:latest
docker push ccr.ccs.tencentyun.com/agent-platform/frontend:latest
```

## TKE 部署

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret.example.yaml   # 先替换为真实值
kubectl apply -f deploy/k8s/backend.yaml
kubectl apply -f deploy/k8s/frontend.yaml
kubectl apply -f deploy/k8s/ingress.yaml
kubectl apply -f deploy/k8s/hpa.yaml
```

部署前需要修改：

- `secret.example.yaml`：CDB、Redis、JWT、DeepSeek、COS、管理员密码。
- `configmap.yaml`：Redis、ES、Milvus、OIDC、CORS、OTLP 地址。
- `backend.yaml` / `frontend.yaml`：TCR 镜像地址。
- `ingress.yaml`：CLB 实例、域名、TLS Secret。

## 配置说明

- 生产必须设置 `JWT_SECRET`（至少 32 字节随机值）和 `BOOTSTRAP_ADMIN_PASSWORD`。
- 设置 `DEMO_SEED=false` 可关闭演示数据初始化。
- `STORAGE_TYPE=cos` 时配置 `COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_REGION`、`COS_BUCKET`。
- `MILVUS_ENABLED=true`、`ES_ENABLED=true` 时配置实际服务地址。
- DeepSeek 使用 `DEEPSEEK_API_KEY`，可另配 `DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`。
- OIDC 使用 `OIDC_ISSUER_URI`，后端同时保留本地管理员登录。

## 可观测性

- Prometheus：`/actuator/prometheus`。
- 健康检查：`/actuator/health`。
- OTLP tracing：`OTEL_EXPORTER_OTLP_ENDPOINT`。
- 生产建议接入 CLS 日志采集与 TMP 告警。

## 本地基础设施

`deploy/docker-compose.yml` 提供 MySQL、Redis、Milvus、Elasticsearch、MinIO、etcd 和前后端，用于联调。
