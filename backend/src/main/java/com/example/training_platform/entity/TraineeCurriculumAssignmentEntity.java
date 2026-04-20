package com.example.training_platform.entity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
@Table(name = "trainee_curriculum_assignments")
public class TraineeCurriculumAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long traineeId;
    private Long curriculumId;
    private Long assignedBy;
    private String status;
    @Column(insertable = false, updatable = false)
    private LocalDateTime assignedAt;
    private LocalDateTime endedAt;
}
