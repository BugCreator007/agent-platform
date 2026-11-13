---
name: backend-release-deploy
description: 构建、打包、Docker/TKE 部署、腾讯云配置、密钥注入、滚动升级和回滚时使用。
---

# 构建与部署

## 构建

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
mvn -DskipTests package

cd ../frontend
npm install
npm run build
```

## 本地

```bash
# 使用 .env（已忽略，600 权限）
./scripts/run-backend.sh

# 或完整基础设施
docker compose -f deploy/docker-compose.yml up -d
```

默认：
- 前端 `http://localhost:5173`
- 后端 `http://localhost:8081`
- MySQL `localhost:3307`
- Milvus `localhost:19530`
- ES `localhost:9200`

## 镜像

```bash
docker build -t ccr.ccs.tencentyun.com/agent-platform/backend:latest backend
docker build -t ccr.ccs.tencentyun.com/agent-platform/frontend:latest frontend
docker push ...
```

## TKE

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret.example.yaml
kubectl apply -f deploy/k8s/backend.yaml
kubectl apply -f deploy/k8s/frontend.yaml
kubectl apply -f deploy/k8s/ingress.yaml
kubectl apply -f deploy/k8s/hpa.yaml
```

部署前检查：
- Secret 不提交 Git。
- JWT_SECRET 至少 32 字节且与旧环境不同。
- DB/Redis/Milvus/ES/COS 地址与网络策略。
- CORS 只允许生产前端域名。
- 生产 `DEMO_SEED=false`，管理员密码通过 Secret 注入。
- 健康检查指向 `/actuator/health/readiness` 和 `/liveness`。

## 腾讯云映射

- TKE：后端/前端工作负载
- TCR：镜像
- CLB/WAF：入口与防护
- CDB MySQL：业务库
- Redis：限流/Token 黑名单
- COS：文档存储
- Elasticsearch：关键词检索
- Milvus on TKE：向量检索
- TDMQ：异步事件（预留）
- CLS/APM/TMP：日志、链路、监控
- KMS/SSM/CAM：密钥与权限
- CODING/GitHub Actions：CI

## 回滚

- 保留上一版镜像 tag。
- `kubectl rollout undo deployment/agent-backend -n agent-platform`。
- 数据库迁移优先向前兼容，不做破坏性删除；必要时新增回滚迁移。
