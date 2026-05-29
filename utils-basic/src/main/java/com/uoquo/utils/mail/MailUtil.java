/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.mail;

import java.util.ArrayList;
import java.util.List;
import jakarta.mail.MessagingException;

/**
 * 描述：邮件发送工具类. <br>
 * 日期：2018-02-26 16:33 <br>
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
public class MailUtil {
    
    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param title   标题
     * @param content 内容
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, String title, String content) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        send(toMail, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param title   标题
     * @param content 内容
     * @param file    附件
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, String title, String content, MailFile file) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        files.add(file);
        send(toMail, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param title   标题
     * @param content 内容
     * @param files   附件列表
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, String title, String content, List<MailFile> files) throws MessagingException {
        List<String> toList = new ArrayList<String>();
        toList.add(toMail);
        List<String> ccList = new ArrayList<String>();
        send(toList, ccList, title, content, files);
    }
    
    /**
     * 邮件发送 . <br>
     * @param toList  收件人列表
     * @param title   标题
     * @param content 内容
     * @throws MessagingException 异常信息
     */
    public static void send(List<String> toList, String title, String content) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        send(toList, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toList  收件人列表
     * @param title   标题
     * @param content 内容
     * @param file    附件
     * @throws MessagingException 异常信息
     */
    public static void send(List<String> toList, String title, String content, MailFile file) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        files.add(file);
        send(toList, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toList  收件人列表
     * @param title   标题
     * @param content 内容
     * @param files   附件列表
     * @throws MessagingException 异常信息
     */
    public static void send(List<String> toList, String title, String content, List<MailFile> files) throws MessagingException {
        List<String> ccList = new ArrayList<String>();
        send(toList, ccList, title, content, files);
    }
    
    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param ccMail  抄送人
     * @param title   标题
     * @param content 内容
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, String ccMail, String title, String content) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        send(toMail, ccMail, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param ccMail  抄送人
     * @param title   标题
     * @param content 内容
     * @param file    附件
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, String ccMail, String title, String content, MailFile file) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        files.add(file);
        send(toMail, ccMail, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param ccMail  抄送人
     * @param title   标题
     * @param content 内容
     * @param files   附件列表
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, String ccMail, String title, String content, List<MailFile> files) throws MessagingException {
        List<String> toList = new ArrayList<String>();
        toList.add(toMail);
        List<String> ccList = new ArrayList<String>();
        ccList.add(ccMail);
        send(toList, ccList, title, content, files);
    }
    
    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param ccList  抄送人列表
     * @param title   标题
     * @param content 内容
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, List<String> ccList, String title, String content) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        send(toMail, ccList, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param ccList  抄送人列表
     * @param title   标题
     * @param content 内容
     * @param file    附件
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, List<String> ccList, String title, String content, MailFile file) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        files.add(file);
        send(toMail, ccList, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toMail  收件人
     * @param ccList  抄送人列表
     * @param title   标题
     * @param content 内容
     * @param files   附件列表
     * @throws MessagingException 异常信息
     */
    public static void send(String toMail, List<String> ccList, String title, String content, List<MailFile> files) throws MessagingException {
        List<String> toList = new ArrayList<String>();
        toList.add(toMail);
        send(toList, ccList, title, content, files);
    }

    
    /**
     * 邮件发送 . <br>
     * @param toList  收件人列表
     * @param ccList  抄送人列表
     * @param title   标题
     * @param content 内容
     * @throws MessagingException 异常信息
     */
    public static void send(List<String> toList, List<String> ccList, String title, String content) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        send(toList, ccList, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toList  收件人列表
     * @param ccList  抄送人列表
     * @param title   标题
     * @param content 内容
     * @param file    附件
     * @throws MessagingException 异常信息
     */
    public static void send(List<String> toList, List<String> ccList, String title, String content, MailFile file) throws MessagingException {
        List<MailFile> files = new ArrayList<MailFile>();
        files.add(file);
        send(toList, ccList, title, content, files);
    }

    /**
     * 邮件发送 . <br>
     * @param toList  收件人列表
     * @param ccList  抄送人列表
     * @param title   标题
     * @param content 内容
     * @param files   附件列表
     * @throws MessagingException 异常信息
     */
    public static void send(List<String> toList, List<String> ccList, String title, String content, List<MailFile> files) throws MessagingException {
        MailSender mail = new MailSender();
        mail.setSubject(title);
        mail.setContent(content);
        mail.addToEmail(toList);
        mail.addCcEmail(ccList);
        mail.addAttachment(files);
        mail.send();
    }
    
}
