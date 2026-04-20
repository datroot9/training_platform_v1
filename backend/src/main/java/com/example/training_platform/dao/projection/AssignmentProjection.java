package com.example.training_platform.dao.projection;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Entity;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
public class AssignmentProjection {
    private Long id;
    private Long traineeId;
    private Long curriculumId;
    private String curriculumName;
    private String curriculumDescription;
    private String mentorName;
    private String mentorEmail;
    private String status;
    private LocalDateTime assignedAt;
    private LocalDateTime endedAt;
}
