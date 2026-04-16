package com.example.training_platform.auth;

import com.example.training_platform.auth.dto.AuthResponse;
import com.example.training_platform.auth.dto.LoginRequest;
import com.example.training_platform.auth.dto.LogoutRequest;
import com.example.training_platform.auth.dto.RefreshRequest;
import com.example.training_platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/login")
    @Operation(security = {})
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse data = authService.login(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Login successful", data));
    }

    @PostMapping("/refresh")
    @Operation(security = {})
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse data = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Token refreshed", data));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Logout successful", null));
    }
}
