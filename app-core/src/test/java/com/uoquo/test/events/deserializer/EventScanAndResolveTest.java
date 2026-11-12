package com.uoquo.test.events.deserializer;

import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventPackageScanner} 去重与 {@link DataTypeResolver} 对嵌套类、泛型名称的解析。
 */
@DisplayName("事件类型扫描与解析")
class EventScanAndResolveTest {

    /** 用于模拟位于嵌套类中的数据类型（简单类名在扫描范围内唯一） */
    public static class UniqueNestedPojo {
    }

    @Test
    @DisplayName("多个扫描包路径重叠时，同一个类只入索引一次，不被误判为歧义")
    void overlappingPackagesAreDeduplicated() {
        EventPackageScanner scanner = new EventPackageScanner(new String[]{
                "com.uoquo.test.events",
                "com.uoquo.test.events.deserializer",
                "com.uoquo.test"
        });

        List<Class<?>> matches = scanner.findBySimpleName(EventScanAndResolveTest.class.getSimpleName());

        assertEquals(1, matches.size());
        assertEquals(EventScanAndResolveTest.class, matches.get(0));
    }

    @Test
    @DisplayName("重叠扫描包下的类型仍能被唯一解析（而不是退化为 Map）")
    void resolveWithOverlappingPackages() {
        DataTypeResolver resolver = new DataTypeResolver(new EventPackageScanner(new String[]{
                "com.uoquo.test.events",
                "com.uoquo.test"
        }));

        // 发送方包名与本服务不同，通过简单类名回退解析
        assertEquals(EventScanAndResolveTest.class, resolver.resolve("other.service.EventScanAndResolveTest"));
    }

    @Test
    @DisplayName("warmUp：构建索引并返回类的数量，重复调用不会重复扫描")
    void warmUpBuildsIndexOnce() {
        EventPackageScanner scanner = new EventPackageScanner(new String[]{"com.uoquo.test.events.deserializer"});
        assertFalse(scanner.isWarmedUp());

        int first = scanner.warmUp();

        assertTrue(first > 0);
        assertTrue(scanner.isWarmedUp());
        assertEquals(first, scanner.warmUp());
        assertEquals(1, scanner.findBySimpleName(EventScanAndResolveTest.class.getSimpleName()).size());
    }

    @Test
    @DisplayName("多个线程同时首次使用时，结果一致")
    void concurrentFirstUse() throws Exception {
        EventPackageScanner scanner = new EventPackageScanner(new String[]{"com.uoquo.test.events"});
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> scanner.findBySimpleName(EventScanAndResolveTest.class.getSimpleName()).size()));
            }
            for (java.util.concurrent.Future<Integer> future : futures) {
                assertEquals(1, future.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("嵌套类名（Outer$Inner）：全限定名可直接加载")
    void resolveNestedByBinaryName() {
        DataTypeResolver resolver = new DataTypeResolver(new EventPackageScanner(new String[0]));

        assertEquals(UniqueNestedPojo.class, resolver.resolve(UniqueNestedPojo.class.getName()));
    }

    @Test
    @DisplayName("嵌套类名（Outer$Inner）：包名不同时按内部类简单名回退解析")
    void resolveNestedBySimpleNameFallback() {
        DataTypeResolver resolver = new DataTypeResolver(new EventPackageScanner(new String[]{"com.uoquo.test.events"}));

        assertEquals(UniqueNestedPojo.class, resolver.resolve("other.service.Outer$UniqueNestedPojo"));
    }

    @Test
    @DisplayName("带泛型参数的类型名：取原始类型解析")
    void resolveGenericTypeName() {
        DataTypeResolver resolver = new DataTypeResolver(new EventPackageScanner(new String[0]));

        assertEquals(List.class, resolver.resolve("java.util.List<com.x.Foo>"));
    }
}
