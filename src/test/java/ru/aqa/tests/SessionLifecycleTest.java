package ru.aqa.tests;

import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.aqa.support.BaseApiTest;

import java.util.UUID;

@Feature("Вход, действия и выход")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Работа пользовательских сессий")
class SessionLifecycleTest extends BaseApiTest {
    @Test
    @DisplayName("Успешный вход отправляет один корректный запрос на аутентификацию")
    void successfulLogin() {
        login(token);
        verifyExternal("/auth", token, 1);
        totalExternalRequests(1);
    }

    @Test
    @DisplayName("Пользователь входит, выполняет действие, выходит и теряет доступ")
    void completeLifecycle() {
        login(token);
        stub("/doAction", 200, "");
        assertOk(request(token, "ACTION"));
        assertOk(request(token, "LOGOUT"));
        assertClientError(request(token, "ACTION"));
        verifyExternal("/auth", token, 1);
        verifyExternal("/doAction", token, 1);
        totalExternalRequests(2);
    }

    @Test
    @DisplayName("До входа действие запрещено и внешний сервис не вызывается")
    void actionBeforeLogin() {
        assertClientError(request(token, "ACTION"));
        noExternalRequests();
    }

    @Test
    @DisplayName("Выход удаляет сессию без обращения к внешнему сервису")
    void logoutIsLocal() {
        login(token);
        environment.mock().resetRequests();
        assertOk(request(token, "LOGOUT"));
        assertClientError(request(token, "ACTION"));
        noExternalRequests();
    }

    @Test
    @DisplayName("После выхода пользователь может войти снова")
    void loginAfterLogout() {
        login(token);
        assertOk(request(token, "LOGOUT"));
        assertClientError(request(token, "ACTION"));
        assertOk(request(token, "LOGIN"));
        stub("/doAction", 200, "");
        assertOk(request(token, "ACTION"));
        verifyExternal("/auth", token, 2);
        verifyExternal("/doAction", token, 1);
        totalExternalRequests(3);
    }

    @Test
    @DisplayName("Каждое действие активного пользователя вызывает внешний сервис")
    void repeatedActions() {
        login(token);
        stub("/doAction", 200, "");
        for (int i = 0; i < 3; i++) assertOk(request(token, "ACTION"));
        verifyExternal("/auth", token, 1);
        verifyExternal("/doAction", token, 3);
        totalExternalRequests(4);
    }

    @Test
    @DisplayName("Выход одного пользователя не завершает сессию другого")
    void independentSessions() {
        String secondToken = UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        login(token);
        assertClientError(request(secondToken, "ACTION"));
        login(secondToken);
        stub("/doAction", 200, "");
        assertOk(request(token, "ACTION"));
        assertOk(request(secondToken, "ACTION"));
        assertOk(request(token, "LOGOUT"));
        assertClientError(request(token, "ACTION"));
        assertOk(request(secondToken, "ACTION"));
        verifyExternal("/auth", token, 1);
        verifyExternal("/auth", secondToken, 1);
        verifyExternal("/doAction", token, 1);
        verifyExternal("/doAction", secondToken, 2);
        totalExternalRequests(5);
    }

}
