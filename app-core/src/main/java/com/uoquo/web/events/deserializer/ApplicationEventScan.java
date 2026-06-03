package com.uoquo.web.events.deserializer;

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
 *
 * @author xuhz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ApplicationEventScan {

    String[] value() default {};

    String[] basePackages() default {};

    Class<?>[] basePackageClasses() default {};

}
