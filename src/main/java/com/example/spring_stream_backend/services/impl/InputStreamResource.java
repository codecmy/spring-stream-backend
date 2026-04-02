package com.example.spring_stream_backend.services.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.io.AbstractResource;

import java.io.InputStream;
@AllArgsConstructor
@Getter
public class InputStreamResource extends AbstractResource {
    private final InputStream inputStream;
    private final String description;
}
