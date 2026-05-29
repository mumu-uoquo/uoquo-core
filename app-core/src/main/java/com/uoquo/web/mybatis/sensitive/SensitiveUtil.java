/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.sensitive;

import com.uoquo.mybatis.sensitive.SensitiveData;
import com.uoquo.mybatis.sensitive.SensitiveField;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.crypto.AES;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加解密工具<br/>
 * 优化点：
 * <ul>
 *   <li>缓存 @SensitiveField 注解字段，避免重复反射</li>
 *   <li>支持父类继承字段的加解密</li>
 *   <li>解密侧支持嵌套对象和 List 字段（与加密侧对称）</li>
 * </ul>
 *
 * @author xuhz
 */
public class SensitiveUtil {
    private static final Logger log = LoggerFactory.getLogger(SensitiveUtil.class);

    /**
     * 加密标识：字符串有这个前缀就说明已经加密过
     */
    private static final String KEY_SENSITIVE = "sensitive_";

    /**
     * 缓存：类 -> 带有 @SensitiveField 注解的字段列表（含父类）
     */
    private static final ConcurrentHashMap<Class<?>, List<Field>> SENSITIVE_FIELDS_CACHE = new ConcurrentHashMap<>();

    // ========================== 加密 ==========================

    /**
     * 加密字符串
     */
    public static String encrypt(String value, String key) throws GeneralSecurityException {
        if (Objects.isNull(value) || value.isEmpty()) {
            return value;
        }
        // 没有标识才加密(防止重复加密)
        if (!value.startsWith(KEY_SENSITIVE)) {
            value = KEY_SENSITIVE + AES.encrypt(value, key);
        }
        return value;
    }

    /**
     * 加密对象（泛型入口）
     */
    @SuppressWarnings("unchecked")
    public static <T> T encrypt(T obj, String key) {
        if (Objects.isNull(obj)) {
            return null;
        }
        if (obj instanceof String strVal) {
            if (StringUtil.isNull(obj)) {
                return obj;
            }
            try {
                return (T) encrypt(strVal, key);
            } catch (Exception e) {
                log.error("[{}]加密失败.", obj, e);
                return obj;
            }
        } else if (obj instanceof List<?>) {
            List<Object> list = (List<Object>) obj;
            encryptList(list, key);
        } else {
            encryptObject(obj, key);
        }
        return obj;
    }

    private static void encryptList(List<Object> list, String key) {
        if (Objects.isNull(list)) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Object val = list.get(i);
            if (val instanceof String strVal) {
                try {
                    list.set(i, encrypt(strVal, key));
                } catch (GeneralSecurityException e) {
                    log.error("list第[{}]值加密失败.", i, e);
                }
            } else if (val != null && !isSimpleType(val)) {
                encryptObject(val, key);
            }
        }
    }

    private static <T> void encryptObject(T paramsObject, String key) {
        if (isSimpleType(paramsObject)) {
            return;
        }
        List<Field> fields = getSensitiveFields(paramsObject.getClass());
        if (fields.isEmpty()) {
            return;
        }
        String className = paramsObject.getClass().getTypeName();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object object = field.get(paramsObject);
                if (Objects.isNull(object)) {
                    continue;
                }
                log.debug("[{}]字段[{}]原始的值[{}].", className, field.getName(), object);
                if (object instanceof String strVal) {
                    field.set(paramsObject, encrypt(strVal, key));
                } else if (object instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) object;
                    encryptList(list, key);
                } else {
                    encryptObject(object, key);
                }
            } catch (Exception e) {
                log.error("[{}]字段[{}]加密失败.", className, field.getName(), e);
            } finally {
                field.setAccessible(false);
            }
        }
    }

    // ========================== 解密 ==========================

    /**
     * 解密字符串
     */
    public static String decrypt(String value, String key) throws GeneralSecurityException {
        if (Objects.isNull(value)) {
            return null;
        }
        // 有标识则解密(防止重复解密)
        if (value.startsWith(KEY_SENSITIVE)) {
            value = AES.decrypt(value.substring(KEY_SENSITIVE.length()), key);
        }
        return value;
    }

    /**
     * 解密对象<br/>
     * 逻辑：加了 @SensitiveField 注解的字段，进行解密（支持嵌套对象和 List）
     */
    public static <T> void decrypt(Class<?> clazz, T result, String key) {
        if (Objects.isNull(result)) {
            return;
        }
        List<Field> fields = getSensitiveFields(clazz);
        if (fields.isEmpty()) {
            return;
        }
        String className = clazz.getTypeName();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object object = field.get(result);
                if (Objects.isNull(object)) {
                    continue;
                }
                log.debug("[{}]字段[{}]获取DB的值[{}].", className, field.getName(), object);
                if (object instanceof String strVal) {
                    String decrypted = decrypt(strVal, key);
                    field.set(result, decrypted);
                    log.debug("[{}]字段[{}]解密后的值[{}].", className, field.getName(), decrypted);
                } else if (object instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) object;
                    decryptList(list, key);
                } else {
                    // 嵌套对象递归解密
                    decrypt(object.getClass(), object, key);
                }
            } catch (Exception e) {
                log.error("[{}]字段[{}]解密失败.", className, field.getName(), e);
            } finally {
                field.setAccessible(false);
            }
        }
    }

    /**
     * 解密List
     */
    private static void decryptList(List<Object> list, String key) {
        if (Objects.isNull(list)) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Object val = list.get(i);
            if (val instanceof String strVal) {
                try {
                    list.set(i, decrypt(strVal, key));
                } catch (GeneralSecurityException e) {
                    log.error("list第[{}]值[{}]解密失败.", i, val, e);
                }
            } else if (val != null && !isSimpleType(val)) {
                decrypt(val.getClass(), val, key);
            }
        }
    }

    // ========================== 工具方法 ==========================

    /**
     * 获取类中带有 @SensitiveField 注解的字段（含继承链），带缓存
     */
    private static List<Field> getSensitiveFields(Class<?> clazz) {
        return SENSITIVE_FIELDS_CACHE.computeIfAbsent(clazz, c -> {
            List<Field> sensitiveFields = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isAnnotationPresent(SensitiveField.class)) {
                        sensitiveFields.add(field);
                    }
                }
                current = current.getSuperclass();
            }
            return Collections.unmodifiableList(sensitiveFields);
        });
    }

    /**
     * 判断是否为简单类型（无需递归处理）
     */
    private static boolean isSimpleType(Object object) {
        return Objects.isNull(object)
                || object.getClass().isPrimitive()
                || object instanceof Number
                || object instanceof Enum
                || object instanceof Date
                || object instanceof Map
                || object instanceof Collection
                || object instanceof String;
    }
}
