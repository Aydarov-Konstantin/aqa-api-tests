package ru.aqa.tests;

import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.aqa.support.BaseApiTest;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER;

@Feature("Взаимодействие с внешним сервисом")
@DisplayName("Успешные ответы и ошибки внешнего сервиса")
class ExternalServiceTest extends BaseApiTest {
    static Stream<Arguments> successfulResponses() {
        return Stream.of("LOGIN", "ACTION").flatMap(action -> Stream.of(
                Arguments.of(action, 200, "", "пустое тело"),
                Arguments.of(action, 200, "это не JSON", "обычный текст"),
                Arguments.of(action, 200, "{broken", "повреждённый JSON"),
                Arguments.of(action, 200, "{\"result\":\"ERROR\"}", "тело с ERROR при успешном HTTP-коде"),
                Arguments.of(action, 201, "created", "HTTP 201"),
                Arguments.of(action, 202, "accepted", "HTTP 202"),
                Arguments.of(action, 204, "", "HTTP 204 без тела")));
    }

    @ParameterizedTest(name = "{0}: {3} — операция успешна")
    @DisplayName("Успех определяется HTTP-кодом внешнего сервиса")
    @MethodSource("successfulResponses")
    void successfulResponseBodyIsIgnored(String action, int status, String body, String description) {
        if (action.equals("ACTION")) login(token);
        String path = action.equals("LOGIN") ? "/auth" : "/doAction";
        stub(path, status, body);
        assertOk(request(token, action));
        if (action.equals("LOGIN")) {
            stub("/doAction", 200, "");
            assertOk(request(token, "ACTION"));
        }
        verifyExternal("/auth", token, 1);
        verifyExternal("/doAction", token, 1);
        totalExternalRequests(2);
    }

    static Stream<Arguments> failedResponses() {
        return Stream.of("LOGIN", "ACTION").flatMap(action -> Stream.of(400, 401, 403, 404, 429, 500, 503)
                .map(status -> Arguments.of(action, status)));
    }

    @ParameterizedTest(name = "{0}: внешний сервис вернул HTTP {1} — операция отклонена")
    @DisplayName("Ошибка внешнего сервиса отклоняет операцию")
    @MethodSource("failedResponses")
    void failedExternalResponse(String action, int status) {
        if (action.equals("ACTION")) login(token);
        String path = action.equals("LOGIN") ? "/auth" : "/doAction";
        stub(path, status, "{\"result\":\"OK\"}");
        assertError(request(token, action));
        verifyExternal(path, token, 1);
        if (action.equals("LOGIN")) {
            assertClientError(request(token, "ACTION"));
            totalExternalRequests(1);
        } else {
            assertOk(request(token, "LOGOUT"));
            assertClientError(request(token, "ACTION"));
            totalExternalRequests(2);
        }
    }

    @Test
    @DisplayName("После неудачного входа повторный успешный вход создаёт сессию")
    void recoverAfterFailedLogin() {
        stub("/auth", 503, "");
        assertError(request(token, "LOGIN"));
        assertClientError(request(token, "ACTION"));
        stub("/auth", 200, "");
        assertOk(request(token, "LOGIN"));
        stub("/doAction", 200, "");
        assertOk(request(token, "ACTION"));
        verifyExternal("/auth", token, 2);
        verifyExternal("/doAction", token, 1);
        totalExternalRequests(3);
    }

    @Test
    @DisplayName("После ошибки внешнего действия сессия сохраняется, действие можно повторить")
    void recoverAfterFailedAction() {
        login(token);
        stub("/doAction", 503, "");
        assertError(request(token, "ACTION"));
        stub("/doAction", 200, "");
        assertOk(request(token, "ACTION"));
        verifyExternal("/auth", token, 1);
        verifyExternal("/doAction", token, 2);
        totalExternalRequests(3);
    }

    @Test
    @DisplayName("Обрыв связи при входе возвращает ошибку и не создаёт сессию")
    void networkFailureDuringLogin() {
        environment.mock().stubFor(post(urlEqualTo("/auth"))
                .willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));
        assertError(request(token, "LOGIN"));
        assertClientError(request(token, "ACTION"));
        verifyExternal("/auth", token, 1);
        totalExternalRequests(1);
    }

    @Test
    @DisplayName("Обрыв связи при действии возвращает ошибку; после восстановления действие успешно")
    void networkFailureDuringAction() {
        login(token);
        environment.mock().stubFor(post(urlEqualTo("/doAction"))
                .willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));
        assertError(request(token, "ACTION"));
        stub("/doAction", 200, "");
        assertOk(request(token, "ACTION"));
        verifyExternal("/auth", token, 1);
        verifyExternal("/doAction", token, 2);
        totalExternalRequests(3);
    }
}
