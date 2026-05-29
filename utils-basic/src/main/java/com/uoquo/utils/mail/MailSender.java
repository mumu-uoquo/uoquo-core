/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.mail;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.mail.MailSenderInfo.MailType;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：邮件发送. <br>
 * 日期：2018-02-26 16:12 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-02-26     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class MailSender {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    // 字符编码
    private String charset = Config.getString("app.email.charset", "UTF-8");

    // 发送服务器地址
    private final String smtpHost = Config.getString("app.email.smtp.host");

    // 发送服务器端口
    private final String smtpPort = Config.getString("app.email.smtp.port", "25");

    // 发送服务器验证
    private final boolean smtpAuth = Config.getBoolean("app.email.smtp.auth", false);
    
    // 发件人帐号
    private final String sendUser = Config.getString("app.email.from.mail");

    // 发件人密码
    private final String sendPswd = Config.getString("app.email.from.pswd");
    
    // 发件人姓名
    private final String sendName = Config.getString("app.email.from.name", Charset.forName(charset));

    // 邮件标题
    private String subject;
    
    // 邮件正文
    private String content;

    // 邮件格式
    private MailType mailType = MailType.HTML;
    
    // 收件人
    private List<String>  toList = new ArrayList<String>();
    
    // 抄送人
    private List<String>  ccList = new ArrayList<String>();
    
    // 附件
    private List<MailFile> attachment = new ArrayList<MailFile>();
    
    /**
     * 设置邮件内容.
     * @param content 正文
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * 设置邮件标题.
     * @param subject 标题
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    /**
     * 设置邮件字符编码（默认UTF-8） . <br>
     * @param charset 字符编码
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }
    
    /**
     * 设置邮件类型（默认html格式） . <br>
     * @param mailType 邮件类型（html、text）
     */
    public void setContentType(MailType mailType) {
        this.mailType = mailType;
    }

    /**
     * 添加收件人：单个.
     * @param email 单个收件人
     */
    public void addToEmail(String email) {
        if (StringUtil.isNull(email)) {
            return;
        }
        toList.add(email);
    }
    
    /**
     * 添加收件人：多个.
     * @param emailList 收件人列表
     */
    public void addToEmail(List<String> emailList) {
        if (emailList == null) {
            return;
        }
        this.toList.addAll(emailList);
    }
    
    /**
     * 添加抄送人：单个.
     * @param email 单个抄送人
     */
    public void addCcEmail(String email) {
        if (StringUtil.isNull(email)) {
            return;
        }
        ccList.add(email);
    }
    
    /**
     * 添加抄送人：多个.
     * @param emailList 抄送人列表
     */
    public void addCcEmail(List<String> emailList) {
        if (emailList == null) {
            return;
        }
        this.ccList.addAll(emailList);
    }

    /**
     * 添加附件：单个.
     * @param file 单个附件
     */
    public void addAttachment(MailFile file) {
        if (file == null) {
            return;
        }
        attachment.add(file);
    }

    /**
     * 添加附件：多个.
     * @param files 附件列表
     */
    public void addAttachment(List<MailFile> files) {
        if (files == null) {
            return;
        }
        this.attachment.addAll(files);
    }

    /**
     * 发送邮件.
     * @throws MessagingException 异常信息
     */
    public void send() throws MessagingException {
        // 基本校验
        if (StringUtil.isNull(smtpHost)) {
            throw new MessagingException("邮件服务器地址不能为空，请检查配置文件");
        }
        if (StringUtil.isNull(sendUser)) {
            throw new MessagingException("发信人不能为空，请检查配置文件");
        }
        if (toList.isEmpty()) {
            throw new MessagingException("收件人不能为空");
        }
        // 
        MailSenderInfo mailInfo = new MailSenderInfo();
        mailInfo.setMailServerHost(smtpHost);   
        mailInfo.setMailServerPort(smtpPort);   
        mailInfo.setCharset(charset);
        mailInfo.setValidate(smtpAuth);   
        mailInfo.setUserName(sendUser);   
        mailInfo.setPassword(sendPswd);
        if (StringUtil.isNull(sendName)) {
            mailInfo.setFromName(sendUser);
        } else {
            mailInfo.setFromName(sendName);
        }
        // 没有邮箱后缀时，默认以smtp的后缀为准，一般是exchange邮箱会用到
        if (!sendUser.contains("@")) {
            try {
                String temp = smtpHost.substring(smtpHost.indexOf(".") + 1);
                mailInfo.setFromAddress(sendUser + "@" + temp);
            } catch (Exception e) {
                mailInfo.setFromAddress(sendUser);
            }
        } else {
            mailInfo.setFromAddress(sendUser);
        }
        mailInfo.setToAddress(toList);
        mailInfo.setCcAddress(ccList);
        mailInfo.setSubject(subject);   
        mailInfo.setContent(content);
        mailInfo.setAttachFiles(attachment);
        // 默认为html方法发送
        switch (mailType) {
            case TEXT:
                send(mailInfo, false);
                break;
            default:
                send(mailInfo, true);
                break;
        }
    }
    
    /**
     * 邮件发送.
     * @param mailInfo 待发送邮件
     * @param isHtml   是否HTML格式（默认false）
     * @throws MessagingException 异常信息
     */
    private void send(MailSenderInfo mailInfo, boolean isHtml) throws MessagingException {
        // 编码字符集，解决标题及发件人的中文乱码问题
        String charset = MimeUtility.mimeCharset(mailInfo.getCharset());
        // 判断是否需要身份认证  
        SMTPAuthenticator authenticator = null;
        Properties pro = mailInfo.getProperties();
        if (mailInfo.isValidate()) {
            // 如果需要身份认证，则创建一个密码验证器
            authenticator = new SMTPAuthenticator(mailInfo.getUserName(), mailInfo.getPassword());
        }
        // 根据邮件会话属性和密码验证器构造一个发送邮件的session   
        Session sendMailSession = Session.getDefaultInstance(pro, authenticator);   
        // 根据session创建一个邮件消息 
        Message mailMessage = new MimeMessage(sendMailSession); 
        // 创建邮件发送者地址 
        Address from = null;
        try {
            String fromName = MimeUtility.encodeText(mailInfo.getFromName(), charset, null);
            from = new InternetAddress(mailInfo.getFromAddress(), fromName); 
        } catch (UnsupportedEncodingException e) {
            from = new InternetAddress(mailInfo.getFromAddress()); 
        }
        // 设置邮件消息的发送者 
        mailMessage.setFrom(from);
        // 创建邮件的接收者地址，并设置到邮件消息中
        if (!mailInfo.getToAddress().isEmpty()) {
            Address[] to = new InternetAddress[mailInfo.getToAddress().size()];
            int i = 0;
            for (String temp : mailInfo.getToAddress()) {
                to[i++] = new InternetAddress(temp);
            }
            mailMessage.setRecipients(Message.RecipientType.TO, to);
        }
        // 创建邮件的抄送者地址，并设置到邮件消息中
        if (!mailInfo.getCcAddress().isEmpty()) {
            Address[] cc = new InternetAddress[mailInfo.getCcAddress().size()];
            int j = 0;
            for (String temp : mailInfo.getCcAddress()) {
                cc[j++] = new InternetAddress(temp);
            }
            mailMessage.setRecipients(Message.RecipientType.CC, cc);
        }
        // 设置邮件消息的主题
        try {
            mailMessage.setSubject(MimeUtility.encodeText(mailInfo.getSubject(), charset, null));
        } catch (UnsupportedEncodingException e1) {
            mailMessage.setSubject(mailInfo.getSubject());
        }
        // 设置邮件消息发送的时间
        mailMessage.setSentDate(new Date());

        // 邮件对象，MiniMultipart类是一个容器类，包含MimeBodyPart类型的对象
        Multipart mainPart = new MimeMultipart();
        
        // 邮件正文
        BodyPart mailBody = new MimeBodyPart();
        if (isHtml) { 
            mailBody.setContent(mailInfo.getContent(), "text/html; charset=" + mailInfo.getCharset());  
        } else {
            try {
                mailBody.setText(MimeUtility.encodeText(mailInfo.getContent(), charset, null));
            } catch (UnsupportedEncodingException e) {
                mailBody.setText(mailInfo.getContent());
            }
        }
        mainPart.addBodyPart(mailBody); //添加邮件正文至邮件对象
        
        // 邮件附件
        for (MailFile item : mailInfo.getAttachFiles()) {
            BodyPart attachment = new MimeBodyPart();
            FileDataSource fds  = new FileDataSource(item.getAbsoluteFile());// 得到附件数据源
            attachment.setDataHandler(new DataHandler(fds)); // 得到附件本身
            //设置文件名，并解决中文名乱码问题 
            String fileName = item.getAliasName();
            try {
                attachment.setFileName(MimeUtility.encodeText(fileName, charset, null));
            } catch (UnsupportedEncodingException e) {
                attachment.setFileName(fileName);
            }
            mainPart.addBodyPart(attachment);  //添加邮件附件至邮件对象
        }

        // 添加邮件内容对象
        mailMessage.setContent(mainPart); 
        // 保存邮件 
        mailMessage.saveChanges();
        // 发送邮件 
        Transport.send(mailMessage);
    }
}
