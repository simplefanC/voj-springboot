package com.simplefanc.voj.judger.judge.remote.httpclient;

import cn.hutool.core.lang.Assert;
import org.apache.http.HttpStatus;

import java.io.IOException;

public class HttpStatusValidator implements SimpleHttpResponseValidator {

    public static HttpStatusValidator SC_OK = new HttpStatusValidator(HttpStatus.SC_OK);

    public static HttpStatusValidator SC_MOVED_PERMANENTLY = new HttpStatusValidator(HttpStatus.SC_MOVED_PERMANENTLY);

    public static HttpStatusValidator SC_MOVED_TEMPORARILY = new HttpStatusValidator(HttpStatus.SC_MOVED_TEMPORARILY);

    /////////////////////////////////////////////////////////////////
    private int httpStatusCode;

    public HttpStatusValidator(int httpStatusCode) {
        super();
        this.httpStatusCode = httpStatusCode;
    }

    @Override
    public void validate(SimpleHttpResponse response) throws IOException {
        if (response.getStatusCode() != httpStatusCode) {
            // FileTool.writeFile(response.getStatusCode() + "-" + httpStatusCode,
            // response.getBody());
        }
        Assert.isTrue(response.getStatusCode() == httpStatusCode,
                String.format("expected=%s, received=%s", httpStatusCode, response.getStatusCode()));
    }

}
