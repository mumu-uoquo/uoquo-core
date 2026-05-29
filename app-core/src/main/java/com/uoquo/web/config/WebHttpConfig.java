/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ServiceConfig;
import com.uoquo.web.controller.GlobalExceptionResolver;
import com.uoquo.utils.spring.SpringUtil;
import com.uoquo.web.interceptor.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.Parser;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.jspecify.annotations.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author xuhz
 */
@Configuration
@AutoConfigureAfter(ServiceConfig.class)
@ConditionalOnWebApplication
public class WebHttpConfig implements WebMvcConfigurer {
    private final Logger log = LoggerFactory.getLogger(getClass());
    // 统一出错处理地址（不走拦截器）
    @Value("${server.error.path:${error.path:/error}}")
    private String errorPath;
    /**
     * 不走任何过滤器的URL
     */
    protected @NonNull List<String> excludePaths = new ArrayList<>();

    @PostConstruct
    public void setProperties(){
        log.debug("WebHttpConfig init ...");
        // 不走任何过滤器的URL
        excludePaths.addAll(Arrays.asList(errorPath, "/get/version", "/error/**", "/static/**",
                "/**/favicon.ico", "/**/swagger*/**", "/**/api-docs", "/**/api-docs/**"));
    }

    /* ******************* web ******************* */
    /**
     * 静态文件（favicon.ico）.
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // favicon图标处理
        registry.addResourceHandler("/**/favicon.ico").addResourceLocations("classpath:/public/favicon.ico");
    }

    /**
     * 添加参数解析器.<br>
     * 说明：简化入参的处理，整合params和requestBody
     */
    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> argumentResolvers) {
        //argumentResolvers.add(new RequestParamResolver());
    }

//    /**
//     * 添加跨域支持（仅独立应用使用，微服务将跨域迁移到网关处理）.<br>
//     * 需要允许前端携带cookie，同时前端需要配置 withCredentials=true
//     * <pre>
//     * 以jquery为例：
//     * $.ajax({
//     *   xhrFields: {
//     *       withCredentials: true //是否携带cookie
//     *   },
//     *   crossDomain: true,
//     *   ....
//     * });
//     * </pre>
//     */
//    // 方法1（不可以使用，将会与拦截器冲突，导致OPTIONS请求无法通过）
//    @Override
//    public void addCorsMappings(CorsRegistry registry) {
//        registry.addMapping("/**")
//                .allowedOrigins("*") // 允许跨域访问的域名，若有80，443外的端口，也需要带端口号，各个应用最好按实际进行固定，不要采用*来允许所有（如："http://cdn.uoquo.com:8080", "https://cdn.uoquo.com"）
//                .allowedMethods("GET", "POST", "OPTIONS")
//                .allowedHeaders("*")    // 允许的请求头header
//                .allowCredentials(true) // 允许前端发送cookie
//                //.exposedHeaders("*")    // 允许的响应头header
//                .maxAge(3600); // 单位秒，一次OPTIONS预校验的有效期，该期间内跨域请求，不需要再发送预校验请求
//    }
//
//    // 方法2（推荐）
//    @Bean
//    public FilterRegistrationBean corsFilter() {
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowCredentials(true);
//        config.addAllowedOrigin("*"); //Arrays.asList("http://cdn.uoquo.com", "https://cdn.uoquo.com")
//        config.addAllowedOriginPattern("*");
//        config.addAllowedHeader("*");
//        config.addAllowedMethod("*");
//        config.setMaxAge(3600L); //
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//        FilterRegistrationBean bean = new FilterRegistrationBean(new CorsFilter(source));
//        bean.setOrder(0);
//        return bean;
//    }

    /* ******************* web interceptor ****************** */
    /* 顺序：registry的order数值越小越优先.                      */
    /* 注意：只有采用bean注解，拦截器中的service才能正确注入!      */
    /* ****************************************************** */
    @Bean
    @ConditionalOnMissingBean(value = GlobalInterceptor.class, search = SearchStrategy.CURRENT)
    public @NonNull GlobalInterceptor globalInterceptor() {
        log.debug("加载BEAN：GlobalInterceptor");
        return new GlobalInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(value = CurrentUserInterceptorAdapter.class, search = SearchStrategy.CURRENT)
    public @NonNull CurrentUserInterceptorAdapter currentUserInterceptor() {
        log.debug("加载BEAN：CurrentUser4TokenInterceptor");
        return new CurrentUser4TokenInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(value = CheckParamInterceptor.class, search = SearchStrategy.CURRENT)
    public @NonNull CheckParamInterceptor checkParamInterceptor() {
        log.debug("加载BEAN：CheckParamInterceptor");
        return new CheckParamInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(value = CheckLoginInterceptor.class, search = SearchStrategy.CURRENT)
    public @NonNull CheckLoginInterceptor checkLoginInterceptor() {
        log.debug("加载BEAN：CheckLoginInterceptor");
        return new CheckLoginInterceptor();
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        log.debug("加载拦截器：addInterceptors");
        // 用户信息
        registry.addInterceptor(currentUserInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(excludePaths)
                .order(-999);
        // 日志记录
        registry.addInterceptor(globalInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(excludePaths)
                .order(-990);
        // 签名校验
        registry.addInterceptor(checkParamInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(excludePaths)
                .order(-900);
        // 登录拦截
        registry.addInterceptor(checkLoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(excludePaths)
                .excludePathPatterns("/**/login/**", "/**/login*")
                .order(-800);
    }

//    /* *******************    web filter   ****************** */
//    /* 顺序：FilterRegistrationBean的order数值越小越优先.         */
//    /* 注意：只有采用bean注解，过滤器中的service才能正确注入!         */
//    /*      @WebFilter无法控制顺序，@Order和Ordered都无效!        */
//    /* ****************************************************** */
//
//    /* ******************* 字符集处理 ******************* */
//    @Value("${spring.http.encoding.charset:UTF-8}")
//    private String encodingCharset;
//
//    @Value("${spring.http.encoding.force:true}")
//    private boolean encodingForce;
//
//
//    /*
//     * 请求参数字符集处理.<br>
//     */
//    @Bean
//    @Order(Ordered.HIGHEST_PRECEDENCE)
//    public CharacterEncodingFilter characterEncodingFilter() {
//        CharacterEncodingFilter filter = new CharacterEncodingFilter();
//        filter.setEncoding(encodingCharset);
//        filter.setForceEncoding(encodingForce);
//        return filter;
//    }

    /* ******************* 消息转换器 ******************* */
    /**
     * 请求响应消息处理.
     * 暂时不使用GSON处理，还是采用spring自带的jackson
     */
    // 添加转换器（方式一），也是推荐的方式
    // 这样做springboot会把我们自定义的converter放在顺序上的最高优先级（List的头部）
    // 即有多个converter都满足Accpet/ContentType/MediaType的规则时，优先使用我们这个
//    @Bean
//    public GsonHttpMessageConverter gsonHttpMessageConverter() {
//        log.debug("加载转换器：GsonHttpMessageConverter");
//        GsonHttpMessageConverter gsonConverter = new GsonHttpMessageConverter();
//        gsonConverter.setGson(JSONUtil.getGson());
//        return gsonConverter;
//    }

    // 重置转换器列表（不推荐，该方法会覆盖默认转换器，常用于需完全自定义转换器的情况）
    // 通常在只有一个自定义WebMvcConfigurerAdapter时，会把这个方法里面添加的converter(s)依次放在最高优先级（List的头部）
    // 虽然第一种方式的代码先执行，但是bean的添加比这种方式晚，所以该方式的优先级 大于 方式一
    @Override
    public void configureMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        log.debug("重置转换器列表：configureMessageConverters");
    }

    // 扩展转换器列表（推荐）
    // 同一个WebMvcConfigurerAdapter中的configureMessageConverters方法先于extendMessageConverters方法执行
    // 可以理解为这种是三种方式中最后执行，不过这里可以通过add指定顺序来调整优先级，也可以使用remove/clear来删除converter，功能强大
    // 使用converters.add(xxx)会放在最低优先级（List的尾部）
    // 使用converters.add(0, xxx)会放在最高优先级（List的头部）
    @Override
    public void extendMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        log.debug("扩展转换器列表：extendMessageConverters");
        // 理论上同样类型的转换器只会有一个，不知道什么原因导致有重复，所以此处删掉重复的
        // 自定义json序列化和反序列化（日期、分页等）
        List<HttpMessageConverter<?>> list = converters.stream().filter(c -> c instanceof MappingJackson2HttpMessageConverter).collect(Collectors.toList());
        if (!list.isEmpty()) {
            // 只处理第一个
            MappingJackson2HttpMessageConverter converter = (MappingJackson2HttpMessageConverter) list.getFirst();
            ObjectMapper mapper = JsonUtil.initialJackson(converter.getObjectMapper());
            converter.setObjectMapper(mapper);
            // 删除多余的
            list.stream().skip(1).forEach(converters::remove);
        }
        // 设置string消息转换器的字符集，防止字符串输出时出现中文乱码
        list = converters.stream().filter(c -> c instanceof StringHttpMessageConverter).toList();
        if (!list.isEmpty()) {
            // 只处理第一个
            StringHttpMessageConverter converter = (StringHttpMessageConverter) list.getFirst();
            converter.setDefaultCharset(StandardCharsets.UTF_8);
            // 删除多余的
            list.stream().skip(1).forEach(converters::remove);
        }
        /*
        // 使用gson替换jackson
        GsonHttpMessageConverter gsonConverter = new GsonHttpMessageConverter();
        gsonConverter.setGson(JSONUtil.getGson());
        converters.add(gsonConverter);
        */
    }

    /**
     * form表单数据格式化
     */
    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addParser(new Parser<Date>() {
            @NonNull
            @Override
            public Date parse(@NonNull String var1, @NonNull Locale var2) throws ParseException {
                return Objects.requireNonNull(DateUtil.parse(var1));
            }
        });
    }

    /* ******************* 全局异常拦截器 ******************* */
    /**
     * ExceptionHandlerExceptionResolver : 拦截器、业务异常、入参绑定
     * BasicErrorController              : 404， filter等
     */
    @Bean
    @ConditionalOnMissingBean(value = ExceptionHandlerExceptionResolver.class, search = SearchStrategy.CURRENT)
    public ExceptionHandlerExceptionResolver globalExceptionResolver() {
        log.debug("加载BEAN：GlobalExceptionResolver");
        return SpringUtil.registerBean("GlobalExceptionResolver", GlobalExceptionResolver.class);
    }

}
