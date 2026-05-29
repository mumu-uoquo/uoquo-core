/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.mail;

import com.uoquo.utils.StringUtil;
import java.io.File;
import java.io.Serial;
import java.net.URI;

/**
 * 描述：邮件附件. <br>
 * 日期：2018-02-26 16:10 <br>
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
public class MailFile extends File {
    @Serial
    private static final long serialVersionUID = -6162496934460350687L;
    
    /**
     * 别名.
     */
    private String alias;


    public MailFile(URI uri) {
        super(uri);
    }

    public MailFile(String pathname) {
        super(pathname);
    }
    
    public MailFile(File parent, String child) {
        super(parent, child);
    }

    public MailFile(String parent, String child) {
        super(parent, child);
    }
    
    /**
     * 获取附件名称.
     */
    public String getAliasName() {
        if (StringUtil.isNull(alias)) {
            return this.getName();
        }
        if (alias.indexOf(".") > 0) {
            return alias;
        }
        String ext = this.getName().substring(this.getName().lastIndexOf("."));
        return alias + ext;
    }

    /**
     * 设置附件别名.
     * @param alias 别名
     */
    public void setAliasName(String alias) {
        this.alias = alias;
    }
}
