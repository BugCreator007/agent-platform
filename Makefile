JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21)
export JAVA_HOME

.PHONY: backend admin-web user-web build test

backend:
	cd backend && mvn spring-boot:run

admin-web:
	cd admin-web && npm install && npm run dev

user-web:
	cd user-web && npm install && npm run dev

build:
	cd backend && mvn -DskipTests package
	cd admin-web && npm install && npm run build
	cd user-web && npm install && npm run build

test:
	cd backend && mvn test
