package com.example.training_platform.dao.projection;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Entity;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
public class TraineeListProjection {
    private Long id;
    private String email;
    private String fullName;
    private boolean isActive;
    private Long mentorId;
    private LocalDateTime createdAt;
    /** Null when trainee has no ACTIVE curriculum assignment */
    private Long activeAssignmentId;
    private String activeCurriculumName;
    private Integer completedTaskCount;
    private Integer totalTaskCount;
}
