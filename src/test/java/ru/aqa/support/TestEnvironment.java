package ru.aqa.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public final class TestEnvironment implements ExtensionContext.Store.CloseableResource {
    public static final String API_KEY = "qazWSXedc";
    private static final Pattern PORT = Pattern.compile("Tomcat started on port (\\d+)");
    private final WireMockServer mock = new WireMockServer(wireMockConfig().bindAddress("127.0.0.1").dynamicPort());
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final Path log = Path.of("target", "logs", "application.log");
    private Process application;
    private URI endpoint;
    private Thread shutdownHook;

    public TestEnvironment() throws Exception {
        try {
            Path jar = Path.of(System.getProperty("sut.jar", "sut/internal-0.0.1-SNAPSHOT.jar")).toAbsolutePath();
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("Не найден JAR приложения: " + jar
                        + ". Поместите исходный JAR в sut/");
            }
            Files.createDirectories(log.getParent());
            mock.start();
            String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
            Path java = Path.of(System.getProperty("java.home"), "bin", executable);
            application = new ProcessBuilder(java.toString(), "-Dsecret=" + API_KEY,
                    "-Dmock=" + mock.baseUrl() + "/", "-jar", jar.toString(),
                    "--server.address=127.0.0.1", "--server.port=0", "--spring.output.ansi.enabled=never")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            shutdownHook = new Thread(this::stop, "stop-aqa-application");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            awaitReadiness();
            writeReportMetadata();
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    private void awaitReadiness() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            if (!application.isAlive()) {
                throw startupError("Приложение завершилось с кодом " + application.exitValue());
            }
            var match = PORT.matcher(Files.readString(log));
            if (match.find()) {
                endpoint = URI.create("http://127.0.0.1:" + match.group(1) + "/endpoint");
                try {
                    client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(2))
                            .GET().build(), HttpResponse.BodyHandlers.discarding());
                    return;
                } catch (IOException ignored) {
                }
            }
            Thread.sleep(100);
        }
        throw startupError("Приложение не готово за 60 секунд");
    }

    private IllegalStateException startupError(String reason) throws IOException {
        return new IllegalStateException(reason + ". Лог: " + log.toAbsolutePath() + "\n" + Files.readString(log));
    }

    private void writeReportMetadata() throws IOException {
        Path results = Path.of("target", "allure-results");
        Files.createDirectories(results);
        var properties = new java.util.Properties();
        properties.setProperty("Java", System.getProperty("java.version"));
        properties.setProperty("OS", System.getProperty("os.name"));
        properties.setProperty("Application", "internal-0.0.1-SNAPSHOT.jar");
        properties.setProperty("Application.URL", endpoint.toString());
        properties.setProperty("WireMock.URL", mock.baseUrl());
        try (var output = Files.newOutputStream(results.resolve("environment.properties"))) {
            properties.store(output, "Test environment");
        }
    }

    public WireMockServer mock() {
        return mock;
    }

    public ApiResponse send(String method, String body, String apiKey, String contentType) {
        return Allure.step("Отправить запрос к API", () -> {
            Allure.addAttachment("Запрос к приложению", "text/plain", method + " " + endpoint
                    + "\nContent-Type: " + contentType + "\nAccept: application/json\nX-Api-Key: "
                    + (apiKey == null ? "<отсутствует>" : "<тестовое значение>") + "\n\n" + body, ".txt");
            var builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json").header("Content-Type", contentType);
            if (apiKey != null) builder.header("X-Api-Key", apiKey);
            var response = client.send(builder.method(method,
                    HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            Allure.addAttachment("Ответ приложения", "text/plain", "HTTP " + response.statusCode()
                    + "\n" + response.headers().map() + "\n\n" + response.body(), ".txt");
            return new ApiResponse(response.statusCode(), response.headers(), response.body());
        });
    }

    private synchronized void stop() {
        if (application != null && application.isAlive()) {
            application.destroy();
            try {
                if (!application.waitFor(5, TimeUnit.SECONDS)) {
                    application.destroyForcibly();
                    application.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException interrupted) {
                application.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (mock.isRunning()) mock.stop();
    }

    @Override
    public void close() {
        stop();
        if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
