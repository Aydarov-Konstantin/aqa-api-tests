package ru.aqa.support;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public final class EnvironmentExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(EnvironmentExtension.class);

    private TestEnvironment environment(ExtensionContext context) {
        return context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(Startup.class, key -> new Startup(),
                Startup.class).get();
    }

    private static final class Startup implements ExtensionContext.Store.CloseableResource {
        private TestEnvironment environment;
        private Exception failure;

        private Startup() {
            try {
                environment = new TestEnvironment();
            } catch (Exception exception) {
                failure = exception;
            }
        }

        private TestEnvironment get() {
            if (failure != null) {
                throw new IllegalStateException("Не удалось подготовить тестовое окружение", failure);
            }
            return environment;
        }

        @Override
        public void close() {
            if (environment != null) environment.close();
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        environment(context).mock().resetAll();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var events = environment(context).mock().getAllServeEvents();
        StringBuilder journal = new StringBuilder();
        for (var event : events) {
            journal.append(event.getRequest().getMethod()).append(" ").append(event.getRequest().getUrl())
                    .append("\n").append(event.getRequest().getHeaders()).append("\n")
                    .append(event.getRequest().getBodyAsString()).append("\nОтвет внешнего сервиса: ")
                    .append(event.getResponse().getStatus()).append("\n\n");
        }
        Allure.addAttachment("Обращения к внешнему сервису", "text/plain",
                journal.isEmpty() ? "Приложение не обращалось к внешнему сервису." : journal.toString(), ".txt");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == TestEnvironment.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return environment(context);
    }
}
