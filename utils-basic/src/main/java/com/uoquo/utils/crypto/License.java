/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uoquo.utils.Config;
import com.uoquo.utils.NetworkUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;

/**
 * 描述：系统授权管理. <br>
 * <p>
 * 提供三个核心功能：<br>
 * 1. 获取机器信息（机器码、应用名、端口、系统时间），返回公钥加密后的字符串；<br>
 * 2. 写入授权，传入通过私钥加密后的内容，校验机器信息后保存到本地授权文件；<br>
 * 3. 授权信息加载，从授权文件读取内容，用公钥解密并校验机器信息后返回字符串。<br>
 * </p>
 * <p>
 * 授权文件 JSON 结构（由授权方颁发）：
 * <pre>{@code
 * {
 *   "machineCode": "AABBCCDDEEFF",   // 机器码（MAC，逗号分隔多块网卡）
 *   "appName":     "my-app",         // 应用名称
 *   "appPort":     "8080",           // 应用端口（可为空）
 *   "timestamp":   1720000000000,    // 颁发时间戳
 *   ... （授权方可追加其他字段，如 expiryDate 等）
 * }
 * }</pre>
 * </p>
 * <p>
 * 使用场景：<br>
 * <ul>
 *   <li>应用部署时，调用 {@link #getMachineInfo()} 获取机器信息密文，发送给授权方；</li>
 *   <li>授权方用私钥解密查看明文，在 JSON 中追加授权字段后用私钥加密颁发，调用 {@link #save(String)} 写入；</li>
 *   <li>应用启动时，调用 {@link #load()} 读取并验证授权，返回授权 JSON 明文供业务层使用。</li>
 * </ul>
 * </p>
 *
 * 日期：2019-03-19 14:01 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-03-19     Administrator    创建
 * 2.0          2025-07-08     uoquo team       重构：拆分为 getMachineInfo / writeLicense / loadLicense，增加机器信息校验
 * </pre>
 *
 * @since   JDK 1.8
 * @version 2.0
 * @author  uoquo team
 */
public class License {

    protected static final Logger log = LoggerFactory.getLogger(License.class);

    // 私钥（不编译到程序包，仅作为源码留存备用）
    //private static final String PRIVATE_KEY = "30820275020100300d06092a864886f70d01010105000482025f3082025b02010002818100877ddb82b5800e2059e15fd7ab1f984be2468e9bc44d57395729f3e18529ab085a412bcd174f3f426b7bdda08667ee76e2fc238813bd2019058fa5c47ca8bd052b12ecce22d25f288a9e3b147d752eb04dbb39e5327530d6d1ed279b0364a78eeabafabd3b6d55d0b7060c4f1fbbd38c683b6b42dcd16c1480d440bf1a84a49f0203010001028180686b235fc196f5cc12d8b0ef59ef1884ead6ab92fa1f0ca8a13730bfcdcb460742df54ed53187ccd285ea677cefd8bf6cd89b9ac6661ebb9bce26ec355bb092831e2ab9cbfd1865ca894f750720af02c8da233a602dc0462f1642b160bf90257519311075ce62b9b430c1ecf70eaf803b4a9c78b3524750840214d7be83c6519024100c6302ed7c29b1fe8643d159e6480545409b97b2eb64d7d340f2b747df3d0b3c11e3b67297ccda1f25a8e26f5e6eb7ea11806597582dded2446db5da281c3fbd3024100af03c58863d7f23e0aaa8f7e9350d10e99b3f4a6df00bba78e8a10721a02438d39a8af5357e2b64a090ad0afa0b7d5e2ebdc9747627a6e6d257e0418dccff08502404cf97dd8131f88df0bbfa5dbe510eaafd12f1726e76b654b88f0c9c7f9a07f906c1b8a4fd4c75134fdf2e432f1c359655c1d6b3576e4972c55d0f1a7225b6a3d024076ec942ca2d3d98ae90a8f294e874b17a5c00fcdd89911eac0c3f67ae24b89858c99a0517cd5d452a8a44dbc19f4f1851a0ab24d5290a93ac79042ce763562550240759be5b4cba78c75770986f95c35102083d891abccb88a1e3464a93b93e9d83bb7ce653a4ab1a43e07cfc048843f165eccd2cf0fe24b0f76dc30348282061ca7";

    /**
     * 公钥（内置，用于加密机器信息 & 解密授权内容）.
     */
    private static final String PUBLIC_KEY = "30819f300d06092a864886f70d010101050003818d0030818902818100877ddb82b5800e2059e15fd7ab1f984be2468e9bc44d57395729f3e18529ab085a412bcd174f3f426b7bdda08667ee76e2fc238813bd2019058fa5c47ca8bd052b12ecce22d25f288a9e3b147d752eb04dbb39e5327530d6d1ed279b0364a78eeabafabd3b6d55d0b7060c4f1fbbd38c683b6b42dcd16c1480d440bf1a84a49f0203010001";

    /**
     * 授权文件路径.
     */
    private static final String LICENSE_FILE = Config.APP_PATH + "/config/license.dat";

    static {
        synchronized (License.class) {
            log.info("license file path: {}", LICENSE_FILE);
            File file = new File(LICENSE_FILE);
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
            }
        }
    }

    /** 工具类，禁止实例化. */
    private License() {}

    // =========================================================================
    // 内部类：授权信息
    // =========================================================================

    /**
     * 授权信息实体.
     * <p>
     * 用于在 {@link License} 各方法间传递授权数据，也是授权文件 JSON 的对象映射。<br>
     * 授权方在颁发授权时，可在此对象基础上追加业务字段（如过期时间）后序列化写入文件。
     * </p>
     */
    public static class LicenseInfo {

        /** 机器码（物理网卡 MAC，去连字符、大写，多块网卡逗号分隔，已排序）. */
        private String machineCode;

        /** 应用名称（对应 {@code app.name}）. */
        private String appName;

        /** 应用端口（对应 {@code app.port}，可为空）. */
        private String appPort;

        /** 序列号（由申请方填写，随机器信息一并提交给授权方）. */
        private String serialNo;

        /** 激活码（由授权方填写后写入授权文件，供业务层校验）. */
        private String activationCode;

        /** 时间戳（生成机器信息时的系统时间，毫秒）. */
        private Long timestamp;

        /** 过期时间戳（毫秒，由授权方设置；为 null 时表示永久有效）. */
        private Long expiryDate;

        public String getMachineCode()               { return machineCode;     }
        public void   setMachineCode(String v)       { machineCode = v;        }

        public String getAppName()                   { return appName;         }
        public void   setAppName(String v)           { appName = v;            }

        public String getAppPort()                   { return appPort;         }
        public void   setAppPort(String v)           { appPort = v;            }

        public String getSerialNo()                  { return serialNo;        }
        public void   setSerialNo(String v)          { serialNo = v;           }

        public String getActivationCode()            { return activationCode;  }
        public void   setActivationCode(String v)    { activationCode = v;     }

        public Long   getTimestamp()                 { return timestamp;       }
        public void   setTimestamp(Long v)           { timestamp = v;          }

        public Long   getExpiryDate()                { return expiryDate;      }
        public void   setExpiryDate(Long v)          { expiryDate = v;         }
    }

    // -------------------------------------------------------------------------
    // 1. 获取机器信息
    // -------------------------------------------------------------------------

    /**
     * 获取机器信息并用公钥加密（序列号为空）.
     * <p>等同于 {@link #getMachineInfo(String) getMachineInfo(null)}。</p>
     *
     * @return 公钥加密后的 hex 字符串（机器信息密文）
     * @throws IllegalArgumentException 当必填配置项（{@code app.name}）缺失或无法获取机器码时抛出
     * @throws RuntimeException         当 RSA 加密失败时抛出
     */
    public static String getMachineInfo() {
        return getMachineInfo(null);
    }

    /**
     * 获取机器信息并用公钥加密.
     * <p>
     * 收集以下信息并组装为 {@link LicenseInfo}：<br>
     * <ul>
     *   <li>{@code machineCode}：物理网卡 MAC 地址（无连字符、大写，多块网卡逗号分隔，已排序）</li>
     *   <li>{@code appName}：应用名称（{@code app.name}）</li>
     *   <li>{@code appPort}：应用端口（{@code app.port}，可为空）</li>
     *   <li>{@code serialNo}：序列号（由调用方传入，可为空）</li>
     *   <li>{@code timestamp}：当前系统时间戳（毫秒）</li>
     * </ul>
     * 将对象序列化为 JSON 后用公钥加密，以 hex 字符串返回，可发送给授权方。
     * </p>
     *
     * @param serialNo 序列号，可为 null 或空字符串
     * @return 公钥加密后的 hex 字符串（机器信息密文）
     * @throws IllegalArgumentException 当必填配置项（{@code app.name}）缺失或无法获取机器码时抛出
     * @throws RuntimeException         当 RSA 加密失败时抛出
     */
    public static String getMachineInfo(String serialNo) {
        // 1. 收集当前机器信息
        LicenseInfo info = buildCurrentMachineInfo();

        // 2. 校验必填项
        if (StringUtil.isNull(info.getAppName())) {
            throw new IllegalArgumentException("请配置程序名称 [app.name]");
        }
        if (StringUtil.isNull(info.getMachineCode())) {
            throw new IllegalArgumentException("无法获取机器码，请检查网卡状态");
        }

        // 3. 填充序列号和时间戳
        info.setSerialNo(serialNo);
        info.setTimestamp(System.currentTimeMillis());
        String json = JsonUtil.serialize(info);

        // 4. 公钥加密
        try {
            String encrypted = RSA.encrypt(json, PUBLIC_KEY);
            log.debug("getMachineInfo: machine info encrypted, length={}", encrypted.length());
            return encrypted;
        } catch (GeneralSecurityException | IOException e) {
            log.error("getMachineInfo: RSA encrypt failed.", e);
            throw new RuntimeException("获取机器信息加密失败，请联系程序提供商", e);
        }
    }

    // -------------------------------------------------------------------------
    // 2. 写入授权
    // -------------------------------------------------------------------------

    /**
     * 写入授权内容到本地授权文件.
     * <p>
     * 传入由授权方用私钥加密后的授权内容（hex 字符串）。写入前会：<br>
     * <ol>
     *   <li>用公钥解密，得到授权 JSON 明文；</li>
     *   <li>校验 JSON 中的 {@code machineCode}、{@code appName}、{@code appPort}
     *       是否与当前运行环境一致，不一致则拒绝写入；</li>
     *   <li>校验通过后将密文写入 {@code license.dat}。</li>
     * </ol>
     * </p>
     *
     * @param encryptedContent 由私钥加密后的授权密文（hex 字符串），不能为空
     * @throws IllegalArgumentException 当 {@code encryptedContent} 为空时抛出
     * @throws IllegalStateException    当授权内容中的机器信息与当前环境不匹配时抛出
     * @throws RuntimeException         当 RSA 解密或写文件失败时抛出
     */
    public static void save(String encryptedContent) {
        if (StringUtil.isNull(encryptedContent)) {
            throw new IllegalArgumentException("授权内容不能为空");
        }

        // 1. 解密，得到授权 JSON 明文
        String plainText;
        try {
            plainText = RSA.decryptByPublicKey(encryptedContent, PUBLIC_KEY);
        } catch (GeneralSecurityException | IOException e) {
            log.error("writeLicense: RSA decrypt failed.", e);
            throw new RuntimeException("授权内容解密失败，请确认授权文件来源正确。", e);
        }

        // 2. 解析 JSON，校验机器信息
        LicenseInfo licenseInfo = JsonUtil.deserialize(plainText, LicenseInfo.class);
        if (licenseInfo == null) {
            throw new IllegalStateException("授权内容格式非法，无法解析为 JSON。");
        }
        verifyMachineInfo(licenseInfo);

        // 3. 校验通过，写入密文
        writeToFile(LICENSE_FILE, encryptedContent);
        log.info("writeLicense: license written to [{}]", LICENSE_FILE);
    }

    // -------------------------------------------------------------------------
    // 3. 加载授权
    // -------------------------------------------------------------------------

    /**
     * 从授权文件加载、解密并校验授权内容，返回 {@link LicenseInfo} 对象.
     * <p>等同于 {@link #loadLicense(Class) loadLicense(LicenseInfo.class)}。</p>
     *
     * @return 公钥解密并校验通过的 {@link LicenseInfo} 对象
     * @throws IllegalStateException 当授权文件不存在、内容为空或机器信息不匹配时抛出
     * @throws RuntimeException      当 RSA 解密失败时抛出，通常说明授权文件已损坏或非法
     */
    public static LicenseInfo load() {
        return decryptAndVerify(LicenseInfo.class);
    }

    /**
     * 从授权文件加载、解密并校验授权内容，反序列化为指定类型对象.
     * <p>
     * {@code clazz} 必须是 {@link LicenseInfo} 本身或其子类，子类中可定义额外的业务字段
     * （如过期时间、授权模块等），JSON 反序列化时会自动映射。<br>
     * 机器信息校验（{@code machineCode}、{@code appName}、{@code appPort}）逻辑与
     * {@link #load()} 完全一致。
     * </p>
     *
     * @param <T>   授权信息类型，必须是 {@link LicenseInfo} 或其子类
     * @param clazz 目标类型的 Class 对象，不能为 null
     * @return 反序列化并校验通过的授权信息对象
     * @throws IllegalArgumentException 当 {@code clazz} 为 null 时抛出
     * @throws IllegalStateException    当授权文件不存在、内容为空或机器信息不匹配时抛出
     * @throws RuntimeException         当 RSA 解密或 JSON 反序列化失败时抛出
     */
    public static <T extends LicenseInfo> T loadLicense(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz 不能为 null");
        }
        return decryptAndVerify(clazz);
    }

    // -------------------------------------------------------------------------
    // 私有工具方法
    // -------------------------------------------------------------------------

    /**
     * 读取授权文件、公钥解密、反序列化并校验机器信息的核心实现.
     *
     * @param <T>   授权信息类型
     * @param clazz 反序列化目标类型
     * @return 反序列化并校验通过的授权信息对象
     */
    private static <T extends LicenseInfo> T decryptAndVerify(Class<T> clazz) {
        // 1. 读取授权文件
        File file = new File(LICENSE_FILE);
        if (!file.exists()) {
            throw new IllegalStateException(
                    String.format("授权文件 [%s] 不存在，请联系程序提供商进行授权。", LICENSE_FILE));
        }
        String encryptedContent;
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(LICENSE_FILE));
            encryptedContent = new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.error("loadLicense: read license file [{}] error: {}", LICENSE_FILE, e.getMessage());
            throw new RuntimeException(
                    String.format("读取授权文件 [%s] 失败，请联系程序提供商。", LICENSE_FILE), e);
        }
        if (StringUtil.isNull(encryptedContent)) {
            throw new IllegalStateException(
                    String.format("授权文件 [%s] 内容为空，请联系程序提供商进行授权。", LICENSE_FILE));
        }

        // 2. 公钥解密（授权方用私钥加密，此处用公钥解密）
        String plainText;
        try {
            plainText = RSA.decryptByPublicKey(encryptedContent, PUBLIC_KEY);
            log.debug("loadLicense: license decrypted successfully.");
        } catch (GeneralSecurityException | IOException e) {
            log.error("loadLicense: RSA decrypt failed, license file [{}] may be corrupted: {}", LICENSE_FILE, e.getMessage());
            throw new RuntimeException("授权文件解密失败，请联系程序提供商重新授权。", e);
        }

        // 3. 反序列化为目标类型，校验机器信息
        T licenseInfo = JsonUtil.deserialize(plainText, clazz);
        if (licenseInfo == null) {
            throw new IllegalStateException("授权内容格式非法，无法解析为 JSON。");
        }
        verifyMachineInfo(licenseInfo);

        log.info("loadLicense: license verified successfully.");
        return licenseInfo;
    }

    /**
     * 收集当前运行环境的机器信息，填充为 {@link LicenseInfo} 对象.
     * <p>填充的字段：{@code machineCode}、{@code appName}、{@code appPort}。</p>
     *
     * @return 包含当前机器信息的 {@link LicenseInfo}
     */
    private static LicenseInfo buildCurrentMachineInfo() {
        // 机器码：物理网卡 MAC，规范化为无连字符大写，TreeSet 自动排序
        List<String> macs = NetworkUtil.getPhysicalMac();
        if (macs.isEmpty()) {
            macs = NetworkUtil.getMacAddress();
        }
        Set<String> macSet = new TreeSet<>();
        for (String mac : macs) {
            macSet.add(mac.replaceAll("-", "").toUpperCase());
        }

        LicenseInfo info = new LicenseInfo();
        info.setMachineCode(String.join(",", macSet));
        info.setAppName(Config.getString("app.name", ""));
        info.setAppPort(Config.getString("app.port", ""));
        return info;
    }

    /**
     * 校验授权信息中的机器信息与当前运行环境是否一致.
     * <p>
     * 校验规则：<br>
     * <ul>
     *   <li>{@code appName}：必须完全相等；</li>
     *   <li>{@code appPort}：两侧均为空则视为通过，否则必须完全相等；</li>
     *   <li>{@code machineCode}：必须与当前机器码完全相等（整字符串比较）。</li>
     * </ul>
     * </p>
     *
     * @param licenseInfo 已解密的授权信息对象
     * @throws IllegalStateException 当任一字段校验失败时抛出，包含具体字段名及期望/实际值
     */
    private static void verifyMachineInfo(LicenseInfo licenseInfo) {
        LicenseInfo current = buildCurrentMachineInfo();

        // ---- 校验 appName ----
        String licensedName = licenseInfo.getAppName() == null ? "" : licenseInfo.getAppName().trim();
        String currentName  = current.getAppName()     == null ? "" : current.getAppName().trim();
        if (!currentName.equals(licensedName)) {
            log.error("verifyMachineInfo: appName mismatch, licensed=[{}], current=[{}]", licensedName, currentName);
            throw new IllegalStateException("授权校验失败：应用名称不匹配，请重新申请授权。");
        }

        // ---- 校验 appPort ----
        String licensedPort = licenseInfo.getAppPort() == null ? "" : licenseInfo.getAppPort().trim();
        String currentPort  = current.getAppPort()     == null ? "" : current.getAppPort().trim();
        boolean portEmpty = StringUtil.isNull(licensedPort) && StringUtil.isNull(currentPort);
        if (!portEmpty && !currentPort.equals(licensedPort)) {
            log.error("verifyMachineInfo: appPort mismatch, licensed=[{}], current=[{}]", licensedPort, currentPort);
            throw new IllegalStateException("授权校验失败：应用端口不匹配，请重新申请授权。");
        }

        // ---- 校验 machineCode ----
        String licensedCode = licenseInfo.getMachineCode() == null ? "" : licenseInfo.getMachineCode().trim();
        String currentCode  = current.getMachineCode()     == null ? "" : current.getMachineCode().trim();
        if (StringUtil.isNull(licensedCode)) {
            log.error("verifyMachineInfo: machineCode is empty in license.");
            throw new IllegalStateException("授权校验失败：授权文件中未包含机器码，请重新申请授权。");
        }
        if (!currentCode.equals(licensedCode)) {
            log.error("verifyMachineInfo: machineCode mismatch, licensed=[{}], current=[{}]", licensedCode, currentCode);
            throw new IllegalStateException("授权校验失败：机器码不匹配，请重新申请授权。");
        }

        log.debug("verifyMachineInfo: passed. name=[{}], port=[{}], macs=[{}]", currentName, currentPort, currentCode);

        // ---- 校验过期时间（为 null 表示永久有效）----
        Long expiryDate = licenseInfo.getExpiryDate();
        if (expiryDate != null) {
            // 取当天 0 点时间戳，只要授权日期 >= 今天 0 点，当天 24 点前均有效
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE,      0);
            cal.set(Calendar.SECOND,      0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();
            if (expiryDate < todayStart) {
                log.error("verifyMachineInfo: license expired, expiryDate=[{}], todayStart=[{}]", expiryDate, todayStart);
                throw new IllegalStateException("授权校验失败：授权已过期，请联系程序提供商重新授权。");
            }
        }
    }

    /**
     * 将内容写入指定文件（覆盖写）.
     *
     * @param filePath 目标文件路径
     * @param content  待写入内容
     * @throws RuntimeException 当写文件失败时抛出
     */
    private static void writeToFile(String filePath, String content) {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            log.error("writeToFile: write to [{}] error: {}", filePath, e.getMessage());
            throw new RuntimeException(
                    String.format("写入文件 [%s] 失败，请检查文件权限或联系程序提供商。", filePath), e);
        }
    }
}
