package xlike.top.cloudflare.utils;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class OkHttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(OkHttpUtils.class);
    private static final int DEFAULT_READ_TIMEOUT = 30;
    private static final int DEFAULT_CONNECT_TIMEOUT = 30;

    private static OkHttpClient getClient(Integer connectTimeout, Integer readTimeout, Proxy proxy) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(Objects.requireNonNullElse(connectTimeout, DEFAULT_CONNECT_TIMEOUT), TimeUnit.SECONDS);
        builder.readTimeout(Objects.requireNonNullElse(readTimeout, DEFAULT_READ_TIMEOUT), TimeUnit.SECONDS);
        if (proxy != null) {
            builder.proxy(proxy);
            logger.info("使用代理服务器 {}:{}", proxy.address().toString(), ((InetSocketAddress) proxy.address()).getPort());
        }
        return builder.build();
    }

    private static Response sendRequest(String url, String method, Map<String, String> headers, RequestBody body,
                                        Integer connectTimeout, Integer readTimeout, Proxy proxy) throws IOException {
        logger.info("准备发送 {} 请求到 URL: {}", method, url);
        Request.Builder requestBuilder = new Request.Builder().url(url).method(method, body);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
                logger.debug("添加请求头：{} = {}", header.getKey(), header.getValue());
            }
        }

        Request request = requestBuilder.build();
        OkHttpClient client = getClient(connectTimeout, readTimeout, proxy);
        long startTimeMillis = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        DateTimeFormatter beginFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try {
            logger.info("请求开始，当前时间: {}", startTime.format(beginFormatter));
            Response response = client.newCall(request).execute();
            long endTimeMillis = System.currentTimeMillis();
            LocalDateTime endTime = LocalDateTime.now();
            DateTimeFormatter endFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            logger.info("请求结束，当前时间: {} ,耗时 {} 毫秒，返回状态码: {}", 
                        endTime.format(endFormatter), (endTimeMillis - startTimeMillis), response.code());
            return response;
        } catch (IOException e) {
            long errorTimeMillis = System.currentTimeMillis();
            logger.error("请求失败，耗时 {} 毫秒，错误信息: {}", (errorTimeMillis - startTimeMillis), e.getMessage());
            throw e;
        }
    }

    public static Response get(String url, Map<String, String> headers, Proxy proxy) throws IOException {
        return sendRequest(url, "GET", headers, null, null, null, proxy);
    }

    public static Response post(String url, Map<String, String> headers, RequestBody body, Proxy proxy) throws IOException {
        return sendRequest(url, "POST", headers, body, null, null, proxy);
    }

    public static Response put(String url, Map<String, String> headers, RequestBody body, Proxy proxy) throws IOException {
        return sendRequest(url, "PUT", headers, body, null, null, proxy);
    }

    public static Response delete(String url, Map<String, String> headers, Proxy proxy) throws IOException {
        return sendRequest(url, "DELETE", headers, null, null, null, proxy);
    }

    public static Response deleteWithBody(String url, Map<String, String> headers, RequestBody body, Proxy proxy) throws IOException {
        return sendRequest(url, "DELETE", headers, body, null, null, proxy);
    }

    public static Proxy createProxy(String hostname, int port) {
        logger.info("创建代理，主机名: {}，端口: {}", hostname, port);
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, port));
    }

    public static RequestBody createJsonRequestBody(String json) {
        logger.info("创建JSON请求体: {}", json);
        return RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
    }

    public static RequestBody createFormRequestBody(Map<String, String> formData) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
            logger.debug("添加表单字段：{} = {}", entry.getKey(), entry.getValue());
        }
        return formBuilder.build();
    }

    public static String getResponseBodyAsString(Response response) throws IOException {
        if (response.body() != null) {
            String responseBody = response.body().string();
            logger.debug("响应体内容: {}", responseBody);
            return responseBody;
        }
        return null;
    }

    public static void closeResponse(Response response) {
        if (response != null && response.body() != null) {
            logger.info("关闭响应，URL: {}", response.request().url());
            response.close();
        }
    }
}