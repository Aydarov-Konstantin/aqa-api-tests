package ru.aqa.support;

import java.net.http.HttpHeaders;

public record ApiResponse(int status, HttpHeaders headers, String body) {
}
