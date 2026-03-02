package com.example.spring_stream_backend.auth;

import com.example.spring_stream_backend.filter.HankoSessionFilter;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Map;

@Component
public class HankoSessionValidator {
    private final RestClient restClient;
    private final String hankoBaseUrl;
    private final String hankoCookieName;

    public HankoSessionValidator(
            @Value("${hanko.baseUrl}") String hankoBaseUrl,
            @Value("${hanko.cookieName}") String hankoCookieName,
            RestClient.Builder restClientBuilder
    ){
        this.hankoBaseUrl = hankoBaseUrl;
        this.hankoCookieName = hankoCookieName;
        this.restClient = restClientBuilder.build();
    }
    public boolean isSessionValid(HttpServletRequest request){
        String token = extractSessionToken(request);
        if(token == null || token.isEmpty()){
            return false;
        }
        HankoSessionValidationResponse body=restClient.post()
                .uri(hankoBaseUrl+"/sessions/validate")
                .body(Map.of("session_token", token))
                .retrieve()
                .body(HankoSessionValidationResponse.class);

        return body != null && body.isValid();
    }
    private String extractSessionToken(HttpServletRequest request){
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c ->hankoCookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }
}
