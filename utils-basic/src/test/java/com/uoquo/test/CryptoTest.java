/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.crypto.AES;
import com.uoquo.utils.crypto.MD5;
import com.uoquo.utils.crypto.SHA;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

public class CryptoTest {

    @Test
    public void testMd5() {

        String name = "张三";
        StringBuffer sb = new StringBuffer(name.length());
        sb.append(name.substring(0, 1));
        sb.append("*");
        if (name.length() > 2) {
            for (int i = 3; i < name.length(); i++) {
                sb.append("*");
            }
            sb.append(name.substring(name.length() -1));
        }
        System.out.println(sb.toString());

        System.out.println("张三".length());
        System.out.println("张A".length());
        System.out.println("张三".substring(0, 1));
        System.out.println("张A".substring(0, 1));

        // hex 要测试下性能
        String msg = "123456";
        System.out.println(MD5.encrypt(msg));
    }

    @Test
    public void testAes() {
        String ems = "e6e061838856bf47e1de730719fb2609";
        String key = "5804455350000000";
        String dms = "76acf2870874f143c8fed870d941256382a18d3c91e7debe5ab03455b3608a13a69937626e76c563e432867afc1793f4";
        try {
            System.out.println(key.length());
            System.out.println(AES.encrypt(ems, key));
            System.out.println(AES.decrypt(dms, key));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSha() {
        String msg = "1234567";
        String sha = SHA.sha1(msg);
        System.out.println(sha.length() +", "+ sha);

        sha = SHA.sha224(msg);
        System.out.println(sha.length() +", "+ sha);

        sha = SHA.sha256(msg);
        System.out.println(sha.length() +", "+ sha);

        sha = SHA.sha384(msg);
        System.out.println(sha.length() +", "+ sha);

        sha = SHA.sha512(msg);
        System.out.println(sha.length() +", "+ sha);
    }

    @Test
    public void  testByte2Hex() {
        byte[] data = {0x1, 0x2, 0x3, 0x4};
        int len = 50_000_000;

        long bgn = System.currentTimeMillis();
        for (int i = 0; i < len; i++) {
            StringUtil.byte2hex(data);
        }
        System.out.println("耗时：" + (System.currentTimeMillis() - bgn)); // 约3323ms

        bgn = System.currentTimeMillis();
        for (int i = 0; i < len; i++) {
            StringBuffer temp = new StringBuffer(data.length * 2);
            for (int k = 0; k < data.length; k++) {
                temp.append(String.format("%02x", data[k]));
            }
            temp.toString();
        }
        System.out.println("耗时：" + (System.currentTimeMillis() - bgn)); // 约95309ms
    }


    @Test
    public void  testHex2Byte() {
        String msg = "01ab01ab01AB01AB01AB01AB01AB01ABFFEDFE01AB01AB01AB01AB01AB01AB01AB01ABFFEDFE01AB01AB01AB01AB01AB01AB01AB01ABFFEDFE01AB01AB01AB01AB01AB01AB01AB01ABFFEDFE01AB01AB01AB01AB01AB01AB01AB01ABFFEDFE";
        int len = 50_000_000;

        long bgn = System.currentTimeMillis();
        for (int i = 0; i < len; i++) {
            StringUtil.hex2byte(msg);
        }
        System.out.println("耗时：" + (System.currentTimeMillis() - bgn)); // 约27941ms

//        bgn = System.currentTimeMillis();
//        for (int i = 0; i < len; i++) {
//            char[] chars = msg.toLowerCase().toCharArray();
//            byte[] bytes = new byte[chars.length / 2];
//            for (int m = 0, k = 0; k < chars.length; ) {
//                 bytes[m++] = (byte) Integer.parseInt(msg.substring(k++, ++k), 16);
//            }
//        }
//        System.out.println("耗时：" + (System.currentTimeMillis() - bgn)); // 约111809ms
//
//        bgn = System.currentTimeMillis();
//        for (int i = 0; i < len; i++) {
//            BigInteger big = new BigInteger(msg, 16);
//            big.toByteArray();
//        }
//        System.out.println("耗时：" + (System.currentTimeMillis() - bgn)); // 约114961ms
    }
}
