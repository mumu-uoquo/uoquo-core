/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import com.uoquo.utils.crypto.SnowFlake;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.event.Level;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * 描述：配置文件读取类. <br>
 * 目的：在程序启动的最早期（Spring 容器初始化之前）解析配置文件，供工具类和前置判断使用，
 * 因此本类只依赖 slf4j 和 snakeyaml，不依赖 Spring、Jackson 等重量级组件.<br>
 * 配置来源与顺序（后加载的覆盖先加载的，每一步先 classpath、后 {应用目录}/config 目录）：
 * <ol>
 *   <li>application.properties、application.yml</li>
 *   <li>bootstrap.properties、bootstrap.yml</li>
 *   <li>指定环境的配置：application-{profile}.*、bootstrap-{profile}.*<br>
 *       环境通过 -Dspring.profiles.active 或环境变量 SPRING_PROFILES_ACTIVE 指定，支持逗号分隔的多个环境（后面的覆盖前面的）；
 *       未指定时，依次查找 prod、demo、test、dev，使用第一个存在的环境.</li>
 * </ol>
 * 规则：
 * <ul>
 *   <li>properties文件按 UTF-8 读取；yml支持多文档（---），文档中的 spring.config.activate.on-profile 条件按生效的环境判断
 *       （指定的环境；未指定时为按默认顺序找到的环境）.</li>
 *   <li>占位符：${key}、${key:默认值}，支持嵌套（如 ${a:${b:c}}）. key 先从配置中查找，再从系统属性、环境变量中查找
 *       （环境变量同时支持 SPRING_PROFILES_ACTIVE 这样的大写下划线形式）. 循环引用会记录错误日志，并按未找到处理.</li>
 *   <li>yml中的列表以 JSON 数组字符串保存（如 ["a","b"]）.</li>
 *   <li>配置文件存在但解析失败时记录 ERROR 日志，该文件被忽略（但仍视为该环境的配置文件存在，不会回退到其他环境）.</li>
 *   <li>缓存至JVM内存，只是当前节点实例有效. 运行期通过 put 设置的值，在 refresh 后依然保留.</li>
 *   <li>启动阶段日志级别默认 INFO，可通过 -Dapp.log.level（或环境变量）调整；日志中的敏感配置（密码、密钥等）会被屏蔽.</li>
 * </ul>
 * 日期：2018-01-18 17:16 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-18     xuhz.           创建
 * 1.1          2026-11-10     uoquo team       重写占位符解析（修复多占位符错位、循环引用栈溢出）；properties按UTF-8读取；
 *                                              yml支持多文档、非字符串key，解析失败记录ERROR；支持多个环境；
 *                                              日志脱敏；增加静态refresh（先构建后原子替换）；去掉对JsonUtil的依赖
 * </pre>
 * @since   JDK 1.8
 * @version 1.1
 * @author  uoquo team
 */
public class Config {
    /**
     * 日志对象.
     */
    protected static final Logger log = LoggerFactory.getLogger(Config.class);

    /** 默认的环境查找顺序（未指定环境时，使用第一个存在的）. */
    private static final String[] DEFAULT_PROFILES = { "prod", "demo", "test", "dev" };
    /** yml 文档的环境条件. */
    private static final String KEY_ON_PROFILE = "spring.config.activate.on-profile";
    /** 敏感配置的key（日志中屏蔽其值）. */
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)(pass|pswd|pwd|secret|token|credential|private|apikey|access-?key|[._-]key$)");

    /** 锁：refresh 与 put 互斥. */
    private static final Object LOCK = new Object();

    /** 当前生效的配置（引用整体替换，读取无需加锁）. */
    private static volatile Map<String, String> config = new ConcurrentHashMap<>();

    /** 运行期通过 put 设置的值（refresh 后依然保留）. */
    private static final Map<String, String> RUNTIME = new ConcurrentHashMap<>();

    /**
     * 当前生效的环境：指定的环境；未指定时，为按默认顺序（prod、demo、test、dev）找到的第一个存在配置文件的环境.<br>
     * 应用运行期间环境不会改变（变化需要重启），因此保存为静态字段，用于确定读取哪些环境的配置文件，
     * 以及判断yml文档的 on-profile 条件.
     */
    private static volatile List<String> activeProfiles = List.of();

    /** 启动阶段日志级别（Spring 还没有实例化，logback 默认为 DEBUG，因此自行控制）. */
    private static volatile Level logLevel = Level.INFO;

    /**
     * 当前应用编码（2位16进制）.
     */
    public static volatile String APP_CODE = "00";

    /**
     * 当前应用节点（1位16进制）.
     */
    public static volatile String APP_NODE = "0";

    /**
     * 当前应用运行类型（dev，test，demo，prod）.
     */
    public static volatile String APP_TYPE = "prod";

    /**
     * 当前应用运行目录.
     */
    public static volatile String APP_PATH = null;

    /**
     * 当前应用缓存目录.
     */
    public static volatile String TEMP_PATH = null;

    static {
        refresh();
    }

    private Config() {
    }

    // ------------------------------------------------------------------------------------------------
    // 对外方法
    // ------------------------------------------------------------------------------------------------

    /**
     * 添加被缓存的对象.
     * 
     * @param key   键
     * @param value 值（null：删除该配置）
     */
    public static void put(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key不能为空");
        }
        synchronized (LOCK) {
            if (value == null) {
                RUNTIME.remove(key);
                config.remove(key);
            } else {
                RUNTIME.put(key, value);
                config.put(key, value);
            }
        }
    }
    
    /**
     * 添加被缓存的对象.
     * 
     * @param key   键
     * @param value 值（null：删除该配置）
     */
    public static void put(String key, Boolean value) {
        put(key, value == null ? null : value.toString());
    }
    
    /**
     * 添加被缓存的对象.
     * 
     * @param key   键
     * @param value 值（null：删除该配置）
     */
    public static void put(String key, Number value) {
        put(key, value == null ? null : value.toString());
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
     * @return 字符串（已去除首尾空白）
     */
    public static String getString(String key, String def) {
        String value = (key == null) ? null : config.get(key);
        return value == null ? def : value.trim();
    }

    /**
     * 得到key的值（字符串）. <br>
     * 默认值：空字符串<br>
     * 说明：将配置值的UTF-8字节，按指定字符集重新解释为字符串.
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
            warn("parse string of [{}] from 'UTF-8' to '{}' error.", key, charset, e);
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
            warn("parse int of [{}] error. value=[{}]", key, mask(key, val));
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
            warn("parse long of [{}] error. value=[{}]", key, mask(key, val));
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
     * 默认值：false<br>
     * true、on、yes、1（忽略大小写）为true，其他为false.
     * @param  key 取得其值的键
     * @param  def 默认值
     * @return 数字
     */
    public static boolean getBoolean(String key, boolean def) {
        String val = getString(key);
        if (StringUtil.isNull(val)) {
            return def;
        }
        return "true".equalsIgnoreCase(val) || "on".equalsIgnoreCase(val)
                || "yes".equalsIgnoreCase(val) || "1".equals(val);
    }

    /**
     * 获取所有的key
     */
    public static Set<String> getKeys() {
        return new HashSet<>(config.keySet());
    }

    /**
     * 获取当前生效的环境（指定的环境；未指定时为按默认顺序找到的第一个存在配置文件的环境）.
     * @return 环境列表（不可修改），没有可用的环境时为空列表
     */
    public static List<String> getActiveProfiles() {
        return activeProfiles;
    }

    /**
     * 刷新缓存. <br>
     * 重新读取配置文件，全部解析成功后才整体替换当前配置（刷新过程中，其他线程读取到的始终是完整的配置）；
     * 运行期通过 put 设置的值会保留. 应用编码或节点不合法时抛出{@link IllegalStateException}，当前配置不变.
     */
    public static void refresh() {
        synchronized (LOCK) {
            initLogLevel();
            debug("refresh config cache.");
            String appPath = Path.of(".").toAbsolutePath().normalize().toString(); // 执行命令所在位置（如：/home/app/jetty-8007）
            activeProfiles = resolveActiveProfiles(appPath);
            info("active profiles: {}", activeProfiles);
            // 1. 读取配置文件
            Map<String, String> raw = loadFiles(appPath);
            // 2. 解析配置文件（替换其中的变量）
            Map<String, String> resolved = resolveAll(raw);
            resolved.putAll(RUNTIME);
            // 3. 全局变量
            String code = trimToDefault(resolved.get("app.code"), "00");
            String node = trimToDefault(resolved.get("app.node"), "0");
            String type = trimToDefault(resolved.get("app.type"), "prod");
            String temp = trimToDefault(resolved.get("app.temp-dir"), appPath + "/temp");
            try {
                // 实例化雪花算法，无业务逻辑，仅用来校验应用码和节点码是否合法
                new SnowFlake(code, node, true);
            } catch (RuntimeException e) {
                error("invalid app.code [{}] or app.node [{}]: {}", code, node, e.getMessage());
                throw new IllegalStateException(
                        String.format("应用编码app.code[%s]或节点app.node[%s]不合法: %s", code, node, e.getMessage()), e);
            }
            // 4. 整体替换
            config    = new ConcurrentHashMap<>(resolved);
            APP_PATH  = appPath;
            APP_CODE  = code;
            APP_NODE  = node;
            APP_TYPE  = type;
            TEMP_PATH = temp;
            // 设置系统变量，供异常码使用
            System.setProperty("app.code", code);
            System.setProperty("app.node", node);
            if (isEnabled(Level.DEBUG)) {
                debug("cache config finished. {}", maskAll(resolved));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 读取配置文件
    // ------------------------------------------------------------------------------------------------

    /**
     * 确定生效的环境，应用运行期间不会改变（环境变化需要重启应用）.<br>
     * 优先使用指定的环境（-Dspring.profiles.active 优先于环境变量 SPRING_PROFILES_ACTIVE，多个环境以逗号分隔）；
     * 未指定时，按 prod、demo、test、dev 的顺序，取第一个存在配置文件的环境；都不存在则为空.
     * @param appPath 应用运行目录
     */
    private static List<String> resolveActiveProfiles(String appPath) {
        String active = System.getProperty("spring.profiles.active");
        if (StringUtil.isNull(active)) {
            active = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        List<String> list = new ArrayList<>();
        if (StringUtil.notNull(active)) {
            for (String item : active.split(",")) {
                if (!item.trim().isEmpty()) {
                    list.add(item.trim());
                }
            }
            return Collections.unmodifiableList(list);
        }
        for (String profile : DEFAULT_PROFILES) {
            if (existsProfileFile(appPath, profile)) {
                return List.of(profile);
            }
        }
        return List.of();
    }

    /**
     * 指定环境的配置文件是否存在（classpath 或 {应用目录}/config 目录下，任意一个即可）.
     */
    private static boolean existsProfileFile(String appPath, String profile) {
        for (String name : new String[] { "application", "bootstrap" }) {
            for (String ext : new String[] { ".properties", ".yml" }) {
                String fileName = name + "-" + profile + ext;
                if ((classLoader().getResource(fileName) != null) || Files.isRegularFile(Path.of(appPath, "config", fileName))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 按加载顺序读取所有的配置文件.
     */
    private static Map<String, String> loadFiles(String appPath) {
        Map<String, String> map = new HashMap<>();
        // 优先加载默认配置
        readConfigFile(map, appPath, "application", null);
        readConfigFile(map, appPath, "bootstrap",   null);
        // 再加载生效环境的配置（多个环境时，后面的覆盖前面的）
        for (String profile : activeProfiles) {
            readConfigFile(map, appPath, "application", profile);
            readConfigFile(map, appPath, "bootstrap",   profile);
        }
        return map;
    }

    /**
     * 加载配置文件内容（properties 和 yml）.
     * @param type 环境（为空则读取默认配置）
     */
    private static void readConfigFile(Map<String, String> map, String appPath, String name, String type) {
        String base = (type == null) ? name : name + "-" + type;
        loadFile(map, appPath, base + ".properties", false);
        loadFile(map, appPath, base + ".yml",        true);
    }

    /**
     * 加载一个配置文件：先读 classpath，再读 {应用目录}/config 目录.
     */
    private static void loadFile(Map<String, String> map, String appPath, String fileName, boolean yml) {
        debug("cache config from file [{}] start.", fileName);
        // 1. 读取classpath下的配置
        String text = null;
        try (InputStream in = classLoader().getResourceAsStream(fileName)) {
            if (in != null) {
                text = readText(in);
            }
        } catch (IOException e) {
            error("read config from classpath file [{}] error: {}", fileName, e.getMessage());
        }
        if (text != null) {
            if (parse(map, text, yml, "classpath file [" + fileName + "]")) {
                info("cache config from classpath file [{}] end.", fileName);
            }
        } else {
            debug("cache config from classpath file [{}] not found.", fileName);
        }
        // 2. 读取APP_PATH下的config
        Path path = Path.of(appPath, "config", fileName);
        if (Files.isRegularFile(path)) {
            try {
                String content = readText(Files.newInputStream(path));
                if (parse(map, content, yml, "app_path file [" + path + "]")) {
                    info("cache config from app_path file [{}] end.", path);
                }
            } catch (IOException e) {
                error("read config from app_path file [{}] error: {}", path, e.getMessage());
            }
        } else {
            debug("cache config from app_path file [{}] not found.", path);
        }
    }

    /** 加载 classpath 资源的类加载器（线程上下文类加载器为空时，使用本类的类加载器）. */
    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = Config.class.getClassLoader();
        }
        return (loader == null) ? ClassLoader.getSystemClassLoader() : loader;
    }

    /**
     * 按UTF-8读取文本（去除BOM），并关闭输入流.
     */
    private static String readText(InputStream in) throws IOException {
        try (InputStream is = in) {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return (!text.isEmpty() && text.charAt(0) == '\uFEFF') ? text.substring(1) : text;
        }
    }

    /**
     * 解析配置内容到map中（全部解析成功才写入）.
     * @return 是否解析成功
     */
    private static boolean parse(Map<String, String> map, String text, boolean yml, String desc) {
        Map<String, String> temp = new HashMap<>();
        try {
            if (yml) {
                parseYml(temp, text);
            } else {
                Properties props = new Properties();
                props.load(new StringReader(text));
                for (String key : props.stringPropertyNames()) {
                    temp.put(key, props.getProperty(key));
                }
            }
        } catch (YAMLException | IllegalArgumentException | IOException e) {
            error("parse config from {} error, the file is ignored: {}", desc, e.getMessage());
            return false;
        }
        map.putAll(temp);
        return true;
    }

    /**
     * 解析yml（支持多文档）为properties格式.<br>
     * 一个yml文件可以用 --- 分成多个文档，每个文档可以通过 spring.config.activate.on-profile 声明自己只在某些环境下生效：
     * <pre>
     * spring:
     *   config:
     *     activate:
     *       on-profile: dev   # 只在dev环境生效；"!dev"表示非dev环境生效；"dev,test"表示任意一个即可
     * </pre>
     * 这里使用当前生效的环境（{@link #activeProfiles}，在{@link #refresh()}时确定）判断：
     * 条件满足的文档才合并到配置中，不满足的文档整个跳过；没有声明条件的文档始终生效.<br>
     * 注：环境条件与当前正在读取哪个文件无关（例如读取 application.yml 时，其中 on-profile: dev 的文档，
     * 是否生效取决于运行时生效的环境），因此使用全局的生效环境，而不是由文件名推断.
     * @param out  解析结果
     * @param text yml内容
     */
    private static void parseYml(Map<String, String> out, String text) {
        List<Object> docs = new ArrayList<>();
        for (Object doc : newYaml().loadAll(text)) { // 先全部解析，任何一个文档有语法错误，则整个文件都不生效
            docs.add(doc);
        }
        for (Object doc : docs) {
            if (doc == null) {
                continue;
            }
            if (!(doc instanceof Map)) {
                warn("yml document is not a map, ignored.");
                continue;
            }
            Map<String, String> temp = new HashMap<>();
            flatten(temp, null, (Map<?, ?>) doc);
            // 文档的环境条件
            String onProfile = temp.remove(KEY_ON_PROFILE);
            if ((onProfile == null) || matchProfile(onProfile)) {
                out.putAll(temp);
            }
        }
    }

    /**
     * 创建yml解析器：安全模式，且不把日期格式的值转为Date.
     */
    private static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        DumperOptions dumper  = new DumperOptions();
        return new Yaml(new SafeConstructor(options), new Representer(dumper), dumper, options, new NoTimestampResolver());
    }

    /**
     * 不识别时间戳的解析器（2024-01-01 保持为字符串）.
     */
    private static final class NoTimestampResolver extends Resolver {
        @Override
        public void addImplicitResolver(Tag tag, Pattern regexp, String first, int limit) {
            if (!Tag.TIMESTAMP.equals(tag)) {
                super.addImplicitResolver(tag, regexp, first, limit);
            }
        }
    }

    /**
     * 判断环境条件：逗号分隔，以“!”开头表示取反，满足其中任意一项即可（与当前生效的环境比较）.
     */
    private static boolean matchProfile(String condition) {
        List<String> profiles = activeProfiles;
        for (String item : condition.split("[,\\[\\]\"\\s]+")) {
            if (item.isEmpty()) {
                continue;
            }
            if (item.startsWith("!")) {
                if (!profiles.contains(item.substring(1))) {
                    return true;
                }
            } else if (profiles.contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将yaml的map展开为properties格式.
     * @param prefix 前缀
     */
    private static void flatten(Map<String, String> out, String prefix, Map<?, ?> map) {
        for (Map.Entry<?, ?> item : map.entrySet()) {
            String key = String.valueOf(item.getKey()); // yaml的key可能是数字、布尔值
            if ((prefix != null) && !prefix.isEmpty()) {
                key = prefix + "." + key;
            }
            Object val = item.getValue();
            if (val == null) {
                continue;
            } else if (val instanceof Map) {
                flatten(out, key, (Map<?, ?>) val); // 递归处理
            } else if ((val instanceof Collection) || (val instanceof Object[])) {
                StringBuilder sb = new StringBuilder();
                toJson(sb, val);
                out.put(key, sb.toString());
            } else {
                out.put(key, val.toString());
            }
        }
    }

    /**
     * 序列化为JSON（仅用于yml中的列表等复杂值，不依赖Jackson）.
     */
    private static void toJson(StringBuilder sb, Object val) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof Boolean) {
            sb.append(val);
        } else if (val instanceof Number) {
            double d = ((Number) val).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                quote(sb, val.toString());
            } else {
                sb.append(val);
            }
        } else if (val instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> item : ((Map<?, ?>) val).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, String.valueOf(item.getKey()));
                sb.append(':');
                toJson(sb, item.getValue());
            }
            sb.append('}');
        } else if ((val instanceof Collection) || (val instanceof Object[])) {
            Collection<?> items = (val instanceof Collection)
                    ? (Collection<?>) val
                    : Arrays.asList((Object[]) val);
            sb.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                toJson(sb, item);
            }
            sb.append(']');
        } else {
            quote(sb, val.toString());
        }
    }

    private static void quote(StringBuilder sb, String str) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------------------------------------
    // 占位符解析
    // ------------------------------------------------------------------------------------------------

    /**
     * 解析所有配置中的占位符.
     */
    private static Map<String, String> resolveAll(Map<String, String> raw) {
        Map<String, String> resolved = new HashMap<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String key : raw.keySet()) {
            resolveKey(key, raw, resolved, visiting);
        }
        return resolved;
    }

    /**
     * 解析指定key的值（带缓存，检测循环引用）.
     * @return 解析后的值；key不存在或者发生循环引用时返回null
     */
    private static String resolveKey(String key, Map<String, String> raw, Map<String, String> resolved,
                                     Set<String> visiting) {
        String done = resolved.get(key);
        if (done != null) {
            return done;
        }
        String val = raw.get(key);
        if (val == null) {
            return null;
        }
        if (!visiting.add(key)) {
            error("circular reference of config: {} -> {}", String.join(" -> ", visiting), key);
            return null;
        }
        try {
            String result = resolveText(val, raw, resolved, visiting);
            resolved.put(key, result);
            return result;
        } finally {
            visiting.remove(key);
        }
    }

    /**
     * 替换文本中的占位符 ${key}、${key:默认值}（支持嵌套）.
     */
    private static String resolveText(String text, Map<String, String> raw, Map<String, String> resolved,
                                      Set<String> visiting) {
        if (text.indexOf("${") < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("${", i)) {
                int end = findClose(text, i + 2);
                if (end < 0) { // 没有结束符，按普通文本处理
                    sb.append(text, i, text.length());
                    break;
                }
                String inner = text.substring(i + 2, end);
                int colon = findColon(inner);
                String name = (colon < 0 ? inner : inner.substring(0, colon));
                String def  = (colon < 0 ? null  : inner.substring(colon + 1));
                name = resolveText(name, raw, resolved, visiting).trim(); // key中也可能有占位符
                String val = resolveKey(name, raw, resolved, visiting);
                if (val == null) {
                    val = getBySystem(name); // 若没有缓存，则从系统配置或环境变量中查找
                }
                if (val == null) {
                    if (def != null) {
                        val = resolveText(def, raw, resolved, visiting); // spring以冒号分割，前一个为变量，后一个为默认值
                    } else {
                        warn("config placeholder \"${{}}\" is unknown, replaced with empty string.", name);
                        val = "";
                    }
                }
                sb.append(val);
                i = end + 1;
            } else {
                sb.append(text.charAt(i++));
            }
        }
        return sb.toString();
    }

    /**
     * 查找 `${` 对应的 `}`（支持嵌套）.
     * @param from `${` 之后的位置
     * @return `}` 的位置，没有则返回-1
     */
    private static int findClose(String str, int from) {
        int depth = 0;
        for (int i = from; i < str.length(); i++) {
            if (str.startsWith("${", i)) {
                depth++;
                i++;
            } else if (str.charAt(i) == '}') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /**
     * 查找 key 与默认值之间的冒号（不在嵌套的占位符内）.
     */
    private static int findColon(String str) {
        int depth = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.startsWith("${", i)) {
                depth++;
                i++;
            } else if (str.charAt(i) == '}') {
                depth--;
            } else if ((str.charAt(i) == ':') && (depth == 0)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取系统配置或环境变量. <br>
     * 顺序：系统属性 &gt; 环境变量 &gt; 环境变量的大写下划线形式（如 spring.profiles.active 对应 SPRING_PROFILES_ACTIVE）.
     * @param key 键
     * @return 设置的值，没有则返回null
     */
    private static String getBySystem(String key) {
        if (key.isEmpty()) {
            return null;
        }
        String val = System.getProperty(key);
        if (StringUtil.isNull(val)) {
            val = System.getenv(key);
        }
        if (StringUtil.isNull(val)) {
            val = System.getenv(key.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_"));
        }
        return StringUtil.isNull(val) ? null : val;
    }

    private static String trimToDefault(String val, String def) {
        return (val == null || val.trim().isEmpty()) ? def : val.trim();
    }

    // ------------------------------------------------------------------------------------------------
    // 日志（此时spring还没有实例化，因此自行控制日志级别；敏感配置屏蔽）
    // ------------------------------------------------------------------------------------------------

    private static void initLogLevel() {
        String level = System.getProperty("app.log.level");
        if (StringUtil.isNull(level)) {
            level = System.getenv("APP_LOG_LEVEL");
        }
        if (StringUtil.notNull(level)) {
            try {
                logLevel = Level.valueOf(level.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // 不合法的级别，保持原有级别
            }
        }
    }

    private static boolean isEnabled(Level level) {
        return logLevel.toInt() <= level.toInt();
    }

    /** 屏蔽敏感配置的值. */
    private static String mask(String key, String val) {
        return (key != null && SENSITIVE_KEY.matcher(key).find()) ? "******" : val;
    }

    private static Map<String, String> maskAll(Map<String, String> map) {
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, String> item : map.entrySet()) {
            result.put(item.getKey(), mask(item.getKey(), item.getValue()));
        }
        return result;
    }

    private static void debug(String key, Object... args) {
        if (isEnabled(Level.DEBUG)) {
            log.debug(key, args);
        }
    }
    private static void info(String key, Object... args) {
        if (isEnabled(Level.INFO)) {
            log.info(key, args);
        }
    }
    private static void warn(String key, Object... args) {
        if (isEnabled(Level.WARN)) {
            log.warn(key, args);
        }
    }
    private static void error(String key, Object... args) {
        if (isEnabled(Level.ERROR)) {
            log.error(key, args);
        }
    }
}
