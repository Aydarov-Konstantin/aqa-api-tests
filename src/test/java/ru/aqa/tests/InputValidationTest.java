package ru.aqa.tests;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.aqa.support.BaseApiTest;

import java.util.List;
import java.util.stream.Stream;

@Feature("Проверка входных данных")
@DisplayName("Валидация токена и действия")
class InputValidationTest extends BaseApiTest {
    static Stream<Arguments> invalidTokens() {
        List<Arguments> tokens = List.of(
                Arguments.of(null, "токен отсутствует"),
                Arguments.of("", "пустой токен"),
                Arguments.of("A", "1 символ"),
                Arguments.of("A".repeat(31), "31 символ"),
                Arguments.of("A".repeat(33), "33 символа"),
                Arguments.of("A".repeat(256), "256 символов"),
                Arguments.of("a".repeat(32), "строчные латинские буквы"),
                Arguments.of("A".repeat(31) + "a", "одна строчная буква"),
                Arguments.of("А".repeat(32), "кириллическая А"),
                Arguments.of("A".repeat(31) + " ", "пробел"),
                Arguments.of("A".repeat(31) + "-", "дефис"),
                Arguments.of("A".repeat(31) + "_", "подчёркивание"),
                Arguments.of("A".repeat(31) + "+", "знак плюс"),
                Arguments.of("A".repeat(31) + "&", "амперсанд"),
                Arguments.of("A".repeat(31) + "\n", "перевод строки"));
        return Stream.of("LOGIN", "ACTION", "LOGOUT").flatMap(action -> tokens.stream().map(args ->
                Arguments.of(action, args.get()[0], args.get()[1])));
    }

    @ParameterizedTest(name = "{0}: {2} — запрос отклонён")
    @DisplayName("Некорректный токен отклоняется")
    @MethodSource("invalidTokens")
    void invalidToken(String action, String invalidToken, String description) {
        assertError(request(invalidToken, action));
        noExternalRequests();
    }

    @ParameterizedTest(name = "Допустимый токен из 32 символов: {0}")
    @DisplayName("Допустимые токены принимаются")
    @ValueSource(strings = {"00000000000000000000000000000000", "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            "0123456789ABCDEF0123456789ABCDEF"})
    void validTokens(String validToken) {
        login(validToken);
        verifyExternal("/auth", validToken, 1);
        totalExternalRequests(1);
    }

    @ParameterizedTest(name = "AQA-001: токен с буквами {0} должен работать на протяжении всей сессии")
    @DisplayName("AQA-001: все разрешённые буквы в токене должны приниматься")
    @ValueSource(strings = {"G", "Z", "GHIJKLMNOPQRSTUVWXYZ"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("Документация разрешает все заглавные латинские буквы A–Z и цифры. "
            + "Приложение отклоняет G–Z. "
            + "подробности и шаги воспроизведения находятся в docs/BUG_REPORT.md.")
    void allDocumentedLettersAreAccepted(String letters) {
        String validToken = "A".repeat(32 - letters.length()) + letters;
        stub("/auth", 200, "");
        assertOk(request(validToken, "LOGIN"), "AQA-001: токен длиной 32 символа из A–Z0–9 должен приниматься");
        stub("/doAction", 200, "");
        assertOk(request(validToken, "ACTION"));
        assertOk(request(validToken, "LOGOUT"));
        assertClientError(request(validToken, "ACTION"));
        verifyExternal("/auth", validToken, 1);
        verifyExternal("/doAction", validToken, 1);
        totalExternalRequests(2);
    }

    static Stream<Arguments> invalidActions() {
        return Stream.of(
                Arguments.of(null, "действие отсутствует"),
                Arguments.of("", "пустое действие"),
                Arguments.of("UNKNOWN", "неизвестное действие"),
                Arguments.of("login", "строчные буквы"),
                Arguments.of("Login", "смешанный регистр"));
    }

    @ParameterizedTest(name = "{1} — запрос отклонён")
    @DisplayName("Некорректное действие отклоняется")
    @MethodSource("invalidActions")
    void invalidAction(String action, String description) {
        assertError(request(token, action));
        noExternalRequests();
    }

    @Test
    @DisplayName("Отклонённый запрос не завершает существующую сессию")
    void invalidRequestDoesNotChangeSession() {
        login(token);
        assertClientError(request(token, "UNKNOWN"));
        stub("/doAction", 200, "");
        assertOk(request(token, "ACTION"));
        verifyExternal("/auth", token, 1);
        verifyExternal("/doAction", token, 1);
        totalExternalRequests(2);
    }
}
