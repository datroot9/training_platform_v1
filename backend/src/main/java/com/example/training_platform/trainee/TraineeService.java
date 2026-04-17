package com.example.training_platform.trainee;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.example.training_platform.auth.PasswordHashService;
import com.example.training_platform.trainee.dto.CreateTraineeRequest;
import com.example.training_platform.trainee.dto.CreateTraineeResponse;
import com.example.training_platform.trainee.dto.ResetPasswordResponse;
import com.example.training_platform.trainee.dto.TraineeResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TraineeService {

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHashService passwordHashService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TraineeService(JdbcTemplate jdbcTemplate, PasswordHashService passwordHashService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHashService = passwordHashService;
    }

    public CreateTraineeResponse create(Long mentorId, CreateTraineeRequest request) {
        String tempPassword = generateTempPassword(12);
        String passwordHash = passwordHashService.hash(tempPassword);
        try {
            jdbcTemplate.update(
                    """
                    insert into users (email, full_name, role, password_hash, must_change_password, password_updated_at, mentor_id, is_active)
                    values (?, ?, 'TRAINEE', ?, 1, CURRENT_TIMESTAMP, ?, 1)
                    """,
                    request.email(),
                    request.fullName(),
                    passwordHash,
                    mentorId
            );
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, request.email());
        return new CreateTraineeResponse(
                userId,
                request.email(),
                request.fullName(),
                "TRAINEE",
                tempPassword,
                true
        );
    }

    public List<TraineeResponse> list(Long mentorId, String query) {
        String keyword = query == null ? null : query.trim();
        if (keyword == null || keyword.isEmpty()) {
            return jdbcTemplate.query(
                    """
                    select id, email, full_name, is_active, mentor_id, created_at
                    from users
                    where role = 'TRAINEE' and mentor_id = ?
                    order by created_at desc
                    """,
                    this::mapTrainee,
                    mentorId
            );
        }
        return jdbcTemplate.query(
                """
                select id, email, full_name, is_active, mentor_id, created_at
                from users
                where role = 'TRAINEE' and mentor_id = ?
                  and (lower(email) like lower(concat('%', ?, '%'))
                    or lower(full_name) like lower(concat('%', ?, '%')))
                order by created_at desc
                """,
                this::mapTrainee,
                mentorId,
                keyword,
                keyword
        );
    }

    public void setActive(Long mentorId, Long traineeId, boolean active) {
        int updated = jdbcTemplate.update(
                "update users set is_active = ? where id = ? and role = 'TRAINEE' and mentor_id = ?",
                active,
                traineeId,
                mentorId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found");
        }
    }

    public ResetPasswordResponse resetPassword(Long mentorId, Long traineeId) {
        UserBasicProjection trainee = findTrainee(mentorId, traineeId);
        String tempPassword = generateTempPassword(12);
        String hash = passwordHashService.hash(tempPassword);
        jdbcTemplate.update(
                """
                update users
                set password_hash = ?, must_change_password = 1, password_updated_at = CURRENT_TIMESTAMP
                where id = ? and role = 'TRAINEE' and mentor_id = ?
                """,
                hash,
                traineeId,
                mentorId
        );
        return new ResetPasswordResponse(trainee.id(), trainee.email(), tempPassword, true);
    }

    private UserBasicProjection findTrainee(Long mentorId, Long traineeId) {
        List<UserBasicProjection> rows = jdbcTemplate.query(
                "select id, email from users where id = ? and role = 'TRAINEE' and mentor_id = ?",
                (rs, i) -> new UserBasicProjection(rs.getLong("id"), rs.getString("email")),
                traineeId,
                mentorId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found");
        }
        return rows.get(0);
    }

    private TraineeResponse mapTrainee(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        LocalDateTime createdAt = createdAtTs == null ? null : createdAtTs.toLocalDateTime();
        return new TraineeResponse(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getBoolean("is_active"),
                rs.getLong("mentor_id"),
                createdAt
        );
    }

    private String generateTempPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(TEMP_PASSWORD_CHARS.length());
            sb.append(TEMP_PASSWORD_CHARS.charAt(index));
        }
        return sb.toString();
    }

    private record UserBasicProjection(Long id, String email) {
    }
}
