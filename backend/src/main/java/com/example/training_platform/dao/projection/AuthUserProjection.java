package com.example.training_platform.dao.projection;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Entity;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
public class AuthUserProjection {
    private Long id;
    private String email;
    private String role;
    private String passwordHash;
    private boolean isActive;
    private boolean mustChangePassword;
}
