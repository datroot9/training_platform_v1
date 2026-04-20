package com.example.training_platform.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTaskStatusRequest(
        @NotBlank
        @Pattern(regexp = "NOT_STARTED|IN_PROGRESS|DONE", message = "status must be NOT_STARTED, IN_PROGRESS, or DONE")
        String status
) {
}
