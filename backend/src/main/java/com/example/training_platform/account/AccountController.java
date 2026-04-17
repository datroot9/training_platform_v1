package com.example.training_platform.account;

import java.util.Map;

import com.example.training_platform.account.dto.ChangePasswordRequest;
import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(Authentication authentication,
                                                                           @Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        accountService.changePassword(current.userId(), request.oldPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                "Password changed successfully",
                Map.of("message", "Password changed successfully")
        ));
    }
}
