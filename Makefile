JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21)
export JAVA_HOME

.PHONY: backend frontend build test

backend:
	cd backend && mvn spring-boot:run

frontend:
	cd frontend && npm install && npm run dev

build:
	cd backend && mvn -DskipTests package
	cd frontend && npm install && npm run build

test:
	cd backend && mvn test
