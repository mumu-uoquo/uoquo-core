/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.exception;

import com.uoquo.web.BaseReturnCode;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 描述：所有异常的基类. <br>
 * 备注：错误码为8位16进制的字符串构成，规则如下：
 * <pre>
 * 1~2：应用编码，由配置文件（system.properties）定义的  system.app.code 值决定
 * 3~3：节点编码，由配置文件（system.properties）定义的  system.app.node 值决定
 * 4~8：具体错误编码
 * </pre>
 * 日期：2018-01-25 11:20 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-25     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public abstract class AbstractBaseException extends RuntimeException {

    /**
     * 应用编码，从配置文件读取.
     */
    private final transient String  appCode = System.getProperty("app.code", "00");

    /**
     * 应用节点，从配置文件读取.
     */
    private final transient String appNode  = System.getProperty("app.node", "0");

    /**
     * 状态码.
     */
    protected String status = "";

    /**
     * 错误码，抛出异常的代码中设置.
     */
    protected String code = "";
    
    /**
     * 错误消息，抛出异常的代码中设置.
     */
    protected String mesg = "";

    /**
     * 错误消息参数，用于替换mesg中的占位符
     */
    protected Object[] args = null;

    /**
     * 消息级别，主要用于出错时，前端的提示方式 .
     */
    protected String level;

    /**
     * 错误堆栈信息.
     */
    private String trace = "";
    // 用于标识是否打印过堆栈信息，防止死循环
    private transient boolean printStackTrace = false;

    /**
     * 请求ID（用于记录日志）.
     */
    private transient String traceId = "";

    public AbstractBaseException() {
    }

    /**
     * 构造方法：异常基类.
     * @param code 错误码
     */
    public AbstractBaseException(BaseReturnCode code) {
        this(code, (Throwable)null, null, (Object) null);
    }

    /**
     * 构造方法：异常基类.
     * @param code 错误码
     * @param mesg 错误消息
     */
    public AbstractBaseException(BaseReturnCode code, String mesg) {
        this(code, (Throwable)null, mesg, (Object) null);
    }

    /**
     * 构造方法：异常基类.
     * @param code 错误码
     * @param mesg 错误消息
     * @param args 消息参数（用于替换mesg中的占位符）
     */
    public AbstractBaseException(BaseReturnCode code, String mesg, Object... args) {
        this(code, (Throwable)null, mesg, args);
    }

    /**
     * 构造方法：异常基类.
     * @param code 错误码
     * @param ex   异常信息
     */
    public AbstractBaseException(BaseReturnCode code, Throwable ex) {
        this(code, ex, (ex == null) ? null : ex.getMessage(), (Object) null);
    }
    
    /**
     * 构造方法：异常基类.
     * @param code 错误码
     * @param mesg 错误消息
     * @param ex   异常信息
     */
    public AbstractBaseException(BaseReturnCode code, String mesg, Throwable ex) {
        this(code, ex, mesg, (Object) null);
    }

    /**
     * 构造方法：异常基类.
     * @param code 错误码
     * @param mesg 错误消息
     * @param args 消息参数（用于替换mesg中的占位符）
     * @param ex   异常信息
     */
    public AbstractBaseException(BaseReturnCode code, Throwable ex, String mesg, Object... args) {
        super((mesg != null) ? mesg : code.getText(), ex);
        this.status = code.getCode();
        this.code   = this.getCode();
        this.mesg   = (mesg != null) ? mesg : code.getText();
        this.args   = args;
        this.level  = code.getLevel().getText();
    }
    
    /**
     * 设置响应码.
     * @param status 响应码
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取响应码. <br>
     * 规则：业务响应码，0：请求正常
     * @return 业务响应码
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取错误编码. <br>
     * 原设计：应用节点（3个字符）作为前缀
     * @return 完整错误码
     */
    public String getCode() {
        if (code == null || code.trim().isEmpty()) {
            return appCode + appNode + status;
        } else {
            return code;
        }
    }

    /**
     * 设置错误编码.<br>
     * @param code 错误编码
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取错误级别. <br>
     * @return 错误级别
     */
    public String getLevel() {
        return level;
    }

    /**
     * 设置错误级别.<br>
     * @param level 错误级别
     */
    public void setLevel(String level) {
        this.level = level;
    }

    /**
     * 获取错误消息. <br>
     * @return 错误消息
     */
    public String getMesg() {
        if (mesg == null || mesg.trim().isEmpty()) {
            return super.getMessage();
        }
        if (args != null && args.length > 0) {
            return String.format(mesg, args);
        } else {
            // mesg.toString() 防止message中有占位符时，System.out.print出错
            return mesg.toString();
        }
    }
    
    /**
     * 设置错误信息.<br>
     * @param mesg 错误信息
     */
    public void setMesg(String mesg) {
        this.mesg = mesg;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    /**
     * 获取错误堆栈信息. <br>
     * @return 错误堆栈消息
     */
    public String getTrace() {
        try (
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
        ) {
            // 自定义堆栈信息
            if (trace != null) {
                pw.write(trace);
                pw.write("\r\n");
            }
            if (mesg != null) {
                pw.write(this.getMesg());
                pw.write("\r\n");
            }
            // 异常本身的堆栈信息
            if (this.getCause() == null) {
                this.printStackTrace(pw);
            } else {
                this.getCause().printStackTrace(pw);
            }
            return sw.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 设置错误堆栈信息.<br>
     * @param trace 错误堆栈信息
     */
    public void setTrace(String trace) {
        if (this.trace == null) {
            this.trace = trace;
        } else {
            this.trace = String.format("%s\r\n%s", trace, this.trace);
        }
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 格式化异常信息位JSON字串.
     */
    public String toJson() {
        StringBuffer result = new StringBuffer();
        result.append("{");
        result.append("\"status\": \"").append(getStatus()).append("\", ");
        result.append("\"code\"  : \"").append(getCode()  ).append("\", ");
        result.append("\"level\" : \"").append(getLevel() ).append("\", ");
        result.append("\"mesg\"  : \"").append(getMesg()  ).append("\", ");
        result.append("\"trace\" : \"").append(getTrace() ).append("\"");
        result.append("}");
        return result.toString();
    }

    @Override
    public String getMessage() {
        if (printStackTrace || mesg == null || mesg.isEmpty()) {
            return super.getMessage();
        } else {
            // 标识当前异常已经打过堆栈信息了，防止printStackTrace再次进入该getMessage方法导致死循环
            this.printStackTrace = true;
            return this.getTrace();
        }
    }
}
