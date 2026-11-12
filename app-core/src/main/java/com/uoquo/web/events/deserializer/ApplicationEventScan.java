package com.uoquo.web.events.deserializer;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 仿照 {@link org.springframework.cloud.bus.jackson.RemoteApplicationEventScan} 的注解，
 * 用于扫描应用事件类（主要作用于单体应用）。
 * <p>
 * 注意：本注解仅用于扫描应用事件类，不支持 {@code @ComponentScan} 的其他功能。
 * </p>
 * <p>
 * {@link #value()} 与 {@link #basePackages()} 互为别名，{@link #basePackageClasses()} 指定的类所在的包也会被扫描。
 * </p>
 *
 * @author xuhz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ApplicationEventScan {

    /**
     * 要扫描的包路径（支持 Ant 风格通配符），与 {@link #basePackages()} 等价。
     */
    @AliasFor("basePackages")
    String[] value() default {};

    /**
     * 要扫描的包路径（支持 Ant 风格通配符），与 {@link #value()} 等价。
     */
    @AliasFor("value")
    String[] basePackages() default {};

    /**
     * 以指定类所在的包作为扫描包路径。
     */
    Class<?>[] basePackageClasses() default {};

}
