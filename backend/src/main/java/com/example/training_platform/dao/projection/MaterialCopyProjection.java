package com.example.training_platform.dao.projection;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Entity;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
public class MaterialCopyProjection {
    private Long id;
    private Integer sortOrder;
    private String fileName;
    private String storagePath;
    private Long fileSizeBytes;
    private LocalDateTime uploadedAt;
}
