/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.annotation.json;

public enum SensitiveType {
    // 脱敏类型
    DEFAULT,            // 默认脱敏（全*）
    NAME,               // 姓名（张*，张*丰）
    PASSWORD,           // 密码（******）
    EMAIL,              // 邮箱（abc****@qq.com）
    PHONE,              // 手机号（138*****000）
    ID_CARD,            // 身份证（510********8283）
    BANK_CARD,          // 银行卡（123456********1234）
    ADDRESS,            // 地址（北京市海淀区**号**栋）

    CUSTOM,             // 自定义模式

    // 加解密
    CRYPT_AES ,         // 加密模式：AES（对称）
    CRYPT_TAES ,        // 加密模式：基于TOTP时间片作为秘钥的AES
    CRYPT_RSA ,         // 加密模式：RSA（非对称，序列化时用私钥加密，反序列化时用私钥解密，接收方反向操作）
    
    // 加解密（国密）
    CRYPT_SM4 ,         // 国密模式：SM4（类似于AES）
    CRYPT_TSM4 ,        // 国密模式：SM4（类似于TAES）
    CRYPT_SM2 ,         // 国密模式：SM2（类似于RSA）
}
