/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign;

import com.uoquo.web.exception.RemoteServiceException;
import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：feign接收数据异常后的解码器. <br>
 * 日期：2018-02-02 14:42 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-02-02     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class FeignErrorDecoder implements ErrorDecoder {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    final Decoder decoder;
    final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    public FeignErrorDecoder(Decoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            // feign.SynchronousMethodHandler
            Object res = decoder.decode(response, String.class);
            if (res instanceof Exception) {
                return (Exception) res;
            } else {
                return new RuntimeException(res.toString());
            }
        } catch (FeignException.ServiceUnavailable e) {
            log.error("ServiceUnavailable: {}", e.getMessage());
            return new RemoteServiceException("服务不存在", e);
        } catch (Exception e) {
            log.error("FeignErrorDecoder: {}", e.getMessage());
            return defaultDecoder.decode(methodKey, response);
        }
    }
}
