/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * 描述：条码工具类. <br>
 * 背景：使用ZXing，生成与解析条码. <br>
 * 日期：2019-06-04 19:17 <br>
 * 说明：
 * <ul>
 *   <li>一维码的文字绘制在条码下方的独立区域，不会遮挡条码；图片总高度即入参height.</li>
 *   <li>一维码的图片宽度可能大于入参width（ZXing会按条码实际所需宽度放大）.</li>
 *   <li>参数不合法抛出{@link IllegalArgumentException}，编码失败抛出{@link BarcodeException}.</li>
 *   <li>文字绘制使用AWT逻辑字体，Linux服务器需安装字体库（如fontconfig、DejaVu），且建议开启java.awt.headless.</li>
 * </ul>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-06-04     Administrator.           创建
 * 1.1          2026-11-10     uoquo team       修复EAN-13校验位与文字位置；文字改为绘制在条码下方；
 *                                              异常改为非受检异常；QR增加纠错等级与边距设置；
 *                                              增加条码图像解析方法decode（UPC-A补前导0）、decodeRaw（原始内容）
 * </pre>
 * @since   JDK 1.8
 * @version 1.1
 * @author  uoquo team
 */
public class BarcodeUtil {
    private static final String CHARSET = "UTF-8";
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    /** 一维码最小宽度. */
    private static final int MIN_WIDTH_1D = 100;
    /** 一维码最小高度（含底部文字区域）. */
    private static final int MIN_HEIGHT_1D = 50;
    /** 二维码最小边长. */
    private static final int MIN_SIZE_QR = 200;
    /** ITF、CODE_128 ZXing支持的最大内容长度. */
    private static final int MAX_LENGTH_1D = 80;

    /** 文字字号范围，实际字号随图片高度按比例计算. */
    private static final int MIN_FONT_SIZE = 9;
    private static final int MAX_FONT_SIZE = 14;
    /** EAN-13 数字缩小后的最小字号. */
    private static final int MIN_DIGIT_FONT_SIZE = 6;

    /** QR码边距（模块数）. */
    private static final int QR_MARGIN = 2;

    /** EAN-13 编码结构（单位：模块）. */
    private static final int EAN13_MODULES = 95;           // 条码总模块数（不含静区）
    private static final int EAN13_DEFAULT_MARGIN = 9;     // ZXing默认两侧静区合计模块数
    private static final int EAN13_DIGIT_MODULES = 7;      // 每个数字占用的模块数
    private static final int EAN13_LEFT_GROUP_OFFSET = 3;  // 左侧6位数字起点（起始符之后）
    private static final int EAN13_RIGHT_GROUP_OFFSET = 50; // 右侧6位数字起点（起始符+左侧42+中间分隔5）
    private static final int EAN13_GROUP_DIGITS = 6;

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern EVEN_DIGITS = Pattern.compile("([0-9]{2})+"); // 偶数位数字

    /** MultiFormatWriter 无状态，可共享. */
    private static final MultiFormatWriter WRITER = new MultiFormatWriter();

    /** QR码编码参数（只读）. */
    private static final Map<EncodeHintType, Object> QR_HINTS;

    /** 条码解析参数（只读）. */
    private static final Map<DecodeHintType, Object> DECODE_HINTS;

    static {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QR_MARGIN);
        QR_HINTS = Collections.unmodifiableMap(hints);

        Map<DecodeHintType, Object> decodeHints = new EnumMap<>(DecodeHintType.class);
        decodeHints.put(DecodeHintType.CHARACTER_SET, CHARSET);
        decodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        decodeHints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        DECODE_HINTS = Collections.unmodifiableMap(decodeHints);
    }

    /**
     * 条码生成失败异常（ZXing编码失败）.
     */
    public static class BarcodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BarcodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 一维码（EAN-13）.<br>
     * 编码：采用BarcodeFormat.EAN_13编码<br>
     * 特点：13位固定长度，仅支持数字，最后一位为校验位<br>
     * 输入：13位时校验最后一位；不足13位时前补0至12位，并自动追加校验位<br>
     * @param code   条码字符（不超过13位的数字）
     * @param width  图片宽度
     * @param height 图片高度（含底部文字）
     */
    public static BufferedImage generateEAN13(String code, int width, int height) {
        width  = Math.max(width, MIN_WIDTH_1D);
        height = Math.max(height, MIN_HEIGHT_1D);
        code = normalizeEAN13(code);

        int fontSize   = fontSizeFor(height);
        int textHeight = textAreaHeight(fontSize);
        int barHeight  = height - textHeight;

        // 左侧静区需容纳第1位数字，调大静区边距
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, Math.max(EAN13_DEFAULT_MARGIN, 2 * (digitWidth(fontSize) + 4)));
        BitMatrix matrix = encode(code, BarcodeFormat.EAN_13, width, barHeight, hints);
        BufferedImage image = render(matrix, height);

        // 条码实际起止位置与单模块宽度，不依赖ZXing内部的边距计算
        int[] rect = matrix.getEnclosingRectangle();
        int left = rect[0];
        int unit = Math.max(1, rect[2] / EAN13_MODULES);

        Graphics2D gd = createTextGraphics(image);
        try {
            // 每个数字只有7个模块宽的单元，按单元宽度（留1像素间隙）选取不会重叠的最大字号
            gd.setFont(fitDigitFont(gd, fontSize, EAN13_DIGIT_MODULES * unit - 1));
            FontMetrics fm = gd.getFontMetrics();
            int baseline = baseline(barHeight, textHeight, fm);
            // 第1位：位于起始符左侧
            gd.drawString(code.substring(0, 1), Math.max(0, left - fm.charWidth(code.charAt(0)) - unit - 1), baseline);
            // 第2 ~ 7位、第8 ~ 13位：每位数字在各自7个模块的单元内居中
            drawEAN13Digits(gd, fm, code.substring(1, 7), left + EAN13_LEFT_GROUP_OFFSET * unit, unit, baseline);
            drawEAN13Digits(gd, fm, code.substring(7), left + EAN13_RIGHT_GROUP_OFFSET * unit, unit, baseline);
        } finally {
            gd.dispose();
        }
        return image;
    }

    /**
     * 一维码（ITF条码）.<br>
     * 编码：采用BarcodeFormat.ITF编码<br>
     * 特点：仅支持偶数位数字<br>
     * @param code   条码字符（偶数位数字，长度不超过80）
     * @param width  图片宽度
     * @param height 图片高度（含底部文字）
     */
    public static BufferedImage generateITF(String code, int width, int height) {
        width  = Math.max(width, MIN_WIDTH_1D);
        height = Math.max(height, MIN_HEIGHT_1D);
        if (StringUtil.isNull(code)) {
            throw new IllegalArgumentException("条码内容必填.");
        }
        if (!EVEN_DIGITS.matcher(code).matches()) {
            throw new IllegalArgumentException("ITF条码必须为偶数长度的数字串.");
        }
        if (code.length() > MAX_LENGTH_1D) {
            throw new IllegalArgumentException("ITF条码字符长度不能超过" + MAX_LENGTH_1D + "位.");
        }
        return generateTextBarCode(code, BarcodeFormat.ITF, width, height);
    }

    /**
     * 一维码（128条码）.<br>
     * 编码：采用BarcodeFormat.CODE_128编码<br>
     * 特点：支持ASCII的所有128字符，常用于字母数字组合<br>
     * @param code   条码字符（1 &lt;= 长度 &lt;= 80）
     * @param width  图片宽度
     * @param height 图片高度（含底部文字）
     */
    public static BufferedImage generateCode128(String code, int width, int height) {
        width  = Math.max(width, MIN_WIDTH_1D);
        height = Math.max(height, MIN_HEIGHT_1D);
        if (StringUtil.isNull(code)) {
            throw new IllegalArgumentException("条码内容必填.");
        }
        if (code.length() > MAX_LENGTH_1D) {
            throw new IllegalArgumentException("CODE_128条码字符长度不能超过" + MAX_LENGTH_1D + "位.");
        }
        return generateTextBarCode(code, BarcodeFormat.CODE_128, width, height);
    }

    /**
     * 二维码（QR_CODE）.<br>
     * 纠错等级M，边距2个模块.
     * @param text   二维码字符
     * @param width  图片宽度
     * @param height 图片高度
     */
    public static BufferedImage generateQRCode(String text, int width, int height) {
        width  = Math.max(width, MIN_SIZE_QR);
        height = Math.max(height, MIN_SIZE_QR);
        if (StringUtil.isNull(text)) {
            throw new IllegalArgumentException("条码内容必填.");
        }
        BitMatrix matrix = encode(text, BarcodeFormat.QR_CODE, width, height, QR_HINTS);
        return render(matrix, matrix.getHeight());
    }

    /**
     * 解析条码图像，返回条码内容.<br>
     * 支持ZXing可识别的一维码与二维码（含EAN-13、ITF、CODE_128、QR_CODE），自动识别格式，
     * 并尝试反色图像. 图像中包含多个条码时只返回其中一个.<br>
     * <b>注意：</b>以0开头的EAN-13，ZXing会识别为UPC-A并返回12位内容. 本方法会给UPC-A补回前导0，
     * 统一返回13位，与{@link #generateEAN13}的输入保持一致. 因此真正的UPC-A条码（北美商品码）
     * 也会返回13位，如需原始的12位内容，请使用{@link #decodeRaw(BufferedImage)}.
     * @param image 条码图像
     * @return 条码内容；图像中未识别到条码时返回null
     * @throws IllegalArgumentException 图像为空
     */
    public static String decode(BufferedImage image) {
        return decode(image, true);
    }

    /**
     * 解析条码图像流，返回条码内容（UPC-A补前导0，规则同{@link #decode(BufferedImage)}）.
     * 流由调用方负责关闭.
     * @param in 图像输入流（PNG、JPEG、GIF、BMP等ImageIO支持的格式）
     * @return 条码内容；图像中未识别到条码时返回null
     * @throws IllegalArgumentException 输入流为空，或不是可识别的图像格式
     * @throws BarcodeException         读取图像失败
     */
    public static String decode(InputStream in) {
        return decode(readImage(in), true);
    }

    /**
     * 解析条码图像，返回ZXing识别出的原始内容，<b>不</b>给UPC-A补前导0（其余规则同{@link #decode(BufferedImage)}）.<br>
     * 适用于需要12位UPC-A内容的场景. 注意：以0开头的EAN-13也会被识别为UPC-A，返回12位.
     * @param image 条码图像
     * @return 条码原始内容；图像中未识别到条码时返回null
     * @throws IllegalArgumentException 图像为空
     */
    public static String decodeRaw(BufferedImage image) {
        return decode(image, false);
    }

    /**
     * 解析条码图像流，返回ZXing识别出的原始内容，不给UPC-A补前导0，规则同{@link #decodeRaw(BufferedImage)}.
     * 流由调用方负责关闭.
     * @param in 图像输入流
     * @return 条码原始内容；图像中未识别到条码时返回null
     * @throws IllegalArgumentException 输入流为空，或不是可识别的图像格式
     * @throws BarcodeException         读取图像失败
     */
    public static String decodeRaw(InputStream in) {
        return decode(readImage(in), false);
    }

    /**
     * 解析条码图像.
     * @param padUPCA 是否给UPC-A内容补前导0（转为EAN-13的13位形式）
     */
    private static String decode(BufferedImage image, boolean padUPCA) {
        if (image == null) {
            throw new IllegalArgumentException("条码图像必填.");
        }
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        // 先用局部自适应二值化（抗光照不均），失败再用全局直方图二值化（对清晰的生成图更稳）
        Result result = tryDecode(new HybridBinarizer(source));
        if (result == null) {
            result = tryDecode(new GlobalHistogramBinarizer(source));
        }
        if (result == null) {
            return null;
        }
        String text = result.getText();
        return padUPCA && result.getBarcodeFormat() == BarcodeFormat.UPC_A ? "0" + text : text;
    }

    /** 读取图像流. */
    private static BufferedImage readImage(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("条码图像流必填.");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(in);
        } catch (IOException e) {
            throw new BarcodeException("读取条码图像错误.", e);
        }
        if (image == null) {
            throw new IllegalArgumentException("不支持的图像格式.");
        }
        return image;
    }

    /**
     * 使用指定的二值化方式解码，未识别到条码时返回null.<br>
     * MultiFormatReader带状态（hints），非线程安全，因此每次新建.
     */
    private static Result tryDecode(Binarizer binarizer) {
        try {
            return new MultiFormatReader().decode(new BinaryBitmap(binarizer), DECODE_HINTS);
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * 生成带底部文本（文本内容即条码内容）的一维码.
     * @param code   条码字符
     * @param format 条码格式
     * @param width  图片宽度
     * @param height 图片总高度（条码 + 文字区域）
     */
    private static BufferedImage generateTextBarCode(String code, BarcodeFormat format, int width, int height) {
        int fontSize   = fontSizeFor(height);
        int textHeight = textAreaHeight(fontSize);
        int barHeight  = height - textHeight;

        BitMatrix matrix = encode(code, format, width, barHeight, null);
        BufferedImage image = render(matrix, height);

        Graphics2D gd = createTextGraphics(image);
        try {
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
            FontMetrics fm = gd.getFontMetrics(font);
            int strW = fm.stringWidth(code);
            // 文字比图片宽时按比例缩小
            if (strW > image.getWidth()) {
                font = font.deriveFont(Math.max(6f, fontSize * (float) image.getWidth() / strW));
                fm = gd.getFontMetrics(font);
                strW = fm.stringWidth(code);
            }
            gd.setFont(font);
            gd.drawString(code, Math.max(0, (image.getWidth() - strW) / 2), baseline(barHeight, textHeight, fm));
        } finally {
            gd.dispose();
        }
        return image;
    }

    /**
     * 校验并规范化EAN-13内容，返回13位（含校验位）的数字串.
     */
    public static String normalizeEAN13(String code) {
        if (StringUtil.isNull(code)) {
            throw new IllegalArgumentException("条码内容必填.");
        }
        if (!DIGITS.matcher(code).matches()) {
            throw new IllegalArgumentException("EAN-13条码必须是数字字符.");
        }
        if (code.length() > 13) {
            throw new IllegalArgumentException("EAN-13条码字符长度不能超过13位.");
        }
        if (code.length() == 13) {
            if (calcEAN13Checksum(code.substring(0, 12)) != code.charAt(12) - '0') {
                throw new IllegalArgumentException("最后一位校验数不正确.");
            }
            return code;
        }
        // 不足12位，前导补0
        String body = String.format("%12s", code).replace(' ', '0');
        return body + calcEAN13Checksum(body);
    }

    /**
     * EAN-13校验位计算（入参为不含校验位的12位数字）.<br>
     * 从左起，奇数位权重为1，偶数位权重为3，求和后用10减去和的个位数（个位为0时校验位为0）.
     */
    private static int calcEAN13Checksum(String digits12) {
        int sum = 0;
        for (int i = 0; i < digits12.length(); i++) {
            sum += (digits12.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10;
    }

    /**
     * 绘制EAN-13的一组数字，每个数字在其7个模块宽的单元内居中.
     */
    private static void drawEAN13Digits(Graphics2D gd, FontMetrics fm, String digits, int start, int unit, int baseline) {
        for (int i = 0; i < EAN13_GROUP_DIGITS; i++) {
            char c = digits.charAt(i);
            int center = start + (i * EAN13_DIGIT_MODULES * 2 + EAN13_DIGIT_MODULES) * unit / 2;
            gd.drawString(String.valueOf(c), center - fm.charWidth(c) / 2, baseline);
        }
    }

    /**
     * 调用ZXing编码，统一异常：参数不合法抛IllegalArgumentException，编码失败抛BarcodeException.
     */
    private static BitMatrix encode(String content, BarcodeFormat format, int width, int height,
                                    Map<EncodeHintType, ?> hints) {
        try {
            return WRITER.encode(content, format, width, height, hints);
        } catch (WriterException e) {
            throw new BarcodeException("生成" + format + "条码错误.", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(format + "条码内容不合法: " + e.getMessage(), e);
        }
    }

    /**
     * 根据点矩阵生成黑白图.<br>
     * 图片高度可大于矩阵高度，多出的部分为白色（用于绘制文字）.
     */
    private static BufferedImage render(BitMatrix matrix, int imageHeight) {
        int width  = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * imageHeight];
        Arrays.fill(pixels, WHITE);
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    pixels[offset + x] = BLACK;
                }
            }
        }
        BufferedImage image = new BufferedImage(width, imageHeight, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, imageHeight, pixels, 0, width);
        return image;
    }

    /** 创建绘制文字用的画笔（黑色、文字抗锯齿）. */
    private static Graphics2D createTextGraphics(BufferedImage image) {
        Graphics2D gd = image.createGraphics();
        gd.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gd.setColor(Color.BLACK);
        return gd;
    }

    /** 字号随图片高度按比例计算. */
    private static int fontSizeFor(int height) {
        return Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, height / 5));
    }

    /** 底部文字区域高度. */
    private static int textAreaHeight(int fontSize) {
        return (int) Math.ceil(fontSize * 1.5);
    }

    /** 文字在底部文字区域内垂直居中时的基线位置. */
    private static int baseline(int barHeight, int textHeight, FontMetrics fm) {
        return barHeight + (textHeight - fm.getHeight()) / 2 + fm.getAscent();
    }

    /**
     * 选取字体：字号不超过maxSize，且0~9中最宽的数字不超过maxWidth（最小字号6）.
     */
    private static Font fitDigitFont(Graphics2D gd, int maxSize, int maxWidth) {
        for (int size = maxSize; size > MIN_DIGIT_FONT_SIZE; size--) {
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
            FontMetrics fm = gd.getFontMetrics(font);
            int widest = 0;
            for (char c = '0'; c <= '9'; c++) {
                widest = Math.max(widest, fm.charWidth(c));
            }
            if (widest <= maxWidth) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, MIN_DIGIT_FONT_SIZE);
    }

    /** 指定字号下数字的绘制宽度. */
    private static int digitWidth(int fontSize) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D gd = createTextGraphics(tmp);
        try {
            return gd.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize)).charWidth('0');
        } finally {
            gd.dispose();
        }
    }
}
