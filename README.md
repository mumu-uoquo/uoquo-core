# Uoquo Core

核心组件库 — 为单体应用和微服务架构提供统一的基础设施能力。

---

## 技术栈

| 项目 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.5.14 |
| Spring Cloud | 2025.0.2 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Nacos | 2.4.3 |
| Seata | 2.6.0 |
| MyBatis | 3.5.19 |
| Druid | 1.2.28 |

---

## 模块结构

```
uoquo-core
├── dependencies          # 版本管理中心（父 POM）
├── uoquo-annotations     # 注解、异常、通用对象定义
├── utils-basic           # 通用工具类库
├── app-core              # 单体应用核心
└── cloud-core            # 微服务核心
```

### 模块依赖关系

```
dependencies (父 POM，版本管理)
    │
    ├── uoquo-annotations     ← 无内部依赖（最底层）
    │       提供：注解定义、异常体系、分页对象、统一响应体
    │
    ├── utils-basic           ← 依赖 uoquo-annotations
    │       提供：加解密、HTTP、JSON、Redis、Spring 工具
    │
    ├── app-core              ← 依赖 utils-basic
    │       提供：拦截器链、签名校验、MyBatis 增强、事件系统
    │
    └── cloud-core            ← 依赖 app-core
            提供：Feign 增强、分布式事件、负载均衡、分布式事务
```

**引入规则：**
- 单体应用只需引入 `app-core`（自动传递 `utils-basic` 和 `uoquo-annotations`）
- 微服务应用引入 `cloud-core`（自动传递所有上层模块）

---

## 快速开始

### 单体应用

```xml
<parent>
    <groupId>com.uoquo</groupId>
    <artifactId>dependencies</artifactId>
    <version>1.1.0</version>
</parent>

<dependencies>
    <dependency>
        <groupId>com.uoquo</groupId>
        <artifactId>app-core</artifactId>
        <version>${uoquo.version}</version>
    </dependency>
</dependencies>
```

### 微服务应用

```xml
<parent>
    <groupId>com.uoquo</groupId>
    <artifactId>dependencies</artifactId>
    <version>1.1.0</version>
</parent>

<dependencies>
    <dependency>
        <groupId>com.uoquo</groupId>
        <artifactId>cloud-core</artifactId>
        <version>${uoquo.version}</version>
    </dependency>
    <!-- 微服务必须引入 bootstrap 支持 -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-bootstrap</artifactId>
    </dependency>
</dependencies>
```

---

## 构建与打包

### 编译安装

```bash
mvn clean install
```

### 多环境打包

通过 `-P` 参数指定环境，或者依据项目中 `src/main/resources/system-*.properties` 文件自动识别：

```bash
# 手动指定
mvn package -Pdev
mvn package -Ptest
mvn package -Pdemo
mvn package -Pprod

# 自动识别优先级：dev > test > demo > prod
```

| 条件 | 打包环境 |
|------|---------|
| 无 system 文件 | dev（默认） |
| 存在 `system.properties` | prod |
| 存在 `system-dev.properties` | dev |
| 存在 `system-test.properties` | test |
| 存在 `system-demo.properties` | demo |
| 存在 `system-prod.properties` | prod |

### 打包产物

打包后的 `target/` 目录结构：

```
target/
├── ${project}.jar        # 主程序包
├── config/               # 配置文件（properties / yml）
├── lib/                  # 外部依赖（dll / so / 第三方 jar）
├── *.sh / *.bat          # 启动脚本
└── git.properties        # Git 提交信息
```

---

## 各模块详细说明

各模块的详细文档请参考对应目录下的 README.md：

- [dependencies/README.md](./dependencies/README.md) — 版本管理与构建配置
- [uoquo-annotations/README.md](./uoquo-annotations/README.md) — 注解与对象定义
- [utils-basic/README.md](./utils-basic/README.md) — 通用工具类
- [app-core/README.md](./app-core/README.md) — 单体应用核心
- [cloud-core/README.md](./cloud-core/README.md) — 微服务核心

---

## Maven 私服

| 用途 | 地址 |
|------|------|
| 公共仓库（读取） | https://mvn.uoquo.com/repository/maven-public/ |
| Release 发布 | https://mvn.uoquo.com/repository/maven-releases/ |
| Snapshot 发布 | https://mvn.uoquo.com/repository/maven-snapshots/ |
