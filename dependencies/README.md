# dependencies

版本管理中心（父 POM），不包含任何 Java 源代码，负责统一管理所有模块的第三方依赖版本、构建插件和环境配置。

---

## 主要职责

1. **依赖版本统一管理** — 通过 `<dependencyManagement>` 集中定义所有第三方库的版本号，子模块引用时无需指定版本
2. **BOM 导入** — 通过 `scope=import` 引入 Spring Boot、Spring Cloud、Spring Cloud Alibaba 的 BOM
3. **全局构建配置** — 定义编译器版本、资源过滤、Git 信息生成等所有模块共享的构建行为
4. **插件版本管理** — 通过 `<pluginManagement>` 统一 maven-jar-plugin、spring-boot-maven-plugin 等版本
5. **多环境支持** — 通过 `<profiles>` 实现 dev / test / demo / prod 环境切换
6. **私服配置** — 定义 Maven 仓库地址和发布地址

---

## 关键依赖版本

### 框架层

| 类别 | 库 | 版本 |
|------|---|------|
| Web 框架 | Spring Boot | 3.5.14 |
| 微服务框架 | Spring Cloud | 2025.0.2 |
| 阿里巴巴微服务 | Spring Cloud Alibaba | 2025.0.0.0 |

### 中间件

| 类别 | 库 | 版本 |
|------|---|------|
| 注册/配置中心 | Nacos Client | 2.4.3 |
| 分布式事务 | Seata | 2.6.0 |
| 数据库连接池 | Druid | 1.2.28 |
| 消息队列 | Kafka | 由 Spring Boot BOM 管理 |

### 数据层

| 类别 | 库 | 版本 |
|------|---|------|
| ORM | MyBatis | 3.5.19 |
| MyBatis-Spring | mybatis-spring | 3.0.5 |
| SQL 解析 | JSqlParser | 5.3 |
| MySQL 驱动 | mysql-connector-j | 9.7.0 |
| H2 数据库 | h2 | 2.4.240 |

### 工具库

| 类别 | 库 | 版本 |
|------|---|------|
| HTTP 客户端 | OkHttp | 4.12.0 |
| JSON (Jackson) | 由 Spring Boot BOM 管理 | — |
| JSON (Gson) | gson | 2.11.0 |
| JSON (Fastjson2) | fastjson2 | 2.0.56 |
| 二维码 | ZXing | 3.5.2 |
| 压缩 | commons-compress | 1.28.0 |
| 加密 | Bouncy Castle | 1.84 |
| API 文档 | SpringDoc | 2.8.17 |
| 网络 | Netty | 4.2.14.Final |

---

## 全局构建配置

### 资源过滤

- `src/main/java` 中的 `*.properties` 和 `*.xml` 会被包含
- `src/main/resources` 排除环境目录（dev/test/demo/prod）和脚本文件
- 根据激活的 profile 选择对应环境目录覆盖配置

### 全局插件

| 插件 | 版本 | 功能 |
|------|------|------|
| maven-resources-plugin | 3.5.0 | 资源复制（config / 启动脚本 / lib） |
| maven-surefire-plugin | 3.5.5 | 跳过测试 |
| git-commit-id-maven-plugin | 10.0.0 | 生成 git.properties |
| maven-compiler-plugin | 3.15.0 | Java 21 编译，开启 `-parameters` |

### 可选插件（pluginManagement）

| 插件 | 版本 | 用途 |
|------|------|------|
| maven-javadoc-plugin | 3.12.0 | 生成 Javadoc 包 |
| maven-source-plugin | 3.4.0 | 生成 Source 包 |
| maven-dependency-plugin | 3.10.0 | 拷贝依赖到 lib |
| maven-jar-plugin | 3.5.0 | JAR 打包配置 |
| maven-war-plugin | 3.5.1 | WAR 打包配置 |
| spring-boot-maven-plugin | 3.5.14 | Spring Boot 打包 |

---

## 多环境打包

### 自动识别规则

| 优先级 | 条件 | 环境 |
|:------:|------|------|
| 最高 | 存在 `system-dev.properties` | dev |
| ↓ | 存在 `system-test.properties` | test |
| ↓ | 存在 `system-demo.properties` | demo |
| ↓ | 存在 `system-prod.properties` | prod |
| ↓ | 存在 `system.properties` | prod |
| 最低 | 无上述文件 | dev（默认） |

### 手动指定

```bash
mvn package -Ptest
```

手动指定 `-P` 参数时会忽略自动识别规则。
