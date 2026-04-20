package com.example.training_platform.dao.projection;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Entity;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
public class TaskTemplateCopyProjection {
    private Long learningMaterialId;
    private Integer sortOrder;
    private String title;
    private String description;
    private Integer estimatedDays;
}
