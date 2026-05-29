/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.http.listener;

import java.io.IOException;

/**
 * 描述：自定义上传进度回调. <br>
 * 日期：2018-09-27 16:01 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-09-27     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class ProgressUploadListener {
    
    /**
     * 成功.
     * @param fileName 文件名称
     * @param total    文件总长度
     */
    public void onSuccess(String fileName, long total) {
        // do nothing
    }
    
    /**
     * 进度.
     * @param fileName 文件名称
     * @param total    文件总长度（有可能为-1）
     * @param write    已下载长度
     * @param read     当前读取的字节量（可以累加最近一秒的数据量，计算速率）
     */
    public void onProgress(String fileName, long total, long write, long read) {
        // do nothing
    }
    
    /**
     * 失败.
     * @param fileName 文件名称
     * @param e 出错的异常信息
     */
    public void onFailed(String fileName, Throwable e) throws IOException {
        if (e instanceof IOException) {
            throw (IOException)e;
        }
        throw new IOException(e);
    }
}
