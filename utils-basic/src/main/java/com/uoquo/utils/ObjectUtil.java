/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import com.uoquo.utils.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * 对象工具类
 * @author xuhz
 */
public class ObjectUtil {
    private final static Logger logger = LoggerFactory.getLogger(ObjectUtil.class);

    /**
     * 对象转Map
     * @param obj 对象
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        String json = JsonUtil.serialize(obj);
        return JsonUtil.deserializeAsMap(json, String.class, Object.class);
    }

    /**
     * 比较两个对象，可指定需忽略的字段
     * @param obj1 第一个对象
     * @param obj2 第二个对象
     * @param ignoreFields 要忽略的字段名
     * @return 内容不同的字段及前后值 [{"field":"xx", "value1":xx, "value2":xx}]
     */
    public static <T> List<Map<String, Object>> compare(T obj1, T obj2, String... ignoreFields) {
        return compare(obj1, obj2, null, ignoreFields);
    }

    /**
     * 比较两个对象，可指定需忽略的字段
     * @param obj1 第一个对象
     * @param obj2 第二个对象
     * @param comparators 自定义属性比较器
     * @param ignoreFields 要忽略的字段名
     * @return 内容不同的字段及前后值 [{"field":"xx", "value1":xx, "value2":xx}]
     */
    public static <T> List<Map<String, Object>> compare(T obj1, T obj2, Map<String, PropertyComparator> comparators, String... ignoreFields) {
        if (obj1 == null && obj2 == null) {
            // 20251027 用返回空代替异常，方便使用的地方不做异常处理
//            throw new IllegalArgumentException("比较对象不能为null");
            return Collections.emptyList();
        }

        Class<?> clazz = (obj1 != null) ? obj1.getClass() : obj2.getClass();

        // 基础类型及常用值类型直接比较，无需反射字段循环
        if (isSimpleType(clazz)) {
            if (!isEqual(obj1, obj2)) {
                Map<String, Object> diff = new HashMap<>();
                diff.put("field", clazz.getSimpleName());
                diff.put("value1", obj1);
                diff.put("value2", obj2);
                return Collections.singletonList(diff);
            }
            return Collections.emptyList();
        }

        // Collection 类型直接比较（忽略顺序），无需反射字段循环
        if (obj1 instanceof Collection || obj2 instanceof Collection) {
            if (!isEqual(obj1, obj2)) {
                Collection<?> c1 = (obj1 instanceof Collection) ? (Collection<?>) obj1 : Collections.emptyList();
                Collection<?> c2 = (obj2 instanceof Collection) ? (Collection<?>) obj2 : Collections.emptyList();
                
                // 计算差集：c1 有但 c2 没有的元素
                Set<String> set1 = toStringSet(c1);
                Set<String> set2 = toStringSet(c2);
                Set<String> onlyInC1 = new LinkedHashSet<>(set1);
                onlyInC1.removeAll(set2);
                Set<String> onlyInC2 = new LinkedHashSet<>(set2);
                onlyInC2.removeAll(set1);
                
                Map<String, Object> diff = new HashMap<>();
                diff.put("field", clazz.getSimpleName());
                diff.put("value1", onlyInC1);
                diff.put("value2", onlyInC2);
                return Collections.singletonList(diff);
            }
            return Collections.emptyList();
        }
        
        // 其他类型的对象
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> ignoreSet = new HashSet<>(Arrays.asList(ignoreFields));

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            if (ignoreSet.contains(fieldName)) {
                logger.debug("比较对象[{}]字段[{}]：手动忽略", clazz, fieldName);
                continue;
            }
            try {
                field.setAccessible(true);
                Object value1 = (obj1 == null) ? null : field.get(obj1);
                Object value2 = (obj2 == null) ? null : field.get(obj2);

                PropertyComparator comparator = (comparators == null) ? null : comparators.get(fieldName);
                boolean isEqual = (comparator != null)
                        ? comparator.compare(value1, value2)
                        : isEqual(value1, value2);
                logger.debug("比较对象[{}]字段[{}]的结果为[{}]：{} -> {}", clazz, fieldName, isEqual, value1, value2);
                if (!isEqual) {
                    Map<String, Object> differences = new HashMap<>();
                    differences.put("field", fieldName);
                    differences.put("value1", value1);
                    differences.put("value2", value2);
                    result.add( differences);
                }
            } catch (IllegalAccessException e) {
                logger.warn("无法访问对象[{}]字段：{}", clazz, fieldName, e);
            }
        }

        return result;
    }

    /**
     * 判断是否为基础类型或常用值类型（无需反射字段，直接 equals 比较）
     */
    private static boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || clazz == Boolean.class
                || clazz == Byte.class
                || clazz == Short.class
                || clazz == Integer.class
                || clazz == Long.class
                || clazz == Float.class
                || clazz == Double.class
                || clazz == Character.class
                || clazz == BigDecimal.class
                || clazz == BigInteger.class
                || clazz == Date.class
                || Number.class.isAssignableFrom(clazz)
                || clazz.isEnum();
    }

    /**
     * 比较两个对象是否相等。
     * Collection 类型忽略顺序，转为排序后的列表再比较。
     */
    private static boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        if ((obj1 instanceof Collection<?> c1) && (obj2 instanceof Collection<?> c2)) {
            if (c1.size() != c2.size()) {
                return false;
            }
            // 忽略顺序：转为字符串 Set 比较
            return toStringSet(c1).equals(toStringSet(c2));
        }
        return obj1.equals(obj2);
    }

    /**
     * 将集合元素转为字符串 Set（自动去重）
     */
    private static Set<String> toStringSet(Collection<?> collection) {
        Set<String> set = new LinkedHashSet<>(collection.size());
        for (Object item : collection) {
            set.add(item == null ? null : item.toString());
        }
        return set;
    }

    /**
     * 自定义比较器接口
     */
    @FunctionalInterface
    public interface PropertyComparator {
        boolean compare(Object value1, Object value2);
    }
}
