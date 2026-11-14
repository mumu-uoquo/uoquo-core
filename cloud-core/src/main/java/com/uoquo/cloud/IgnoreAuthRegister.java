/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.BaseCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

/**
 * 描述：微服务启动就绪后，将免登录的 URL 登记到 Redis，供网关做登录和权限校验的第一层拦截. <br>
 * 规则：{@link IgnoreAuth#all()} 或 {@link IgnoreAuth#login()} 为 true 的 URL，
 *      放入全局免登录集合 {@link BaseCacheKey#GLOBAL_PERMISSION}.<br>
 * 说明：
 * <ul>
 *   <li>网关只负责登录和权限校验，参数签名校验在应用层完成，因此不登记免签名（params）的 URL。</li>
 *   <li>服务自身的拦截器完全由注解判断，不读取该集合。</li>
 *   <li>注解的取值规则与拦截器一致：方法上有注解时以方法为准，否则取类上的注解。</li>
 *   <li>登记的是服务内的请求路径（含 {@code server.servlet.context-path}），路径模式（如 {@code /user/{id}}）按原样登记。</li>
 *   <li>全局集合是永久的，为了在接口变化后能清除已经不存在的 URL，每个应用会把本次登记的 URL 另存一份
 *       （{@link BaseCacheKey#GLOBAL_PERMISSION_APP_PREFIX} + 应用名），下次启动时，
 *       从全局集合中删除"上次登记过、本次已不存在"的 URL。</li>
 *   <li>先登记新的，再删除旧的（而不是先全部删除），避免重启期间网关短暂拦截登录等接口。</li>
 * </ul>
 * 备注：不同应用的上下文前缀不同，登记的 URL 不会重复；滚动发布期间旧实例的处理由运维层面负责.
 * @author  uoquo team
 */
public class IgnoreAuthRegister implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(IgnoreAuthRegister.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            register(event.getApplicationContext());
        } catch (Exception e) {
            // Redis 异常或扫描异常不应影响服务启动
            log.error("登记免登录的URL到Redis失败.", e);
        }
    }

    private void register(ApplicationContext context) {
        Set<String> permission = scan(context);

        // 1. 登记到全局集合
        RedisUtil.putSetAll(BaseCacheKey.GLOBAL_PERMISSION, permission, null);

        // 2. 清理上次登记过、本次已经不存在的URL
        String appName = ServiceAppKeyRegister.getAppName();
        if (appName == null) {
            log.warn("未配置[app.name]，无法记录本应用登记的免登录URL，接口变化后已删除的URL不会被清理.");
        } else {
            String appKey = BaseCacheKey.GLOBAL_PERMISSION_APP_PREFIX + appName;
            Set<String> previous = RedisUtil.getSets(appKey, String.class);
            int removed = 0;
            for (String url : previous) {
                if (!permission.contains(url)) {
                    RedisUtil.removeSetItem(BaseCacheKey.GLOBAL_PERMISSION, url);
                    removed++;
                    log.info("移除已不存在的免登录URL：{}", url);
                }
            }
            // 3. 记录本次登记的URL（替换上一次的记录）
            RedisUtil.remove(appKey);
            RedisUtil.putSetAll(appKey, permission, null);
            if (removed > 0) {
                log.info("已移除 {} 个已不存在的免登录URL.", removed);
            }
        }
        log.info("已登记免登录URL {} 个.", permission.size());
        if (log.isDebugEnabled()) {
            log.debug("免登录URL：{}", permission);
        }
    }

    /**
     * 扫描当前应用中，{@code all()} 或 {@code login()} 为 true 的 URL.
     */
    private Set<String> scan(ApplicationContext context) {
        String contextPath = context.getEnvironment().getProperty("server.servlet.context-path", "");
        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        Set<String> permission = new TreeSet<>();
        for (RequestMappingHandlerMapping mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            String prefix = contextPath;
            mapping.getHandlerMethods().forEach((info, handler) -> {
                IgnoreAuth ignoreAuth = findIgnoreAuth(handler);
                if (ignoreAuth == null || !(ignoreAuth.all() || ignoreAuth.login())) {
                    return;
                }
                for (String pattern : info.getPatternValues()) {
                    if (StringUtil.notNull(pattern)) {
                        permission.add(prefix + pattern);
                    }
                }
            });
        }
        return permission;
    }

    /**
     * 获取注解（与拦截器的规则一致：先方法，后类）.
     * @return 没有注解时返回 null
     */
    private IgnoreAuth findIgnoreAuth(HandlerMethod handler) {
        IgnoreAuth annotation = handler.getMethodAnnotation(IgnoreAuth.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(handler.getBeanType(), IgnoreAuth.class);
        }
        return annotation;
    }
}
