# cloud-core

微服务核心模块，在 `app-core` 基础上扩展微服务能力。通过 Spring Boot 自动配置和 `@ConditionalOnMissingBean` 机制，自动替换单体模式下的组件为微服务版本。

依赖 `app-core`（自动传递 `utils-basic` 和 `uoquo-annotations`）。

---

## 包结构

```
com.uoquo.cloud
├── CloudAutoConfiguration.java          # 微服务自动配置入口
├── CloudConfig.java                     # 拦截器注入配置
│
├── config/
│   ├── KafkaConfig.java                             # Kafka 消费者配置
│   └── RemoteEventDeserializerAutoConfiguration.java # 远程事件反序列化器自动注册
│
├── events/
│   ├── RemoteEvent.java                 # 远程事件定义
│   ├── RemoteEventListenerAdapter.java  # 远程事件监听适配器
│   ├── RemoteEventListenerFactory.java  # 远程事件监听工厂
│   ├── BusErrorChannelHandler.java      # Bus 错误通道处理
│   ├── UoquoOriginFactory.java          # 事件来源工厂
│   └── deserializer/
│       ├── RemoteEventDeserializer.java       # 自定义反序列化器
│       ├── RemoteEventPackageScanner.java     # 包扫描器
│       ├── DataTypeResolver.java              # 泛型数据类型解析
│       └── BuiltinTypeRegistry.java           # 内置类型注册表
│
├── feign/
│   ├── FeignHeaderInterceptor.java      # 请求头透传拦截器
│   ├── FeignDecoder.java                # 响应解码器
│   ├── FeignEncoder.java                # 请求编码器
│   ├── FeignErrorDecoder.java           # 错误解码器
│   ├── config/
│   │   └── FeignConfiguration.java      # Feign 全局配置
│   ├── converter/                       # HTTP 消息转换器
│   │   ├── BooleanHttpMessageConverter.java
│   │   ├── DateHttpMessageConverter.java
│   │   ├── NumberHttpMessageConverter.java
│   │   └── MapFormHttpMessageConverter.java
│   └── processor/
│       └── DateFormatter.java           # 日期格式化处理器
│
├── interceptor/
│   ├── CurrentUser4GatewayInterceptor.java   # 网关模式用户信息提取
│   └── CheckParam4GatewayInterceptor.java    # 网关模式签名校验
│
├── kafka/
│   ├── DeserializationFailureHandler.java    # 反序列化失败处理
│   └── DeserializationFailureData.java       # 失败数据哨兵对象
│
└── loadbalancer/
    ├── IphashLoadBalancer.java                    # IP Hash 负载均衡器
    ├── IphashLoadBalancerClient.java              # 负载均衡客户端
    ├── IphashLoadBalancerClientAutoConfiguration.java  # 自动配置
    └── IphashLoadBalancerConfiguration.java       # 配置类
```

---

## 核心能力详解

### 1. 微服务拦截器升级

`cloud-core` 通过 `@ConditionalOnMissingBean` 自动替换 `app-core` 中的拦截器为网关感知版本：

| 单体模式（app-core） | 微服务模式（cloud-core） | 变化说明 |
|----------------------|--------------------------|---------|
| `CurrentUserInterceptorAdapter` | `CurrentUser4GatewayInterceptor` | 从网关转发的 Header 中提取用户信息，而非直接解析 Token |
| `CheckParamInterceptor` | `CheckParam4GatewayInterceptor` | 增加网关签名校验层 |

#### 网关签名校验

微服务额外校验一个 `signature-gateway` 字段，确保请求经过了网关：

```
gateway签名 = SignParamUtil.sign(appSign + timestamp, globalSecret)
```

如果客户端绕过网关直接访问微服务，网关签名校验将失败，请求被拒绝。

---

### 2. Feign 远程调用增强

#### FeignHeaderInterceptor — 请求头透传

每次 Feign 调用自动携带：

| 透传内容 | 说明 |
|---------|------|
| 用户信息 | appid / token / userId 等 |
| 三层签名 | app-sign / gateway-sign / feign-sign |
| 追踪信息 | TraceId（MDC） |
| 分布式事务 | Seata XID |
| 时间戳 | 当前服务器时间 |

Feign 签名计算：
```
feign签名 = SignParamUtil.sign(appSign + gatewaySign + timestamp, globalSecret)
```

#### FeignDecoder — 响应解码

1. 检查 HTTP 状态码
2. 如果响应是 `ReturnData` 格式，解包提取 `data` 字段
3. 如果 `status != 00000`，抛出 `RemoteServiceException`
4. 支持流式响应（InputStream）直接透传

#### FeignErrorDecoder — 错误处理

远程服务返回非 200 时，统一包装为 `RemoteServiceException`。

#### 消息转换器

为 Feign 提供自定义类型转换：

| 转换器 | 处理类型 |
|--------|---------|
| `BooleanHttpMessageConverter` | Boolean / boolean |
| `DateHttpMessageConverter` | Date（多格式兼容） |
| `NumberHttpMessageConverter` | Integer / Long / Double 等 |
| `MapFormHttpMessageConverter` | Map → Form 表单 |

---

### 3. 分布式事件系统

基于 Spring Cloud Bus + Kafka 实现跨服务事件广播。

#### 发布事件

```java
@Autowired
UoquoEventPublisher eventPublisher;

RemoteEvent event = new RemoteEvent();
event.setBusinessType("ORDER");
event.setOperationType("PAID");
event.setOperatorId("user-001");
event.setOldData(oldOrder);
event.setNewData(newOrder);
eventPublisher.publishRemote(event);
```

#### 监听事件

```java
@RemoteEventListener(businessType = "ORDER")
public void onOrderPaid(RemoteEvent event) {
    // 所有订阅了该 businessType 的服务实例都会收到
}
```

#### 事件反序列化

`RemoteEventDeserializer` 支持泛型数据类型的自动解析：
- 扫描指定包下的类型注册
- 内置类型（String / Integer / Map 等）自动识别
- 自定义 `DataTypeResolver` 解析复杂泛型

---

### 4. IP Hash 负载均衡

基于客户端真实 IP 的 Hash 取模，保证同一 IP 的请求始终路由到同一服务实例：

```
实例索引 = hash(clientIP) % 实例数量
```

适用场景：
- 有状态的 WebSocket 连接
- 本地缓存命中率优化
- 会话亲和性需求

降级策略：无法获取客户端 IP 时，降级为随机选择。

#### 配置

```yaml
# 启用 IP Hash 负载均衡
spring:
  cloud:
    loadbalancer:
      configurations: iphash
```

---

### 5. 分布式事务（Seata）

通过 Feign Header 自动透传 Seata XID，实现跨服务的分布式事务一致性：

```java
@GlobalTransactional
public void createOrder(OrderDTO dto) {
    orderService.save(dto);              // 本地事务
    inventoryClient.deduct(dto.getSku()); // 远程调用（XID 自动透传）
    paymentClient.charge(dto.getAmount()); // 远程调用（XID 自动透传）
}
```

无需额外配置，`FeignHeaderInterceptor` 自动检测 Seata 上下文并透传。

---

### 6. Kafka 集成

#### 反序列化失败处理

`DeserializationFailureHandler` 全局拦截 Kafka 消息反序列化异常：

- 记录异常日志（含 topic、partition、offset）
- 返回哨兵对象 `DeserializationFailureData`
- 消费者可通过类型判断跳过无效消息

```java
@KafkaListener(topics = "my-topic")
public void consume(Object message) {
    if (message instanceof DeserializationFailureData) {
        // 跳过无法反序列化的消息
        return;
    }
    // 正常处理
}
```

---

## 注意事项

### 1. bootstrap.yml 加载

Spring Cloud 2025.0.x 默认不加载 `bootstrap.yml`，需显式引入：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

### 2. 自动配置生效条件

`CloudAutoConfiguration` 需要在应用启动类上扫描到：

```java
@SpringBootApplication(scanBasePackages = {"com.uoquo", "your.package"})
```

### 3. Feign 配置

推荐在 `@FeignClient` 中指定 configuration：

```java
@FeignClient(name = "user-service", configuration = FeignConfiguration.class)
public interface UserClient {
    // ...
}
```
