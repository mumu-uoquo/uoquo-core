/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.mail;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;

/**
 * 描述：SMTP验证信息. <br>
 * 日期：2018-02-26 16:11 <br>
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
public class SMTPAuthenticator extends Authenticator {
    private String  user;
    private String  pswd;
    
    public SMTPAuthenticator(String user, String pswd) {
        this.user = user;
        this.pswd = pswd;
    }

    @Override
    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(user, pswd);
    }
}
