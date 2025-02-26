package xlike.top.cloudflare.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import xlike.top.cloudflare.utils.OkHttpUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Administrator
 */
@RestController
@RequestMapping("/api/cloudflare")
public class CloudflareController {

    private static final Logger logger = LoggerFactory.getLogger(CloudflareController.class);

    @Value("${cloudflare.auth}")
    private String AUTH;

    @Value("${cloudflare.account-id}")
    private String ACCOUNT_ID;

    @Value("${cloudflare.tunnel-id}")
    private String TUNNEL_ID;

    @Value("${cloudflare.api-token}")
    private String API_TOKEN;

    @Value("${cloudflare.zone-id}")
    private String ZONE_ID;

    private String API_URL;

    @PostConstruct
    public void init() {
        if (AUTH.isEmpty() || ACCOUNT_ID.isEmpty() || TUNNEL_ID.isEmpty() || API_TOKEN.isEmpty() || ZONE_ID.isEmpty()) {
            throw new IllegalStateException("Cloudflare 配置未正确设置，请检查环境变量");
        }
        API_URL = "https://api.cloudflare.com/client/v4/accounts/" + ACCOUNT_ID + "/cfd_tunnel/" + TUNNEL_ID + "/configurations";
    }

    @PostMapping("/auth")
    public Map<String, Object> auth(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        if (request.get("auth") == null || Objects.equals(request.get("auth"), "") || !request.get("auth").equals(AUTH)) {
            response.put("success", false);
            response.put("message", "无效的授权");
            return response;
        }
        response.put("success", true);
        return response;
    }


    // 添加公共主机名
    @PostMapping("/add-hostnames")
    public Map<String, Object> addPublicHostnames(@RequestBody Map<String, Object> request) {
        if (request.get("auth") == null || Objects.equals(request.get("auth"), "") || !request.get("auth").equals(AUTH)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "无效的授权");
            return response;
        }
        Map<String, Object> response = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            List<String> subdomains = (List<String>) request.get("subdomains");
            String serviceUrl = (String) request.get("serviceUrl");
            if (subdomains == null || subdomains.isEmpty() || serviceUrl == null || serviceUrl.isEmpty()) {
                response.put("success", false);
                response.put("message", "子域列表和服务 URL 不能为空");
                return response;
            }
            // 获取当前 Tunnel 配置
            JSONArray currentIngress = getCurrentIngress();
            for (String subdomain : subdomains) {
                JSONObject rule = new JSONObject();
                rule.put("hostname", subdomain);
                rule.put("service", serviceUrl);
                currentIngress.add(0, rule);
            }

            // 更新 Tunnel 配置
            JSONObject config = new JSONObject();
            config.put("ingress", currentIngress);
            JSONObject tunnelRequestBody = new JSONObject();
            tunnelRequestBody.put("config", config);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + API_TOKEN);
            headers.put("Content-Type", "application/json");

            okhttp3.RequestBody tunnelBody = OkHttpUtils.createJsonRequestBody(tunnelRequestBody.toJSONString());
            Response tunnelResponse = OkHttpUtils.put(API_URL, headers, tunnelBody, null);
            String tunnelResponseBody = OkHttpUtils.getResponseBodyAsString(tunnelResponse);
            logger.info("添加主机名 Tunnel 响应: {}", tunnelResponseBody);
            JSONObject tunnelResult = JSON.parseObject(tunnelResponseBody);
            OkHttpUtils.closeResponse(tunnelResponse);

            if (!tunnelResponse.isSuccessful()) {
                response.put("success", false);
                response.put("message", "更新 Tunnel 配置失败: " + tunnelResult.getString("errors"));
                return response;
            }

            // 更新 DNS CNAME 记录
            String tunnelCnameTarget = TUNNEL_ID + ".cfargotunnel.com";
            for (String subdomain : subdomains) {
                String dnsApiUrl = "https://api.cloudflare.com/client/v4/zones/" + ZONE_ID + "/dns_records";
                JSONObject dnsRequestBody = new JSONObject();
                dnsRequestBody.put("type", "CNAME");
                dnsRequestBody.put("name", subdomain);
                dnsRequestBody.put("content", tunnelCnameTarget);
                dnsRequestBody.put("ttl", 1); // 自动 TTL
                dnsRequestBody.put("proxied", true); // 启用 Cloudflare 代理

                okhttp3.RequestBody dnsBody = OkHttpUtils.createJsonRequestBody(dnsRequestBody.toJSONString());
                Response dnsResponse = OkHttpUtils.post(dnsApiUrl, headers, dnsBody, null);
                String dnsResponseBody = OkHttpUtils.getResponseBodyAsString(dnsResponse);
                logger.info("添加 DNS CNAME 响应 ({}): {}", subdomain, dnsResponseBody);
                JSONObject dnsResult = JSON.parseObject(dnsResponseBody);
                OkHttpUtils.closeResponse(dnsResponse);

                if (!dnsResponse.isSuccessful()) {
                    logger.error("添加 DNS 记录失败 ({}): {}", subdomain, dnsResult.getString("errors"));
                    response.put("success", false);
                    response.put("message", "添加 DNS 记录失败 (" + subdomain + "): " + dnsResult.getString("errors"));
                    return response;
                }
            }

            response.put("success", true);
            response.put("message", "公共主机名和 DNS 记录添加成功");
            response.put("data", tunnelResult);
        } catch (IOException e) {
            logger.error("调用 Cloudflare API 失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "请求失败: " + e.getMessage());
        }
        return response;
    }

    // 获取公共主机名列表
    @PostMapping("/list-hostnames")
    public Map<String, Object> listPublicHostnames(@RequestBody Map<String, Object> request) {
        logger.info("获取公共主机名列表请求: {}", request);
        if (request.get("auth") == null || Objects.equals(request.get("auth"), "") || !request.get("auth").equals(AUTH)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "无效的授权");
            return response;
        }
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + API_TOKEN);
            headers.put("Content-Type", "application/json");

            logger.info("请求列表 URL: {}", API_URL);
            Response apiResponse = OkHttpUtils.get(API_URL, headers, null);
            String responseBody = OkHttpUtils.getResponseBodyAsString(apiResponse);
            logger.info("API 响应: {}", responseBody);
            OkHttpUtils.closeResponse(apiResponse);

            JSONObject result = JSON.parseObject(responseBody);
            if (!apiResponse.isSuccessful()) {
                response.put("success", false);
                response.put("message", "获取列表失败: " + result.getString("errors"));
                return response;
            }

            JSONObject resultData = result.getJSONObject("result");
            if (resultData == null) {
                logger.warn("响应中缺少 'result' 字段: {}", responseBody);
                response.put("success", true);
                response.put("data", new JSONArray());
                return response;
            }

            // 从 config 中获取 ingress
            JSONObject config = resultData.getJSONObject("config");
            if (config == null) {
                logger.warn("响应中缺少 'config' 字段: {}", responseBody);
                response.put("success", true);
                response.put("data", new JSONArray());
                return response;
            }

            JSONArray ingress = config.getJSONArray("ingress");
            if (ingress == null) {
                logger.warn("响应中缺少 'ingress' 字段或为空: {}", responseBody);
                response.put("success", true);
                response.put("data", new JSONArray());
                return response;
            }

            // 过滤掉无 hostname 的规则（如默认 404）
            List<JSONObject> hostnames = ingress.stream()
                    .filter(obj -> ((JSONObject) obj).containsKey("hostname"))
                    .map(obj -> (JSONObject) obj)
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("data", hostnames);
        } catch (IOException e) {
            logger.error("获取 Cloudflare 配置失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "请求失败: " + e.getMessage());
        }
        return response;
    }

    // 删除公共主机名
    @PostMapping("/delete-hostname")
    public Map<String, Object> deletePublicHostname(@RequestBody Map<String, String> request) {
        if (request.get("auth") == null || Objects.equals(request.get("auth"), "") || !request.get("auth").equals(AUTH)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "无效的授权");
            return response;
        }
        Map<String, Object> response = new HashMap<>();
        try {
            String hostname = request.get("hostname");
            if (hostname == null || hostname.isEmpty()) {
                response.put("success", false);
                response.put("message", "主机名不能为空");
                return response;
            }

            // 获取当前 Tunnel 配置并删除主机名
            JSONArray currentIngress = getCurrentIngress();
            JSONArray updatedIngress = new JSONArray();
            for (Object rule : currentIngress) {
                JSONObject jsonRule = (JSONObject) rule;
                if (!jsonRule.containsKey("hostname") || !jsonRule.getString("hostname").equals(hostname)) {
                    updatedIngress.add(jsonRule);
                }
            }

            // 更新 Tunnel 配置
            JSONObject config = new JSONObject();
            config.put("ingress", updatedIngress);
            JSONObject tunnelRequestBody = new JSONObject();
            tunnelRequestBody.put("config", config);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + API_TOKEN);
            headers.put("Content-Type", "application/json");

            okhttp3.RequestBody tunnelBody = OkHttpUtils.createJsonRequestBody(tunnelRequestBody.toJSONString());
            Response tunnelResponse = OkHttpUtils.put(API_URL, headers, tunnelBody, null);
            String tunnelResponseBody = OkHttpUtils.getResponseBodyAsString(tunnelResponse);
            logger.info("删除主机名 Tunnel 响应: {}", tunnelResponseBody);
            JSONObject tunnelResult = JSON.parseObject(tunnelResponseBody);
            OkHttpUtils.closeResponse(tunnelResponse);

            if (!tunnelResponse.isSuccessful()) {
                response.put("success", false);
                response.put("message", "删除 Tunnel 配置失败: " + tunnelResult.getString("errors"));
                return response;
            }

            // 查询并删除 DNS CNAME 记录
            String dnsApiUrl = "https://api.cloudflare.com/client/v4/zones/" + ZONE_ID + "/dns_records?name=" + hostname;
            Response dnsListResponse = OkHttpUtils.get(dnsApiUrl, headers, null);
            String dnsListResponseBody = OkHttpUtils.getResponseBodyAsString(dnsListResponse);
            logger.info("查询 DNS 记录响应 ({}): {}", hostname, dnsListResponseBody);
            JSONObject dnsListResult = JSON.parseObject(dnsListResponseBody);
            OkHttpUtils.closeResponse(dnsListResponse);

            if (dnsListResponse.isSuccessful() && dnsListResult.getBooleanValue("success")) {
                JSONArray dnsRecords = dnsListResult.getJSONArray("result");
                if (dnsRecords != null && !dnsRecords.isEmpty()) {
                    String recordId = dnsRecords.getJSONObject(0).getString("id"); // 假设只有一个匹配记录
                    String deleteDnsApiUrl = "https://api.cloudflare.com/client/v4/zones/" + ZONE_ID + "/dns_records/" + recordId;

                    Response dnsDeleteResponse = OkHttpUtils.delete(deleteDnsApiUrl, headers, null);
                    String dnsDeleteResponseBody = OkHttpUtils.getResponseBodyAsString(dnsDeleteResponse);
                    logger.info("删除 DNS 记录响应 ({}): {}", hostname, dnsDeleteResponseBody);
                    JSONObject dnsDeleteResult = JSON.parseObject(dnsDeleteResponseBody);
                    OkHttpUtils.closeResponse(dnsDeleteResponse);

                    if (!dnsDeleteResponse.isSuccessful()) {
                        logger.error("删除 DNS 记录失败 ({}): {}", hostname, dnsDeleteResult.getString("errors"));
                        response.put("success", false);
                        response.put("message", "删除 DNS 记录失败: " + dnsDeleteResult.getString("errors"));
                        return response;
                    }
                } else {
                    logger.warn("未找到 DNS 记录 ({}): {}", hostname, dnsListResponseBody);
                }
            } else {
                logger.error("查询 DNS 记录失败 ({}): {}", hostname, dnsListResult.getString("errors"));
                response.put("success", false);
                response.put("message", "查询 DNS 记录失败: " + dnsListResult.getString("errors"));
                return response;
            }

            response.put("success", true);
            response.put("message", "主机名和 DNS 记录删除成功");
        } catch (IOException e) {
            logger.error("删除 Cloudflare 主机名失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "请求失败: " + e.getMessage());
        }
        return response;
    }

    // 辅助方法：获取当前 ingress 配置
    private JSONArray getCurrentIngress() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + API_TOKEN);
        headers.put("Content-Type", "application/json");

        Response apiResponse = OkHttpUtils.get(API_URL, headers, null);
        String responseBody = OkHttpUtils.getResponseBodyAsString(apiResponse);
        logger.info("获取当前配置响应: {}", responseBody);
        OkHttpUtils.closeResponse(apiResponse);

        JSONObject result = JSON.parseObject(responseBody);
        if (apiResponse.isSuccessful() && result.getJSONObject("result") != null) {
            JSONObject config = result.getJSONObject("result").getJSONObject("config");
            if (config != null) {
                JSONArray ingress = config.getJSONArray("ingress");
                return ingress != null ? ingress : new JSONArray();
            }
        }
        logger.warn("无法获取当前配置，返回空数组: {}", responseBody);
        return new JSONArray();
    }
}