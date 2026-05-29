/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：条码工具类. <br>
 * 背景：使用ZXing，生成条码. <br>
 * 日期：2019-06-04 19:17 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-06-04     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class BarcodeUtil {
    private static final Logger log = LoggerFactory.getLogger(BarcodeUtil.class);
    
    private static final String CHARSET = "UTF-8";
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private static final Pattern pattern = Pattern.compile("([0-9][0-9]){1,}"); // 偶数匹配

    /**
     * 一维码（EAN-13）.<br>
     * 编码：采用BarcodeFormat.EAN_13编码<br>
     * 特点：13位固定长度，仅支持数字，最后一位为校验位，不足13位将前补0<br>
     * @param code   条码字符（12位或者13位的数字）
     * @param width  图片宽度
     * @param height 图片高度
     */
    public static BufferedImage  generateEAN13(String code, int width, int height) throws Exception {
        width  = Math.max(width, 100);
        height = Math.max(height, 30);
        // 长度检测
        if (StringUtil.isNull(code) || (code.length() > 13)) {
            throw new IllegalArgumentException("EAN-13条码字符长度必须小于13位.");
        }
        // 全数字检测
        try {
            Long codeL = Long.valueOf(code);
            if (code.length() < 13) {
                code = String.format("%012d", codeL);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("EAN-13条码必须是数字字符.");
        }
        // 校验位判断
        int check = getStandardUPCEANChecksum(code);
        if (code.length() == 13) {
            int temp = Character.digit(code.charAt(12), 10);
            if (check != temp) {
                throw new IllegalArgumentException("最后一位校验数不正确.");
            }
        } else {
            code += check;
        }
        try {
            // 文字条形码
            Map<EncodeHintType, String> hints = new HashMap<EncodeHintType, String>();
            hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
            BitMatrix bitMatrix = new MultiFormatWriter().encode(code, BarcodeFormat.EAN_13, width, height, hints);
            BufferedImage image = toBufferedImage(bitMatrix);
            // 2. 文字大小
            int fontSize = 12; // 文字大小
            
            // 3. 给条形码添加文字
            Graphics2D gd = image.createGraphics();
            gd.setFont(new Font(null, Font.PLAIN, fontSize));
            FontMetrics fm = gd.getFontMetrics();
            int strW = fm.stringWidth(code);
            int strH = fm.getHeight();
            // 3.1 底部空白
            gd.setColor(Color.WHITE);
            gd.fillRect(0, height - strH / 2, width, strH);
            // 3.2 第1个字符
            int left = width / 2 - strW * 8;
            gd.setColor(Color.WHITE);
            gd.fillRect(0, height - strH, strW, strH);
            gd.setColor(Color.BLACK);
            gd.drawString(code.substring(0, 1), left, height - 4);
            // 3.3 第2 ~ 7个字符
            left += (int) (strW * 1.7);
            gd.setColor(Color.WHITE);
            gd.fillRect(left, height - strH, strW * 6, strH);
            gd.setColor(Color.BLACK);
            gd.drawString(code.substring(1, 7), left, height - 4);
            // 3.4 第8 ~ 13个字符
            left += (int) (strW * 6.8);
            gd.setColor(Color.WHITE);
            gd.fillRect(left, height - strH, strW * 6, strH);
            gd.setColor(Color.BLACK);
            gd.drawString(code.substring(7), left, height - 4);
            
            gd.dispose();
            return image;
        } catch (WriterException e) {
            log.warn("draw EAN-13 barcode for [{}] error.", code, e);
            throw new Exception("生成EAN-13条码错误.", e);
        }
    }

    /**
     * 一维码（ITF条码）.<br>
     * 编码：采用BarcodeFormat.ITF编码<br>
     * 特点：仅支持偶数位数字<br>
     * @param code   条码字符
     * @param width  图片宽度
     * @param height 图片高度
     */
    public static BufferedImage  generateITF(String code, int width, int height) throws Exception {
        width  = Math.max(width, 100);
        height = Math.max(height, 30);
        // 长度检测
        if (StringUtil.isNull(code) || (code.length() % 2 != 0)) {
            throw new IllegalArgumentException("ITF条码字符长度必须为偶数.");
        }
        // 全数字检测
        Matcher matcher = pattern.matcher(code);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("ITF条码字符必须为偶数长度的数字串.");
        }
        try {
            return generateBarCode(code, BarcodeFormat.ITF, width, height);
        } catch (WriterException e) {
            log.warn("draw ITF barcode for [{}] error.", code, e);
            throw new Exception("生成ITF条码错误.", e);
        }
    }

    /**
     * 一维码（128条码）.<br>
     * 编码：采用BarcodeFormat.CODE_128编码<br>
     * 特点：支持ASCII的所有128字符，常用于字母数字组合<br>
     * @param code   条码字符（1 &lt; 长度 &lt; 80）
     * @param width  图片宽度
     * @param height 图片高度
     */
    public static BufferedImage  generateCode128(String code, int width, int height) throws Exception {
        width  = Math.max(width, 100);
        height = Math.max(height, 30);
        // 长度检测
        if (StringUtil.isNull(code)) {
            throw new IllegalArgumentException("条码内容必填.");
        }
        try {
            return generateBarCode(code, BarcodeFormat.CODE_128, width, height);
        } catch (WriterException e) {
            log.warn("draw code_128 barcode for [{}] error.", code, e);
            throw new Exception("生成CODE_128条码错误.", e);
        }
    }
    
    /**
     * 生成带底部文本的条码.
     * @param code   条码字符
     * @param format 条码格式
     * @param width  图片宽度
     * @param height 图片高度
     */
    private static BufferedImage generateBarCode(String code,  BarcodeFormat format, int width, int height) throws WriterException {
        // 1. 条形码
        Map<EncodeHintType, String> hints = new HashMap<EncodeHintType, String>();
        hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(code, format, width, height, hints);
        BufferedImage image = toBufferedImage(bitMatrix);
        // 2. 文字大小
        int fontSize = 13; // 文字大小
        // 3. 给条形码添加文字
        Graphics2D gd = image.createGraphics();
        gd.setFont(new Font(null, Font.PLAIN, fontSize));
        FontMetrics fm = gd.getFontMetrics();
        int strW = fm.stringWidth(code);
        int strH = fm.getHeight();
        // 3.1 填充背景色
        gd.setColor(Color.WHITE);
        gd.fillRect(0, height - strH, width, strH);
        // 3.2 绘制文字
        gd.setColor(Color.BLACK);
        gd.drawString(code, (width - strW) / 2, height - 4);
        gd.dispose();
        return image;
    }
    
    /**
     * 二维码（QR_CODE）.
     * @param text   二维码字符
     * @param width  图片宽度
     * @param height 图片高度
     */
    public static BufferedImage  generateQRCode(String text, int width, int height) throws Exception {
        width  = Math.max(width, 200);
        height = Math.max(height, 200);
        // 长度检测
        if (StringUtil.isNull(text)) {
            throw new IllegalArgumentException("条码内容必填.");
        }
        try {
            Map<EncodeHintType, String> hints = new HashMap<EncodeHintType, String>();
            hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            return toBufferedImage(bitMatrix);
        } catch (WriterException e) {
            log.warn("draw qr code for [{}] error.", text, e);
            throw new Exception("生成QR CODE错误.", e);
        }
    }
    
    /** 
     * 根据点矩阵生成黑白图.
     */  
    private static BufferedImage toBufferedImage(BitMatrix matrix) {
        int width  = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }
        return image;
    }
    
    /**
     * UPC，EAN校验位计算.<br>
     * 校验位计算
     * <ol>
     *   <li>从右开始依次为码值编码。</li>
     *   <li>每个奇数编码值乘以3而每个偶数编码值乘以1。</li>
     *   <li>加总乘积得数，然后用10 减去和的最后一位数，得到校验位。</li>
     * </ol>
     */
    public static int getStandardUPCEANChecksum(CharSequence s) {
        int length = s.length();
        int sum = 0;
        for (int i = length - 1; i >= 0; i -= 2) {
            int digit = s.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                throw new IllegalArgumentException("必须是数字字符.");
            }
            sum += digit;
        }
        sum *= 3;
        for (int i = length - 2; i >= 0; i -= 2) {
            int digit = s.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                throw new IllegalArgumentException("必须是数字字符.");
            }
            sum += digit;
        }
        return (1000 - sum) % 10;
    }
}
