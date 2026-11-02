package ru.aqa.support;

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Epic("API пользовательских сессий")
@ExtendWith(EnvironmentExtension.class)
public abstract class BaseApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    protected TestEnvironment environment;
    protected String token;

    @BeforeEach
    void prepareTest(TestEnvironment environment) {
        this.environment = environment;
        token = UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    protected ApiResponse request(String token, String action) {
        return request(token, action, TestEnvironment.API_KEY);
    }

    protected ApiResponse request(String token, String action, String key) {
        return environment.send("POST", form(token, action), key, "application/x-www-form-urlencoded");
    }

    protected static String form(String token, String action) {
        return (token == null ? "" : "token=" + encode(token))
                + (action == null ? "" : (token == null ? "" : "&") + "action=" + encode(action));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    protected void stub(String path, int status, String body) {
        environment.mock().stubFor(post(urlEqualTo(path)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    protected void login(String token) {
        stub("/auth", 200, "");
        assertOk(request(token, "LOGIN"));
    }

    protected void assertOk(ApiResponse response) {
        assertOk(response, "Операция должна завершиться успешно");
    }

    protected void assertOk(ApiResponse response, String reason) {
        Allure.step("Проверить успешный ответ", () -> {
            assertAll(reason,
                    () -> assertTrue(response.status() >= 200 && response.status() < 300,
                            reason + ": ожидался HTTP 2xx, получен " + response.status() + "; " + response.body()),
                    () -> assertJsonContentType(response),
                    () -> assertEquals(JSON.readTree("{\"result\":\"OK\"}"), JSON.readTree(response.body()),
                            reason + ": тело успешного ответа"));
        });
    }

    protected void assertError(ApiResponse response) {
        Allure.step("Проверить ответ об ошибке", () -> {
            assertTrue(response.status() >= 400 && response.status() < 600,
                    "Ожидался HTTP 4xx/5xx, получен " + response.status() + "; " + response.body());
            assertJsonContentType(response);
            var json = JSON.readTree(response.body());
            assertAll("Ответ должен соответствовать формату ошибки из задания",
                    () -> assertTrue(json.isObject(), "Ответ должен быть JSON-объектом"),
                    () -> assertEquals("ERROR", json.path("result").asText(), "Поле result"),
                    () -> assertTrue(json.path("message").isTextual() && !json.path("message").asText().isBlank(),
                            "Поле message должно содержать причину ошибки"));
        });
    }

    protected void assertClientError(ApiResponse response) {
        assertError(response);
        assertTrue(response.status() < 500, "Некорректный запрос должен давать клиентскую ошибку HTTP 4xx");
    }

    private void assertJsonContentType(ApiResponse response) {
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                "Ответ должен иметь Content-Type application/json");
    }

    protected RequestPatternBuilder externalRequest(String path, String token) {
        return postRequestedFor(urlEqualTo(path))
                .withHeader("Content-Type", matching("application/x-www-form-urlencoded(?:;.*)?"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalTo("token=" + encode(token)));
    }

    protected void verifyExternal(String path, String token, int count) {
        environment.mock().verify(count, externalRequest(path, token));
    }

    protected void noExternalRequests() {
        environment.mock().verify(0, anyRequestedFor(anyUrl()));
    }

    protected void totalExternalRequests(int count) {
        environment.mock().verify(count, anyRequestedFor(anyUrl()));
    }
}
