# uoquo-annotations

注解、异常体系、通用对象定义。作为最底层的模块，不依赖任何其他内部模块，仅使用少量 provided 级别的外部依赖。

---

## 设计原则

- 只定义接口和数据结构，不包含实现逻辑
- 所有外部依赖标记为 `provided`，由上层模块引入实际实现
- 可被所有层级的模块安全引用，不会引入额外传递依赖

---

## 包结构总览

```
com.uoquo
├── annotation/
│   ├── json/                   # JSON 序列化相关注解
│   │   ├── Sensitive           # 脱敏/加解密注解
│   │   └── SensitiveType       # 脱敏类型枚举
│   └── web/                    # Web 相关注解
│       └── IgnoreAuth          # 免认证校验注解
│
├── condition/                  # Spring 条件注解
│   ├── ConditionOnPropertyExists      # 条件注解定义
│   └── OnPropertyExistsCondition      # 条件判断实现
│
├── mybatis/
│   ├── page/                   # 分页对象
│   │   ├── PageList<T>         # 分页集合（继承 ArrayList）
│   │   └── PageResult<T>      # 分页响应体
│   ├── sensitive/              # MyBatis 敏感数据注解
│   │   ├── SensitiveData       # 标记类或方法需要加解密
│   │   └── SensitiveField      # 标记字段需要加解密
│   └── sharding/               # 分表注解
│       ├── ITableShardStrategy # 分表策略接口
│       └── TableShardAnnotation # 分表注解定义
│
├── utils/
│   └── CurrentUser             # 当前用户上下文（ThreadLocal）
│
└── web/
    ├── BaseCacheKey             # 缓存 Key 常量定义
    ├── BaseReturnCode           # 错误码抽象基类
    ├── ReturnData<T>            # API 统一响应体
    ├── ReturnLevel              # 消息级别枚举
    ├── SystemReturnCode         # 系统预定义错误码
    ├── param/
    │   ├── IdParam              # 通用 ID 入参
    │   └── PageRequest          # 分页查询入参
    └── exception/               # 异常体系
        ├── AbstractBaseException    # 异常基类
        ├── UoquoException           # 通用业务异常
        └── ...                      # 各类业务异常
```

---

## 统一响应体 ReturnData

所有 API 接口统一返回此结构：

```json
{
  "status": "00000",
  "code": "00000",
  "level": "SUCCESS",
  "message": "请求成功",
  "data": { ... }
}
```

| 字段 | 说明 |
|------|------|
| `status` | 业务状态码（00000 = 成功） |
| `code` | 完整错误码（含应用编码 + 节点编码 + 状态码） |
| `level` | 消息级别（SILENT / INFO / WARN / ERROR / NOTICE / SUCCESS） |
| `message` | 错误描述 |
| `data` | 业务数据 |

---

## 错误码体系

错误码为 5 位字符串，按前缀分类：

| 前缀 | 分类 | 示例 |
|------|------|------|
| 00 | 成功 | 00000 请求成功 |
| 01 | 系统错误 | 01001 参数错误、01500 系统内部错误 |
| 02 | 认证相关 | 02005 Token 为空、02020 账户未登录 |

完整错误码 = 应用编码（2位）+ 节点编码（1位）+ 状态码（5位），例如 `00101500`。

---

## 异常体系

所有业务异常继承 `AbstractBaseException`，包含以下字段：

| 字段 | 说明 |
|------|------|
| `status` | 业务状态码 |
| `code` | 完整错误码 |
| `mesg` | 错误消息（支持占位符） |
| `args` | 消息参数 |
| `level` | 消息级别 |
| `trace` | 堆栈信息（生产环境返回 traceId） |
| `traceId` | 请求追踪 ID |

### 预定义异常

| 异常类 | 错误码 | 说明 |
|--------|--------|------|
| `ParamEmtpyException` | 01002 | 参数为空 |
| `ParamErrorException` | 01001 | 参数错误 |
| `ParamSignEmptyException` | 02001 | 签名参数为空 |
| `ParamSignErrorException` | 02002 | 签名参数错误 |
| `AppkeyEmptyException` | 02003 | AppKey 为空 |
| `AppkeyInvalidException` | 02004 | AppKey 失效 |
| `TokenEmptyException` | 02005 | Token 为空 |
| `TokenInvalidException` | 02006 | Token 失效 |
| `AccountUnLoginException` | 02020 | 账户未登录 |
| `AccountKickOutException` | 02021 | 账号异地登录被踢出 |
| `ForbiddenException` | 01403 | 无权操作 |
| `ResourceNotFoundException` | 01404 | 资源不存在 |
| `TooManyRequestException` | 01429 | 请求过多（限流） |
| `SystemErrorException` | 01500 | 系统内部错误 |
| `SystemWarnException` | 01495 | 系统内部警告 |
| `RemoteServiceException` | 01503 | 远程服务不可用 |

### 使用示例

```java
// 抛出预定义异常
throw new ParamErrorException();

// 抛出自定义消息
throw new UoquoException(SystemReturnCode.PARAM_ERROR, "用户名不能为空");

// 带参数的消息
throw new UoquoException(SystemReturnCode.PARAM_ERROR, "字段 %s 不能为空", "username");
```

---

## @IgnoreAuth 免认证注解

用于 Controller 方法或类上，控制跳过哪些校验环节：

```java
@IgnoreAuth(login = true)          // 跳过登录校验
@IgnoreAuth(params = true)         // 跳过签名校验
@IgnoreAuth(timestamp = true)      // 跳过时间戳校验
@IgnoreAuth(all = true)            // 跳过所有校验（慎用）
@IgnoreAuth(inner = true)          // 仅允许内部服务调用
@IgnoreAuth(refreshExpiresTime = false)  // 不刷新 Token 有效期
```

---

## @Sensitive JSON 脱敏注解

用于 DTO/Param 的字段上，在 JSON 序列化时自动脱敏或加解密：

```java
public class UserDTO {
    @Sensitive(type = SensitiveType.PHONE)
    private String phone;       // 输出: 138****0000

    @Sensitive(type = SensitiveType.ID_CARD)
    private String idCard;      // 输出: 510***********8283

    @Sensitive(type = SensitiveType.CRYPT_AES)
    private String secret;      // 输出: AES 加密后的密文
}
```

支持的脱敏类型：

| 类型 | 说明 | 示例 |
|------|------|------|
| DEFAULT | 全部替换为 * | ******* |
| NAME | 姓名脱敏 | 张*丰 |
| PASSWORD | 密码脱敏 | ****** |
| EMAIL | 邮箱脱敏 | abc****@qq.com |
| PHONE | 手机号脱敏 | 138****0000 |
| ID_CARD | 身份证脱敏 | 510********8283 |
| BANK_CARD | 银行卡脱敏 | 1234********1234 |
| ADDRESS | 地址脱敏 | 北京市海淀区**号 |
| CUSTOM | 自定义正则 | 按 pattern 替换 |
| CRYPT_AES | AES 加密 | 对称加密 |
| CRYPT_RSA | RSA 加密 | 非对称加密 |
| CRYPT_SM4 | 国密 SM4 | 类似 AES |
| CRYPT_SM2 | 国密 SM2 | 类似 RSA |

---

## 分页对象

### PageList

继承 `ArrayList`，在查询时用于承载分页参数和结果：

```java
// Service 层使用
PageHelper.startPage(pageNum, pageSize);
PageList<User> list = userMapper.selectList(params);
// list 自动包含 total / pages / hasNextPage 等信息
```

### PageResult

用于 API 响应的分页数据封装：

```java
// Controller 层返回
return new ReturnData<>(PageResult.of(list));
```

---

## CurrentUser 用户上下文

基于 ThreadLocal 的当前请求用户信息容器，由拦截器自动填充：

```java
// 获取当前用户 ID
String userId = CurrentUser.getUserId();
// 获取当前 Token
String token = CurrentUser.getToken();
// 获取 AppKey
String appId = CurrentUser.getAppId();
```
