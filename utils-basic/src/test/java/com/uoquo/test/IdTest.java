/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.uoquo.utils.DateUtil;
import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.crypto.*;
import com.uoquo.utils.DataUtil;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;
import java.util.zip.CRC32;

public class IdTest {

    @Test
    public void getUlid2() {
        ULID ulid = new ULID(26);
        String id = ulid.nextId();
        System.out.println(id);
        System.out.println(ulid.toString());
        System.out.println(ulid.getYearAndMonth());
        System.out.println(ulid.getTimestamp());
        System.out.println(StringUtil.toBinaryString(ulid.getTimestamp()));
        ULID ulid2 = ULID.parse(id);
        System.out.println(ulid2.toString());
        System.out.println(ulid2.getYearAndMonth());
        System.out.println(ulid2.getTimestamp());
        System.out.println(StringUtil.toBinaryString(ulid2.getTimestamp()));
    }

    @Test
    public void getUlid() {
        for (int i = 0; i < 10; i++) {
            System.out.println(IDGenerator.getNextULID());
        }

        ULID ulid = new ULID(16);
        int maxLen = 10;
        List<String> list = new ArrayList<>(maxLen);
        long time = System.currentTimeMillis();
        for (int i = 0; i < maxLen; i++) {
//            list.add(ulid.nextInt()+"");
            list.add(ulid.nextId());
        }
        System.out.println("times 1: "+ (System.currentTimeMillis() - time));
        list.forEach(System.out::println);
//        System.out.println(list.get(0));
//        System.out.println(list.get(maxLen-1));
        System.out.println("times 2: "+ (System.currentTimeMillis() - time));

        ulid = new ULID(16);
        System.out.println(ulid.nextId());
        ULID ulid2 = ULID.parse(ulid.toString());
        System.out.println(ulid2.toString());
        System.out.println(ulid2.getYearAndMonth());

        ulid = new ULID(26);
        System.out.println(ulid.nextId());
        ulid2 = ULID.parse(ulid.toString());
        System.out.println(ulid2.toString());
        System.out.println(ulid2.getYearAndMonth());
    }

    @Test
    public void getLongId() {
        long id = IDGenerator.getNextLong();
        System.out.println(id);
        System.out.println(Long.toBinaryString(id));

        id = IDGenerator.getNextLong();
        System.out.println(id);
        System.out.println(Long.toBinaryString(id));

        id = IDGenerator.getNextLong();
        System.out.println(id);
        System.out.println(Long.toBinaryString(id));
    }

    @Test
    public void getIntId() {
        int id = IDGenerator.getNextInt();
        System.out.println(id);
        System.out.println(Integer.toBinaryString(id));

        id = IDGenerator.getNextInt();
        System.out.println(id);
        System.out.println(Integer.toBinaryString(id));

        id = IDGenerator.getNextInt();
        System.out.println(id);
        System.out.println(Integer.toBinaryString(id));
    }

    @Test
    public void getStringUUID() {
        long time = Clock.systemUTC().millis();
        long time1 = time & 0x7FFFF;
        long time2 = time / 500;
        System.out.println(Long.toBinaryString(time));
        System.out.println(Long.toBinaryString(time << 12));
        System.out.println(Long.toBinaryString(time1));
        System.out.println(Long.toBinaryString(time1 << 12));
        System.out.println(Long.toBinaryString(time2));
        System.out.println(Long.toBinaryString(time2 << 12));
        time2 = time2 & 0x7FFFF;
        System.out.println(Long.toBinaryString(time2));
        System.out.println(Long.toBinaryString(time2 << 12));



        System.out.println(IDGenerator.getUUID());

        System.out.println(Long.toBinaryString(1681128207));
        System.out.println(Long.toBinaryString(1681128207/500));
        System.out.println(Long.toBinaryString(1261010944));
        System.out.println(Long.toBinaryString(1261011027));



//        //获取UUID
//        String uuid = UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
//        //生成后缀
//        long suffix = Math.abs(uuid.hashCode() % 100000000);
//        System.out.println(Integer.MAX_VALUE);
//        System.out.println(uuid.hashCode());
//        System.out.println(Integer.toString(uuid.hashCode(), 36));
//        System.out.println(Integer.toString(uuid.hashCode(), 62));
//        System.out.println(suffix);
//        System.out.println(Long.toString(suffix, 36));
//        System.out.println(Long.toString(suffix, 62));
    }

    @Test
    public void getStringId() {
        System.out.println(IDGenerator.getNextString());
        System.out.println(IDGenerator.getNextString());
        System.out.println(IDGenerator.getNextString());
        System.out.println(IDGenerator.getNextString());
        System.out.println(IDGenerator.getNextString());
        System.out.println(IDGenerator.getNextString());
    }

    @Test
    public void testHex() {
        System.out.println("===>");
        System.out.println(-1 ^ (-1 << 10));
        System.out.println(-1 ^ (-1 << 8));
        System.out.println(-1 ^ (-1 << 4));

        System.out.println("===>");
        int MAX_APP_CODE_NUM = -1 ^ (-1 << 8); // 255
        int MAX_APP_NODE_NUM = -1 ^ (-1 << 4); // 15
        System.out.println(MAX_APP_CODE_NUM);
        System.out.println(MAX_APP_NODE_NUM);
        System.out.println(Integer.toString(MAX_APP_CODE_NUM, 16));
        System.out.println(Integer.toString(MAX_APP_NODE_NUM, 16));

        int code = -257;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = -256;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = -255;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = -254;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = -100;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = -1;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = 0;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = 1;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = 100;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = 254;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = 255;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = 256;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
        code = 257;
        System.out.println("===>");
        System.out.println(code +", "+ (code & MAX_APP_CODE_NUM));
        System.out.println(Integer.toString(code, 16) +", " + Integer.toString(code & MAX_APP_CODE_NUM, 16));
    }


    @Test
    public void testCRC32() {
        System.out.println(IDGenerator.getNextInt());
        System.out.println(IDGenerator.getNextInt());
        System.out.println(IDGenerator.getNextInt());
        System.out.println(IDGenerator.getNextInt());

//        int len = 10_000_000;
//        Map<Integer, List<Long>> map = new HashMap<>();
//        for (int i = 0; i < len; i++) {
//            long lid = IDGenerator.getNextLong();
//            int  cid = IDGenerator.getCRC32(lid);
//
//            List<Long> list = map.get(cid);
//            if (list == null) {
//                list = new ArrayList<>();
//            }
//            list.add(lid);
//            map.put(cid, list);
//        }
//
//        for (Map.Entry<Integer, List<Long>> item : map.entrySet()) {
//            if (item.getValue().size() > 1) {
//                System.out.println(item.getKey() + " : " + item.getValue());
//            }
//        }
    }

    @Test
    public void testTimestamp() {
        long time = DateUtil.parse("2030-12-30 23:59:59").getTime() - DateUtil.parse("2020-01-01 00:00:00").getTime();
        System.out.println(time);
        time = time / 1000;
        System.out.println(time);
        System.out.println(Long.toHexString(time));
        System.out.println(Long.toBinaryString(time));
        System.out.println(Long.toBinaryString(2147483647L));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 134217727000L * 1),  DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 134217727000L * 10), DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 134217727000L * 20), DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 134217727000L * 30), DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 134217727000L * 60), DateUtil.FORMAT_TIMESTAMP));

        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 33554431000L * 1),  DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 33554431000L * 10), DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 33554431000L * 20), DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 33554431000L * 30), DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(new Date(DateUtil.parse("2020-01-01 00:00:00").getTime() + 33554431000L * 60), DateUtil.FORMAT_TIMESTAMP));

//        System.out.println(Long.toOctalString(time));
//        System.out.println(Long.toUnsignedString(time));
    }

    @Test
    public void testBase32() {
        try {
            // 标准
            String text = "hello 中国 1230";
            String dest = Base32.encode(text);
            System.out.println(dest);
            byte[] data = Base32.decode(dest);
            System.out.println(new String(data, StandardCharsets.UTF_8));

            // Crockford 解码
            Base32 base32 = new Base32("0123456789ABCDEFGHJKMNPQRSTVWXYZ");
            dest = base32.encodeInternal(text.getBytes(StandardCharsets.UTF_8));
            System.out.println(dest);
            data = base32.decodeInternal(dest);
            System.out.println(new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 双因子动态验证码
     */
    @Test
    public void testTOTP() {
        byte[] secret = "76JS2KSSK5RLSZN4".getBytes(StandardCharsets.UTF_8);
        int digits = 6;
        // 1. 时间因子动态密码
        // 当前时间
        System.out.println(OTPUtils.TOTP(secret, digits));
        System.out.println(OTPUtils.TOTPSteam(secret, digits));
        // 指定时间
        long time = 1003822404000L; // 2001-10-23 15:33:24
        int period = 1;
        System.out.println(OTPUtils.TOTP(secret, period, time, digits, OTPUtils.HashAlgorithm.HmacSHA256));
        System.out.println(OTPUtils.TOTPSteam(secret, period, time, digits, OTPUtils.HashAlgorithm.HmacSHA512));
        // 2. 动态密码
        for (int i = 0; i < 100; i++) {
            System.out.println("============= "+ i);
            // 事件因子（计数器）
            System.out.println(OTPUtils.HOTP(secret, i, digits));
            System.out.println(OTPUtils.HOTPSteam(secret, i, digits));
            // 时间因子（时间戳）
            System.out.println(OTPUtils.TOTP(secret, period, time, digits, OTPUtils.HashAlgorithm.HmacSHA256));
        }
        // 3. 自定义格式动态码（可用于激活码、注册码等）
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(time).array();
            byte[] hash = OTPUtils.generateHash(secret, data);
            byte[] bytes = new byte[digits];
            int offset = hash[hash.length - 1] & (hash.length - digits);
            System.arraycopy(hash, offset, bytes, 0, bytes.length);
            System.out.println(Base32.encode(bytes));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSerialNumber2() {
        int salesTyp = 2; // 1：试用，2：包年
        String code  = "aaaaaa";
        String order = "bbbb";
        String serial = SerialNumUtil.generateSerial(salesTyp, code, order);
        System.out.println("serial: " + serial);
        System.out.println("type:"+ SerialNumUtil.getType(serial));
        System.out.println("year:"+ SerialNumUtil.getYear(serial));
        System.out.println("month:"+ SerialNumUtil.getMonth(serial));

//        // type
//        byte[] bytes = Base32.decode(serial);
//        short batch = DataUtil.getShort(bytes);
//        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(batch)));
//        byte head = (byte) ((batch & 0xC000) >>> 14);
//        System.out.println("h: "+ head);
//        int lidx = 2 + head;
//        batch = (short) (batch << lidx);
//        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(batch)));
//        batch = (short) (((batch & 0xFFFF) >>> 6));
//        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(batch)));
    }
    @Test
    public void testSerialNumber() {
        String machineCode = "aaaaaa";
        String orderNumber = "bbbb";
        byte type = 1; // 1：试用，2：包年
        byte hidx = (byte)(getRandomInt(0, 100) & 0x03);
        byte year = (byte)(24 % 16); // 年最大0x0F;
        byte month = (byte)10;

        System.out.println("h: " + hidx +"  "+ Integer.toBinaryString(hidx));
        System.out.println("y: " + year +"  "+ Integer.toBinaryString(year));
        System.out.println("c: " + type +"  "+ Integer.toBinaryString(type));
        System.out.println("m: " + month +"  "+ Integer.toBinaryString(month));
        System.out.println("");

        short ycm = 0x00;
        System.out.println(Integer.toBinaryString(ycm));
        if (hidx % 2 == 0) {
            // YCM
            System.out.println("ycm");
            ycm = (short) (ycm | (year << 6));
            System.out.println(Integer.toBinaryString(ycm));
            ycm = (short) (ycm | (type << 4));
            System.out.println(Integer.toBinaryString(ycm));
            ycm = (short) (ycm | month);
            System.out.println(Integer.toBinaryString(ycm));
        } else {
            // MYC
            System.out.println("myc");
            ycm = (short) (ycm | (month << 6));
            System.out.println(Integer.toBinaryString(ycm));
            ycm = (short) (ycm | (year << 2));
            System.out.println(Integer.toBinaryString(ycm));
            ycm = (short) (ycm | type);
            System.out.println(Integer.toBinaryString(ycm));
        }
        System.out.println(Integer.toBinaryString(ycm));

        short random = (short)getRandomInt(10_000, 65_535);
        System.out.println("\nrandom: "+ random);
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));
        System.out.println(Integer.toBinaryString(0xC000));
        random = (short) (random | 0xC000);
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));

        System.out.println("\nhead: " + hidx);
        short head = hidx;
//        System.out.println(Integer.toBinaryString(head));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(head)));
        System.out.println(Integer.toBinaryString(head << 14));
        System.out.println(Integer.toBinaryString(0x3FFF));
        head = (short) (((head << 14) | 0x3FFF));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(head)));

        System.out.println("\nrandom & head");
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(head)));
        random = (short) (random & head);
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));

//        System.out.println("");
//        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));
//        System.out.println(Integer.toBinaryString(0x3FF));
//        random = (short) (random & 0x3FF);
//        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));
        // HHLLYYYYCCMMMMRR
        // HH：2 bit 序号
        // YCM: 10 bit 年月
        // L+R：4 bit随机内容
        // L.length = val(H)
        // R.length = 4 - L
        // 3F<L：16 - （2 + L)
        // 3F>R:
        System.out.println("\nbefore random:");
        int idx = 6 - (2 + hidx); //
        System.out.println("4 - "+ hidx +" = "+idx);
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));
        System.out.println(Integer.toBinaryString(0x3FF));
        System.out.println(Integer.toBinaryString((0x3FF << idx)));
        random = (short) (random | (0x3FF << idx));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));

        System.out.println("\nbefore ycm:");
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(ycm)));
        ycm = (short) (ycm << idx);
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(ycm)));

        int lidx = 2 + hidx;
        int ridx = 6 - lidx;
        System.out.println(String.format("h=%d, i=%d, l=%d, r=%d", hidx, idx, lidx, ridx));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt((short) (0x3F << (16 - lidx)))));
        ycm = (short) (ycm | (0x3F << (16 - lidx)));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(ycm)));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt((short) (0x3F >> lidx))));
        ycm = (short) (ycm | (0x3F >>> lidx));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(ycm)));


        System.out.println("\nafter ycm:");
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(ycm)));
        random = (short) (random & ycm);
        System.out.println(Integer.toBinaryString(Short.toUnsignedInt(random)));

        try {
            byte[] arr = DataUtil.getBytes((random & 0xFFFF));
            byte[] bytes = new byte[10];
            bytes[0] = arr[0];
            bytes[1] = arr[1];
            // OTP
            int digits = 7;
            byte[] secret = machineCode.getBytes(StandardCharsets.UTF_8);
            byte[] data = orderNumber.getBytes(StandardCharsets.UTF_8);
            byte[] hash = OTPUtils.generateHash(secret, data);
            int offset = hash[hash.length - 1] & (hash.length - digits);
            System.arraycopy(hash, offset, bytes, 2, digits);
            // CRC
            byte[] temp = new byte[9];
            System.arraycopy(bytes, 0, temp, 0, temp.length);
            bytes[9] = CRCUtil.crc8Standard(temp);
            System.out.println(Base32.encode(bytes));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getRandomInt(int x1, int x2){
        double f = Math.random()/Math.nextDown(1.0);
        double x = x1*(1.0 - f) + x2*f;
        return (int) x;
    }



    @Test
    public void testYear(){
        Calendar calendar = Calendar.getInstance();

        System.out.println(calendar.get(Calendar.YEAR) % 16);
        System.out.println(calendar.get(Calendar.MONTH));

        calendar.set(2022, 0, 1);
        System.out.println(calendar.get(Calendar.YEAR) % 16);
        System.out.println(calendar.get(Calendar.MONTH));

        calendar.set(1988, 11, 1);
        System.out.println(calendar.get(Calendar.YEAR) % 16);
        System.out.println(calendar.get(Calendar.MONTH));
    }

}
