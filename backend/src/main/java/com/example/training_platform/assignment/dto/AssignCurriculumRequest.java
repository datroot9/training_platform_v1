package com.example.training_platform.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record AssignCurriculumRequest(
        @NotNull Long curriculumId
) {
}
