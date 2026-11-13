/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 描述：启动前初始化配置和常量（系统属性，以及最低优先级的默认配置）. <br>
 * 原理：通过 {@code META-INF/spring.factories} 注册为 {@link SpringApplicationRunListener}，
 *      在 {@code SpringApplication.run()} 开始时（{@link #starting}）自动执行，早于配置文件加载和环境准备，
 *      因此启动类的 main 方法不需要再写 {@code init()}；以 war 部署到外部容器时（不执行 main）同样生效。<br>
 * 备注：
 * <ul>
 *   <li>多次执行只有第一次生效（如 Spring Cloud 的引导上下文会再创建一个 SpringApplication）.</li>
 *   <li>不使用静态 Logger：避免在系统属性设置完成之前提前初始化日志框架.</li>
 *   <li>已经通过 -D 指定的系统属性不会被覆盖.</li>
 * </ul>
 * @author  uoquo team
 */
public class ServiceEnvironmentInitializer implements SpringApplicationRunListener, Ordered {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /** 默认配置属性源的名称（重复添加时同名的会被替换）. */
    private static final String DEFAULT_PROPERTIES_NAME = "uoquoDefaultProperties";

    /** SpringApplication 通过反射调用，构造方法的参数必须是 (SpringApplication, String[]). */
    public ServiceEnvironmentInitializer(SpringApplication application, String[] args) {
    }

    @Override
    public void starting(ConfigurableBootstrapContext bootstrapContext) {
        init();
    }

    /**
     * 环境准备完成后，添加最低优先级的默认配置：WEB临时目录.<br>
     * 作为最低优先级的属性源，-D 参数、环境变量、配置文件、配置中心中的显式配置都会优先生效，只有没有配置时才使用该默认值。<br>
     * 目录必须是绝对路径，否则时间长了之后（系统清理 /tmp 下的临时文件）上传文件将会出错。<br>
     * 参考：
     * <ul>
     *   <li>https://www.jianshu.com/p/88b04815f043</li>
     *   <li>https://www.jianshu.com/p/7f9a8b44bd94</li>
     *   <li>https://www.cnblogs.com/nuccch/p/11546494.html</li>
     * </ul>
     */
    @Override
    public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
        Path tmpDir = Path.of(Config.TEMP_PATH).toAbsolutePath().normalize();
        try {
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            // 创建失败时不提供默认值，使用容器自身的默认目录；此时日志框架可能还没有按配置初始化，输出到标准错误
            System.err.println("创建 WEB 临时目录失败：[" + tmpDir + "] " + e);
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(DEFAULT_PROPERTIES_NAME,
                Map.of("spring.servlet.multipart.location", tmpDir.toString())));
    }

    @Override
    public int getOrder() {
        // 尽量早于其他监听器
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 初始化配置参数（只执行一次）.
     */
    private static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        // 1. 程序根目录（供配置文件、日志配置中的 ${app.path} 使用）
        setIfAbsent("app.path", Config.APP_PATH);

        // 2. 网络过滤相关信息
        // 由于HostInfoEnvironmentPostProcessor优先于配置文件，因此需要将网络过滤相关信息放入系统属性中
        // 参见：https://blog.csdn.net/xichenguan/article/details/76636342
        // 注意：系统属性优先于配置文件，没有配置时不能设置（空值会覆盖配置文件中的值）
        setIfAbsent("spring.cloud.inetutils.preferred-networks", Config.getString("app.preferred.networks"));

        // 3. 运行环境
        // 与 Config 的规则一致：系统属性 > 环境变量 SPRING_PROFILES_ACTIVE > 按 prod、demo、test、dev 查找存在配置文件的环境，
        // 都没有时使用 app.type（默认 prod）。
        // 代码中判断是否生产环境读取的是系统属性，因此需要保证它有值。
        if (StringUtil.isNull(System.getProperty("spring.profiles.active"))) {
            List<String> profiles = Config.getActiveProfiles();
            String active = profiles.isEmpty() ? Config.APP_TYPE : String.join(",", profiles);
            setIfAbsent("spring.profiles.active", active);
        }

        // 非开发模式，需要先进行授权校验
//        if (!"dev".equals(Config.APP_TYPE)) {
//            License.verify();
//        }
    }

    /**
     * 系统属性没有值时才设置（已通过 -D 指定的不覆盖，值为空时不设置）.
     */
    private static void setIfAbsent(String key, String value) {
        if (StringUtil.notNull(value) && StringUtil.isNull(System.getProperty(key))) {
            System.setProperty(key, value);
        }
    }
}
