/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.GeneralSecurityException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uoquo.utils.Config;
import com.uoquo.utils.FileUtil;
import com.uoquo.utils.NetworkUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;

/**
 * 描述：系统授权码. <br>
 * 算法：. <br>
 * 日期：2019-03-19 14:01 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-03-19     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class License {
    protected static final Logger log = LoggerFactory.getLogger(License.class);
    // 私钥（不编译到程序包，仅作为源码留存备用）
    //private static final String PRIVATE_KEY = "30820275020100300d06092a864886f70d01010105000482025f3082025b02010002818100877ddb82b5800e2059e15fd7ab1f984be2468e9bc44d57395729f3e18529ab085a412bcd174f3f426b7bdda08667ee76e2fc238813bd2019058fa5c47ca8bd052b12ecce22d25f288a9e3b147d752eb04dbb39e5327530d6d1ed279b0364a78eeabafabd3b6d55d0b7060c4f1fbbd38c683b6b42dcd16c1480d440bf1a84a49f0203010001028180686b235fc196f5cc12d8b0ef59ef1884ead6ab92fa1f0ca8a13730bfcdcb460742df54ed53187ccd285ea677cefd8bf6cd89b9ac6661ebb9bce26ec355bb092831e2ab9cbfd1865ca894f750720af02c8da233a602dc0462f1642b160bf90257519311075ce62b9b430c1ecf70eaf803b4a9c78b3524750840214d7be83c6519024100c6302ed7c29b1fe8643d159e6480545409b97b2eb64d7d340f2b747df3d0b3c11e3b67297ccda1f25a8e26f5e6eb7ea11806597582dded2446db5da281c3fbd3024100af03c58863d7f23e0aaa8f7e9350d10e99b3f4a6df00bba78e8a10721a02438d39a8af5357e2b64a090ad0afa0b7d5e2ebdc9747627a6e6d257e0418dccff08502404cf97dd8131f88df0bbfa5dbe510eaafd12f1726e76b654b88f0c9c7f9a07f906c1b8a4fd4c75134fdf2e432f1c359655c1d6b3576e4972c55d0f1a7225b6a3d024076ec942ca2d3d98ae90a8f294e874b17a5c00fcdd89911eac0c3f67ae24b89858c99a0517cd5d452a8a44dbc19f4f1851a0ab24d5290a93ac79042ce763562550240759be5b4cba78c75770986f95c35102083d891abccb88a1e3464a93b93e9d83bb7ce653a4ab1a43e07cfc048843f165eccd2cf0fe24b0f76dc30348282061ca7";
    // 公钥
    private static final String PUBLIC_KEY  = "30819f300d06092a864886f70d010101050003818d0030818902818100877ddb82b5800e2059e15fd7ab1f984be2468e9bc44d57395729f3e18529ab085a412bcd174f3f426b7bdda08667ee76e2fc238813bd2019058fa5c47ca8bd052b12ecce22d25f288a9e3b147d752eb04dbb39e5327530d6d1ed279b0364a78eeabafabd3b6d55d0b7060c4f1fbbd38c683b6b42dcd16c1480d440bf1a84a49f0203010001";
    
    private static final int MAX_ISSUE_DAY = 1000; // 最大授权多少天
    
    /**
     * 授权文件路径.
     */
    private static final String LICENSE_FILE = Config.APP_PATH + "/config/app.license";
    
    static {
        synchronized (License.class) {
            log.info("license file path: {}", LICENSE_FILE);
            File file = new File(LICENSE_FILE);
            if (!file.exists()) {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
            }
        }
    }
    
    /**
     * 数据签名.
     * @param privateKey  私钥
     * @param licenseFile 待签名文件
     */
    public static void sign(String privateKey, String licenseFile) {
        // 读取待授权信息
        StringBuilder data = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(licenseFile));
            String temp = null;
            while ((temp = reader.readLine()) != null) {
                data.append(temp);
            }
            // 进行RSA解密
            temp = data.toString();
            if (StringUtil.isNull(temp)) {
                throw new IllegalArgumentException(String.format("文件[%s]内容为空，请检查待授权文件。", licenseFile));
            }
            try {
                temp = RSA.decrypt(temp, privateKey);
                data.setLength(0); // 清空
                data.append(temp);
            } catch (Exception e) {
                // RSA解密失败，则不做任何处理，说明内容没有变更
            }
        } catch (FileNotFoundException e) {
            log.info("license file [{}] not found.", licenseFile);
            throw new IllegalArgumentException(String.format("文件[%s]不存在，请将待授权文件放在该位置。", licenseFile));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.info("read license file [{}] error.", licenseFile, e);
            throw new RuntimeException("程序运行错误，请联系程序提供商", e);
        } finally {
            FileUtil.close(reader);
        }
        // 添加待授权信息
        Calendar clr = Calendar.getInstance();
        Map<String, Object> map = new HashMap<>();
        map.put("start_date", clr.getTimeInMillis());
        clr.add(Calendar.DAY_OF_YEAR, MAX_ISSUE_DAY);
        map.put("expiry_date", clr.getTimeInMillis());
        data.append(map.get("start_date"));
        data.append(map.get("expiry_date"));
        // 生成授权内容
        String license;
        try {
            license = RSA.sign(data.toString(), privateKey);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("RSA签名失败", e);
        }
        map.put("license", license);
        // 将授权信息写入文件
        write2File(licenseFile, JsonUtil.serialize(map));
    }
    
    /**
     * 签名校验.
     */
    public static void verify() {
        StringBuffer data = getSignData();
        String beforeSign = data.toString();
        Map<String, Object> map;
        // 解析授权信息
        FileReader reader = null;
        try {
            reader = new FileReader(LICENSE_FILE);
            map = JsonUtil.deserialize(reader, Map.class);
            if (map == null) {
                log.error("parse license file [{}] error.", LICENSE_FILE);
                // 文件解析时错误，说明还未取得合法的授权文件
                throw new IllegalArgumentException(String.format("程序还未取的授权码，请将文件[%s]发给程序提供商进行授权。", LICENSE_FILE));
            }
        } catch (Exception e) {
            log.error("read license file [{}] error.", LICENSE_FILE, e);
            // 其他错误，则要求重新授权
            try {
                write2File(LICENSE_FILE, RSA.encrypt(beforeSign, PUBLIC_KEY));
            } catch (Exception e1) {
                log.error("rsa encrypterror. file [{}] content:{}.", LICENSE_FILE, beforeSign, e);
                write2File(LICENSE_FILE, beforeSign);
            }
            throw new RuntimeException(String.format("授权码解析错误，请将文件[%s]发给程序提供商进行授权。", LICENSE_FILE), e);
        } finally {
            FileUtil.close(reader);
        }
        
        // 判断授权文件内容合法性
        try {
            Number startTime  = null;
            try {
                startTime = (Number) map.get("start_date");
            } catch (ClassCastException e) {
                String temp = (String)map.get("start_date");
                startTime = Long.parseLong(temp);
            }
            Number expiryTime = null;
            try {
                expiryTime = (Number) map.get("expiry_date");
            } catch (ClassCastException e) {
                String temp = (String)map.get("expiry_date");
                expiryTime = Long.parseLong(temp);
            }
            String license    = (String) map.get("license");
            if ((startTime == null) || (expiryTime == null) || StringUtil.isNull(license)) {
                throw new Exception("授权内容不合法");
            }
            // 校验授权时段
            long nowTime = Calendar.getInstance().getTimeInMillis();
            if ((startTime.longValue() > nowTime) || (expiryTime.longValue() < nowTime)) {
                throw new Exception("非授权时段，授权已过期，请联系程序提供商重新授权");
            }
            // 校验授权信息
            data.append(startTime.longValue());
            data.append(expiryTime.longValue());
            boolean flag = RSA.verify(data.toString(), PUBLIC_KEY, license);
            if (!flag) {
                throw new Exception("授权码校验失败，请联系程序提供商重新授权");
            }
        } catch (Exception e) {
            log.error("verify license file [{}] error.", LICENSE_FILE, e);
            // 授权校验失败，则等待重新授权
            write2File(LICENSE_FILE, beforeSign);
            throw new RuntimeException(String.format("授权文件不合法，请将文件[%s]发给程序提供商进行授权。", LICENSE_FILE), e);
        }
    }
    
    /**
     * 获取签名数据.
     */
    private static StringBuffer getSignData() {
        StringBuffer data = new StringBuffer();
        // 读取system.properties
        String name = Config.getString("app.name");
        if (StringUtil.isNull(name)) {
            throw new IllegalArgumentException("请配置程序名称[app.name]");
        }
        data.append(name);
        String port = Config.getString("app.port");
        // 20260519：某些项目不需要占用端口
        // if (StringUtil.isNull(port)) {
        //     throw new IllegalArgumentException("请配置程序端口[app.port]");
        // }
        data.append(port);
        String code = Config.getString("app.code");
        if (StringUtil.isNull(code)) {
            throw new IllegalArgumentException("请配置程序编码[app.code]");
        }
        data.append(code);
        String node = Config.getString("app.node");
        if (StringUtil.isNull(node)) {
            throw new IllegalArgumentException("请配置节点序号[app.node]");
        }
        data.append(node);
        String type = Config.getString("app.type");
        if (StringUtil.isNull(type)) {
            throw new IllegalArgumentException("请配置环境类型[app.type]");
        }
        data.append(type);
        // 读取系统mac地址（后期可以增加别的硬件信息）
        // 优先只获取物理网卡，若获取不到物理网卡，则获取所有网卡
        List<String> macs = NetworkUtil.getPhysicalMac();
        if (macs.isEmpty()) {
            macs = NetworkUtil.getMacAddress();
        }
        for (String item : macs) {
            data.append(item.replaceAll("-", ""));
        }
        return data;
    }
    
    /**
     * 将内容写入文件.
     */
    private static void write2File(String filePath, String content) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(filePath);
            writer.write(content);
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException(String.format("程序将内容[%s]写入文件[%s]时出错，请联系程序提供商.", content, filePath), e);
        } finally {
            FileUtil.close(writer);
        }
    }
}
