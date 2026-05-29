/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.spring;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;

/**
 * 验证码工具类
 */
@Component
public class CaptchaUtil {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Random random = new Random();

    // 验证码图片宽(推荐比例: imH:imgW=1:(length+1) )
    @Value("${app.captcha.width:120}")
    private int imgW = 120;
    // 验证码图片高
    @Value("${app.captcha.height:30}")
    private int imgH = 30;
    // 背景干扰文字个数（useNoise=true时有用）
    @Value("${app.captcha.disturb:50}")
    private int disturb = 50;
    // 是否添加杂点
    @Value("${app.captcha.noise:false}")
    private boolean useNoise = false;
    // 是否画混淆曲线
    @Value("${app.captcha.curve:true}")
    private boolean useCurve = true;
    // 字符类型
    @Value("${app.captcha.type:auto}")
    private String strtype  = "auto";
    // 字体编号
    @Value("${app.captcha.font:auto}")
    private String fontNum  = "auto";
    // 验证码位数
    @Value("${app.captcha.length:4}")
    private int    length   = 4;
    // 验证码字体大小(px，建议 height*2/3)
    private int fontSize = 25;

    // 定义验证码字符。
    private static String digit[] = {"0","1","2","3","4","5","6","7","8","9"};
    private static String upper[] = {"A","B","C","D","E","F","G","H","J","K","L","M","N","P","Q","R","S","T","U","V","W","X","Y","Z"};
    private static String lower[] = {"a","b","c","d","e","f","g","h","i","j","k","m","n","p","q","r","s","t","u","v","w","x","y","z",};
    private static String alnum[] = {
            "A","B","C","D","E","F","G","H","J","K","M","N","P","Q","R","S","T","U","V","W","X","Y","Z",
            "a","b","c","d","e","f","g","h","i","j","k","m","n","p","q","r","s","t","u","v","w","x","y","z",
            "2","3","4","5","6","7","8","9"};
    private static String alpha[] = {
            "A","B","C","D","E","F","G","H","J","K","M","N","P","Q","R","S","T","U","V","W","X","Y","Z",
            "a","b","c","d","e","f","g","h","i","j","k","m","n","p","q","r","s","t","u","v","w","x","y","z"};

    @PostConstruct
    public void initConfiguration() {
        log.debug("CaptchaService init ...");
        if (imgW==0) {
            imgW = (int)(length * fontSize * 1.5 + fontSize * 1.5);
        }
        if (imgH==0) {
            imgH = (int)(fontSize * 2);
        }
    }

    /** 获取验证码 */
    public String getCaptchaValue() {
        String[] strArray;
        if ("digit".equalsIgnoreCase(strtype)) {
            strArray = digit;
        } else if ("upper".equalsIgnoreCase(strtype)) {
            strArray = upper;
        } else if ("lower".equalsIgnoreCase(strtype)) {
            strArray = lower;
        } else if ("alpha".equalsIgnoreCase(strtype)) {
            strArray = alpha;
        } else {
            strArray = alnum;
        }
        StringBuilder randstr = new StringBuilder();
        for (int i = 0; i < length; i++) {
            randstr.append(strArray[randomInt(0, strArray.length)]);
        }
        return randstr.toString();
    }

    /** 生成验证码图片 */
    public BufferedImage generateCaptchaImage(String captchaValue) {
        int width  = (int)((length  + 1) * fontSize * 1.5);
        int height = (int)(fontSize * 2);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Color bcolor = getRandomColor(230, 255);
        Color fcolor = getRandomColor(50, 150);
        Graphics2D g = image.createGraphics();
        g.setColor(bcolor);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (useNoise) { writeNoise(g, alnum, width, height); }
        if (useCurve) { writeCurve(g, fcolor, width, height); }
        Font font = this.getFont();
        g.setColor(fcolor);
        g.setFont(font);
        float codeNXL = fontSize / 2F;
        float codeNXT = fontSize * 1.2F;
        char[] rands = captchaValue.toCharArray();
        for (char rand : rands) {
            double a = randomInt(1, 3);
            double alpha = Math.PI / randomInt(8, 15);
            alpha = Math.pow(-1, a) * alpha;
            float px = (alpha > 0) ? (codeNXL + fontSize / 2F) : codeNXL;
            float py = codeNXT;
            g.rotate(-alpha, px, py);
            g.drawString(String.valueOf(rand), px, py);
            g.rotate(alpha, px, py);
            codeNXL += fontSize * (1 + randomInt(2, 7) * 0.1F);
            codeNXT  = fontSize * (1 + randomInt(1, 5) * 0.1F);
        }
        return image;
    }

    /**
     * 将BufferedImage转换为Web可用的Base64 Data URL
     */
    public String convertToWebBase64(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        byte[] imageBytes = baos.toByteArray();
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return "data:image/" + format + ";base64," + base64;
    }

    private Graphics2D writeNoise(Graphics2D g, String[] strArray, int imgW, int imgH) {
        for(int i=0;i<disturb;i++) {
            Color ncolor = new Color(randomInt(150,225), randomInt(150,225), randomInt(150,225));
            String str = strArray[randomInt(0, strArray.length)];
            int px = randomInt(0, imgW);
            int py = randomInt(0, imgH);
            g.setColor(ncolor);
            g.drawString(str, px, py);
        }
        return g;
    }

    private Graphics2D writeCurve(Graphics2D g, Color fcolor, int imgW, int imgH) {
        g.setColor(fcolor);
        float A = randomInt(1, imgH/2F);
        float b = randomInt(-imgH/4F, imgH/4F);
        float f = randomInt(-imgH/4F, imgH/4F);
        float T = randomInt(imgH*1.5, imgW*2);
        float w = (2 * (float)Math.PI) / T;
        int px1 = randomInt(1, imgH/2F);
        int py1 = (int)(A * (float)Math.sin(w*px1 + f) + b + imgH/2F);
        int pxEnd = randomInt(imgW/2F, imgW*0.667f);
        int px  = px1;
        int py  = py1;
        for(; px<=pxEnd; px=px+1) {
            if (w==0) break;
            py = (int)(A * (float)Math.sin(w*px + f) + b + imgH/2F);
            int i = randomInt(1, 3);
            while (i>0) { g.drawLine(px1, py1, px+i, py+i); px1 = px+i; py1 = py+i; i--; }
        }
        A = randomInt(1, imgH/2F);
        f = randomInt(-imgH/4F, imgH/4F);
        T = randomInt(imgH*1.5, imgW*2);
        w = (2 * (float)Math.PI) / T;
        b = (int)(py - A * (float)Math.sin(w*px + f) - imgH/2F);
        pxEnd = imgW;
        for(px=px1; px<=pxEnd; px=px+1) {
            if (w==0) break;
            py = (int)(A * (float)Math.sin(w*px + f) + b + imgH/2F);
            int i = randomInt(1, 3);
            while (i>0) { g.drawLine(px1, py1, px+i, py+i); px1 = px+i; py1 = py+i; i--; }
        }
        return g;
    }

    private Font getFont() {
        Font font;
        try {
            String fontFileName = "";
            if ("auto".equals(fontNum)) {
                int ftemp = useNoise ? randomInt(1, 7) : randomInt(1, 10);
                fontFileName = "ttfs/" + ftemp + ".ttf";
            } else {
                fontFileName = "ttfs/" + fontNum + ".ttf";
            }
            ClassPathResource resource = new ClassPathResource(fontFileName);
            font = Font.createFont(Font.TRUETYPE_FONT, resource.getInputStream());
            font = font.deriveFont(Font.PLAIN, fontSize);
        } catch (Exception e) {
            font = new Font("Times New Roman", Font.PLAIN, fontSize);
        }
        return font;
    }

    private Color getRandomColor(int fc, int bc) {
        Random random = new Random();
        fc = (fc>255) ? 255 : Math.max(fc, 0);
        bc = (bc>255) ? 255 : Math.max(bc, 0);
        if (fc>bc) { int t = fc; fc=bc; bc=t; }
        int r = fc + random.nextInt(bc - fc);
        int g = fc + random.nextInt(bc - fc);
        int b = fc + random.nextInt(bc - fc);
        return new Color(r, g, b);
    }

    private Color getRandomLightColor() {
        return new Color(200 + random.nextInt(55), 200 + random.nextInt(55), 200 + random.nextInt(55));
    }

    private Color getRandomDarkColor() {
        return new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150));
    }

    private int randomInt(double from, double to) {
        Random r = new Random();
        int len = (int)Math.ceil(to-from);
        int str = (int)Math.floor(from);
        int temp = r.nextInt( Math.abs(len) );
        temp = (len<0) ? -temp : temp;
        return str + temp;
    }

}
