/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.mail;

import com.uoquo.utils.json.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 描述：邮件详细信息. <br>
 * 日期：2018-02-26 16:11 <br>
 * 变更：
 * <pre>
 * VersionDate  ModifiedBy Content
 * --------  ----------  ------------  -----------------------
 * 1.0 2018-02-26  xuhz.  创建
 * </pre>
 * @version 1.0
 * @author  uoquo team
 */
public class MailSenderInfo {
    
    // 发送邮件的服务器的IP和端口
    private String mailServerHost;
    private String mailServerPort = "25";  
    // 是否需要身份验证
    private boolean validate = false;  
    
    // 登陆邮件发送服务器的用户名和密码
    private String userName;
    private String password;
    
    // 邮件发送者的地址
    private String fromAddress;
    
    // 邮件发送者的名称
    private String fromName;
    
    // 邮件接收者的地址
    private List<String> toAddress = new ArrayList<String>();
    
    // 邮件抄送者的地址
    private List<String> ccAddress = new ArrayList<String>();
    
    // 邮件附件的文件名
    private List<MailFile> attachFiles = new ArrayList<MailFile>();
    
    // 邮件主题
    private String subject;
    // 邮件的文本内容
    private String content;
    // 邮件编码
    private String charset = "UTF-8";
    
    // 邮件格式（text，html），默认html
    public enum MailType {
        TEXT, HTML
    }

    /**
     * 获得邮件会话属性.
     */ 
    public Properties getProperties() {
        Properties p = new Properties();
        p.put("mail.smtp.host", this.mailServerHost);
        p.put("mail.smtp.port", this.mailServerPort);
        p.put("mail.smtp.auth", validate ? "true" : "false");
        return p;
    }

    public String getMailServerHost() {
        return mailServerHost;
    }

    public void setMailServerHost(String mailServerHost) {
        this.mailServerHost = mailServerHost;
    }

    public String getMailServerPort() {
        return mailServerPort;
    }

    public void setMailServerPort(String mailServerPort) {
        this.mailServerPort = mailServerPort;
    }

    public boolean isValidate() {
        return validate;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public List<String> getToAddress() {
        return toAddress;
    }

    public void setToAddress(List<String> toAddress) {
        this.toAddress = toAddress;
    }

    public List<String> getCcAddress() {
        return ccAddress;
    }

    public void setCcAddress(List<String> ccAddress) {
        this.ccAddress = ccAddress;
    }

    public List<MailFile> getAttachFiles() {
        return attachFiles;
    }

    public void setAttachFiles(List<MailFile> attachFiles) {
        this.attachFiles = attachFiles;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
    
    @Override
    public String toString() {
        return JsonUtil.serialize(this);
    }
}
