package com.example.training_platform.trainee;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.training_platform.auth.PasswordHashService;
import com.example.training_platform.common.dto.PagedResponse;
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
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

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

    public PagedResponse<TraineeResponse> list(Long mentorId,
                                               String query,
                                               Boolean active,
                                               Integer page,
                                               Integer size,
                                               String sortBy,
                                               String sortDir) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String safeSortBy = resolveSortColumn(sortBy);
        String safeSortDir = resolveSortDirection(sortDir);
        String keyword = normalizeKeyword(query);

        StringBuilder whereClause = new StringBuilder(
                """
                from users
                where role = 'TRAINEE' and mentor_id = ?
                """
        );
        List<Object> params = new ArrayList<>();
        params.add(mentorId);

        if (keyword != null) {
            whereClause.append(
                    """
                      and (lower(email) like lower(concat('%', ?, '%'))
                        or lower(full_name) like lower(concat('%', ?, '%')))
                    """
            );
            params.add(keyword);
            params.add(keyword);
        }
        if (active != null) {
            whereClause.append(" and is_active = ?");
            params.add(active);
        }

        Long totalElements = jdbcTemplate.queryForObject(
                "select count(*) " + whereClause,
                Long.class,
                params.toArray()
        );
        long safeTotal = totalElements == null ? 0 : totalElements;

        String listSql =
                """
                select id, email, full_name, is_active, mentor_id, created_at
                """
                        + whereClause +
                        " order by " + safeSortBy + " " + safeSortDir + " limit ? offset ?";

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add((long) safePage * safeSize);
        List<TraineeResponse> rows = jdbcTemplate.query(listSql, this::mapTrainee, listParams.toArray());
        return PagedResponse.of(rows, safePage, safeSize, safeTotal);
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

    private static int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static String normalizeKeyword(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolveSortColumn(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "created_at";
        }
        return switch (sortBy.trim()) {
            case "email" -> "email";
            case "full_name", "fullName" -> "full_name";
            case "created_at", "createdAt" -> "created_at";
            default -> "created_at";
        };
    }

    private static String resolveSortDirection(String sortDir) {
        if (sortDir == null || sortDir.isBlank()) {
            return "desc";
        }
        return "asc".equals(sortDir.trim().toLowerCase(Locale.ROOT)) ? "asc" : "desc";
    }
}
