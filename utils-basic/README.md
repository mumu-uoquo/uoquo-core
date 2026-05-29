# utils-basic

通用工具类库，提供加解密、HTTP 请求、JSON 处理、文件操作、Spring 集成等基础能力。依赖 `uoquo-annotations`。

---

## 包结构

```
com.uoquo.utils
├── BarcodeUtil              # 条形码/二维码生成与解析
├── BcdUtil                  # BCD 编码转换
├── CompressUtil             # 压缩/解压（ZIP、GZIP）
├── Config                   # 配置文件读取
├── DataUtil                 # 数据转换工具
├── DateUtil                 # 日期时间工具
├── FileUtil                 # 文件读写工具
├── IDGenerator              # ULID 唯一 ID 生成器
├── NetworkUtil              # 网络工具（IP、MAC）
├── ObjectUtil               # 对象工具（深拷贝）
├── PinYinUtil               # 中文转拼音
├── SignParamUtil             # 请求参数签名
├── StringUtil               # 字符串工具
├── ThreadPoolUtil           # 线程池工具
│
├── crypto/                  # 加解密
│   ├── AES                  # AES 对称加密
│   ├── RSA                  # RSA 非对称加密
│   ├── MD5                  # MD5 哈希
│   ├── SHA                  # SHA1/SHA256 哈希
│   ├── SM2                  # 国密 SM2（非对称）
│   ├── SM3                  # 国密 SM3（哈希）
│   ├── SM4                  # 国密 SM4（对称）
│   ├── Base32               # Base32 编解码
│   ├── CRCUtil              # CRC 校验
│   ├── OTPUtils             # TOTP/HOTP 一次性密码
│   ├── SnowFlake            # 雪花算法 ID 生成
│   ├── ULID                 # ULID 生成器
│   ├── License              # 软件许可证工具
│   ├── SerialNumUtil        # 序列号生成
│   └── BCProvider           # Bouncy Castle 提供者注册
│
├── http/                    # HTTP 客户端（基于 OkHttp）
│   ├── HttpUtil             # HTTP 请求工具
│   └── HttpParams           # 请求参数构建器
│
├── json/                    # JSON 处理
│   ├── JsonUtil             # Jackson 序列化/反序列化
│   ├── GsonUtil             # Gson 序列化/反序列化（备用）
│   ├── TypeToken            # 泛型类型令牌
│   ├── Types                # 类型工具
│   └── jackson/             # Jackson 扩展
│       ├── DateContextualSerializer     # 日期序列化（自适应格式）
│       ├── DateContextualDeserializer   # 日期反序列化（多格式兼容）
│       ├── SensitiveSerializer          # @Sensitive 脱敏序列化
│       ├── SensitiveDeserializer        # @Sensitive 解密反序列化
│       ├── SensitiveAnnotationIntrospector  # 注解扫描器
│       ├── StringNullSerializer         # null 值序列化为空字符串
│       └── StringNullSerializerProvider # 序列化提供者
│
├── mail/                    # 邮件
│   └── MailSenderInfo       # 邮件发送信息封装
│
└── spring/                  # Spring 集成
    ├── SpringUtil           # Spring 上下文工具（getBean / registerBean）
    ├── RedisUtil            # Redis 操作封装
    ├── CaptchaUtil          # 验证码图片生成
    └── GenericJson2RedisSerializer  # Redis JSON 序列化器
```

---

## 核心工具详解

### IDGenerator — 唯一 ID 生成

基于 ULID（Universally Unique Lexicographically Sortable Identifier）算法：

```java
String id = IDGenerator.getNextULID();
// 输出：01HK3YP8KWTE2RJZ4N9DFWP8SQ（26位，按时间排序）
```

特点：有序、全局唯一、URL 安全、比 UUID 更紧凑。

---

### DateUtil — 日期工具

推荐统一使用 UTC 格式：`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`

```java
DateUtil.format(new Date());              // 格式化
DateUtil.parse("2024-01-01T00:00:00Z");   // 解析
DateUtil.addDays(date, 7);                // 加减天数
```

---

### 加解密工具

#### MD5

```java
MD5.encrypt("abc");                  // 字符串 MD5
MD5.encrypt("abc", "salt");          // 加盐 MD5
MD5.encryptFile("D:/a.txt", "salt"); // 文件 MD5
```

#### SHA

```java
SHA.sha1("abc");
SHA.sha256("abc");
```

#### AES（对称加密）

```java
String encrypted = AES.encrypt("明文数据", "16位密钥");
String decrypted = AES.decrypt(encrypted, "16位密钥");
```

#### RSA（非对称加密）

```java
// 生成密钥对
Map<String, String> keyPair = RSA.generateKeyPair();
String publicKey = keyPair.get("publicKey");
String privateKey = keyPair.get("privateKey");

// 公钥加密 → 私钥解密
String encrypted = RSA.encrypt(publicKey, "data");
String decrypted = RSA.decrypt(privateKey, encrypted);

// 私钥签名 → 公钥验签
String signature = RSA.signed(privateKey, "data");
boolean valid = RSA.verify(publicKey, "data", signature);
```

#### 国密 SM2 / SM4

```java
// SM4（对称，类似 AES）
String encrypted = SM4.encrypt("明文", "密钥");
String decrypted = SM4.decrypt(encrypted, "密钥");

// SM2（非对称，类似 RSA）
// 公钥加密 / 私钥解密
```

#### OTP（一次性密码）

```java
// TOTP 验证（用于双因素认证）
boolean valid = OTPUtils.verifyTOTP(secret, userCode);
```

---

### HttpUtil — HTTP 客户端

基于 OkHttp 封装，支持 GET / POST / PUT / DELETE，支持上传下载进度监听：

```java
// GET 请求
String response = HttpUtil.get("https://api.example.com/users");

// POST JSON
String response = HttpUtil.postJson("https://api.example.com/users", jsonBody);

// 文件上传（带进度）
HttpUtil.upload(url, file, new ProgressUploadListener() {
    @Override
    public void onProgress(long bytesWritten, long contentLength) {
        // 进度回调
    }
});
```

---

### JsonUtil — JSON 序列化

基于 Jackson 实现，内置多项增强：

```java
// 序列化
String json = JsonUtil.serialize(object);

// 反序列化（简单类型）
User user = JsonUtil.deserialize(json, User.class);

// 反序列化（泛型类型）
List<User> users = JsonUtil.deserialize(json, new TypeToken<List<User>>(){}.getType());

// 带类型信息的序列化（用于 Redis 存储）
JsonUtil.serializeWithType(object, writer);
```

内置能力：
- 日期格式自适应（UTC / ISO 8601 / 时间戳 / 自定义格式）
- `@Sensitive` 注解驱动的脱敏/加解密
- null 值处理策略
- 泛型类型安全反序列化

---

### Spring 集成工具

#### SpringUtil — 上下文工具

```java
// 获取 Bean
UserService userService = SpringUtil.getBean(UserService.class);
Object bean = SpringUtil.getBean("beanName");

// 动态注册 Bean
SpringUtil.registerBean("myBean", MyClass.class, arg1, arg2);
```

#### RedisUtil — Redis 操作

```java
// 单值操作
RedisUtil.put("key", value, 3600);           // 存储，3600秒过期
User user = RedisUtil.get("key", User.class); // 读取

// 集合操作
RedisUtil.putSetItem("set:key", item, 3600);
Set<String> items = RedisUtil.getSets("set:key", String.class);

// 列表操作
RedisUtil.putListItem("list:key", item, 3600);
List<String> list = RedisUtil.getList("list:key", String.class);

// Hash 操作
RedisUtil.putHash("hash:key", "field", value, 3600);
User user = RedisUtil.getHash("hash:key", "field", User.class);

// 本地缓存（减少 Redis 网络交互，1分钟 TTL）
User cached = RedisUtil.getLocalCache("key", User.class);
```

#### CaptchaUtil — 验证码

```java
@Autowired
CaptchaUtil captchaUtil;

// 生成验证码文本
String code = captchaUtil.getCaptchaValue();
// 生成验证码图片
BufferedImage image = captchaUtil.generateCaptchaImage(code);
// 转为 Base64 Data URL
String dataUrl = captchaUtil.convertToWebBase64(image, "png");
```

支持配置项（application.yml）：

```yaml
app:
  captcha:
    width: 120          # 图片宽度
    height: 30          # 图片高度
    length: 4           # 字符数
    type: auto          # 字符类型（digit/upper/lower/alpha/auto）
    noise: false        # 是否添加噪点
    curve: true         # 是否画干扰曲线
```

---

### ThreadPoolUtil — 线程池

基于 Spring `ThreadPoolTaskScheduler` 封装，支持单次/循环/Cron 任务：

```java
// 单次执行
ThreadPoolUtil.executeOnce(() -> doSomething());

// 延迟执行
ThreadPoolUtil.executeOnce(() -> doSomething(), 5000);

// 循环执行（每10秒）
ThreadPoolUtil.execute(() -> doSomething(), 10);

// Cron 表达式
ThreadPoolUtil.execute(() -> doSomething(), "0 0/5 * * * ?");

// 带返回值
Future<String> future = ThreadPoolUtil.executeOnce(() -> compute());
```
