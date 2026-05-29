/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.common.propertyeditors;

import com.uoquo.utils.StringUtil;
import org.springframework.beans.propertyeditors.CustomBooleanEditor;
import org.jspecify.annotations.Nullable;

/**
 * 描述：spring解析boolean型参数处理类. <br>
 * 备注：不允许为空时，赋默认值false <br>
 * 日期：2018-01-30 19:22 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-30     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class BooleanEditor extends CustomBooleanEditor {  

    private final boolean allowEmpty;
    
    public BooleanEditor(boolean allowEmpty) {
        super(allowEmpty);
        this.allowEmpty = allowEmpty;
    }
    
    @Override
    public void setAsText(@Nullable String text) throws IllegalArgumentException {
        String input = (text != null ? text.trim() : null);
        if (StringUtil.isNull(input) && !allowEmpty) {
            input = "false"; // 不允许为空时，默认为false
        }
        super.setAsText(input);
    }
}
