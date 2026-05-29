# 1. 简介
各种工具类
# 2. 使用方法
## 2.1 ID生成器（IDGenerator）
```java
IDGenerator.getNextULID();
```
## 2.2 文件操作（FileUtil）

## 2.3 字符串操作（StringUtil）

## 2.4 线程池工具（ThreadPoolUtil）
很少使用（用spring自带的注解即可完成大多数业务）

## 2.5 日期工具（DateUtil）
日期在序列化时，建议都采用UTC时间格式（yyyy-MM-dd'T'HH:mm:ss.SSS'Z'）

## 2.6 压缩工具（CompressUtil）

## 2.7 条形码、二维码（BarcodeUtil）

## 2.8 http请求（HttpUtil）

## 2.9 加解密
### 2.9.1 MD5
```java
// 字符串
MD5.encrypt("abc");
// 字符串加盐
MD5.encrypt("abc", "salt");
// 文件加盐
MD5.encryptFile("D:/a.txt", "salt");
```

### 2.9.2 SHA
```java
SHA.sha1("abc");
SHA.sha256("abc");
```

### 2.9.2 AES
```java
// 加密
AES.encrypt("abcd", "aes_key");
// 解密
AES.decrypt("abcd", "aes_key");
```

### 2.9.2 RSA
```java
// 获取秘钥对
RSA.generateKeyPair();

// 私钥签名
RSA.signed("priveKey", "data");
// 公钥验签
RSA.verify("publicKey", "infoData", "signedData");

// 公钥加密
RSA.encrypt("publicKey", "data");
// 私钥解密
RSA.decrypt("priveKey", "data");

// 私钥加密
RSA.encryptByPrivateKey("priveKey", "data");
// 公钥解密
RSA.decryptByPublicKey("publicKey", "data");
```

TypeToken对Map、List的支持