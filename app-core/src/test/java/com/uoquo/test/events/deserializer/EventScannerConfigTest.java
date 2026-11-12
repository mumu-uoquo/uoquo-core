package com.uoquo.test.events.deserializer;

import com.uoquo.web.config.EventDeserializerAutoConfiguration;
import com.uoquo.web.events.deserializer.ApplicationEventScan;
import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
import com.uoquo.web.events.deserializer.EventScanPackagesProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件类扫描包的收集：{@link ApplicationEventScan} 的各属性 + {@link EventScanPackagesProvider} 扩展点。
 */
@DisplayName("事件扫描器的包路径收集")
class EventScannerConfigTest {

    /** 仅用 basePackages（与 value 互为别名）、basePackageClasses 声明扫描包 */
    @Configuration
    @Import(EventDeserializerAutoConfiguration.class)
    @ApplicationEventScan(basePackages = {"com.uoquo.test.pkg.one"}, basePackageClasses = EventScannerConfigTest.class)
    static class BasePackagesConfig {
    }

    /** 使用 value 声明，并通过扩展点追加包路径（模拟 cloud-core 提供的 @RemoteApplicationEventScan） */
    @Configuration
    @Import(EventDeserializerAutoConfiguration.class)
    @ApplicationEventScan({"com.uoquo.test.pkg.two", "com.uoquo.test.pkg.shared"})
    static class WithProviderConfig {
        @Bean
        EventScanPackagesProvider remoteProvider() {
            return () -> List.of("com.uoquo.test.pkg.remote", "com.uoquo.test.pkg.shared");
        }
    }

    /** 没有任何注解：扫描范围为空 */
    @Configuration
    @Import(EventDeserializerAutoConfiguration.class)
    static class EmptyConfig {
    }

    @Test
    @DisplayName("basePackages 与 basePackageClasses 都会被读取（basePackages 不再被忽略）")
    void readsBasePackagesAndClasses() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BasePackagesConfig.class)) {
            String scanner = ctx.getBean(EventPackageScanner.class).toString();
            assertTrue(scanner.contains("com.uoquo.test.pkg.one"), scanner);
            assertTrue(scanner.contains(EventScannerConfigTest.class.getPackageName()), scanner);
            assertNotNull(ctx.getBean(DataTypeResolver.class));
        }
    }

    @Test
    @DisplayName("合并 @ApplicationEventScan 与扩展点提供的包，并去重")
    void mergesProviderPackages() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(WithProviderConfig.class)) {
            String scanner = ctx.getBean(EventPackageScanner.class).toString();
            assertTrue(scanner.contains("com.uoquo.test.pkg.two"), scanner);
            assertTrue(scanner.contains("com.uoquo.test.pkg.remote"), scanner);
            // 重复声明的包只出现一次
            assertEquals(scanner.indexOf("com.uoquo.test.pkg.shared"), scanner.lastIndexOf("com.uoquo.test.pkg.shared"), scanner);
        }
    }

    @Test
    @DisplayName("容器启动完成后自动预热扫描索引")
    void scannerIsWarmedUpAfterStartup() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BasePackagesConfig.class)) {
            assertTrue(ctx.getBean(EventPackageScanner.class).isWarmedUp());
        }
    }

    @Test
    @DisplayName("可通过 app.event.scanner.warm-up=false 关闭预热，首次使用时仍会扫描")
    void warmUpCanBeDisabled() {
        com.uoquo.utils.Config.put("app.event.scanner.warm-up", "false");
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BasePackagesConfig.class)) {
            EventPackageScanner scanner = ctx.getBean(EventPackageScanner.class);
            assertFalse(scanner.isWarmedUp());

            assertEquals(1, scanner.findBySimpleName(EventScannerConfigTest.class.getSimpleName()).size());
            assertTrue(scanner.isWarmedUp());
        } finally {
            com.uoquo.utils.Config.put("app.event.scanner.warm-up", (String) null);
        }
    }

    @Test
    @DisplayName("没有声明任何扫描包时，扫描范围为空")
    void emptyWhenNothingDeclared() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EmptyConfig.class)) {
            assertTrue(ctx.getBean(EventPackageScanner.class).toString().contains("[]"));
        }
    }

    @Test
    @DisplayName("mergePackages：去空白、去重、保持顺序")
    void mergePackages() {
        List<String> merged = EventScanPackagesProvider.mergePackages(
                new String[]{"a.b", " c.d ", ""}, new String[]{"a.b", "e.f"}, new Class<?>[]{String.class});

        assertEquals(List.of("a.b", "c.d", "e.f", "java.lang"), merged);
    }
}
