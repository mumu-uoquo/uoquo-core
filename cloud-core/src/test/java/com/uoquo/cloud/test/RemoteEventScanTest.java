package com.uoquo.cloud.test;

import com.uoquo.cloud.config.RemoteEventScanAutoConfiguration;
import com.uoquo.web.events.deserializer.EventScanPackagesProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cloud-core 把 {@code @RemoteApplicationEventScan} 声明的包通过 {@link EventScanPackagesProvider} 交给 app-core 的扫描器。
 */
@DisplayName("@RemoteApplicationEventScan 的扫描包提供者")
class RemoteEventScanTest {

    @RemoteApplicationEventScan({"com.demo.event", "com.demo.model"})
    static class ByValue {
    }

    @RemoteApplicationEventScan(basePackages = "com.demo.other", basePackageClasses = RemoteEventScanTest.class)
    static class ByBasePackages {
    }

    @Test
    @DisplayName("读取 value、basePackages、basePackageClasses，并去重")
    void readsAllAttributes() {
        ApplicationContext ctx = Mockito.mock(ApplicationContext.class);
        Mockito.when(ctx.getBeanNamesForAnnotation(RemoteApplicationEventScan.class)).thenReturn(new String[]{"a", "b"});
        Mockito.when(ctx.findAnnotationOnBean("a", RemoteApplicationEventScan.class))
                .thenReturn(ByValue.class.getAnnotation(RemoteApplicationEventScan.class));
        Mockito.when(ctx.findAnnotationOnBean("b", RemoteApplicationEventScan.class))
                .thenReturn(ByBasePackages.class.getAnnotation(RemoteApplicationEventScan.class));

        EventScanPackagesProvider provider = new RemoteEventScanAutoConfiguration().remoteEventScanPackagesProvider(ctx);
        List<String> packages = provider.getPackages();

        assertTrue(packages.containsAll(List.of("com.demo.event", "com.demo.model", "com.demo.other",
                RemoteEventScanTest.class.getPackageName())), packages.toString());
        assertEquals(4, packages.size());
    }

    @Test
    @DisplayName("没有声明注解时返回空列表")
    void emptyWhenNoAnnotation() {
        ApplicationContext ctx = Mockito.mock(ApplicationContext.class);
        Mockito.when(ctx.getBeanNamesForAnnotation(RemoteApplicationEventScan.class)).thenReturn(new String[0]);

        assertTrue(new RemoteEventScanAutoConfiguration().remoteEventScanPackagesProvider(ctx).getPackages().isEmpty());
    }
}
