package ru.aqa.tests;

import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.aqa.support.BaseApiTest;

import java.util.stream.Stream;

@Feature("Проверка API-ключа")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Защита доступа к API")
class ApiKeyTest extends BaseApiTest {
    static Stream<Arguments> invalidKeys() {
        return Stream.of("LOGIN", "ACTION", "LOGOUT").flatMap(action -> Stream.of(
                Arguments.of(action, null, "отсутствует"),
                Arguments.of(action, "", "пустой"),
                Arguments.of(action, "wrong-key", "неверный"),
                Arguments.of(action, "qazwsxedc", "другой регистр"),
                Arguments.of(action, "qazWSXedc-extra", "правильный ключ с суффиксом")));
    }

    @ParameterizedTest(name = "{0}: API-ключ {2} — доступ запрещён")
    @DisplayName("Доступ с некорректным API-ключом запрещён")
    @MethodSource("invalidKeys")
    void rejectedKey(String action, String key, String description) {
        if (!action.equals("LOGIN")) login(token);
        environment.mock().resetRequests();
        assertClientError(request(token, action, key));
        noExternalRequests();
    }

    @Test
    @DisplayName("LOGIN с неверным ключом не создаёт сессию")
    void rejectedLoginDoesNotCreateSession() {
        stub("/auth", 200, "");
        assertClientError(request(token, "LOGIN", "wrong-key"));
        assertClientError(request(token, "ACTION"));
        noExternalRequests();
    }

    @Test
    @DisplayName("LOGOUT с неверным ключом не удаляет действующую сессию")
    void rejectedLogoutDoesNotRemoveSession() {
        login(token);
        stub("/doAction", 200, "");
        assertClientError(request(token, "LOGOUT", "wrong-key"));
        assertOk(request(token, "ACTION"));
        verifyExternal("/auth", token, 1);
        verifyExternal("/doAction", token, 1);
        totalExternalRequests(2);
    }
}
