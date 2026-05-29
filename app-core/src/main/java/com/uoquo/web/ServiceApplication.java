/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.ThreadPoolUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.event.Level;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import jakarta.annotation.PreDestroy;
import javax.management.*;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Set;

/**
 * 描述：应用启动基类，无特殊作用，相关注入的注解都在config类中定义。<br>
 * 说明：如果发布到外部tomcat时，项目的具体启动类直接继承 SpringBootServletInitializer，
 *      不可以在父类中继承，否则外部tomcat会报错，无法启动，导致发布失败。<br>
 * 备注：
 * <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config"> spring properties的加载顺序</a>
 * <pre>
 * 1. 项目的具体启动类，需要继承自该接口，并添加注解
 *     {@code @SpringBootApplication}
 * 2. 微服务项目，启动类需要添加注解
 *     {@code @EnableDiscoveryClient}
 * 3. 需要部署到外部容器时，启动类需要继承
 *     {@code SpringBootServletInitializer}
 * </pre>
 * 日期：2018-01-25 10:06 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-25     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class ServiceApplication implements ApplicationRunner {
    protected static final Logger log = LoggerFactory.getLogger(ServiceApplication.class);

    /**
     * 初始化配置参数 <br>
     * 备注：启动类的main第一行应该执行该方法
     */
    protected static void init() {
        // 设置默认日志级别，用于在logback未初始化时的日志输出
        if (StringUtil.isNull(System.getProperty("app.log.level"))) {
            System.setProperty("app.log.level", Level.INFO.toString());
        }
        // 设置“无头”模式，用于在服务端生成验证码等场景（测试是不生效T_T）
        System.setProperty("java.awt.headless", "true");
        // 设置程序根目录
        System.setProperty("app.path", Config.APP_PATH);
        // 由于HostInfoEnvironmentPostProcessor优先于配置文件，因此需要将网络过滤相关信息放入环境变量中
        // 参见：https://blog.csdn.net/xichenguan/article/details/76636342
        System.setProperty("spring.cloud.inetutils.preferred-networks", Config.getString("app.preferred.networks"));
        // 如果没有指定运行模式，则采用配置文件中指定的模式
        String activeType = System.getProperty("spring.profiles.active");
        if (StringUtil.isNull(activeType)) {
            System.setProperty("spring.profiles.active", Config.getString("app.type", "prod"));
        }
        /*
         * WEB临时目录.<br>
         * 注意：必须配置为绝对目录，否则时间长了之后将会出错.<br>
         * 参考：
         * <ul>
         *   <li>https://www.jianshu.com/p/88b04815f043</li>
         *   <li>https://www.jianshu.com/p/7f9a8b44bd94</li>
         *   <li>https://www.cnblogs.com/nuccch/p/11546494.html</li>
         * </ul>
         */
        File tmpDir = new File(Config.getString("spring.servlet.multipart.location", Config.APP_PATH + "/temp"));
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        // TODO 每次启动时删除已有的临时文件
        System.setProperty("spring.servlet.multipart.location", tmpDir.getAbsolutePath()); // 必须是绝对路径

        // 非开发模式，需要先进行授权校验
//        if (!"dev".equals(Config.APP_TYPE)) {
//            log.info("main license");
//            License.verify();
//        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 项目启动成功后，会立即执行该方法
        // 常用于更新缓存、初始化数据等处理
    }

    /* *********************************** 系统关闭时触发. *********************************** */
    /* 主要用于在关闭时，停止多线程，实现优雅关机。三种实现方法:                                       */
    /* 1. 实现 ApplicationListener<ContextClosedEvent>                                       */
    /* 2. 注解 @EventListener({ ContextClosedEvent.class })                                  */
    /* 3. 注解 @PreDestroy                                                                   */
    /* 其中1和2会被触发多次，因为关闭注入的servlet、各个feign-service、系统本身都会被触发，2优先与1,     */
    /* 方法3只会被触发一次.                                                                     */
    /* ************************************************************************************* */
    /*
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // do nothing
    }

    @EventListener({ ContextClosedEvent.class })
    public void onContextClosedEvent(ContextClosedEvent event) {
        // do nothing
    }

    @PreDestroy
    public void shutdown() {
        ThreadPoolUtil.shutdown();
    }
    */

    @PreDestroy
    public void shutdown() {
        ThreadPoolUtil.shutdown();
    }

    /* *********************************** 获取容器端口（外部部署时使用）. *********************************** */
    /**
     * 获取容器端口.
     */
    protected int getPort() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException {
        try {
            return getTomcatPort();
        } catch (Exception e1) {
            try {
                return getJetty9Port();
            } catch (Exception e2) {
                return getJetty8Port();
            }
        }
    }

    /**
     * 获取tomcat容器端口.
     */
    private int getTomcatPort() throws MalformedObjectNameException {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = mbs.queryNames(new ObjectName("*:type=Connector,*"), Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
        ObjectName obj = names.iterator().next();
        String port = obj.getKeyProperty("port");
        log.warn("tomcat port :{}", port);
        return Integer.parseInt(port);
    }

    /**
     * 获取jetty 9容器端口.
     * https://stackoverflow.com/questions/21474066/jetty-get-webapp-list/21766925#21766925
     */
    private int getJetty9Port() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        /* 应用上下文
        final ObjectName webappcontext9 = new ObjectName("org.eclipse.jetty.webapp:context=*,type=webappcontext,id=*");
        final Set<ObjectName> webappcontexts9 = mbs.queryNames(webappcontext9, null);
        for (final ObjectName objectName : webappcontexts9) {
            log.warn("context:{}", objectName.getKeyProperty("context"));
        }
        */
        // 端口
        Set<ObjectName> names = mbs.queryNames(new ObjectName("org.eclipse.jetty.server:context=*,type=serverconnector,id=*"), null);
        ObjectName obj = names.iterator().next();
        Object port = mbs.getAttributes(obj, new String[] {"port"}).asList().getFirst().getValue();
        log.warn("jetty 9 port :{}", port);
        return Integer.parseInt(port.toString());
    }

    /**
     * 获取jetty 8容器端口.
     */
    private int getJetty8Port() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        /* 应用上下文
        final ObjectName webappcontext8 = new ObjectName("org.eclipse.jetty.webapp:type=webappcontext,id=*,name=*");
        final Set<ObjectName> webappcontexts8 = mBeanServerConnection.queryNames(webappcontext8, null);
        for (final ObjectName objectName : webappcontexts8) {
            log.warn("name:{}", objectName.getKeyProperty("name"));
        }
        */
        // 端口
        Set<ObjectName> names = mbs.queryNames(new ObjectName("org.eclipse.jetty.server.nio:type=selectchannelconnector,id=*"), null);
        ObjectName obj = names.iterator().next();
        Object port = mbs.getAttributes(obj, new String[] {"port"}).asList().getFirst().getValue();
        log.warn("jetty 8 port :{}", port);
        return Integer.parseInt(port.toString());
    }
}
