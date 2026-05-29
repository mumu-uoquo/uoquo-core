/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.spring;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 描述：redis操作工具类. <br>
 * 背景：由于采用spring的自动注入，所有无法放到utils工具包中. <br>
 * 备注：为了兼容各个json底层处理类，以及方便应用解耦，因此序列化时均不带class标识，在反序列化时再明确传入类型. <br>
 * 日期：2018-06-01 09:42 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-06-01     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Component
@ConditionalOnClass(RedisOperations.class)
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisUtil {
    /** 本地缓存时长. */
    private static final int CACHE_EXPIRES = 60 * 1000;

    /** 本地缓存数据的过期时间. */
    private final Map<String, Long> cacheKeys = new ConcurrentHashMap<>();
    /** 本地缓存数据的内容. */
    private final Map<String, Object> cacheData = new ConcurrentHashMap<>();

    // 因为使用频繁，因此采用饿汉模式来初始化单例
    private static final RedisUtil INSTANCE = new RedisUtil();
    private RedisUtil() {
    }

    // 自动注入的spring 操作模板
    private static RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = true)
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        RedisUtil.redisTemplate = redisTemplate;
    }

    public RedisTemplate<String, Object> getRedisTemplate() {
        return RedisUtil.redisTemplate;
    }

    /* ******* 单对象操作 ******* */
    /**
     * 添加单个对象.<br>
     * @param key   键
     * @param value 值
     * @param second 超时时间（秒，为null或者负数时，永久存储）
     */
    public static <T> void put(@NonNull String key, T value, Integer second) {
        if (value == null) { return; }
        byte[] data = parse2Byte(value, false);
        putData(key, data, second);
    }

    /**
     * 添加单个对象（带类型信息）.<br>
     * @param key   键
     * @param value 值
     * @param second 超时时间（秒，为null或者负数时，永久存储）
     */
    public static <T> void putWithType(@NonNull String key, T value, Integer second) {
        if (value == null) { return; }
        byte[] data = parse2Byte(value, true);
        putData(key, data, second);
    }

    private static void putData(String key, byte[] data, Integer second) {
        Duration timeout = getTimeout(second);
        if (timeout != null) {
            redisTemplate.opsForValue().set(key, data, timeout);
        } else {
            redisTemplate.opsForValue().set(key, data);
        }
    }

    /**
     * 获取通过put()方法放入的对象.<br>
     * @param key 键
     * @return 查询到的值
     */
    @Deprecated
    public static <V> V get(@NonNull String key) {
        return get(key, null);
    }

    /**
     * 获取通过put()方法放入的对象.<br>
     * @param key  键
     * @param type 待转换的数据类型
     * @return 查询到的值
     */
    public static <V> V get(@NonNull String key, Type type) {
        byte[] data = (byte[]) redisTemplate.opsForValue().get(key);
        return parse2Object(data, type);
    }

    /* ******* 集合操作 ******* */
    /**
     * 添加集合元素（批量）.<br>
     * @param key   键
     * @param value 值
     * @param second 超时时间（秒，为null或者负数时，永久存储）
     */
    public static void putSetAll(@NonNull String key, Collection<?> value, Integer second) {
        if (value == null) { return; }
        value.forEach((val)->{
            if (val != null) {
                byte[] data = parse2Byte(value, false);
                redisTemplate.opsForSet().add(key, data);
            }
        });
        expire(key, second);
    }

    /**
     * 添加集合元素（单个）.<br>
     * @param key   键
     * @param value 值
     * @param second 超时时间（秒，为null或者负数时，永久存储）
     */
    public static <V> void putSetItem(@NonNull String key, V value, Integer second) {
        if (value == null) { return; }
        byte[] data = parse2Byte(value, false);
        redisTemplate.opsForSet().add(key, data);
        expire(key, second);
    }

    /**
     * 删除集合元素.<br>
     * @param key   键
     * @param value 值
     */
    public static <V> void removeSetItem(@NonNull String key, V value) {
        if (value == null) { return; }
        byte[] data = parse2Byte(value, false);
        redisTemplate.opsForSet().remove(key, data);
    }

    /**
     * 获取集合所有元素.<br>
     * @param key   键
     */
    @Deprecated
    public static <V> Set<V> getSets(String key) {
        return getSets(key, null);
    }

    /**
     * 获取集合所有元素.<br>
     * @param key  键
     * @param type 待转换的数据类型
     * @return 查询到的值
     */
    public static <V> Set<V> getSets(@NonNull String key, Type type) {
        Set<Object> members = redisTemplate.opsForSet().members(key);
        if (members == null) { return new HashSet<>(); }
        Set<V> result = new HashSet<>();
        for (Object item : members) {
            V temp = parse2Object((byte[])item, type);
            result.add(temp);
        }
        return result;
    }

    /**
     * 集合中是否存在.<br>
     * @param key   键
     * @param value 值
     */
    public static <V> boolean existSetItem(@NonNull String key, V value) {
        if (value == null) { return false; }
        byte[] data = parse2Byte(value, false);
        Boolean result = redisTemplate.opsForSet().isMember(key, data);
        return (result != null) && result;
    }

    /* ******* 列表操作 ******* */
    /**
     * 添加列表元素（批量）.<br>
     * @param key     键
     * @param value   值
     * @param second 超时时间（秒，为null或者负数时，永久存储）
     */
    public static <V> void putListAll(@NonNull String key, Collection<V> value, Integer second) {
        if (value == null) { return; }
        List<byte[]> list = new ArrayList<>();
        for (V item : value) {
            byte[] data = parse2Byte(item, false);
            list.add(data);
        }
        redisTemplate.opsForList().rightPushAll(key, list);
        expire(key, second);
    }

    /**
     * 添加列表元素（单个）.<br>
     * @param key     键
     * @param value   值
     * @param second 超时时间（秒，为null或者负数时，永久存储）
     */
    public static <V> void putListItem(@NonNull String key, V value, Integer second) {
        if (value == null) { return; }
        byte[] data = parse2Byte(value, false);
        redisTemplate.opsForList().rightPush(key, data);
        expire(key, second);
    }

    /**
     * 删除列表中的元素.<br>
     * @param key   键
     * @param value 值
     * @return true：删除成功，false：删除失败
     */
    public static <V> boolean removeListItem(@NonNull String key, V value) {
        if (value == null) { return false; }
        byte[] data = parse2Byte(value, false);
        Long size = redisTemplate.opsForList().remove(key, 0, data);
        return (size != null) && (size > 0);
    }

    /**
     * 获取列表所有数据.<br>
     * @param key 键
     * @param type 数据类型
     * @return 查询到的值
     */
    public static <V> List<V> getList(@NonNull String key, Type type) {
        Long len = redisTemplate.opsForList().size(key);
        if ((len == null) || (len == 0)) { return new ArrayList<>(); }
        List<Object> list = redisTemplate.opsForList().range(key, 0, len);
        if (list == null) { return new ArrayList<>(); }
        List<V> result = new ArrayList<>();
        for (Object item : list) {
            V temp = parse2Object((byte[])item, type);
            result.add(temp);
        }
        return result;
    }

    /**
     * 获取最先放入列表的数据（无阻塞）.<br>
     * @param key 键
     * @param type 数据类型
     * @return 查询到的值
     */
    public static <V> V getListPop(@NonNull String key, Type type) {
        byte[] data = (byte[]) redisTemplate.opsForList().leftPop(key);
        return parse2Object(data, type);
    }

    /**
     * 获取最先放入列表的数据（阻塞型）.<br>
     * @param key 键
     * @param type 数据类型
     * @param timeout 等待时间（ms）
     * @return 查询到的值
     */
    public static <V> V getListPop(@NonNull String key, Type type, long timeout) {
        byte[] data = (byte[]) redisTemplate.opsForList().leftPop(key, timeout, TimeUnit.MILLISECONDS);
        return parse2Object(data, type);
    }

    /* ******* HASH操作 ******* */
    /**
     * 添加 Hash 数据
     * @param key 键
     * @param field 列
     * @param value 值
     * @param second 超时时间（秒，为null或者负数时，永久存储）
     */
    public static <V> void putHash(@NonNull String key, @NonNull String field, V value, Integer second) {
        if (value == null) { return; }
        redisTemplate.opsForHash().put(key, field, parse2Byte(value, false));
        expire(key, second);
    }

    /**
     * 获取 Hash 数据
     * @param key 键
     * @param field 列
     * @return 查询到的值
     */
    public static <V> V getHash(@NonNull String key, @NonNull String field) {
        return getHash(key, field, null);
    }

    /**
     * 获取 Hash 数据
     * @param key 键
     * @param field 列
     * @param type 数据类型
     * @return 查询到的值
     */
    public static <V> V getHash(@NonNull String key, @NonNull String field, Type type) {
        byte[] data = (byte[]) redisTemplate.opsForHash().get(key, field);
        return parse2Object(data, type);
    }

    /* ******* 通用操作 ******* */
    /** 是否存在指定键. */
    public static boolean exist(@NonNull String key) {
        if (StringUtil.isNull(key)) { return false; }
        Boolean flag = redisTemplate.hasKey(key);
        return (flag != null) && flag;
    }

    /** 删除缓存数据. */
    public static void remove(@NonNull String key) {
        if (StringUtil.isNull(key)) { return; }
        redisTemplate.delete(key);
    }

    /**
     * 删除所有匹配表达式的key. <br>
     * 注：尽量不使用，效率较低
     * @param pattern 正则表达式
     */
    public static void removeKeys(@NonNull String pattern) {
        if (StringUtil.isNull(pattern)) { return; }
        Set<String> keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }

    /**
     * 获取KEY的有效期（毫秒）.<br>
     * @param key 键
     * @return 剩余过期时间（毫秒，-1 表示永久有效）
     */
    public static Long expire(@NonNull String key) {
        if (StringUtil.isNull(key)) { return null; }
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }

    /**
     * 设置KEY的有效期（秒）.<br>
     * @param key    键
     * @param second 超时时间（秒）
     */
    public static void expire(@NonNull String key, Integer second) {
        if (StringUtil.isNull(key)) { return; }
        Duration timeout = getTimeout(second);
        if (timeout != null) {
            redisTemplate.expire(key, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 设置KEY的过期时间.<br>
     * @param key  键
     * @param date 过期时间
     */
    public static void expire(@NonNull String key, Date date) {
        if (StringUtil.isNull(key) || (date == null)) { return; }
        redisTemplate.expireAt(key, date);
    }

    /** 获取超时时间. */
    private static Duration getTimeout(Integer second) {
        if ((second == null) || (second < 0)) {
            return null;
        } else {
            return Duration.ofSeconds(second.longValue());
        }
    }

    /* ******* 缓存操作 ******* */
    /**
     * 将redis数据在本地缓存1分钟，减少网络交互.<br>
     * @param key 键
     * @param type 数据类型
     */
    @SuppressWarnings("unchecked")
    public static <V> V getLocalCache(@NonNull String key, Type type) {
        if (StringUtil.isNull(key)) { return null; }
        long thisTime = System.currentTimeMillis();
        Long time = INSTANCE.cacheKeys.get(key);
        if ((null == time) || (time < thisTime)) {
            V val = RedisUtil.get(key, type);
            if (val == null) {
                INSTANCE.cacheData.remove(key);
            } else {
                INSTANCE.cacheData.put(key, val);
            }
            INSTANCE.cacheKeys.put(key, thisTime + CACHE_EXPIRES);
            return val;
        } else {
            return (V)INSTANCE.cacheData.get(key);
        }
    }

    /** 清除本地缓存 */
    public static void clearLocalCache(@NonNull String key) {
        INSTANCE.cacheData.remove(key);
        INSTANCE.cacheKeys.remove(key);
    }

    /** 清除本地过期的数据 */
    public static void clearLocalCache() {
        long thisTime = System.currentTimeMillis();
        INSTANCE.cacheKeys.forEach((key, val) -> {
            if (val < thisTime) {
                INSTANCE.cacheData.remove(key);
            }
        });
    }

    /** 将对象转换为byte数组 */
    private static <T> byte[] parse2Byte(T source, boolean withType) {
        if (source instanceof byte[]) {
            return (byte[]) source;
        }
        try (
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        ) {
            if (withType) {
                JsonUtil.serializeWithType(source, writer);
            } else {
                JsonUtil.serialize(source, writer);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new SerializationException("redis-json序列化出错", e);
        }
    }

    /** 将byte数组转换为对象 */
    private static <T> T parse2Object(byte[] data, Type type) {
        if (data == null) { return null; }
        try (
            ByteArrayInputStream inst = new ByteArrayInputStream(data);
            InputStreamReader reader = new InputStreamReader(inst, StandardCharsets.UTF_8);
        ) {
            return JsonUtil.deserialize(reader, type);
        } catch (Exception e) {
            throw new SerializationException("redis-json反序列化出错", e);
        }
    }
}
