package com.example.training_platform.curriculum.dto;

import java.util.List;

public record CurriculumDetailResponse(
        CurriculumResponse curriculum,
        List<LearningMaterialResponse> materials,
        List<TaskTemplateResponse> taskTemplates
) {
}
