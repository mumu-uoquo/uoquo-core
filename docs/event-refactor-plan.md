# 事件机制改造：需求、方案与实施结果

范围：`uoquo-core` 的 `app-core`、`cloud-core`。api-micro、uoquo-demo 不改。
状态：**第一至第四批已实施完成**（见下方“实施结果”）；以下第 1-8 节为实施前确认的需求与方案，保留作为设计依据。

---

## 0. 实施结果

### 0.1 已完成内容

| 批次 | 内容 | 主要位置 |
|---|---|---|
| 第一批 | B1 扫描去重；B2/B3 `operationTime`、`traceId` 修复（抽出 `EventEnricher`）；B4/B5 `dataType` 规范化与监听器数据类型校验（`EventSupport`）；B6 MDC 不再出现 `null`；B7 `@ApplicationEventScan` 别名与 `basePackageClasses`；B8 事件构造时校验必填项；B9 `RemoteEvent.setDestination` 标注 `@Deprecated` | app-core `events/*`、`deserializer/*`；cloud-core `RemoteEvent` |
| 第二批 | 新增扩展点 `EventScanPackagesProvider`；app-core 扫描器合并 `@ApplicationEventScan` 与各 Provider（不实例化 Bean）；cloud-core 删除自己的 `eventPackageScanner`，新增 `RemoteEventScanAutoConfiguration` 提供 `@RemoteApplicationEventScan` 的包（与 Kafka 开关无关） | `EventDeserializerAutoConfiguration`、`RemoteEventScanAutoConfiguration` |
| 第三批 | `UoquoServiceMatcher` 服务级“来自自己”判断 + 静态 `BeanPostProcessor` 包装；`KafkaConfig` 只保留 `KafkaProperties`；发布器统一为 app-core 的实现，微服务事件经 `publishEvent` 由 Bus 的 `RemoteApplicationEventListener` 发出；`RemoteEventListenerFactory` 无条件注册（`CloudConfig`） | cloud-core `UoquoServiceMatcher`、`ServiceMatcherAutoConfiguration`、`CloudConfig` |
| 第四批 | `DefaultUoquoEventPublisher`（`publishEvent`=事务完成后投递；新增 `publishAfterCommit`=仅提交成功后投递；去掉 200ms 延迟）；`EventExecutor` 事件专用线程池；监听适配器抽取基类 `UoquoEventListenerAdapter`（泛型过滤、统一异步、MDC、`CurrentUser` 填充与恢复、异常隔离）；日志脱敏（`EventSupport.maskToken/toLogString`、`BusErrorChannelHandler`）；`RemoteEventDeserializerAutoConfiguration` 的后处理器改 `static` 并在找不到 Bus 的 `mapper` 字段时启动失败 | app-core `DefaultUoquoEventPublisher`、`EventExecutor`、`EventContext`、`UoquoEventListenerAdapter` |

### 0.1.1 后续追加（第五批）

| 内容 | 说明 | 主要位置 |
|---|---|---|
| 合并 `AppEvent` 与 `RemoteEvent` 的重复代码 | 两个类的父类不同，无法共用基类，改为组合：公共信息集中在 `EventHeader`，`HeaderBackedEvent` 接口用默认方法把 `UoquoEvent` 的全部 getter/setter 委托给它。两个事件类只保留与泛型相关的 `dataType`、旧数据、新数据以及各自的构造和路由逻辑，`AppEvent` / `RemoteEvent` 由各约 540 行降到 155 / 183 行，公共部分只在 `EventHeader`（338 行）和 `HeaderBackedEvent`（289 行）中维护一份（总行数只减少约 10%，收益主要是字段与逻辑不再需要在两处同步修改）。JSON 格式不变（属性仍是扁平的，不出现 `header`）。**兼容性**：原来的 `protected` 公共字段（如 `businessId`、`operatorId`）已移除，子类需要改用 getter/setter；本仓库及 api-micro、uoquo-demo 中没有子类直接访问这些字段 | app-core `EventHeader`、`HeaderBackedEvent`、`AppEvent`；cloud-core `RemoteEvent` |
| 扫描索引启动预热 | `EventPackageScanner` 在所有单例创建完成后构建索引并输出类数量与耗时；预热失败只记录日志，首次使用时仍会兜底扫描；多线程首次使用时只扫描一次。可通过 `app.event.scanner.warm-up=false` 关闭 | `EventPackageScanner` |
| 去掉 `RemoteEventDeserializer`/`AppEventDeserializer` 的重复序列化 | 以前先把 JSON 树 `toString()` 再解析一遍，现在直接用 JSON 树反序列化（新增 `JsonUtil.deserialize(JsonNode, ...)`）。结果与原来完全一致 | `JsonUtil`、两个 Deserializer |
| 监听适配器不再干扰 Spring 自身的事件与无参监听方法 | 来自 uoquo-demo 的运行日志：① 适配器接管了应用内所有 `@EventListener` 方法，无参方法（如 springdoc 的 `@EventListener(ApplicationReadyEvent.class) init()`）因为“事件为空”被跳过，永远不会执行（**改造前就存在**）；② DEBUG 下为 `ApplicationReadyEvent` 打日志时会序列化整个应用上下文，每个监听器耗时两分钟以上，启动后约 5 分钟才触发就绪事件（改造前同样存在）。现在：无参监听方法与 `org.springframework.*` 包下的框架事件按 Spring 原生行为同步执行，也不再为它们序列化日志；业务自定义事件（含继承 `ApplicationEvent` 的）行为不变，仍然异步 | `UoquoEventListenerAdapter`、`FrameworkEventListenerTest` |
| 事件ID改名为 `eventId`，删除 `destination` | ① **eventId**：`UoquoEvent` 的 `getId()/setId()` 改名为 `getEventId()/setEventId()`，JSON 属性 `id` 改为 `eventId`，含义是“自定义事件消息的唯一标识”（api-micro 用它作为 `biz_event_record.id`、`log_user_login.id` 入库），仍由发布器在每次发布时生成 ULID 并覆盖，发布前为空。改名后与父类 `RemoteApplicationEvent` 的 `id`（Bus 自己使用的随机 ID，用于 Ack、追踪、equals）不再重名，`RemoteEvent` 不需要再用反射去改父类的 final 字段；② **destination**：从 `UoquoEvent`、`EventHeader`、`HeaderBackedEvent` 中删除。`RemoteEvent` 的路由只由父类的 `destinationService` 决定（构造函数的 `destinationService` 参数不变），`AppEvent` 是本地事件没有路由，其 5 参构造函数（多出的 `destinationService` 参数）一并删除。③ **兼容读取**：旧版 JSON 中的 `destination`（`AppEvent`、`RemoteEvent`）与 `id`（`AppEvent`）会被忽略（`@JsonIgnoreProperties`），历史记录重发不受影响；`RemoteEvent` 旧 JSON 的 `id` 会写入 Bus 的 `id`，不再当作事件ID（重发时发布器会生成新的 `eventId`）。④ `RemoteEvent.getId()` 标记为 `@Deprecated`（返回 Bus 的 ID，不是事件ID），避免业务误用 | `UoquoEvent`、`EventHeader`、`HeaderBackedEvent`、`AppEvent`、`RemoteEvent`、`RemoteEventHeaderTest` |
| 公共信息分组、token 脱敏规则 | ① `UoquoEvent`、`EventHeader`、`HeaderBackedEvent` 的字段与方法按含义分为八组并加了分组注释，三个类的顺序一致：事件标识（`eventId`、`retry`）、会话与请求（`token`、`traceId`）、事件四维度（业务类型、业务子类型、操作类型、操作结果、业务状态）、业务数据、操作人信息（含操作时间）、操作内容（内容、扩展信息）、操作端信息、备注。只是调整位置与注释，没有改变含义；② token 脱敏规则由“首尾各 4 位”改为“完整显示前 20 位，其余隐藏”（`EventSupport.TOKEN_VISIBLE_LENGTH`）。api-micro 的 `UserUtils.formatToken` 同步由前 16 位改为前 20 位（`IDGenerator.getNextULID()` 生成的会话标识部分） | `EventSupport`、`EventLogMaskTest`、api-micro `UserUtils` |
| DEBUG 日志放大的优化 | 来自 demo-app 运行日志：一个事件有约 10 个监听适配器，每个都会输出一条 `received event`（完整 JSON，约 900 字符）和一条 `process event begin`（完整 JSON）。现在：① `publish event`（发布时，每个事件一次）仍输出完整 JSON；② `received event`、`process event`（泛型匹配）降为 TRACE，且只输出一行摘要；③ `skip event`、`process event begin` 保持 DEBUG，只输出一行摘要（新增 `EventSupport.toSummary`：事件ID、业务类型、操作类型、业务ID、重发、数据类型，不含 token 和业务数据），完整内容改到 TRACE（`process event data`）；④ 微服务事件“不是发给本服务”的跳过日志降为 TRACE；⑤ 日志里的监听方法由完整签名改为“类名.方法名”。异常日志（`process event error`）仍输出完整内容（已脱敏 token）。每个事件在 DEBUG 下的 JSON 全文由约 20 份降为 1 份 | `UoquoEventListenerAdapter`、`EventSupport`、`RemoteEventListenerAdapter` |
| 微服务模式日志分析后的修正 | 来自 demo-service-user / demo-service-book 的运行日志（user 因 `KAFKA_ENABLED:false` 关闭了 Bus，book 开启）：① **没有 Bus 时定向事件在本地被处理**：`RemoteEventListenerAdapter.accepts()` 原来只在存在 `ServiceMatcher` 时才按目标服务过滤，Bus 关闭时发给其他服务的事件也会在发送方本地处理（又不会送达目标服务）。现在没有 `ServiceMatcher` 时，改用新增的 `RemoteEventDestination` 匹配 `destinationService`：规则与 Bus 一致（以 `:` 为分隔符的 Ant 匹配），本服务的标识取 `spring.cloud.bus.id`（配置了才有）和 `spring.application.name`，目标为空或 `**` 为广播；本服务的标识都取不到时无法判断，按发给本服务处理。旧的 3 参构造（不接入匹配器，如单元测试）仍不过滤；② **删除误导的 `group-id` 配置**：`KafkaConfig` 把 `spring.kafka.default.*` 绑定到 `KafkaProperties`，其中 `consumer.group-id` 会被 Stream 的 Kafka binder 忽略并输出 `Ignoring provided value(s) for 'group.id'` 的 WARN，Bus 实际的消费组由 `spring.cloud.stream.bindings.springCloudBusInput.group`（`bus-group`）决定。已从 demo-service-user、demo-service-book 的 `application.yml` 删除 `consumer.group-id`（`client-id` 保留）；使用方自己的 `application.yml` 如有同样的配置也可删除；③ **Bus 输出通道提前到启动阶段创建**：`StreamBridge` 的输出绑定默认在第一次发送时才创建（Kafka AdminClient、主题检查、Producer，约 200ms，发生在发布事件的线程上），Kafka 不可用要到第一次发布事件才暴露。新增 `BusOutputBindingPostProcessor`（`BeanFactoryPostProcessor`，仅在启用 Bus 时注册），把 `springCloudBusOutput` 追加到 `spring.cloud.stream.output-bindings`（已有的值保留、不重复追加），由 Stream 在启动时创建该绑定。副作用：Kafka 连不上时，输出绑定与输入绑定一样在启动阶段失败，而不是等到第一次发布事件 | cloud-core `RemoteEventDestination`、`RemoteEventListenerAdapter`、`BusOutputBindingPostProcessor`、`RemoteEventDeserializerAutoConfiguration`、`ServiceMatcherAndLoopbackTest`、`BusBindingAndDestinationTest`；demo-service-user/book `application.yml` |

### 0.2 新增配置项（均有默认值）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `app.event.pool.core-size` | 4 | 事件线程池核心线程数 |
| `app.event.pool.max-size` | 16 | 最大线程数（队列满后才扩展） |
| `app.event.pool.queue-capacity` | 1000 | 等待队列长度 |
| `app.event.pool.keep-alive-seconds` | 60 | 非核心线程空闲存活时间 |
| `app.event.scanner.warm-up` | true | 启动完成后是否预热事件类扫描索引 |

线程池与队列都满时，由提交任务的线程直接执行（不丢事件），并输出 WARN 日志。

### 0.3 对使用方的影响

- **业务代码不需要修改**：继续 `eventPublisher.publishEvent(event)`，在方法返回前调用即可。需要“事务提交成功才发布”时改用 `publishAfterCommit(event)`。
- `UoquoEventPublisher` 新增方法 `publishAfterCommit`；它有默认实现（退化为 `publishEvent`），已有的自定义实现不会编译失败。
- `new AppEvent<>(...)` / `new RemoteEvent<>(...)` 的业务类型、操作类型为空时，构造时即抛出 `IllegalArgumentException`（原先在发布时才抛出）。
- 发送方服务自己的监听器通过本地分发收到自己发的事件（不再经过 Kafka 往返）；同名服务从总线收到的同名服务事件会被丢弃。
- 监听器统一异步执行（原 cloud-core 为同步）；同一事件的多个监听器不保证执行顺序。
- 监听器执行时 `CurrentUser` 由事件中的 token、traceId、操作端、操作人信息填充，执行完毕后恢复。
- 日志中事件的 `token` 已脱敏：完整显示前 20 位（`EventSupport.TOKEN_VISIBLE_LENGTH`），其余用 `***` 代替；不足 20 位的没有可隐藏的部分，原样显示。20 位与 api-micro 存储“短 token”（`UserUtils.formatToken`，由 `IDGenerator.getNextULID()` 生成的会话标识部分）的长度一致。
- `@ApplicationEventScan` 的 `basePackages`、`basePackageClasses` 现在生效，且与 `value` 互为别名。

### 0.4 验证情况

测试通过自建的 JUnit 运行器执行（父 pom `dependencies` 中 surefire 被固定为 `skip=true`，无法通过命令行参数开启，因此没有修改父 pom）。

| 范围 | 结果 |
|---|---|
| app-core 全部测试（`com.uoquo`，含新增 9 个测试类） | 109 个中 108 个通过；1 个失败为**改造前就存在**的 `AppEventDeserializerTest$EdgeCases.shouldDeserializeOperationTime`（`+00:00` 时区格式解析失败，与本次改造无关） |
| cloud-core 全部测试（含新增 3 个测试类） | 41 个全部通过 |

新增的集成测试（`ServiceMatcherAndLoopbackTest`）验证了：发布时本地监听器处理 1 次、发往总线 1 次；自己发出的事件经总线回来时被丢弃；其他服务的事件正常处理且不会被转发回总线；**定向发给其他服务的事件，发送方本地的监听器不会处理**。

最后一项来自 uoquo-demo 场景设计时发现的缺陷：本地分发会让定向给其他服务的 `RemoteEvent` 在发送方本地也被监听器处理。修复方式是在 `UoquoEventListenerAdapter` 增加 `accepts()` 钩子，`RemoteEventListenerAdapter` 用 `ServiceMatcher.isForSelf` 判断，`RemoteEventListenerFactory` 因此新增 `ObjectProvider<ServiceMatcher>` 构造参数（`CloudConfig` 已注入）。

**未覆盖的验证**：没有连接真实的 Kafka 做端到端验证；没有在 api-micro、uoquo-demo 上联调。建议在测试环境验证：多实例下同一服务只处理一次、事务回滚场景、Bus 版本升级后的启动。

其中“Bus 输出通道在启动阶段创建”（0.1.1 最后一行③）依据 Spring Cloud Stream 4.3.2 的源码实现（`FunctionBindingRegistrar` 会为 `spring.cloud.stream.output-bindings` 中没有对应函数的名称注册绑定），单元测试只覆盖配置项的合并，没有启动真实应用验证；启动 demo-service-user / demo-service-book 后，应在 `catalina` 日志里看到 `springCloudBusOutput has 1 subscriber(s)` 和 `Using kafka topic for outbound: SpringCloudBusTopic` 出现在启动阶段（`main` 线程），而不是第一次发布事件时的 `event-pool` 线程。

### 0.5 本次不做、待验证后再评估

- 用 `ResolvableTypeProvider` 替换自定义监听工厂。
- `setDataType(Class<T>)` / `setDataType(String)` 两个重载依赖 Jackson 自行决定使用哪一个：在 String 版本上加 `@JsonProperty("dataType")` 明确指定（不改 API）。
- 监听工厂只识别直接标注的 `@EventListener`，不识别组合注解：改用 `AnnotatedElementUtils` 并排除 `@TransactionalEventListener`（它自带的工厂需要继续接管）。仓库内未发现使用组合注解的地方，有需要再做。
- 事件中 `BigDecimal` 等小数在总线反序列化时先被解析成 double 再还原（`readTree` 默认行为，改造前就存在）：`12.10` 会变成 `12.1`，超过 double 精度的金额会丢精度。启用 `USE_BIG_DECIMAL_FOR_FLOATS` 可以解决，但 `Map` 降级数据中的小数会由 `Double` 变为 `BigDecimal`，需要评估对监听器的影响。
- 把原事件 `eventId` 记录进 `extension`（便于回放追溯）。
- `BusErrorChannelHandler` 的日志措辞（对非反序列化错误也输出 "unresolvable message"）：现有测试断言了该文案，本次只做了 token 脱敏。
- 事件发布后 `newData`/`oldData` 在异步线程中序列化，业务线程如继续修改这些对象会被一并发出；约定“发布后不再修改”，必要时再评估提前序列化或深拷贝。
- 同名服务内的重复消费：消费组配置为每服务一个组，如需调整需结合 Kafka 配置评估。

### 0.6 api-micro 迁移点

> **已完成**：以下内容已在 api-micro 中同步修改（`scheduler-center` 的 `AuthEventListener`、`MessageInfoEventListener`、`AllEventListener`、测试 `JsonTest`，`service-platform` 的 `UserUtils`），并用新版 core 的编译结果对 `scheduler-center`、`service-platform`、`api-gateway` 三个模块做了 javac 编译验证（均通过）。api-micro 本身升级依赖版本后仍需要正常构建和联调。

- `getOperationStatus()` 改为 `getOperationResult()`（只改事件对象的调用；`BizEventRecordParam`、`BizEventRecord` 等入库对象的 `operationStatus` 字段与数据库列 `operation_status` 保持不变）。
- **`UserUtils.formatToken`**：由取 token 前 16 位改为前 20 位（`generateToken()` 中会话标识部分由 `IDGenerator.getNextULID()` 生成，长度 20），与事件日志脱敏保留的长度一致。`biz_event_record.token`、`log_user_login.token` 等列是 `varchar(32)`，可以容纳。历史数据中已按 16 位存储的 token 与新的 20 位前缀不一致，只影响日志类记录（`LogsAuthServiceImpl`、`SseEmitterService`、`OnlineUserServiceImpl` 按短 token 查找）：升级前的在线会话记录会按异常退出处理，历史登录记录不会被匹配。**这是预期效果，不涉及业务处理，也不做数据迁移**（实际业务 token 比 20 位长，日志只存前 20 位）。
- **`RemoteEvent` 4 参构造函数的含义变了（编译不会报错，行为会变）**：旧版（HEAD）是 `(businessType, operationType, operationStatus, destinationService)`，现在是 `(businessType, operationType, operationResult, businessStatus)`，定向服务名改为第 5 个参数。api-micro 中有两处按旧含义传了定向服务名，升级 core 时必须改成 5 参：`new RemoteEvent<>(type, op, result, null, "service-platform")`：
  - `scheduler-center` 的 `AuthEventListener`（第 96 行）
  - `scheduler-center` 的 `MessageInfoEventListener`（第 184 行）
  不改的话，事件会被当作广播发出，且 `businessStatus` 被写成 `"service-platform"`。其余 `new RemoteEvent<>(…)` 都是 3 参，不受影响。
- **事件ID改名为 `eventId`（`getId()` → `getEventId()`，`setId()` → `setEventId()`）**：
  - 通过 `UoquoEvent`/`AppEvent` 调用的地方升级后编译会报错，按提示改即可，如 `AllEventListener.buildLogBusinessOperationParam(UoquoEvent)` 中的 `event.getId()`（第 126、129 行）。
  - **通过 `RemoteEvent` 类型调用 `getId()` 的地方不会报编译错误，但返回的是 Bus 自己的随机 UUID（36 位）**，不是事件ID；用它入库会因超过 `varchar(32)` 而失败。必须全部改成 `getEventId()`：`AuthEventListener` 第 53、157 行，`MessageInfoEventListener` 第 61 行，`AllEventListener` 第 69、73、100 行（这些参数的类型是 `RemoteEvent`）。`RemoteEvent.getId()` 已标记 `@Deprecated`，编译时会有警告可供检查。
  - 已入库的历史事件内容（`biz_event_record.event_content`）里的 `id` 不会被当作事件ID读取（重发时发布器会生成新的 `eventId`），无需迁移数据。
- **`destination` 已删除**：api-micro 中没有使用 `getDestination()/setDestination()`；`new AppEvent<>(…, destinationService)` 的 5 参构造函数已删除（api-micro 中没有使用）。
- `BizEventRecordServiceImpl.retryEvent`：`type` 只匹配 `"RemoteEvent"` 与 `"AppEvent"`，自定义子类事件会落入 `else` 分支，把 Map 当事件发布。
- 升级 core 后，scheduler-center 的 `AllEventListener`（记录所有远程事件的操作日志）会通过本地分发收到自己服务发出的事件，行为与升级前（经 Kafka 回环）一致，但不再依赖 Kafka。

---

## 1. 现状事实（均来自源码，Bus 部分来自 spring-cloud-bus 4.x 源码）

| 项 | 事实 |
|---|---|
| 模块关系 | app-core 是单体 WEB 基础；cloud-core 依赖 app-core，增加微服务能力 |
| 事件模型 | `AppEvent`（仅 JVM 内）、`RemoteEvent`（继承 `RemoteApplicationEvent`，经 Bus/Kafka）。两者字段、`copy()` 基本一致 |
| 发布器 | app-core `EventConfig.busAwarePublisher`（仅本地）。cloud-core `KafkaConfig.busAwarePublisher`（`RemoteApplicationEvent` 走 `busBridge.send`，其余走本地），Kafka 开启时前者退让。两处各有一份约 35 行的“补充发起人信息”重复代码 |
| 监听 | 自定义 `UoquoEventListenerFactory` + `ApplicationListenerMethodAdapter` 子类，用于按泛型 `dataType` 过滤、MDC、吞异常。app-core 版本异步执行，cloud-core 版本同步执行 |
| 数据类型还原 | `DataTypeResolver` + `EventPackageScanner`，由 app-core 提供 Bean，api-micro 的 `BizEventRecordServiceImpl.retryEvent` 直接注入，用于 `RemoteEvent` 和 `AppEvent` 的重放 |
| 回环成因 | `originService` 取应用名（如 `service-platform`），而 Bus 的 `isFromSelf` 将其作为模式去匹配 busId（`service-platform:000`），段数不同，匹配失败，所以自己发的事件被当成别人发的 |
| 消费组 | 每个服务一个消费组（`${app.name}-bus`），同一服务只有一个实例消费一条消息 |
| `springCloudBusInput` | Bus 把函数绑定 `busConsumer-in-0` 映射成了该名字，yml 里的 group、DLQ 配置有效 |
| 事件 `id` | 每次发布都重新生成，用于应用自身标识，业务不依赖，回放时被覆盖是预期行为 |

---

## 2. 已确认的决策

| # | 决策 |
|---|---|
| D1 | 避免回环采用**服务级**方案：`originService` 仍为应用名，同名服务（含兄弟实例）发出的 `RemoteEvent` 一律视为“来自自己”并丢弃 |
| D2 | 发送方服务**自己处理自己发的事件**，通过本地分发，不经 Kafka 往返 |
| D3 | `DataTypeResolver`、`EventPackageScanner` 保留在 app-core。app-core 的扫描器读取 `@ApplicationEventScan`，并合并 cloud-core 通过 `EventScanPackagesProvider` 提供的 `@RemoteApplicationEventScan` 包；cloud-core 删除自己的 `eventPackageScanner`；app-core 不依赖 spring-cloud |
| D4 | 事件 `id` 每次发布覆盖，保持不变 |
| D5 | api-micro 仍为旧版，本次不改，只在文末列出后续迁移点 |

---

## 3. 目标行为

```
发布（业务线程）
  补充发起人信息，并取上下文快照
        │
        ▼ （事务完成后；专用线程池）
  ApplicationEventPublisher.publishEvent(event)
        ├─ 本地监听器适配器（异步）──────────▶ 本服务监听器处理（D2）
        └─ Bus 的 RemoteApplicationEventListener
             （仅 RemoteEvent 且 Bus 启用）
             isFromSelf=true ──▶ busBridge.send ──▶ Kafka

接收（其他服务）
  Kafka ─▶ BusConsumer
             isFromSelf？  origin == 本服务名
               是 ─▶ 丢弃（不重复处理）
               否 ─▶ publishEvent ─▶ 本地监听器适配器（异步）
```

要点：
- 发布走统一入口 `publishEvent(event)`，本地分发和发往 Bus 由 Spring 与 Bus 各自完成。
- Kafka 关闭时没有 Bus 的监听器，自然退化为只在本地分发，不再需要两套发布器。
- `AppEvent` 不是 `RemoteApplicationEvent`，只在本地分发，行为不变。
- Ack、Trace 等 Bus 自带事件不受影响（它们的 origin 是 busId，仍走原有判断）。

---

## 4. 变更清单

### 4.1 回环与自处理（D1、D2）

**cloud-core**
1. 新增 `ServiceMatcher` 包装类和一个 `static` 的 `BeanPostProcessor`，仅在 Bus 存在且启用时生效：
   - `isFromSelf`：事件是 `RemoteEvent` 时，`appName.equals(originService)`；其他事件委托给 Bus 原实现。
   - `isForSelf`、`getBusId`：委托。
   - `appName` 复用 `UoquoOriginFactory` 的取值，保证和事件里写入的 origin 一致。
2. `KafkaConfig`：删除它自己的 `busAwarePublisher`，统一使用 app-core 的发布器。保留 `KafkaProperties`。
3. `RemoteEventListenerFactory` 改为在有 cloud-core 时无条件注册（不再依赖 Kafka 开关），避免关闭 Kafka 时退回 app-core 的工厂，丢失 `RemoteEvent` 的类型过滤。

### 4.2 统一发布器（app-core `EventConfig`）

1. 合并两处重复的“补充发起人信息”，只保留一份。
2. 缺陷修复（见 5.1）：`operationTime`、`traceId`、构造校验、null 事件、MDC 的 `null` 字符串。
3. 触发时机与线程：见 5.2 的 Q1、Q4、Q5。

### 4.3 扫描器合并（D3）

采用扩展点方式，app-core 不引入任何 spring-cloud 依赖：

1. app-core 新增接口 `EventScanPackagesProvider`（只有一个方法 `List<String> getPackages()`）。
2. app-core `EventDeserializerAutoConfiguration.eventPackageScanner`：
   - 读取 `@ApplicationEventScan` 声明的包，并合并所有 `EventScanPackagesProvider` Bean 提供的包（去重）。
   - 取包名时同时处理 `value`、`basePackages`、`basePackageClasses`。
   - 改用不实例化 Bean 的方式获取带注解的 Bean，避免提前初始化。
3. `ApplicationEventScan` 的 `value` 与 `basePackages` 用 `@AliasFor` 互为别名。
4. cloud-core `RemoteEventDeserializerAutoConfiguration`：删除 `eventPackageScanner`，把读取 `@RemoteApplicationEventScan` 的逻辑改为注册一个 `EventScanPackagesProvider` Bean。
5. 单体或没有 cloud-core 时没有该 Provider，扫描范围只有 `@ApplicationEventScan`，行为与现在一致。

### 4.4 反序列化与适配器

1. `EventPackageScanner`：按 Class 去重。
2. `dataType` 规范化：嵌套类 `$`、泛型参数 `<...>` 统一处理，使 `DataTypeResolver`、扫描索引和监听器过滤使用同一套命名规则。
3. 监听器过滤增加“实际数据类型校验”：监听方法声明了具体泛型，而事件数据已降级为 `Map` 或类型不符时，跳过并输出 WARN，避免进入方法后抛 `ClassCastException` 被吞掉。
4. `RemoteEventDeserializerAutoConfiguration`：
   - `BeanPostProcessor` 的 `@Bean` 方法改为 `static`。
   - 找不到 Bus 的 `mapper` 字段时直接启动失败，不再只告警。
   - 用日志确认 `springCloudBusInput` 拦截器是否注册成功。
5. `BusErrorChannelHandler`：区分“无法反序列化”和其他 Integration 异常，日志措辞与实际一致，完整 payload 日志做脱敏。
6. 更新已失真的注释（`RemoteApplicationEventMulticaster` 不存在、“抛出让其重试”等）。

---

## 5. 缺陷修复清单

### 5.1 必修（直接可见的 Bug，风险低）

| # | 问题 | 位置 |
|---|---|---|
| B1 | 同一个类被多个扫描模式命中时重复入索引，被误判为“歧义”，数据退化为 Map | `EventPackageScanner` |
| B2 | `operationTime` 只在 `operatorId` 为空时设置 | `EventConfig`、`KafkaConfig`、`AppEvent.copy`、`RemoteEvent.copy` |
| B3 | 事件 `traceId` 可能为 null，而 MDC 用的是另行生成的值 | 两个发布器 |
| B4 | 嵌套类（`Outer$Inner`）、带泛型的 `dataType` 无法匹配监听器或解析为 Map | `DataTypeResolver`、两个适配器 |
| B5 | 监听器按名称匹配，数据已降级为 Map 仍调用 | 两个适配器 |
| B6 | MDC 出现 `null-xxx`、`xxx-null` | 发布器、适配器 |
| B7 | `@ApplicationEventScan.basePackages/basePackageClasses` 被忽略 | 扫描器、注解 |
| B8 | 发布前校验抛通用 `RuntimeException`，且只在发布时才校验；`@NonNull` 不生效 | 发布器、两个事件类构造器 |
| B9 | `RemoteEvent.setDestination` 修改不影响路由（路由在构造时已固定） | `RemoteEvent` |

B9 的处理方式：不改字段（避免破坏 JSON 兼容），仅标 `@Deprecated` 并写明“路由以构造参数为准”。

### 5.2 本次同时调整的行为（有取舍，需确认）

| # | 内容 | 默认方案 |
|---|---|---|
| Q1（已确认） | 用固定延迟 200ms 猜测事务提交的做法 | `publishEvent(event)`：当前线程存在事务同步时，注册 `TransactionSynchronization`，在 `afterCompletion`（提交或回滚后）再投递；没有事务则立即投递；去掉 200ms；回滚后发布时输出 WARN。业务代码无需改动。<br>新增 `publishAfterCommit(event)`：有事务时仅在提交成功后投递，回滚则丢弃并记录日志；没有事务则立即投递。`UoquoEventPublisher` 接口新增该方法，发布器由 lambda 改为独立实现类 |
| Q2 | 监听器线程里 `CurrentUser` 为空，下游 Feign 带不上 traceId/token | 适配器在执行监听器前，用事件里的 token、traceId、操作人信息填充 `CurrentUser`，执行后清理 |
| Q3 | 发布后事件在其他线程延迟序列化，业务线程可能仍在修改 `newData` | 暂不处理，只在文档中说明（需要深拷贝或提前序列化，成本较高） |
| Q4 | cloud 适配器同步执行，慢监听器会阻塞 Kafka 消费线程和后续监听器 | 与 app-core 一致，统一异步 |
| Q5 | 事件发布和监听共用 `ThreadPoolUtil`（默认 10 线程），监听器里有 Feign 调用时可能挤占其他任务 | 新增事件专用有界线程池，参数可配置（`app.event.pool.*`） |
| Q6 | `token` 随事件进入 Kafka 和日志 | 本次只做日志脱敏，不改事件字段（api-micro 的踢人、记录功能依赖它） |

---

## 6. 需要你确认

1. **Q1（已确认）**：默认事务完成后（`afterCompletion`，提交或回滚都投递）投递并去掉 200ms；另增 `publishAfterCommit(event)`，仅提交成功后才投递。不再保留 `app.event.publish-delay-ms` 配置（cloud-core 虽然依赖 `seata-all`，但相关代码已被注释，api-micro/service-platform 中也未发现 `@GlobalTransactional` 用法）。
   - 不选“仅提交后投递”，是因为登录失败等失败事件常在抛异常回滚的事务内发布，仅提交后投递会丢失。
   - 副作用：事务回滚时，已发布的“成功”事件仍会发出，这与现状一致，只是时机更准确。
2. **Q2**：监听器执行时填充 `CurrentUser`，是否同意？
3. **Q4 / Q5**：统一异步，并新增事件专用线程池，是否同意？
4. **Q6**：本次只做日志脱敏，是否同意？
5. 以下内容本次**不做**，是否同意：
   - 合并 `AppEvent` 与 `RemoteEvent` 的重复代码。
   - 用 `ResolvableTypeProvider` 替换自定义监听器工厂。
   - 精简 `RemoteEvent` 中与父类重复的 `id`、`destination` 字段。
   - 把原 `id` 记录进 `extension`（回放追溯）。

---

## 7. 行为变化与风险

**行为变化**
- 发送方服务自己的监听器会比原来更早收到事件（本地分发，不经 Kafka 往返），Kafka 故障时也能收到。
- 同一服务的其他实例不会再处理本服务发出的事件（原先是消费组内随机一个实例处理）。
- 单体或关闭 Kafka 时，发布路径与 Kafka 开启时一致，只是没有向 Bus 发送这一步。
- 监听器异步化后，同一事件的多个监听器不再保证顺序。
- 重放事件（`retry=true`）的流程不变，仍会被本服务本地处理一次。

**风险与验证**
1. `RemoteApplicationEventListener` 在 `publishEvent` 链路中的执行顺序，以及它发送失败时是否影响其他本地监听器：实现时用测试验证。如有影响，改用自定义异常处理或调整顺序。
2. 包装 `ServiceMatcher`：确认 Bus 内所有注入点都是接口类型，实现时检查。
3. 现有测试可能引用 `KafkaConfig` 的发布器，需随改动调整。
4. 验证方式：为 `ServiceMatcher` 包装类、扫描器去重、`dataType` 规范化、发布器补充信息、适配器过滤分别补单元测试。回环需用 Mock 的 `BusBridge` 做集成验证。完成后运行 app-core 与 cloud-core 的现有测试。

---

## 8. 实施顺序

1. **第一批（低风险 Bug）**：B1、B2、B3、B4、B5、B6、B7、B8、B9。
2. **第二批（D3）**：扫描器合并。
3. **第三批（D1、D2）**：`ServiceMatcher` 包装、发布器统一、`KafkaConfig` 精简、监听工厂无条件注册。
4. **第四批（Q1、Q2、Q4、Q5、Q6）**：事务时机、`CurrentUser` 填充、统一异步与专用线程池、日志脱敏。
5. 每批完成后跑 app-core、cloud-core 测试。

---

## 附：api-micro 后续迁移点（本次不改）

- `getOperationStatus()` 改为 `getOperationResult()`。
- `BizEventRecordServiceImpl.retryEvent`：`type` 只匹配 `"RemoteEvent"` 与 `"AppEvent"`，自定义子类事件会落入 `else` 分支，把 Map 当事件发布。
- 如要启用 D2，scheduler-center 的 `AllEventListener` 会继续记录自己发出的事件，保持现有表现。
