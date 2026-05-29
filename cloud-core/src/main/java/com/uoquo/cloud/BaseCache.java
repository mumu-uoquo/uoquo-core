/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.ThreadPoolUtil;
import com.uoquo.web.cache.CacheCallback;
import com.uoquo.web.exception.SystemErrorException;
import com.uoquo.utils.spring.RedisUtil;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.redis.serializer.SerializationException;

/**
 * 描述：缓存基础�? <br>
 * 备注：因为缓存更新频率相对比较小，所以暂时不考虑多节点分布式并发问题，每次放入数据时�?
 *      都会同步数据到redis，并累加版本号，不考虑别的节点更新过的情况。如有该需求，请慎用！�?br>
 * 原理：采用EHCache和redis配合使用。根据版本号决定是从本地返回数据还是redis中返回数据，
 *      为了减少网络请求，同步的版本号有效期30秒，超过30秒从redis中再次获取，
 *      因此极端情况下缓存数据会�?0秒的延时�?br>
 * 使用：子类提供getInstance的单例方法，或者提供静态的get、put方法，方便使用�?<br>
 * 日期�?018-04-10 09:13 <br>
 * 变更�?
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-04-10     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Deprecated
public abstract class BaseCache<E> {

    protected static final Logger log = LoggerFactory.getLogger(BaseCache.class);

    // 缓存分组名称（key前缀�?
    protected String group;
    // 本地临时缓存有效期（即：本地数据多久与redis同步一次，也即：数据滞后时间）
    protected static int localPeriod  = 30; // 单位s
    protected static int REDIS_PERIOD = -1; // 数据有效期，默认长期有效（如果设置时长，redis会到期自动清除，将导致新启动的应用无法拿到真正的缓存，因此只要放入缓存的数据，都认为长期有效�?
    protected CacheCallback<E> callback;

    protected Map<String, Object>  cacheLocal   = new ConcurrentHashMap<>(); // 缓存的数据，仅当前应用有效，不需要同步至redis(key, VERSION_key)
    protected Map<String, E>       cacheSingle  = new ConcurrentHashMap<>(); // 缓存的单个数�?key)
    protected Map<String, List<E>> cacheList    = new ConcurrentHashMap<>(); // 缓存的列表数�?key)
    protected Map<String, Long>    cacheTime    = new ConcurrentHashMap<>(); // 缓存数据的创�?更新时间(key)
    protected Map<String, Long>    cacheExpiry  = new ConcurrentHashMap<>(); // 缓存数据的过期时�?ms);(VERSION_key, key, VER_key)
    protected Map<String, Integer> cacheVersion = new ConcurrentHashMap<>(); // 缓存的数�?0秒内从redis获取的版本号(key)

    /**
     * 构造函�?
     * @param group     分组名称
     * @param valueType 缓存数据的类�?
     */
    protected BaseCache(final String group, Class<E> valueType) {
        this(group, valueType, localPeriod);
    }

    /**
     * 构造函�?
     * @param group     分组名称
     * @param valueType 缓存数据的类�?
     * @param period    本地缓存与redis通信频率（单位：S，默�?0S，即：数据滞后时间）
     */
    protected BaseCache(String group, Class<E> valueType, int period) {
        this(group, valueType, new CacheCallback() {}, period);
    }

    /**
     * 构造函�?
     * @param group     分组名称
     * @param valueType 缓存数据的类�?
     * @param period    本地缓存与redis通信频率（单位：S，默�?0S，即：数据滞后时间）
     */
    protected BaseCache(final String group, Class<E> valueType, CacheCallback<E> callback, int period) {
        this(group, valueType, callback);
        if (period > 0) {
            localPeriod = period;
        }
    }

    /**
     * 构造函�?
     * @param group     分组名称
     * @param valueType 缓存数据的类�?
     * @param callback  回调函数
     */
    protected BaseCache(final String group, Class<E> valueType, final CacheCallback<E> callback) {
        if (callback == null) {
            throw new SystemErrorException("callback不能为null");
        }
        if (StringUtil.isNull(group)) {
            throw new IllegalArgumentException("缓存组名不能为空");
        }
        this.group    = group;
        this.callback = callback;
        // 定时扫描是否有过期数据（频率1S�?
        ThreadPoolUtil.execute(new Runnable() {
            @Override
            public void run() {
                // 没有需要过期数据，则不处理
                if (cacheExpiry.isEmpty()) {
                    return;
                }
                // 遍历需要处理的过期时间
                long nowTime  = System.currentTimeMillis();
                Iterator<Map.Entry<String, Long>> it = cacheExpiry.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Long> entry = it.next();
                    String key = entry.getKey();
                    try {
                        Long expiry   = entry.getValue();
                        Long dataTime = cacheTime.get(key);
                        // 判断是否达到过期时间
                        boolean flag = false; // 默认不删除对应数�?
                        if ((dataTime == null) || (nowTime - dataTime.longValue() >= expiry)) {
                            flag = true;
                        }
                        if (!flag) {
                            continue; // 没到期则不删�?
                        }
                        if (log.isDebugEnabled() && (dataTime != null)) {
                            log.debug("key={}, expiry={}, time={}, flag={}", key, expiry, nowTime - dataTime.longValue(), flag);
                        }
                        it.remove();
                        remove(key); // 删除对应数据（因为有回调，所以最后删除）
                    } catch (Exception e) {
                        log.error("cache expire error, group={}, key={}", group, key, e);
                    }
                }
            }

            // 删除数据
            private void remove(String key) {
                if (key.startsWith("VER_")) {
                    String temp = key.substring(4);
                    cacheVersion.remove(temp);
                    cacheTime.remove(key);
                    Integer version = (Integer)cacheLocal.get("VERSION_" + temp);
                    try {
                        callback.afterExpires(temp, version); // 过期删除回调
                    } catch (Exception e) {
                        log.warn("callback local version expires error, group={}, key={}, version={}", group, temp, version, e);
                    }
                } else if (cacheSingle.containsKey(key)) {
                    E info = cacheSingle.get(key);
                    cacheSingle.remove(key);
                    cacheTime.remove(key);
                    Integer version = (Integer)cacheLocal.get("VERSION_" + key);
                    try {
                        callback.afterExpires(key, info, version); // 过期删除回调
                    } catch (Exception e) {
                        log.warn("callback single expires error, group={}, key={}, version={}, info={}", group, key, version, info, e);
                    }
                } else if (cacheList.containsKey(key)) {
                    List<E> info = cacheList.get(key);
                    cacheList.remove(key);
                    cacheTime.remove(key);
                    Integer version = (Integer)cacheLocal.get("VERSION_" + key);
                    try {
                        callback.afterExpires(key, info, version); // 过期删除回调
                    } catch (Exception e) {
                        log.warn("callback list expires error, group={}, key={}, version={}, info={}", group, key, version, info, e);
                    }
                } else if (cacheLocal.containsKey(key)) {
                    cacheLocal.remove(key);
                    cacheTime.remove(key);
                }
            }
        }, 1);
    }

    /**
     * 获取本地缓存的对�?<br>
     * @param key   缓存的数据键
     * @return 查询到的对象
     */
    public Object getLocal(String key) {
        key = group + "-" + key; // 添加名称前缀
        return cacheLocal.get(key);
    }

    /**
     * 添加本地缓存的对�?<br>
     * 注：不会放入远端redis
     * @param key   缓存的数据键
     * @param value 缓存的数据�?
     */
    public void putLocal(String key, Object value) {
        putLocal(key, value, REDIS_PERIOD);
    }

    /**
     * 添加本地缓存的对�?<br>
     * 注：不会放入远端redis
     * @param key   缓存的数据键
     * @param value 缓存的数据�?
     * @param timeout 缓存有效期（s�?
     */
    public void putLocal(String key, Object value, Integer timeout) {
        key = group + "-" + key; // 添加名称前缀
        cacheLocal.put(key, value);
        expire(key, timeout);
    }

    /**
     * 获取被缓存的对象.<br>
     * @param key 缓存的数据键
     * @return 查询到的对象
     */
    public E get(String key) {
        key = group + "-" + key; // 添加名称前缀
        // 如果30秒内与远端交互过，则直接返回
        if (cacheVersion.containsKey(key)) {
            return cacheSingle.get(key);
        }
        try {
            Type elementType = getElementType();
            String verKey = "VERSION_" + key;
            Integer verRedis = RedisUtil.get(verKey, Integer.class); // 远端的版本号
            Integer verLocal = (Integer)cacheLocal.get(verKey);      // 本地的版本号
            // 两边都没有对应的版本号，说明没有数据
            if ((verRedis == null) && (verLocal == null)) {
                cacheVersion.put(key, 0);
                expire("VER_" + key, localPeriod); // 与redis同步的版本号仅保�?0s
                return null;
            }
            E temp = null;
            if (verRedis == null) {
                // 远端没有，则保存到远�?
                temp = cacheSingle.get(key);
                if (temp == null) {
                    RedisUtil.remove(key); // 说明从本地删除了，因此删除远�?
                    RedisUtil.put(verKey, verLocal, null);
                } else {
                    Integer timeout = getExpire(key);
                    if (timeout == null) {
                        RedisUtil.put(key, temp, null);
                        RedisUtil.put(verKey, verLocal, null);
                    } else {
                        // 如果timeout / 1000 == 0，说明即将过期，因此删除远端数据也是正常�?
                        RedisUtil.put(key, temp, timeout);
                        RedisUtil.put(verKey, verLocal, null);
                    }
                }
                cacheVersion.put(key, verLocal);
            } else if ((verLocal == null) || !verRedis.equals(verLocal)) {
                // 本地没有或者两边版本不一致时，保存到本地
                temp = RedisUtil.get(key, elementType);
                if (temp == null) {
                    cacheSingle.remove(key); // 说明从远端删除了，因此删除本�?
                } else {
                    Long timeout = RedisUtil.expire(key);
                    cacheSingle.put(key, temp);
                    log.debug("cache from redis, key={}, info={}, timeout={}", key, temp, timeout);
                    if ((timeout != null) && (timeout > 0)) {
                        expire(key, (int)(timeout / 1000));
                        //expire(verKey, timeout / 1000);
                    }
                }
                cacheLocal.put(verKey, verRedis);
                cacheVersion.put(key,  verRedis);
                try {
                    callback.afterChange(key, temp, verRedis);
                } catch (Exception e) {
                    log.warn("callback change error, group={}, key={}, info={}", group, key, temp, e);
                }
            } else {
                // 两边版本一致，不同步，以本地数据为�?
                temp = cacheSingle.get(key);
                cacheVersion.put(key, verLocal);
            }
            expire("VER_" + key, localPeriod); // 与redis同步的版本号仅保�?0s
            return temp;
        } catch (SerializationException e) {
            // 当出现序列化错误时，说明类有更新，此时将本地的数据重新放入redis
            log.warn("get single value from redis serialization error. key = {}", key, e);
            E temp = cacheSingle.get(key);
            if (temp != null) {
                Integer timeout = getExpire(key);
                try {
                    if (timeout == null) {
                        RedisUtil.put(key, temp, null);
                        updateVersion(key, null);
                    } else {
                        RedisUtil.put(key, temp, timeout);
                        // 如果timeout / 1000 == 0，说明即将过期，因此删除远端数据也是正常�?
                        updateVersion(key, timeout);
                    }
                } catch (Exception e2) {
                    log.info("update redis value error. key = {}, val = {}", key, temp, e2);
                }
            }
            return temp;
        } catch (Exception e) {
            log.info("get single value from redis error. key = {}", key, e);
            return cacheSingle.get(key);
        }
    }

    /**
     * 添加被缓存的对象.<br>
     * @param key   缓存的数据键
     * @param value 缓存的数据�?
     */
    public void put(String key, E value) {
        put(key, value, REDIS_PERIOD);
    }

    /**
     * 添加被缓存的对象.<br>
     * @param key   缓存的数据键
     * @param value 缓存的数据�?
     * @param timeout 缓存有效期（s�?
     */
    public void put(String key, E value, Integer timeout) {
        key = group + "-" + key; // 添加名称前缀
        // 更新数据
        cacheSingle.put(key, value);
        expire(key, timeout);
        try {
            RedisUtil.put(key, value, timeout);
        } catch (Exception e) {
            log.warn("put value to redis error. key = {}, data = {}", key, value, e);
        }
        // 更新版本
        updateVersion(key, timeout);
    }

    /**
     * 获取被缓存的List对象.<br>
     * @param key 缓存的数据键
     * @return 查询到的列表对象
     */
    public List<E> getList(String key) {
        key = "LIST_" + group + "-" + key; // 添加名称前缀
        // 如果30秒内与远端交互过，则直接返回
        if (cacheVersion.containsKey(key)) {
            return cacheList.get(key);
        }
        try {
            Type elementType = getElementType();
            String verKey = "VERSION_" + key;
            Integer verRedis = RedisUtil.get(verKey, Integer.class); // 远端的版本号
            Integer verLocal = (Integer)cacheLocal.get(verKey);      // 本地的版本号
            // 两边都没有对应的版本号，说明没有数据
            if ((verRedis == null) && (verLocal == null)) {
                cacheVersion.put(key, 0);
                expire("VER_" + key, localPeriod); // 与redis同步的版本号仅保�?0s
                return null;
            }
            List<E> list = null;
            if (verRedis == null) {
                // 远端没有，则保存到远�?
                list = cacheList.get(key);
                if (list == null) {
                    RedisUtil.remove(key); // 说明从本地删除了，因此删除远�?
                    RedisUtil.put(verKey, verLocal, null);
                } else {
                    Integer timeout = getExpire(key);
                    if (timeout == null) {
                        RedisUtil.putListAll(key, list, null);
                        RedisUtil.put(verKey, verLocal, null);
                    } else {
                        // 如果timeout / 1000 == 0，说明即将过期，因此删除远端数据也是正常�?
                        RedisUtil.putListAll(key, list, timeout);
                        RedisUtil.put(verKey, verLocal, null);
                    }
                }
                cacheVersion.put(key, verLocal);
            } else if ((verLocal == null) || !verRedis.equals(verLocal)) {
                // 本地没有或者两边版本不一致时，保存到本地
                list = RedisUtil.getList(key, elementType);
                if (list == null) {
                    cacheList.remove(key); // 说明从远端删除了，因此删除本�?
                } else {
                    Long timeout = RedisUtil.expire(key);
                    cacheList.put(key, list);
                    if ((timeout != null) && (timeout > 0)) {
                        expire(key, (int)(timeout / 1000));
                        //expire(verKey, timeout / 1000);
                    }
                }
                cacheLocal.put(verKey, verRedis);
                cacheVersion.put(key,  verRedis);
                try {
                    callback.afterChange(key, list, verRedis);
                } catch (Exception e) {
                    log.warn("callback change error, group={}, key={}, info={}", group, key, list, e);
                }
            } else {
                // 两边版本一致，不同步，以本地数据为�?
                list = cacheList.get(key);
                cacheVersion.put(key, verLocal);
            }

            expire("VER_" + key, localPeriod); // 与redis同步的版本号仅保�?0s
            return list;
        } catch (SerializationException e) {
            // 当出现序列化错误时，说明类有更新，此时将本地的数据重新放入redis
            log.warn("get list value from redis serialization error. key = {}", key, e);
            List<E> temp = cacheList.get(key);
            if (temp != null) {
                Integer timeout = getExpire(key);
                try {
                    if (timeout == null) {
                        RedisUtil.putListAll(key, temp, null);
                        updateVersion(key, null);
                    } else {
                        // 如果timeout / 1000 == 0，说明即将过期，因此删除远端数据也是正常�?
                        RedisUtil.putListAll(key, temp, timeout);
                        updateVersion(key, timeout);
                    }
                } catch (Exception e2) {
                    log.info("update redis list value error. key = {}, val = {}", key, temp, e2);
                }
            }
            return temp;
        } catch (Exception e) {
            log.info("get list value from redis error. key = {}", key, e);
            return cacheList.get(key);
        }
    }

    /**
     * 添加被缓存的List对象.<br>
     * @param key   缓存的数据键
     * @param value 列表对象
     */
    public void putList(String key, List<E> value) {
        putList(key, value, REDIS_PERIOD);
    }

    /**
     * 添加被缓存的List对象.<br>
     * @param key   缓存的数据键
     * @param value 列表对象
     * @param timeout 缓存有效期（s�?
     */
    public void putList(String key, List<E> value, Integer timeout) {
        key = "LIST_" + group + "-" + key; // 添加名称前缀
        // 更新数据
        cacheList.put(key, value);
        expire(key, timeout);
        try {
            RedisUtil.putListAll(key, value, timeout);
        } catch (Exception e) {
            log.warn("put list to redis error. key = {}, data = {}, timeout= {}", key, value, timeout, e);
        }
        // 更新版本
        updateVersion(key, timeout);
    }

    /**
     * 添加被缓存的List中的单个元素.<br>
     * @param key   缓存的数据键
     * @param value 列表中的元素
     * @return true：添加，false：没有添�?
     */
    public boolean addList(String key, E value) {
        return addList(key, value, REDIS_PERIOD);
    }

    /**
     * 添加被缓存的List中的单个元素.<br>
     * @param key   缓存的数据键
     * @param value 列表中的元素
     * @param timeout 缓存有效期（s�?
     * @return true：添加，false：没有添�?
     */
    public boolean addList(String key, E value, Integer timeout) {
        List<E> list = getList(key); // 当前缓存的数�?
        if (list == null) {
            list = new ArrayList<>();
        }
        // 更新数据
        list.add(value);
        key = "LIST_" + group + "-" + key; // 添加名称前缀
        cacheList.put(key, list);
        expire(key, timeout);
        try {
            RedisUtil.putListItem(key, value, timeout);
        } catch (Exception e) {
            log.warn("add list to redis error. key = {}, data = {}, timeout= {}", key, value, timeout, e);
        }
        // 更新版本
        updateVersion(key, timeout);
        return true;
    }

    /**
     * 添加被缓存的List中的单个元素.<br>
     * 注：需实现 CacheCallback.skipAdd 方法，当skipAdd返回true时不添加
     * @param key   缓存的数据键
     * @param value 列表中的元素
     * @param callback 跳过条件
     * @return true：添加，false：没有添�?
     */
    public boolean addList(String key, E value, CacheCallback<E> callback) {
        return addList(key, value, callback, REDIS_PERIOD);
    }

    /**
     * 添加被缓存的List中的单个元素.<br>
     * 注：需实现 CacheCallback.skipAdd 方法，当skipAdd返回true时不添加
     * @param key   缓存的数据键
     * @param value 列表中的元素
     * @param callback 跳过条件
     * @param timeout 缓存有效期（s�?
     * @return true：添加，false：没有添�?
     */
    public boolean addList(String key, E value, CacheCallback<E> callback, Integer timeout) {
        if (callback == null) {
            return addList(key, value, timeout);
        }
        List<E> list  = getList(key); // 当前缓存的数�?
        if (list == null) {
            list = new ArrayList<>();
        }
        // 判断是否需要添�?
        for (E item : list) {
            if (callback.skipAdd(item)) {
                return false;
            }
        }
        // 更新数据
        list.add(value);
        key = "LIST_" + group + "-" + key; // 添加名称前缀
        cacheList.put(key, list);
        expire(key, timeout);
        try {
            RedisUtil.putListItem(key, list, timeout);
        } catch (Exception e) {
            log.warn("add list to redis error. key = {}, data = {}, timeout= {}", key, value, timeout, e);
        }
        // 更新版本
        updateVersion(key, timeout);
        return true;
    }

    /**
     * 替换List中满足条件的数据.<br>
     * 注意：替换条件为空，或者没有对应数据时，不做任何处�?br>
     *      需实现 CacheCallback.applyReplace 方法，当applyReplace返回true时替�?
     * @param key   缓存的数据键
     * @param value 替换的数�?
     * @param callback 替换条件
     * @return
     */
    public List<E> replaceList(String key, E value, CacheCallback<E> callback) {
        return replaceList(key, value, callback, REDIS_PERIOD);
    }

    /**
     * 替换List中满足条件的数据.<br>
     * 注意：替换条件为空，或者没有对应数据时，不做任何处�?br>
     *      需实现 CacheCallback.applyReplace 方法，当applyReplace返回true时替�?
     * @param key   缓存的数据键
     * @param value 替换的数�?
     * @param callback 替换条件
     * @param timeout 缓存有效期（s�?
     * @return
     */
    public List<E> replaceList(String key, E value, CacheCallback<E> callback, Integer timeout) {
        if (callback == null) {
            return new ArrayList<>();
        }
        List<E> list1  = getList(key); // 当前缓存的数�?
        if ((list1 == null) || (list1.size() == 0)) {
            return new ArrayList<>();
        }
        key = "LIST_" + group + "-" + key;  // 添加名称前缀
        List<E> list2  = new ArrayList<>(); // 替换后的数据
        List<E> result = new ArrayList<>(); // 替换的数�?
        // 替换本地元素
        for (E item : list1) {
            if (callback.applyReplace(item)) {
                result.add(item);
                if (value != null) {
                    list2.add(value);
                }
            } else {
                list2.add(item);
            }
        }
        if (result.size() == 0) {
            return result;// 没有满足条件的，不操�?
        }
        cacheList.put(key, list2);
        expire(key, timeout);
        try {
            RedisUtil.putListAll(key, list2, timeout); // 直接放替换后的数�?
        } catch (Exception e) {
            log.info("replace list value from redis error. key = {}", key, e);
        }
        updateVersion(key, timeout);
        return result;
    }

    /**
     * 删除List中满足条件的数据.<br>
     * 注意：删除条件为空，或者没有对应数据时，不做任何处�?br>
     *      需实现 CacheCallback.applyRemove 方法，当applyReplace返回true时删�?
     * @param key   缓存的数据键
     * @param callback 删除条件
     * @return
     */
    public List<E> removeList(String key, CacheCallback<E> callback) {
        return removeList(key, callback, REDIS_PERIOD);
    }

    /**
     * 删除List中满足条件的数据.<br>
     * 注意：删除条件为空，或者没有对应数据时，不做任何处�?br>
     *      需实现 CacheCallback.applyRemove 方法，当applyReplace返回true时删�?
     * @param key   缓存的数据键
     * @param callback 删除条件
     * @param timeout 缓存有效期（s�?
     * @return
     */
    public List<E> removeList(String key, CacheCallback<E> callback, Integer timeout) {
        List<E> list1  = getList(key); // 当前缓存的数�?
        if ((list1 == null) || (list1.size() == 0)) {
            return new ArrayList<>();
        }
        if (callback == null) {
            return new ArrayList<>();
        }
        key = "LIST_" + group + "-" + key;  // 添加名称前缀
        List<E> list2  = new ArrayList<>(); // 删除后的数据
        List<E> result = new ArrayList<>(); // 删除的数�?
        // 删除本地元素
        for (E item : list1) {
            if (callback.applyRemove(item)) {
                result.add(item);
            } else {
                list2.add(item);
            }
        }
        if (result.size() == 0) {
            return result;// 没有满足条件的，不操�?
        }
        cacheList.put(key, list2);
        expire(key, timeout);
        // 删除远端元素
        try {
            RedisUtil.putListAll(key, list2, timeout); // 直接放删除后的数�?
        } catch (Exception e) {
            log.info("remove list value from redis error. key = {}", key, e);
        }
        updateVersion(key, timeout);
        return result;
    }

    /**
     * 删除被缓存的对象.
     * @param key   缓存的数据键
     */
    public void removeList(String key) {
        key = "LIST_" + group + "-" + key; // 添加名称前缀
        try {
            String  verKey = "VERSION_" + key;
            Integer verRedis = RedisUtil.get(verKey, Integer.class); // 远端的版本号
            Integer verLocal = (Integer)cacheLocal.get(verKey);      // 本地的版本号
            if ((verRedis == null) && (verLocal == null)) {
                return; // 两边都没有的时候，不需要做任何处理
            }
            List<E> info = cacheList.get(key);
            cacheList.remove(key); // 删除列表数据
            RedisUtil.remove(key); // 远程redis删除
            try {
                callback.afterRemove(key, info); // 删除回调
            } catch (Exception e) {
                log.warn("callback remove error, group={}, key={}, info={}", group, key, info, e);
            }
            log.debug("remove cacheList [{}]", key);
            updateVersion(key, null);
        } catch (Exception e) {
            log.info("remove cacheList error [{}]", key, e);
        }
    }

    /**
     * 删除被缓存的对象.
     * @param key   缓存的数据键
     */
    public void remove(String key) {
        String gkey = group + "-" + key;
        // 本地个性化数据，则直接删除
        if (cacheLocal.containsKey(gkey)) {
            cacheLocal.remove(gkey);
            log.debug("remove cacheLocal [{}]", gkey);
            return;
        }

        try {
            String  verKey = "VERSION_" + gkey;
            Integer verRedis = RedisUtil.get(verKey, Integer.class); // 远端的版本号
            Integer verLocal = (Integer)cacheLocal.get(verKey);      // 本地的版本号
            if ((verRedis == null) && (verLocal == null)) {
                removeList(key); // 两边都没有单个数据的时候，尝试删除列表
                return;
            }
            E info = cacheSingle.get(key);
            cacheSingle.remove(gkey); // 删除单个数据
            RedisUtil.remove(gkey);   // 远程redis删除
            try {
                callback.afterRemove(key, info); // 删除回调
            } catch (Exception e) {
                log.warn("callback remove error, group={}, key={}, info={}", group, key, info, e);
            }
            log.debug("remove cacheSingle [{}]", gkey);
            updateVersion(key, null);
        } catch (Exception e) {
            log.info("remove cacheList error [{}]", gkey, e);
        }
    }

    /**
     * 删除所有的缓存对象.
     */
    public void removeAll() {
        try {
            // 清除远端数据（不清除version信息�?
            RedisUtil.removeKeys(group + "*");
            RedisUtil.removeKeys("LIST_" + group + "*");
            // 清除本地缓存的远端数�?
            cacheSingle.clear();
            cacheList.clear();
            cacheVersion.clear();
            // 删除本地本身数据
            cacheExpiry.clear();
            cacheTime.clear();
            cacheLocal.clear();
        } catch (Exception e) {
            log.warn("remove [{}] all cache", group, e);
        }
    }

    /**
     * 更新版本信息.<br>
     * 注：版本号不做过期处理，永久保存
     * @param key     缓存的数据键
     * @param timeout 缓存时间（为null时，不更新缓存时间）
     */
    private void updateVersion(String key, Integer timeout) {
        String verKey = "VERSION_" + key;
        Integer verRedis = null;
        try {
            verRedis = RedisUtil.get(verKey, Integer.class);
        } catch (Exception e) {
            log.info("get version from redis error. key = {}", verKey, e);
        }
        if (verRedis == null) {
            verRedis = cacheLocal.containsKey(verKey) ? (Integer)cacheLocal.get(verKey) : 0;
        }
        int verNew = verRedis + 1; // 新的版本号，版本号以远端为准，当前对象覆盖远端数据，注：会存在多节点并发问题
        // 本地永久版本�?用于判断是否从redis获取新的数据�?
        cacheLocal.put(verKey, verNew);
        // 本地临时版本号（用于判断是否直接返回本地数据），仅保留固定时间（如：30秒）
        cacheVersion.put(key,  verNew);
        expire("VER_" + key, localPeriod);
        // 更新redis远端版本
        try {
            RedisUtil.put(verKey, verNew, null);
        } catch (Exception e) {
            log.info("set version to redis error. key = {}", verKey, e);
        }
        log.debug("update [{}] verion, redis.version={}, new.version={}", key, verRedis, verNew);
    }

    /**
     * 设置单个缓存数据的有效期（秒�?<br>
     * 说明：主要是会拼接key的group前缀
     * 注意：timeout &lt;= 0，相当于执行remove操作，会删除对应的数�?
     * @param key     �?
     * @param timeout 超时时间（秒�?
     */
    public void expireSingle(String key, Integer timeout) {
        if ((timeout != null) && (timeout > 0)) {
            key = group + "-" + key;  // 添加名称前缀
            expire(key, timeout);
        }
    }

    /**
     * 设置列表缓存数据的有效期（秒�?<br>
     * 说明：主要是会拼接key的LIST_group前缀
     * 注意：timeout &lt;= 0，相当于执行remove操作，会删除对应的数�?
     * @param key     �?
     * @param timeout 超时时间（秒�?
     */
    public void expireList(String key, Integer timeout) {
        if ((timeout != null) && (timeout > 0)) {
            key = "LIST_" + group + "-" + key;  // 添加名称前缀
            expire(key, timeout);
        }
    }

    /**
     * 设置KEY的有效期（秒�?<br>
     * 注意：timeout &lt;= 0，相当于执行remove操作，会删除对应的数�?
     * @param key     �?
     * @param timeout 超时时间（秒�?
     */
    private void expire(String key, Integer timeout) {
        if ((timeout != null) && (timeout > 0)) {
            // 本地缓存的有效期添加一个与redis同步周期内的随机数，从而避免多节点同时触发过期回调导致的并�?
            int random = 0;
            if (!key.startsWith("VER_")) {
                random = (int)(Math.random() * localPeriod);
            }
            cacheTime.put(key, System.currentTimeMillis());
            cacheExpiry.put(key, timeout.intValue() * 1000L + random);
        }
    }

    /**
     * 刷新缓存.<br>
     */
    public abstract void refresh();

    /**
     * 剩余过期时间.
     * @param key �?
     * @return 剩余时间（单位秒�?
     */
    protected Integer getExpire(String key) {
        Long expire = cacheExpiry.get(key);
        Long create = cacheTime.get(key);
        if ((expire == null) || (create == null)) {
            return null;
        }
        // 总过期时�?- 已缓存时�?
        long mill = expire - (System.currentTimeMillis() - create);
        return (int)(mill / 1000L);
    }

    protected Type getElementType() {
        ParameterizedType superGenericSuperclass = (ParameterizedType) this.getClass().getGenericSuperclass();
        return superGenericSuperclass.getActualTypeArguments()[0];
    }
}
