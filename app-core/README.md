# 简介
本项目包主要用于定义基础类、全局异常处理、全局拦截器等

# 目录结构
```bat
com.uoquo.web
|- base 
|- cache  
|- common  
|- controller  
|  |- AppVersionController `获取应用的版本号`
|  |_ GlobalExceptionController
|
|- filter
|  |- LogbackFilter 定义全局请求ID
|  |_ MultiReadHttpServletRequestFilter 复用请求流
|
|- interceptor
|- mybatis
|- servlet
|  |_ CaptchaImageServlet 验证码
|
|- ServiceApplication
|_ ServiceConfig  基础配置类

```

---

# 请求参数签名校验

## 概述

为保证请求参数在传输过程中不被篡改，系统采用 MD5 签名机制对请求进行完整性校验。
签名校验在 `CheckParamInterceptor` 拦截器中执行，校验顺序为：时间戳校验 → 防重提交校验 → 参数签名校验。

## 请求头参数

客户端发起请求时，需在请求头（或URL参数）中传入以下内置参数：

| 参数名             | 常量                          | 说明        | 必填 |
|:----------------|:----------------------------|:----------|:--:|
| `appid`         | `CurrentUser.APPID`         | 应用ID      | 是  |
| `token`         | `CurrentUser.TOKEN`         | 授权令牌      | 是  |
| `nonce`         | `CurrentUser.NONCE`         | 请求随机数（防重） | 是  |
| `timestamp`     | `CurrentUser.TIME`          | 请求时间戳（毫秒） | 是  |
| `device-id`     | `CurrentUser.DEVICE_ID`     | 设备唯一标识    | 是  |
| `user-language` | `CurrentUser.USER_LANGUAGE` | 客户端语言     | 是  |
| `signature-app` | `CurrentUser.SIGN_APP`      | 请求签名值     | 是  |

> 注：参数优先从请求头获取，若请求头中不存在，则从URL参数或Form-Data中获取（兼容SSE等无法添加Header的场景）。

## 签名计算规则

签名由 `WebUtil.signParam()` 计算得出，最终调用 `SignParamUtil.sign()` 执行 MD5 摘要。
拼接顺序如下：

```
MD5( appid + token + language + nonce + deviceId + timestamp + 请求参数 + 请求体 + secret )
```

### 1. 内置参数拼接

按固定顺序依次拼接各内置参数的值（非空时拼接）：

| 顺序 | 参数        | 取值来源        |
|:--:|:----------|:------------|
| 1  | appid     | 请求头 / URL参数 |
| 2  | token     | 请求头 / URL参数 |
| 3  | language  | 请求头 / URL参数 |
| 4  | nonce     | 请求头 / URL参数 |
| 5  | deviceId  | 请求头 / URL参数 |
| 6  | timestamp | 请求头 / URL参数 |

### 2. 请求参数拼接

取 `request.getParameterMap()` 中的所有参数，按 key 的字典序（TreeMap）排列后逐项拼接：

```
key1 + value1 + key2 + value2 + ...
```

**注意：** 以下内置参数已通过请求头单独参与签名，在请求参数遍历时会被跳过，不重复计算：
`appid`、`token`、`nonce`、`timestamp`、`device-id`、`user-language`、`signature-app`

多值参数的处理：
- `null` → 直接拼接 key
- 空数组 → key + ""
- 单值 → key + value
- 多值 → key + JSON序列化结果（`JsonUtil.serialize(vals)`）

### 3. 文件上传

当请求为 `multipart/form-data` 时：
- 文件内容本身不参与签名计算
- 但必须以**文件原始名称**为 key，传入该文件的 **MD5 值**作为请求参数
- 若未传入文件对应的 MD5，将抛出 `IllegalArgumentException`

### 4. 请求体拼接

当 Content-Type **不是** `multipart/form-data` 或 `application/octet-stream` 时，读取请求体的原始字节参与签名。

> 注：Spring 会将 form 表单和文件内容解析到 `parameterMap` 和 `MultipartFile` 中，此时请求体为空，不会重复计算。

### 5. 密钥拼接

最后拼接应用密钥 `secret`，然后对整体内容进行 MD5 摘要，得到签名值。

## 校验流程

`CheckParamInterceptor` 在 `CurrentUser` 拦截器之后执行，流程如下：

```
请求进入
  │
  ├─ OPTIONS 请求 → 放行
  ├─ @IgnoreAuth(all=true) → 跳过所有校验
  ├─ 全局免签路径（Redis 配置） → 跳过校验
  ├─ FEIGN 内部调用 → 跳过校验
  │
  └─ 依次执行：
      1. 时间戳校验（@IgnoreAuth(timestamp=true) 可跳过）
         - 请求时间戳与服务器时间差超过5分钟则拒绝
      2. 防重提交校验
         - 以 appid + nonce 为键，5分钟内不可重复提交
      3. 参数签名校验（@IgnoreAuth(params=true) 可跳过）
         - 校验 appid、signature-app、language、nonce、device-id 不为空
         - 用 WebUtil.signParam() 重新计算签名，与传入的 signature-app 比对
```

## 免签注解 @IgnoreAuth

| 属性                   |  默认值  | 说明                                  |
|:---------------------|:-----:|:------------------------------------|
| `all`                | false | 忽略所有校验（慎用）                          |
| `timestamp`          | false | 忽略时间戳校验（适用于设备与服务器时间不同步的场景）          |
| `params`             | false | 忽略参数签名校验                            |
| `login`              | false | 忽略登录校验                              |
| `inner`              | false | 标记为内部接口，仅允许 FEIGN 调用                |
| `refreshExpiresTime` | true  | 是否刷新 token 过期时间（对于定时轮询接口一般标记为false） |

---


默认采用读写分离模式
