package com.synchat.backend.auth.controller;

import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.dto.SuccessResponse;
import com.synchat.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public SuccessResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}
