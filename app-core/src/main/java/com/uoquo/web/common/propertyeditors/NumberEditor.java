/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.common.propertyeditors;

import com.uoquo.utils.StringUtil;
import org.springframework.beans.propertyeditors.CustomNumberEditor;

/**
 * 描述：基础数值类型解析. <br>
 * 备注：不允许为空时，赋默认值0 <br>
 * 日期：2018-01-30 19:35 <br>
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
public class NumberEditor extends CustomNumberEditor {
    private final boolean allowEmpty;

    public NumberEditor(Class<? extends Number> numberClass, boolean allowEmpty) {
        super(numberClass, allowEmpty);
        this.allowEmpty = allowEmpty;
    }
    
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        String input = (text != null ? text.trim() : null);
        if (StringUtil.isNull(input) && !allowEmpty) {
            input = "0"; // 不允许为空时，默认为0
        }
        super.setAsText(input);
    }
}
