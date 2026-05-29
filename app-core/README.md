# app-core

单体应用核心模块，提供统一的请求处理链路：过滤器 → 拦截器 → 参数签名校验 → 统一异常处理 → 统一响应格式。同时集成 MyBatis 增强、Redis 缓存、事件系统等基础能力。

依赖 `utils-basic`（自动传递 `uoquo-annotations`）。

---

## 包结构

```
com.uoquo.web
├── ServiceApplication.java              # 应用启动基类
├── ServiceAutoConfiguration.java        # 自动配置（注入各组件）
├── ServiceConfig.java                   # 基础配置（OkHttp 连接池等）
│
├── config/
│   ├── EventConfig.java                 # 事件系统配置
│   ├── MyBatisPluginConfig.java         # MyBatis 插件注册
│   ├── RedisConfig.java                 # Redis 连接配置
│   ├── RedisConfigProperties.java       # Redis 配置属性
│   ├── TaskSchedulerConfig.java         # 任务调度配置
│   ├── WebFilterConfig.java             # 过滤器注册
│   └── WebHttpConfig.java               # MVC 配置（拦截器、消息转换器）
│
├── controller/
│   ├── AppVersionController.java        # 应用版本号接口
│   ├── GlobalExceptionController.java   # 404 等未捕获异常处理
│   ├── GlobalExceptionHandler.java      # @ControllerAdvice 异常处理
│   └── GlobalExceptionResolver.java     # HandlerExceptionResolver 实现
│
├── events/
│   ├── AppEvent.java                    # 应用事件（含业务元数据）
│   ├── AppEventListenerAdapter.java     # 事件监听适配器（异步执行）
│   ├── AppEventListenerFactory.java     # 事件监听工厂
│   ├── UoquoEvent.java                  # 基础事件定义
│   ├── UoquoEventListenerFactory.java   # 事件工厂
│   └── UoquoEventPublisher.java         # 事件发布器
│
├── filter/
│   ├── ContentCachingWrapperFilter.java        # 请求体缓存包装
│   ├── LogbackFilter.java                      # 日志 MDC（请求 TraceId）
│   └── RepeatedlyHttpServletRequestWrapper.java # 可重复读取请求体
│
├── interceptor/
│   ├── CurrentUserInterceptorAdapter.java  # 用户信息提取（最高优先级）
│   ├── CurrentUser4TokenInterceptor.java   # Token 模式用户信息
│   ├── CurrentUser4SessionInterceptor.java # Session 模式用户信息
│   ├── GlobalInterceptor.java              # 全局拦截（日志、参数解析）
│   ├── CheckParamInterceptor.java          # 参数签名校验
│   └── CheckLoginInterceptor.java          # 登录状态校验
│
├── mybatis/
│   ├── handler/                         # 类型处理器
│   │   ├── DateTypeHandler.java         # 日期类型
│   │   ├── List2JsonTypeHandler.java    # List ↔ JSON
│   │   └── Map2JsonTypeHandler.java     # Map ↔ JSON
│   ├── interceptor/                     # MyBatis 拦截器
│   │   ├── PageInterceptor.java         # 分页拦截器
│   │   ├── DataPolicyInterceptor.java   # 数据权限拦截器
│   │   ├── SensitiveParameterInterceptor.java   # 入库加密
│   │   ├── SensitiveResultSetInterceptor.java   # 出库解密
│   │   └── SqlCostInterceptor.java      # SQL 耗时监控
│   ├── page/                            # 分页实现
│   │   ├── PageHelper.java              # 分页入口
│   │   ├── Dialect.java                 # 方言接口
│   │   ├── MSUtils.java                 # MappedStatement 工具
│   │   └── dialect/                     # 方言实现
│   │       ├── MySQLDialect.java
│   │       └── OracleDialect.java
│   ├── sensitive/                       # 敏感数据处理
│   │   ├── SensitiveUtil.java           # 加解密工具
│   │   └── MapperMethodResolver.java    # Mapper 方法解析（带缓存）
│   ├── sharding/                        # 分表
│   │   ├── TableShardInterceptor.java   # 分表拦截器
│   │   └── MonthTableShardStrategy.java # 按月分表策略
│   └── sqlparser/                       # SQL 解析
│       ├── SqlDeParser.java             # SQL 反解析器
│       └── TableAliasDeParser.java      # 表别名解析
│
└── utils/
    └── WebUtil.java                     # Web 工具（签名计算、IP 获取等）
```

---

## 请求处理链路

```
HTTP 请求
    │
    ▼
┌─────────────────────────────────────┐
│ Filter 层                            │
│  LogbackFilter → 生成 TraceId        │
│  ContentCachingWrapperFilter → 缓存请求体 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│ Interceptor 层（按优先级顺序）        │
│  1. CurrentUserInterceptorAdapter    │
│     → 提取用户信息到 ThreadLocal      │
│  2. GlobalInterceptor                │
│     → 请求日志、TraceId、参数解析     │
│  3. CheckParamInterceptor            │
│     → 时间戳 → 防重 → 签名校验       │
│  4. CheckLoginInterceptor            │
│     → Token 有效性校验               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│ Controller 层                        │
│  业务逻辑处理                        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│ Exception Handler                    │
│  GlobalExceptionHandler              │
│  → 所有异常统一返回 ReturnData       │
│  → HTTP 状态码始终 200               │
└─────────────────────────────────────┘
```

---

## 请求签名校验

### 签名计算规则

```
签名 = MD5(appid + token + language + nonce + deviceId + timestamp + 请求参数 + 请求体 + secret)
```

### 请求头参数

| 参数名 | 说明 | 必填 |
|--------|------|:----:|
| `appid` | 应用 ID | 是 |
| `token` | 授权令牌 | 是 |
| `nonce` | 请求随机数（防重放） | 是 |
| `timestamp` | 请求时间戳（毫秒） | 是 |
| `device-id` | 设备唯一标识 | 是 |
| `user-language` | 客户端语言 | 是 |
| `signature-app` | 请求签名值 | 是 |

### 请求参数拼接规则

1. 取 `request.getParameterMap()` 所有参数
2. 按 key 字典序排列（TreeMap）
3. 逐项拼接：`key1 + value1 + key2 + value2 + ...`
4. 内置参数（appid / token / nonce 等）已单独参与，遍历时跳过

### 文件上传签名

- 文件内容不参与签名
- 必须传入文件原始名称为 key，文件 MD5 为 value

### 校验流程

```
1. OPTIONS 请求 → 直接放行
2. @IgnoreAuth(all=true) → 跳过所有校验
3. 全局免签路径（Redis 配置） → 跳过
4. FEIGN 内部调用 → 跳过

依次执行：
├── 时间戳校验：请求时间与服务器相差超过 5 分钟则拒绝
├── 防重提交：appid + nonce 为键，5 分钟内不可重复
└── 参数签名：重新计算签名与 signature-app 比对
```

---

## MyBatis 增强

### 分页

基于 jsqlparser 改写 SQL，对业务代码零侵入：

```java
// Service 中使用
PageHelper.startPage(pageNum, pageSize);
PageList<User> users = userMapper.selectByCondition(params);
return PageResult.of(users);

// 模糊分页（不执行 COUNT 查询，性能更优）
PageHelper.startPage(pageNum, pageSize, false);
```

支持方言：MySQL、Oracle

### 敏感数据加解密

在实体类和 Mapper 方法上使用注解，MyBatis 拦截器自动完成加解密：

```java
// 实体类标注
@SensitiveData
public class Patient {
    @SensitiveField
    private String idCard;
    @SensitiveField
    private String phone;
}

// Mapper 方法标注（用于返回 String 时）
@SensitiveData
String selectPhone(@Param("id") String id);
```

加密时机：`SensitiveParameterInterceptor`（Executor.update / Executor.query 前）
解密时机：`SensitiveResultSetInterceptor`（ResultSetHandler.handleResultSets 后）

### SQL 耗时监控

`SqlCostInterceptor` 记录每条 SQL 的执行耗时，超过阈值时输出 WARN 日志。

### 分表

```java
@TableShardAnnotation(
    tableName = "t_log",
    shardStrategy = MonthTableShardStrategy.class,
    lastMonth = false
)
List<Log> selectLogs(@Param("userId") String userId);
```

`MonthTableShardStrategy` 按当前月份生成表名后缀，如 `t_log_202401`。

---

## 事件系统

### 发布事件

```java
@Autowired
UoquoEventPublisher eventPublisher;

AppEvent event = new AppEvent();
event.setBusinessType("USER");
event.setOperationType("CREATE");
event.setOperatorId(CurrentUser.getUserId());
event.setData(user);
eventPublisher.publish(event);
```

### 监听事件

```java
@UoquoEvent
public void onUserCreated(AppEvent event) {
    // 异步执行
}
```

---

## 统一异常处理

三层保障机制：

1. **`GlobalExceptionHandler`**（@ControllerAdvice）— 捕获 Controller 层异常
2. **`GlobalExceptionController`**（ErrorController）— 捕获 404 等 Servlet 异常
3. **`GlobalExceptionResolver`**（HandlerExceptionResolver）— 兜底处理

所有异常统一包装为 `ReturnData`，生产环境只返回 traceId 不返回堆栈。

---

## 配置项

### Redis

```yaml
app:
  redis:
    enabled: true           # 是否启用 Redis
    database: 0
    host: localhost
    port: 6379
    password: xxx
    timeout: 3000
```

### MyBatis 插件

```yaml
app:
  mybatis:
    page: true              # 分页插件
    sensitive: true         # 敏感数据加解密
    sqlcost: true           # SQL 耗时监控
    sharding: true          # 分表插件
```

### 任务调度

```yaml
app:
  task:
    pool:
      max-size: 10          # 线程池最大线程数
```
