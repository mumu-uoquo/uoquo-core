# Uoquo Core

核心组件库 - 为单体应用和微服务架构提供统一的基础设施能力。

## 项目概览

- **组织**: `com.uoquo`
- **Java 版本**: 21
- **Spring Boot**: 3.5.14
- **Spring Cloud**: 2025.0.2
- **Spring Cloud Alibaba**: 2025.0.0.0

## 模块结构

```
uoquo-core
├── dependencies          # 版本管理中心（父 POM）
├── uoquo-annotations     # 注解与通用对象定义
├── utils-basic           # 通用工具类库
├── app-core              # 单体应用核心
└── cloud-core            # 微服务核心
```

### 模块依赖关系

```
dependencies (版本管理中心)
    │
    ├── uoquo-annotations     ← 无内部模块依赖
    │
    ├── utils-basic           ← 依赖 uoquo-annotations
    │
    ├── app-core              ← 依赖 utils-basic
    │
    └── cloud-core            ← 依赖 app-core
```

- 单体应用只需引入 `app-core`
- 微服务应用需引入 `cloud-core`（自动引入 `app-core`）

---

## 各模块说明

### 1. dependencies - 版本管理中心

定义所有第三方依赖版本号和全局构建配置，不包含任何 Java 源代码。

**主要职责**:
- `<dependencyManagement>` 统一管理所有第三方库版本
- `<pluginManagement>` 定义全局编译、打包、资源配置
- `<profiles>` 实现多环境打包（dev / test / demo / prod）
- 配置 Maven 仓库（Nexus 私服）
- Git 提交信息生成（`git-commit-id-maven-plugin`）

**关键依赖版本**:

| 类别　　　　 | 库　　　　　　　　　 | 版本　　　　 |
| --------------| ----------------------| --------------|
| 框架　　　　 | Spring Boot　　　　　| 3.5.14　　　 |
| 框架　　　　 | Spring Cloud　　　　 | 2025.0.2　　 |
| 框架　　　　 | Spring Cloud Alibaba | 2025.0.0.0　 |
| 注册/配置　　| Nacos Client　　　　 | 2.4.3　　　　|
| 分布式事务　 | Seata　　　　　　　　| 2.6.0　　　　|
| 数据库连接池 | Druid　　　　　　　　| 1.2.28　　　 |
| ORM　　　　　| MyBatis Spring Boot　| 3.0.5　　　　|
| HTTP 客户端　| OkHttp　　　　　　　 | 4.12.0　　　 |
| API 文档　　 | SpringDoc　　　　　　| 2.8.17　　　 |
| 网络　　　　 | Netty　　　　　　　　| 4.2.14.Final |
| 加密　　　　 | Bouncy Castle　　　　| 1.84　　　　 |

---

### 2. uoquo-annotations - 注解与通用对象定义

定义项目中使用的自定义注解和通用 Bean 对象。

**包结构**:

| 包路径 | 关键类 | 功能 |
|--------|--------|------|
| `com.uoquo.web.common` | `BaseReturnCode` | 错误码抽象基类（00XXX 系统预留 / 01XXX 系统错误 / 02XXX 认证相关） |
| | `ReturnData<T>` | API 统一响应体（status / code / level / message / data） |
| | `ReturnLevel` | 消息级别枚举（INFO / WARN / ERROR） |
| | `SystemReturnCode` | 系统预定义错误码（SUCCESS / FAIL / EMPTY / ERROR 等） |
| | `BaseCacheKey` | 缓存 Key 抽象基类 |
| `com.uoquo.web.exception` | `AbstractBaseException` | 异常基类（含 code / mesg / level / traceId） |
| | `UoquoException` | 通用业务异常 |
| | `ParamEmtpyException` / `ParamErrorException` | 参数校验异常 |
| | `ParamSignEmptyException` / `ParamSignErrorException` | 签名校验异常 |
| | `TokenEmptyException` / `TokenInvalidException` | Token 认证异常 |
| | `AccountUnLoginException` / `AccountKickOutException` | 账户异常 |
| | `ForbiddenException` / `ResourceNotFoundException` | 权限和资源异常 |
| | `RemoteServiceException` | 远程服务调用异常 |
| | `TooManyRequestException` | 限流异常 |
| `com.uoquo.web.common.annotation` | `IgnoreAuth` | 免认证注解（可跳过 timestamp / params / login 等校验） |
| | `RequestParam` | 自定义请求参数注解 |
| `com.uoquo.web.common.param` | `IdParam` | ID 参数基类 |
| | `PageRequest` | 分页请求基类 |
| `com.uoquo.web.mybatis.page` | `PageList<T>` | 分页列表封装 |
| | `PageResult<T>` | 分页结果封装 |
| `com.uoquo.web.mybatis.sensitive` | `SensitiveData` / `SensitiveField` | 敏感数据脱敏注解 |
| `com.uoquo.web.mybatis.sharding` | `ITableShardStrategy` / `TableShardAnnotation` | 分表策略接口和注解 |
| `com.uoquo.utils` | `CurrentUser` | 当前用户上下文（ThreadLocal） |

---

### 3. utils-basic - 通用工具类库

提供一系列通用工具类，被上层模块依赖。

| 工具类 | 功能 |
|--------|------|
| `IDGenerator` | ULID 唯一 ID 生成器 |
| `FileUtil` | 文件操作 |
| `StringUtil` | 字符串操作 |
| `ThreadPoolUtil` | 线程池工具 |
| `DateUtil` | 日期工具（推荐 UTC 时间格式） |
| `CompressUtil` | 压缩/解压工具 |
| `BarcodeUtil` | 条形码/二维码生成 |
| `HttpUtil` | HTTP 请求工具（基于 OkHttp） |
| `MD5` | MD5 加密（字符串、加盐、文件） |
| `SHA` | SHA1 / SHA256 哈希 |
| `AES` | AES 对称加密/解密 |
| `RSA` | RSA 非对称加密（签名/验签/加解密） |
| `JsonUtil` | JSON 序列化/反序列化 |
| `Config` | 配置读取 |
| `SignParamUtil` | 参数签名工具 |
| `CurrentUser` | 当前用户上下文（ThreadLocal） |

---

### 4. app-core - 单体应用核心

为单体应用提供统一的基础能力，包括参数签名校验、统一异常处理、统一响应格式、MyBatis 增强、缓存体系等。

**主要能力**:

#### 4.1 请求签名校验体系

基于 MD5 签名机制，保证请求的合法性和防重放：

```
签名 = MD5(appid + token + language + nonce + deviceId + timestamp + 请求参数 + 请求体 + secret)
```

校验顺序：时间戳校验（5 分钟）→ 防重提交（5 分钟）→ 参数签名

通过 `@IgnoreAuth` 注解控制免签场景：
- `all`：完全跳过
- `timestamp`：跳过时间戳校验
- `params`：跳过参数签名
- `login`：跳过登录校验
- `inner`：内部调用跳过

#### 4.2 拦截器链

按优先级排序执行：

| 拦截器 | 优先级 | 功能 |
|--------|--------|------|
| `CurrentUserInterceptorAdapter` | -999 | 提取用户信息到 ThreadLocal |
| `GlobalInterceptor` | -990 | 请求日志、TraceId、慢请求告警 |
| `CheckParamInterceptor` | -900 | 签名校验 |
| `CheckLoginInterceptor` | -800 | 登录校验 |

#### 4.3 统一异常处理

`GlobalExceptionHandler` + `GlobalExceptionController` 双重保障，所有异常统一返回 `ReturnData`，HTTP 响应码始终为 200。

#### 4.4 MyBatis 增强

- **分页插件**：基于 jsqlparser 改写 SQL，支持 MySQL / Oracle 方言
- **敏感数据加解密**：`SensitiveParameterInterceptor`（入库加密）+ `SensitiveResultSetInterceptor`（出库解密）
- **SQL 耗时监控**：`SqlCostInterceptor` 记录慢 SQL
- **分表策略**：支持按月分表
- **自定义类型处理器**：日期、List↔JSON、Map↔JSON 互转

#### 4.5 缓存体系

提供 `CacheInterface` 接口，统一本地缓存和 Redis 远程缓存的操作，支持单个和批量读写。

#### 4.6 事件系统

`AppEvent` 提供应用内事件发布/订阅机制，支持业务操作类型、操作者等元数据追踪。

#### 4.7 包结构

```
com.uoquo.web
├── ServiceApplication.java          # 应用启动基类
├── ServiceAutoConfiguration.java    # 自动配置类
├── common/                          # 通用组件（参数解析器 / 属性编辑器）
├── condition/                       # 条件注解
├── config/                          # 配置（Redis / MyBatis / 任务调度 / Web）
├── controller/                      # 全局异常处理 / 版本号接口
├── events/                          # 应用内事件（AppEvent / UoquoEventPublisher）
├── filter/                          # 请求体缓存包装 / 日志 MDC 过滤器
├── interceptor/                     # 拦截器（签名 / 登录 / 用户信息）
├── mybatis/                         # MyBatis 增强（分页 / 脱敏 / 分表 / SQL 监控）
├── swagger/                         # Swagger 自动配置
└── utils/                           # Web 工具类
```

---

### 5. cloud-core - 微服务核心

在 `app-core` 基础上扩展微服务能力，通过 `@ConditionalOnMissingBean` 机制自动替换单体模式下的组件。

**主要能力**:

#### 5.1 微服务拦截器升级

| 替换前（app-core） | 替换后（cloud-core） | 说明 |
|---------------------|----------------------|------|
| `CurrentUserInterceptorAdapter` | `CurrentUser4GatewayInterceptor` | 从网关 Header 提取用户信息 |
| `CheckParamInterceptor` | `CheckParam4GatewayInterceptor` | 额外校验网关签名 |

网关签名校验：`SignParamUtil.sign(appSign + timestamp, globalSecret)`，防止客户端绕过网关直接访问微服务。

#### 5.2 Feign 远程调用增强

- **`FeignHeaderInterceptor`**：自动透传三层签名（app-sign / gateway-sign / feign-sign）、用户信息、TraceId、Seata XID
- **`FeignDecoder`**：自定义响应解码器，优先解析 `ReturnData` 格式，支持流式响应
- **`FeignErrorDecoder`**：统一处理远程异常
- 自定义消息转换器：Boolean / Date / Number / MapForm

#### 5.3 分布式缓存

基于 EHCache（本地）+ Redis（远程）双层架构，版本号机制同步，本地缓存默认 30 秒与 Redis 同步一次。

#### 5.4 远程事件系统

基于 Spring Cloud Bus 实现跨服务的事件发布/订阅，支持丰富的业务元数据（businessType / operationType / operatorId / oldData / newData），自定义反序列化器支持泛型数据类型解析。

#### 5.5 IP Hash 负载均衡

基于客户端 IP 的 Hash 取模，将同一 IP 的请求始终路由到同一服务实例。无法获取 IP 时降级为随机模式。

#### 5.6 分布式事务

集成 Seata，通过 Feign Header 自动透传 XID。

#### 5.7 Kafka 集成

全局处理 Kafka 消息反序列化失败，记录日志后返回哨兵对象 `DeserializationFailureData`。

#### 5.8 包结构

```
com.uoquo.cloud
├── CloudAutoConfiguration.java      # 微服务自动配置
├── CloudConfig.java                 # 微服务配置（拦截器注入）
├── config/                          # Kafka / 远程事件反序列化配置
├── events/                          # 远程事件（RemoteEvent / 反序列化器）
├── feign/                           # Feign 增强（编解码 / 请求头 / 消息转换器）
├── interceptor/                     # 网关模式拦截器（签名 / 用户）
├── kafka/                           # Kafka 反序列化失败处理
└── loadbalancer/                    # IP Hash 负载均衡
```

---

## 快速开始

### 单体应用

```xml
<parent>
    <groupId>com.uoquo</groupId>
    <artifactId>dependencies</artifactId>
    <version>1.0.6</version>
</parent>

<dependency>
    <groupId>com.uoquo</groupId>
    <artifactId>app-core</artifactId>
</dependency>
```

### 微服务应用

```xml
<parent>
    <groupId>com.uoquo</groupId>
    <artifactId>dependencies</artifactId>
    <version>1.0.6</version>
</parent>

<dependency>
    <groupId>com.uoquo</groupId>
    <artifactId>cloud-core</artifactId>
</dependency>
```

---

## 构建

```bash
mvn clean install
```
