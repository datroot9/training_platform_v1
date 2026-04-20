package com.example.training_platform.dao.projection;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Entity;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
public class AssignmentTaskProjection {
    private Long id;
    private Long assignmentId;
    private Long taskTemplateId;
    private Integer sortOrder;
    private String title;
    private String description;
    private Integer estimatedDays;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long learningMaterialId;
    private String learningMaterialFileName;
}
