package com.xingyu.musicvault.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.common.ErrorResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.search.BraveSearchDtos.BraveSearchRequest;
import com.xingyu.musicvault.search.BraveSearchDtos.BraveSearchResponse;
import com.xingyu.musicvault.search.BraveSearchDtos.BraveSearchResultResponse;
import com.xingyu.musicvault.search.BraveSearchDtos.BraveSearchStatusResponse;
import com.xingyu.musicvault.search.BraveSearchDtos.SaveBraveKeyRequest;
import com.xingyu.musicvault.search.BraveSearchDtos.SetBraveEnabledRequest;
import com.xingyu.musicvault.settings.AppSetting;
import com.xingyu.musicvault.settings.SettingsCryptoService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class BraveSearchService {
    private static final String SETTING_KEY = "brave.search.api-key";
    private static final int MAX_QUERY_LENGTH = 160;
    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_COUNT = 10;
    private static final int MAX_RESPONSE_BYTES = 262_144;

    @Inject
    MusicVaultConfig config;

    @Inject
    SettingsCryptoService cryptoService;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public BraveSearchStatusResponse status() {
        AppSetting setting = AppSetting.findById(SETTING_KEY);
        KeyState keyState = keyState(setting);
        return new BraveSearchStatusResponse(
                keyState.configured(),
                keyState.enabled(),
                keyState.searchable(),
                keyState.mode(),
                keyState.message(),
                cryptoService.available(),
                setting == null ? null : setting.updatedAt,
                setting == null ? null : setting.lastCheckedAt,
                safeError(setting == null ? null : setting.lastError)
        );
    }

    public BraveSearchStatusResponse saveKey(SaveBraveKeyRequest request) {
        rejectConsoleMutationInEnvMode();
        String key = normalizeApiKey(request == null ? null : request.apiKey());
        if (!cryptoService.available()) {
            throw new BadRequestException("MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY is required to store Brave Search API Key");
        }
        String operator = normalizeOperator(request == null ? null : request.updatedBy());
        return QuarkusTransaction.requiringNew().call(() -> {
            AppSetting setting = AppSetting.findById(SETTING_KEY);
            if (setting == null) {
                setting = new AppSetting();
                setting.key = SETTING_KEY;
            }
            setting.encryptedValue = cryptoService.encrypt(key);
            setting.enabled = true;
            setting.updatedBy = operator;
            setting.lastError = null;
            setting.lastCheckedAt = null;
            if (!setting.isPersistent()) {
                setting.persist();
            }
            return status();
        });
    }

    public BraveSearchStatusResponse setEnabled(SetBraveEnabledRequest request) {
        rejectConsoleMutationInEnvMode();
        String operator = normalizeOperator(request == null ? null : request.updatedBy());
        return QuarkusTransaction.requiringNew().call(() -> {
            AppSetting setting = AppSetting.findById(SETTING_KEY);
            if (setting == null) {
                setting = new AppSetting();
                setting.key = SETTING_KEY;
                setting.enabled = request != null && request.enabled();
                setting.updatedBy = operator;
                setting.persist();
            } else {
                setting.enabled = request != null && request.enabled();
                setting.updatedBy = operator;
            }
            return status();
        });
    }

    public BraveSearchStatusResponse testConnection() {
        search(new BraveSearchRequest("xingyu music vault", 1));
        return QuarkusTransaction.requiringNew().call(() -> {
            AppSetting setting = AppSetting.findById(SETTING_KEY);
            if (setting != null) {
                setting.lastError = null;
                setting.lastCheckedAt = LocalDateTime.now();
            }
            return status();
        });
    }

    public BraveSearchResponse search(BraveSearchRequest request) {
        String query = normalizeQuery(request == null ? null : request.query());
        int count = normalizeCount(request == null ? null : request.count());
        String apiKey = activeApiKey();
        URI uri = URI.create(config.braveSearchApiUrl()
                + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + count
                + "&search_lang=zh-hans");
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                recordError("Brave Search API Key 鉴权失败");
                throw new WebApplicationException("Brave Search API Key 鉴权失败", Response.Status.BAD_GATEWAY);
            }
            if (response.statusCode() == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
                String message = retryAfter == null || retryAfter.isBlank()
                        ? "Brave Search 请求过于频繁，请稍后再试"
                        : "Brave Search 请求过于频繁，请在 " + retryAfter + " 秒后再试";
                recordError(message);
                throw serviceUnavailable(message);
            }
            if (response.statusCode() >= 400) {
                recordError("Brave Search 请求失败：" + response.statusCode());
                throw new WebApplicationException("Brave Search 请求失败", Response.Status.BAD_GATEWAY);
            }
            if (response.body().length > MAX_RESPONSE_BYTES) {
                recordError("Brave Search 响应过大");
                throw serviceUnavailable("Brave Search 响应过大");
            }
            JsonNode root = objectMapper.readTree(new String(response.body(), StandardCharsets.UTF_8));
            return new BraveSearchResponse(query, parseResults(root, count));
        } catch (WebApplicationException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            recordError("Brave Search 请求超时");
            throw serviceUnavailable("Brave Search 请求超时");
        } catch (Exception exception) {
            recordError("Brave Search 请求异常");
            throw serviceUnavailable("Brave Search 请求异常");
        }
    }

    private WebApplicationException serviceUnavailable(String message) {
        return new WebApplicationException(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ErrorResponse("service_unavailable", message))
                .build());
    }

    private void rejectConsoleMutationInEnvMode() {
        if (config.braveSearchApiKey().isPresent() && !config.braveSearchApiKey().get().isBlank()) {
            throw new BadRequestException("环境变量 MUSIC_VAULT_BRAVE_SEARCH_API_KEY 已配置，控制台托管 Key 当前不生效。如需使用控制台托管 Key，请先移除环境变量。");
        }
    }

    private List<BraveSearchResultResponse> parseResults(JsonNode root, int count) {
        JsonNode values = root.path("web").path("results");
        List<BraveSearchResultResponse> results = new ArrayList<>();
        if (!values.isArray()) {
            return results;
        }
        for (JsonNode item : values) {
            if (results.size() >= count) {
                break;
            }
            String title = text(item, "title");
            String url = text(item, "url");
            if (title == null || url == null) {
                continue;
            }
            results.add(new BraveSearchResultResponse(
                    title,
                    url,
                    domain(url),
                    blankToEmpty(text(item, "description"))
            ));
        }
        return results;
    }

    private String activeApiKey() {
        AppSetting setting = AppSetting.findById(SETTING_KEY);
        KeyState keyState = keyState(setting);
        if (!keyState.searchable()) {
            throw new BadRequestException(keyState.message());
        }
        if ("ENV".equals(keyState.mode())) {
            return config.braveSearchApiKey().get().trim();
        }
        try {
            return cryptoService.decrypt(setting.encryptedValue);
        } catch (RuntimeException exception) {
            recordError("Brave Search API Key 解密失败");
            throw new BadRequestException("Brave Search API Key 配置异常");
        }
    }

    private KeyState keyState(AppSetting setting) {
        if (config.braveSearchApiKey().isPresent() && !config.braveSearchApiKey().get().isBlank()) {
            return new KeyState(true, true, true, "ENV", "使用环境变量 MUSIC_VAULT_BRAVE_SEARCH_API_KEY", true);
        }
        if (setting == null || setting.encryptedValue == null || setting.encryptedValue.isBlank()) {
            return new KeyState(false, false, false, "NONE", "尚未配置 Brave Search API Key", false);
        }
        if (!setting.enabled) {
            return new KeyState(true, false, false, "CONSOLE", "Brave 搜索已暂停", true);
        }
        if (!cryptoService.available()) {
            return new KeyState(true, true, false, "CONSOLE", "MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY 未配置，无法解密控制台托管 Key", true);
        }
        return new KeyState(true, true, true, "CONSOLE", "使用控制台托管 Brave Search API Key", true);
    }

    private void recordError(String message) {
        QuarkusTransaction.requiringNew().run(() -> {
            AppSetting setting = AppSetting.findById(SETTING_KEY);
            if (setting != null) {
                setting.lastError = message;
                setting.lastCheckedAt = LocalDateTime.now();
            }
        });
    }

    private String normalizeApiKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("apiKey is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 512) {
            throw new BadRequestException("apiKey is too long");
        }
        return normalized;
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("query is required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("query is too long");
        }
        return normalized;
    }

    private int normalizeCount(Integer value) {
        if (value == null) {
            return DEFAULT_COUNT;
        }
        if (value < 1 || value > MAX_COUNT) {
            throw new BadRequestException("count must be between 1 and 10");
        }
        return value;
    }

    private String normalizeOperator(String value) {
        return value == null || value.isBlank() ? "admin" : value.trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText();
    }

    private String domain(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? url : host;
        } catch (RuntimeException exception) {
            return url;
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeError(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record KeyState(boolean configured, boolean enabled, boolean searchable, String mode, String message, boolean hasStoredKey) {
    }
}
