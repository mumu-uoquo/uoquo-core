/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.spring;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 描述：获取Spring上下文对象的工具类. <br>
 * 日期：2018-05-17 17:04 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-05-17     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Component
public class SpringUtil implements ApplicationContextAware {
    /**
     * 上下文对象实例.
     */
    private static ApplicationContext applicationContext;

    /**
     * 上下文对象实例.
     */
    private static ConfigurableApplicationContext configurableApplicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        SpringUtil.applicationContext = applicationContext;
    }

    @Autowired
    public void setConfigurableApplicationContext(ConfigurableApplicationContext configurableApplicationContext) {
        SpringUtil.configurableApplicationContext = configurableApplicationContext;
    }

    /**
     * 注册实例对象
     */
    public static <T> T registerBean(String name, Class<T> clazz, Object... args) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (args != null) {
            for (Object arg : args) {
                builder.addConstructorArgValue(arg);
            }
        }
        BeanDefinitionRegistry factory = (BeanDefinitionRegistry) configurableApplicationContext.getBeanFactory();
        try {
            factory.registerBeanDefinition(name, builder.getRawBeanDefinition());
            return configurableApplicationContext.getBean(name, clazz);
        } catch (BeansException e) {
            try {
                factory.removeBeanDefinition(name);
            } catch (BeansException e2) {
                // do nothing;
            }
            throw e;
        }
    }

    /**
     * 通过name获取 Bean.
     * @param name  bean名称
     */
    public static Object getBean(String name) {
        try {
            return applicationContext.getBean(name);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    /**
     * 通过class获取Bean.
     * @param clazz bean类型
     */
    public static <T> T getBean(Class<T> clazz) {
        try {
            return applicationContext.getBean(clazz);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    /**
     * 通过name,以及Clazz返回指定的Bean.
     * @param name  bean名称
     * @param clazz bean类型
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        try {
            return applicationContext.getBean(name, clazz);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }
}
