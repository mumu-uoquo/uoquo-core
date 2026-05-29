/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import com.uoquo.utils.crypto.SnowFlake;
import com.uoquo.utils.json.JsonUtil;

import java.io.*;
import java.nio.charset.Charset;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.event.Level;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 描述：配置文件读取类. <br>
 * 说明：缓存规则
 * <ul>
 *    <li>
 *        <b>文件配置（system.properties、bootstrap.yml、application.yml）</b>
 *        <br>缓存至JVM内存，只是当前节点实例有效
 *    </li>
 * </ul>
 * 日期：2018-01-18 17:16 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-18     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class Config {
    /**
     * 日志对象.
     */
    protected static final Logger log = LoggerFactory.getLogger(Config.class);
    private final Pattern p = Pattern.compile("\\$\\{[a-zA-Z0-9_\\-\\.]+[:\\S]*\\}"); // 将从最外层开始往里递归处理

    /**
     * 当前组，主要用于以后做缓存用，跟其他缓存做区分.
     */
    private final String group = "config";

    /**
     * 配置文件内容缓存.
     */
    private final ConcurrentHashMap<String, String> map;
    
    /**
     * 锁，为了保证创建的instance对象唯一.
     */
    private static final Object lock = new Object();
    
    /**
     * 当前类的单例对象.
     */
    private static volatile Config instance = null;
    
    /**
     * 当前应用编码（2位16进制）.
     */
    public static String APP_CODE = "00";
    
    /**
     * 当前应用节点（1位16进制）.
     */
    public static String APP_NODE = "0";
    
    /**
     * 当前应用运行类型（dev，test，demo，prod）.
     */
    public static String APP_TYPE = "prod";

    /**
     * 当前应用运行目录.
     */
    public static String APP_PATH = null;

    /**
     * 当前应用缓存目录.
     */
    public static String TEMP_PATH = null;

    // DEBUG
    private Level LOG_LEVEL = Level.DEBUG;
    
    static {
        getInstance();
    }
    
    /**
     * 构造方法.
     */
    private Config() {
        map = new ConcurrentHashMap<>();
    }
    
    /**
     * 单例工厂.
     */
    private static Config getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new Config();
                    try {
                        String level = instance.getBySystem("app.log.level");
                        if (StringUtil.notNull(level)) {
                            instance.LOG_LEVEL = Level.valueOf(level.toUpperCase());
                        }
                    } catch (Exception e) {
                        // do nothing
                    }
                    try {
                        // 得到的是执行命令所在位置路径（如：/home/app/jetty-8007）
                        File file = new File(".");
                        APP_PATH = file.getAbsolutePath();
                        APP_PATH = APP_PATH.substring(0, APP_PATH.length() - 1); // 去除最后一位的“.”
                        // 各种获取路径方式的比较
                        // class文件方式：
                        // Config.class.getResource("")  // file:/home/app/uoquo-boss/WEB-INF/classes/com/uoquo/utils/
                        // Config.class.getResource(".") // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getResource("/") // file:/home/app/uoquo-boss/WEB-INF/classes/com/uoquo/utils/
                        // Thread.currentThread().getContextClassLoader().getResource("")  // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Thread.currentThread().getContextClassLoader().getResource(".") // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Thread.currentThread().getContextClassLoader().getResource("/") // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getClassLoader().getResource("")                   // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getClassLoader().getResource(".")                  // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getClassLoader().getResource("/")                  // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getProtectionDomain().getCodeSource().getLocation().getPath() // /home/app/uoquo-boss/WEB-INF/classes/
                        
                        // jar包方式：
                        // Config.class.getResource("")  //         jar:file:/home/app/uoquo-boss/WEB-INF/lib/uoquo-utils-1.0.0.jar!/com/uoquo/utils/
                        // Config.class.getResource(".") // tomcat: jar:file:/home/app/uoquo-boss/WEB-INF/lib/uoquo-utils-1.0.0.jar!/com/uoquo/utils/
                                                         // jetty : null
                        // Config.class.getResource("/") // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Thread.currentThread().getContextClassLoader().getResource("")  // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Thread.currentThread().getContextClassLoader().getResource(".") // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Thread.currentThread().getContextClassLoader().getResource("/") // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getClassLoader().getResource("")                   // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getClassLoader().getResource(".")                  // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getClassLoader().getResource("/")                  // file:/home/app/uoquo-boss/WEB-INF/classes/
                        // Config.class.getProtectionDomain().getCodeSource().getLocation().getPath() // /home/app/uoquo-boss/WEB-INF/lib/uoquo-utils-1.0.0.jar
                    } catch (Exception e) {
                        instance.error("get app runtime path error.", e);
                    }
                    // 加载配置
                    instance.refresh();
                }
            }
        }
        return instance;
    }
    
    /**
     * 缓存单个对象. <br>
     * @param key   键
     * @param value 值
     */
    private void putCache(String key, String value) {
        map.put(this.group + "_" + key, value);
    }
    
    /**
     * 获取单个缓存数据. <br>
     * @param key 键
     * @return 缓存的字符串
     */
    private String getCache(String key) {
        return map.get(this.group + "_" + key);
    }

    /**
     * 获取系统配置或环境变量. <br>
     * @param key 键
     * @return 设置的值
     */
    private String getBySystem(String key) {
        String val = System.getProperty(key);
        if (StringUtil.isNull(val)) {
            val = System.getenv(key);
        }
        return val;
    }

    /**
     * 删除单个缓存数据. <br>
     * @param key 键
     * @return 删除前的字符串
     */
    private String removeCache(String key) {
        String tempK = this.group + "_" + key;
        if (map.containsKey(tempK)) {
            String val = map.get(this.group + "_" + key);
            map.remove(this.group + "_" + key);
            return val;
        } else {
            return null;
        }
    }
    
    /**
     * 清空缓存数据.
     */
    private void clear() {
        map.clear();
    }
    
    /**
     * 添加被缓存的对象.
     * 
     * @param key   键
     * @param value 值
     */
    public static void put(String key, String value) {
        if (value == null) {
            getInstance().removeCache(key);
        } else {
            getInstance().putCache(key, value);
        }
    }
    
    /**
     * 添加被缓存的对象.
     * 
     * @param key   键
     * @param value 值
     */
    public static void put(String key, Boolean value) {
        if (value == null) {
            getInstance().removeCache(key);
        } else {
            getInstance().putCache(key, value.toString());
        }
    }
    
    /**
     * 添加被缓存的对象.
     * 
     * @param key   键
     * @param value 值
     */
    public static void put(String key, Number value) {
        if (value == null) {
            getInstance().removeCache(key);
        } else {
            getInstance().putCache(key, value.toString());
        }
    }

    /**
     * 得到key的值（字符串）.<br>
     * 默认值：空字符串
     * @param  key 取得其值的键
     * @return 字符串
     */
    public static String getString(String key) {
        return getString(key, "");
    }

    /**
     * 得到key的值（字符串）.<br>
     * 默认值：空字符串
     * @param  key 取得其值的键
     * @param  def 默认值（当数据不存在时，返回def）
     * @return 字符串
     */
    public static String getString(String key, String def) {
        String value = getInstance().getCache(key);
        return value == null ? def : value.trim();
    }

    /**
     * 得到key的值（字符串）. <br>
     * 默认值：空字符串
     * 
     * @param  key 取得其值的键
     * @param  charset 字符集
     * @return 字符串
     */
    public static String getString(String key, Charset charset) {
        String val = getString(key);
        try {
            val = new String(val.getBytes(StandardCharsets.UTF_8), charset);
        } catch (Exception e) {
            instance.warn("parse string [{}] from 'UTF-8' to '{}' error.", val, charset, e);
        }
        return val;
    }

    /**
     * 得到key的值（数字）. <br>
     * 默认值：0
     *
     * @param  key 取得其值的键
     * @return 数字
     */
    public static int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * 得到key的值（数字）. <br>
     * 默认值：0
     *
     * @param  key 取得其值的键
     * @param  def 默认值
     * @return 数字
     */
    public static int getInt(String key, int def) {
        String val = getString(key);
        if (StringUtil.isNull(val)) {
            return def;
        }
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            instance.warn("parse int [{}] error.", val, e);
            return def;
        }
    }

    /**
     * 得到key的值（数字）. <br>
     * 默认值：0
     *
     * @param  key 取得其值的键
     * @return 数字
     */
    public static long getLong(String key) {
        return getLong(key, 0L);
    }

    /**
     * 得到key的值（数字）. <br>
     * 默认值：0
     *
     * @param  key 取得其值的键
     * @param  def 默认值
     * @return 数字
     */
    public static long getLong(String key, long def) {
        String val = getString(key);
        if (StringUtil.isNull(val)) {
            return def;
        }
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            instance.warn("parse long [{}] error.", val, e);
            return def;
        }
    }

    /**
     * 得到key的值（boolean）. <br>
     * 默认值：false
     * @param  key 取得其值的键
     * @return 数字
     */
    public static boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * 得到key的值（boolean）. <br>
     * 默认值：false
     * @param  key 取得其值的键
     * @param  def 默认值
     * @return 数字
     */
    public static boolean getBoolean(String key, boolean def) {
        String val = getString(key);
        if (StringUtil.isNull(val)) {
            return def;
        } else if ("true".equalsIgnoreCase(val) || "on".equalsIgnoreCase(val) || "1".equalsIgnoreCase(val)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取所有的key
     */
    public static Set<String> getKeys() {
        Config inst = getInstance();
        int idx  = (inst.group + "_").length();
        HashSet<String> sets = new HashSet<>();
        for (String key : inst.map.keySet()) {
            sets.add(key.substring(idx));
        }
        return sets;
    }

    /**
     * 刷新缓存.
     */
    public void refresh() {
        debug("refresh config cache.");
        this.clear(); // 删除所有缓存
        // 缓存配置文件中的数据到JVM内存
        this.cacheFromFile();
        // 缓存数据库中的配置数据
        // 如果本地文件中有该参数，则覆盖JVM内存中的数据
        // 如果本地文件中没有，则远程缓存至redis中
        // 数据库的缓存，由其他地方调用后调用Config.put进行更新和更改
    }

    /**
     * 查找当前的环境类型<br>
     * 注：java -D 优先于 环境变量
     */
    private String getActiveType() {
        String activeType = this.getBySystem("spring.profiles.active");
        if (StringUtil.notNull(activeType)) {
            return activeType;
        }
        activeType = this.getBySystem("spring.config.activate.on-profile");
        if (StringUtil.notNull(activeType)) {
            return activeType;
        }
        return this.getBySystem("SPRING_PROFILES_ACTIVE");
    }

    /**
     * 从Config中获取数据，并缓存.
     */
    private void cacheFromFile() {
        // 1. 指定加载顺序（因为加载到相关文件后，将跳出循环，因此优先加载指定文件）
        List<String> list = new ArrayList<>();
        // 指定环境
        String activeType = this.getActiveType();
        if (StringUtil.notNull(activeType)) {
            list.add(activeType);
        }
        // 默认顺序
        list.add("prod"); // 生产环境
        list.add("demo"); // 演示环境
        list.add("test"); // 测试环境
        list.add("dev" ); // 开发环境
        // 2. 加载配置
        // 优先加载默认配置
        readConfigFile("application", null);
        readConfigFile("bootstrap",   null);
//        readConfigFile("system",      null);
        // 再加载指定配置（匹配后，自动跳出，不再加载其他环境）
        for (String type : list) {
            boolean f1 = readConfigFile("application", type);
            boolean f2 = readConfigFile("bootstrap",   type);
//            boolean f3 = readConfigFile("system",      type);
            if (f1 || f2 || type.equals(activeType)) {
                break; // 加载配置成功，则不继续
            }
        }
        // 3. 解析配置文件（替换其中的变量）
        boolean flag;
        int i = 0;
        do {
            flag = false;
            for (Map.Entry<String, String> item : this.map.entrySet()) {
                String key = item.getKey();
                String val = item.getValue();
                val = parsePropertieValue(val);
                if (val.contains("${")) {
                    debug("the key[{}]'s value[{}] has char '${'.", key, val);
                    flag = true;
                }
                this.map.replace(key, val);
            }
            if (i++ > 10) {
                warn("parse config value too times.");
                break;
            }
        } while (flag);
        // 4. 设置全局变量
        APP_CODE = Config.getString("app.code", APP_CODE);
        APP_NODE = Config.getString("app.node", APP_NODE);
        APP_TYPE = Config.getString("app.type", APP_TYPE);
        TEMP_PATH = Config.getString("app.temp-dir", APP_PATH + "/temp");
        // 实例化雪花算法，无业务逻辑，仅用来校验应用码和节点码是否合法
        new SnowFlake(Config.APP_CODE, Config.APP_NODE, true);
        // 设置系统变量，供异常码使用
        System.setProperty("app.code", Config.APP_CODE);
        System.setProperty("app.node", Config.APP_NODE);
        debug("cache config finished. {}", this.map);
    }

    /**
     * 加载配置文件类容. <br>
     *
     * @param name 文件名
     * @param type 配置文件类型（dev，test，demo，prod）
     * @return 缓存是否成功
     */
    private boolean readConfigFile(String name, String type) {
        boolean flag1 = loadProperties(name, type);
        boolean flag2 = loadYml(name, type);
        return flag1 || flag2;
    }

    /**
     * 加载properties文件类容. <br>
     *
     * @param name 文件名
     * @param type 配置文件类型（dev，test，demo，prod）
     * @return 缓存是否成功
     */
    private boolean loadProperties(String name, String type) {
        String configName;
        if (StringUtil.notNull(type)) {
            configName = String.format("%s-%s.properties", name, type);
        } else {
            configName = String.format("%s.properties", name);
        }
        debug("cache config from file [{}] start.", configName);
        boolean flag = false;
        // 1. 读取classpath下的配置
        try (
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(configName);
        ) {
            parseProperties(inputStream);
            info("cache config from classpath file [{}] end.", configName);
            flag = true;
        } catch (NullPointerException | IOException e) {
            debug("cache config from classpath file [{}] not found.", configName);
        } catch (Exception e) {
            info("cache config from classpath file [{}] error.", configName, e);
        }
        // 2. 读取APP_PATH下的config
        String configPath = Config.APP_PATH + "/config/" + configName;
        try (
            InputStream inputStrem = new FileInputStream(configPath);
        ) {
            parseProperties(inputStrem);
            info("cache config from app_path file [{}] end.", configPath);
            flag = true;
        } catch (NullPointerException | IOException e) {
            debug("cache config from app_path file [{}] not found.", configPath);
        } catch (Exception e) {
            info("cache config from app_path file [{}] error.", configPath, e);
        }
        return flag;
    }

    /**
     * 解析properties文件类容到内存中.
     */
    private void parseProperties(InputStream inputStrem) throws IOException {
        // 读取config内容
        Properties propertie = new Properties();
        propertie.load(inputStrem);
        // 缓存配置内容
        Enumeration<?> enu = propertie.propertyNames();
        while (enu.hasMoreElements()) {
            String key = (String)enu.nextElement();
            String val = propertie.getProperty(key);
            this.putCache(key, val); // 缓存当前对象
        }
    }

    /**
     * 加载yml文件类容. <br>
     *
     * @param name 文件名
     * @param type 配置文件类型（dev，test，demo，prod）
     * @return 缓存是否成功
     */
    private boolean loadYml(String name, String type) {
        String configName = null;
        if (StringUtil.notNull(type)) {
            configName = String.format("%s-%s.yml", name, type);
        } else {
            configName = String.format("%s.yml", name);
        }
        debug("cache config from file [{}] start.", configName);
        boolean flag = false;
        // 1. 读取classpath下的配置
        try (
            InputStream inputStrem = Thread.currentThread().getContextClassLoader().getResourceAsStream(configName);
        ) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.loadAs(inputStrem, Map.class);
            parseYmlMap(null, map);
            info("cache config from classpath file [{}] end.", configName);
            flag = true;
        } catch (NullPointerException | YAMLException e) {
            debug("cache config from classpath file [{}] not found.", configName);
        } catch (Exception e) {
            warn("cache config from classpath file [{}] error.", configName, e);
        }
        // 2. 读取APP_PATH下的config
        String configPath = Config.APP_PATH + "/config/" + configName;
        try (
            InputStream inputStrem = new FileInputStream(configPath);
        ) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.loadAs(inputStrem, Map.class);
            parseYmlMap(null, map);
            info("cache config from app_path file [{}] end.", configPath);
            flag = true;
        } catch (NullPointerException | YAMLException | IOException e) {
            debug("cache config from app_path file [{}] not found.", configPath);
        } catch (Exception e) {
            info("cache config from app_path file [{}] error.", configPath, e);
        }
        return flag;
    }

    /**
     * 解析yaml文件为properties格式
     * @param prev 前缀
     * @param map  值
     */
    private void parseYmlMap(String prev, Map<String, Object> map) {
        if (map == null) {
            return;
        }

        for (Map.Entry<String, Object> item : map.entrySet()) {
            String key = item.getKey();
            Object val = item.getValue();
            if ((prev != null) && !prev.isEmpty()) {
                key = prev +"."+ key;
            }
            if (val == null) {
                // do nothing
            } else if (val instanceof Map) {
                parseYmlMap(key, (Map<String, Object>)val); // 递归处理
            } else if (val instanceof CharSequence) {
                this.putCache(key, val.toString()); // 缓存当前对象
            } else if (val instanceof Number) {
                this.putCache(key, val.toString()); // 缓存当前对象
            } else if (val instanceof Boolean) {
                this.putCache(key, val.toString()); // 缓存当前对象
            } else {
                try {
                    this.putCache(key, JsonUtil.serialize(val)); // 缓存当前对象
                } catch (Exception e) {
                    warn("put \"{}\" = \"{}\" to config cache error", key, val, e);
                    this.putCache(key, val.toString()); // 缓存当前对象
                }
            }
        }
    }

    /**
     * 解析配置，替换配置中的变量值
     * @param val
     */
    private String parsePropertieValue(String val) {
        Matcher m = p.matcher(val);
        while (m.find()) {
            String tkey = m.group();
            int index   = this.endIndex(tkey);
            String tval = tkey.substring(2, index);
            String append = (index < tkey.length() - 1) ? tkey.substring(index + 1) : "";
            String[] strs = tval.split(":",2); // spring以冒号分割，前一个为变量，后一个为默认值
            String val2 = this.getCache(strs[0]);
            if (StringUtil.isNull(val2)) {
                // 若没有缓存，则从系统配置或环境变量中查找
                String temp = this.getBySystem(strs[0]);
                if (temp != null) {
                    val2 = temp;
                }
            }

            if (val2 != null) {
                val = val.replace(tkey, val2) + append;
            } else if (strs.length > 1) {
                val = val.replace(tkey, strs[1]) + append;
            } else {
                warn("parse value \"{}\" for \"{}\" is unknown, or it's null.", val, tkey);
                val = val.replace(tkey, "") + append;
            }
        }
        // 如果还有没替换的，则继续替换
        m = p.matcher(val);
        if (m.find()) {
            return parsePropertieValue(val);
        } else {
            return val;
        }
    }

    /**
     * 查找 `${` 对应的 `}`
     */
    private int endIndex(String str) {
        int bgnIdx = str.indexOf("${");
        int endIdx = str.indexOf("}");
        while (true) {
            int nextBgnIdx = str.indexOf("${", bgnIdx + 1);
            if (nextBgnIdx == -1 || nextBgnIdx > endIdx) {
                break;
            }
            bgnIdx = nextBgnIdx;
            endIdx = str.indexOf("}", endIdx + 1);
        }
        return endIdx;
    }

    // 此时spring还没有实例化，ch.qos.logback.classic.LoggerContext 默认为 DEBUG
    private void debug(String key, Object... args) {
        if (LOG_LEVEL.toInt() <= Level.DEBUG.toInt()) {
            log.debug(key, args);
        }
    }
    private void info(String key, Object... args) {
        if (LOG_LEVEL.toInt() <= Level.INFO.toInt()) {
            log.info(key, args);
        }
    }
    private void warn(String key, Object... args) {
        if (LOG_LEVEL.toInt() <= Level.WARN.toInt()) {
            log.warn(key, args);
        }
    }
    private void error(String key, Object... args) {
        if (LOG_LEVEL.toInt() <= Level.ERROR.toInt()) {
            log.error(key, args);
        }
    }
}
