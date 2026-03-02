package com.example.spring_stream_backend.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class HankoSessionValidationResponse {
    @JsonProperty("is_valid")
    private boolean isValid;

    @JsonProperty("user_id")
    private String userId;

}
