JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21)
export JAVA_HOME

# 本地开发命令入口
.PHONY: backend admin-web user-web build test

# 启动后端（端口 8081）
backend:
	cd backend && mvn spring-boot:run

# 启动管理后台开发服务器（端口 5173）
admin-web:
	cd admin-web && npm install && npm run dev

# 启动用户端开发服务器（端口 5174）
user-web:
	cd user-web && npm install && npm run dev

# 构建后端 jar 与两个前端产物
build:
	cd backend && mvn -DskipTests package
	cd admin-web && npm install && npm run build
	cd user-web && npm install && npm run build

# 运行后端测试（Spring 上下文 + Modulith 边界）
test:
	cd backend && mvn test
