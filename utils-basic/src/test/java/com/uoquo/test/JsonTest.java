/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.uoquo.test.pojo.User1;
import com.uoquo.test.pojo.User2;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.GsonUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.json.jackson.DateContextualDeserializer;
import com.uoquo.utils.json.jackson.DateContextualSerializer;
import com.uoquo.utils.json.jackson.SensitiveSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

public class JsonTest {

    @Test
    public void str2Object() {
//        String str = "{\"pageNum\":1,\"pageSize\":10,\"total\":1,\"pages\":1,\"hasPrevPage\":false,\"hasNextPage\":false,\"result\":[{\"id\":\"57189640196596271\",\"customerName\":\"张三\",\"customerCode\":\"111111\",\"account\":\"111111\",\"mobile\":\"\",\"wxBind\":null,\"status\":1,\"statusText\":\"启用\",\"deposit\":22.0,\"moneyFirst\":0.0,\"moneyIn\":0.0,\"moneyRemain\":0.0,\"moneyUsable\":0.0,\"marketValue\":0.0,\"totalAssets\":0.0,\"cost\":0.0,\"depositRate\":0.0,\"surplus\":0.0,\"surplusRate\":0.0,\"yield\":0.0,\"openDate\":\"2020-11-11T07:02:26.000Z\",\"created\":\"2020-11-11T07:02:26.000Z\",\"changed\":\"2020-11-11T07:17:00.000Z\"}]}";
        String str = "{\"@class\":\"com.uoquo.test.pojo.User3\",\"name\":\"张三\",\"age\":10,\"birthday\":[\"java.util.Date\",\"2023-01-25T10:51:40.285Z\"],\"friends\":[\"java.util.Arrays$ArrayList\",[{\"@class\":\"com.uoquo.test.pojo.User3\",\"name\":\"张三\",\"age\":10,\"birthday\":[\"java.util.Date\",\"2023-01-25T10:51:40.285Z\"],\"friends\":null}]]}";

        Object a2 = JsonUtil.deserialize(str);
        System.out.println(a2.getClass());
        System.out.println(JsonUtil.serialize(a2));
        System.out.println(JsonUtil.serializeWithType(a2));

    }

    @Test
    public void testJackson2() {
        User1 user1 = new User1();
        user1.setName("张三");
        user1.setAge(10);
        user1.setTime(100L);
        user1.setMoney(21.0D);
        user1.setScore(80.8F);
        user1.setBirthday(new Date());
        User1 user2 = new User1();
        user2.setName("张三");
        user2.setAge(10);
        user2.setBirthday(new Date());
        user2.setFriends(Arrays.asList(user1));

        //
        String str1 = JsonUtil.serialize(user2);
        System.out.println(str1);
        Object b = JsonUtil.deserialize(str1, User2.class);
        System.out.println(b.getClass());
        System.out.println(JsonUtil.serialize(b));
        System.out.println(JsonUtil.serializeWithType(b));
        System.out.println("\r\n");

        //
        String str2 = JsonUtil.serializeWithType(user2);
        System.out.println(str2);
        Object a2 = JsonUtil.deserialize(str2);
        System.out.println(a2.getClass());
        System.out.println(JsonUtil.serializeWithType(a2));
        Object b2 = JsonUtil.deserialize(str2, User1.class);
        System.out.println(b2.getClass());
        System.out.println(JsonUtil.serializeWithType(b2));
        System.out.println("\r\n");

        System.out.println("-- end jackson test--");
    }

    @Test
    public void testJackson() {
        User1 user1 = new User1();
        user1.setName("张三");
        user1.setAge(10);
        user1.setBirthday(new Date());

        User1 user2 = new User1();
        user2.setName("张三");
        user2.setAge(10);
        user2.setBirthday(new Date());
        user2.setFriends(Arrays.asList(user1));

        List<User1> list = new ArrayList<>();
        list.add(user1);
        list.add(user2);

        //
        String str1 = JsonUtil.serialize(user1);
        System.out.println(str1);
        Object a = JsonUtil.deserialize(str1);
        System.out.println(a.getClass());
        Object b = JsonUtil.deserialize(str1, User1.class);
        System.out.println(b.getClass());
        System.out.println("\r\n");

        //
        String str2 = JsonUtil.serializeWithType(user1);
        System.out.println(str2);
        Object a2 = JsonUtil.deserialize(str2);
        System.out.println(a2.getClass());
        Object b2 = JsonUtil.deserialize(str2, User1.class);
        System.out.println(b2.getClass());
        System.out.println("\r\n");

        //
        String str3 = JsonUtil.serialize(user2);
        System.out.println(str3);
        Object a3 = JsonUtil.deserialize(str3);
        System.out.println(a3.getClass());
        Object b3 = JsonUtil.deserialize(str3, User1.class);
        System.out.println(b3.getClass());
        System.out.println("\r\n");
        //
        String str4 = JsonUtil.serialize(list);
        System.out.println(str4);
        Object l1 = JsonUtil.deserialize(str4);
        System.out.println(l1.getClass());
        List<User1> l2 = JsonUtil.deserializeAsList(str4, User1.class);
        System.out.println(l2.getClass());
        List<User1> l3 = JsonUtil.deserialize(str4, List.class, User1.class);
        System.out.println(l3.getClass());
        System.out.println("\r\n");

        System.out.println("-- end jackson test--");
    }

    @Test
    public void testGson() {
        User1 user1 = new User1();
        user1.setName("张三");
        user1.setAge(10);
        user1.setBirthday(new Date());

        User1 user2 = new User1();
        user2.setName("张三");
        user2.setAge(10);
        user2.setBirthday(new Date());
        user2.setFriends(Arrays.asList(user1));

        List<User1> list = new ArrayList<>();
        list.add(user1);
        list.add(user2);

        //
        String str1 = GsonUtil.serialize(user1);
        System.out.println(str1);
        Object a = GsonUtil.deserialize(str1);
        Object b = GsonUtil.deserialize(str1, User1.class);

        //
        String str2 = GsonUtil.serializeWithType(user1);
        System.out.println(str2);
        Object a2 = GsonUtil.deserialize(str2);
        Object b2 = GsonUtil.deserialize(str2, User1.class);

        //
        String str3 = GsonUtil.serialize(list);
        System.out.println(str3);
        List<User1> l2 = GsonUtil.deserializeAsList(str3, User1.class);
        List<User1> l3 = GsonUtil.deserialize(str3, List.class, User1.class);

        System.out.println("----");
    }

    @Test
    public void testType() {
        Date d = this.getType();
        Integer i = this.getType();
    }


    private <T> T getType() {
        try {
            Method method = this.getClass().getMethod("getType");
            Class<?> claz = method.getReturnType();
            Type type = method.getGenericReturnType();
            AnnotatedType atype = method.getAnnotatedReturnType();
            AnnotatedType rtype = method.getAnnotatedReceiverType();
            Type[] aa = method.getGenericParameterTypes();
            TypeVariable<Method>[] ab = method.getTypeParameters();

            System.out.println("a");
            //            Type c = this.getClass().getMethod().getAnnotatedReturnType().getType();
//            Method method = JsonTest.class.getDeclaredMethod("getType", null);
//            this.getClass().
//            System.out.println(rtype);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    public void testDate() {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("userName", "aa");
        paramMap.put("createTimeStart", new Date());
        paramMap.put("createTimeEnd", new Date());
        try {
            System.out.println(JsonUtil.serialize(paramMap));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSensitiveUtil() {
        SensitiveSerializer serializer = new SensitiveSerializer();
        String value = "";

        value = "aaa@bb.com";
        System.out.println(value.replaceAll("(^[^@]{1})[^@]*(@[^@]+$)", "$1***$2"));

        value = "北京市海淀区3号22栋";
        System.out.println(value.replaceAll("\\d", "*"));


        User1 user1 = new User1();
        user1.setId("111");
        user1.setName("张三");
        user1.setAge(10);
        user1.setBirthday(new Date());
        System.out.println(JsonUtil.serialize(user1));
    }

}
