package com.example.training_platform.trainee;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import com.example.training_platform.auth.PasswordHashService;
import com.example.training_platform.common.dto.PagedResponse;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.dao.projection.TraineeListProjection;
import com.example.training_platform.dao.projection.UserBasicProjection;
import com.example.training_platform.entity.UserEntity;
import com.example.training_platform.trainee.dto.CreateTraineeRequest;
import com.example.training_platform.trainee.dto.CreateTraineeResponse;
import com.example.training_platform.trainee.dto.ResetPasswordResponse;
import com.example.training_platform.trainee.dto.TraineeResponse;
import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TraineeService {

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final UserDao userDao;
    private final PasswordHashService passwordHashService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TraineeService(UserDao userDao, PasswordHashService passwordHashService) {
        this.userDao = userDao;
        this.passwordHashService = passwordHashService;
    }

    public CreateTraineeResponse create(Long mentorId, CreateTraineeRequest request) {
        String tempPassword = generateTempPassword(12);
        String passwordHash = passwordHashService.hash(tempPassword);
        UserEntity entity = new UserEntity();
        entity.setEmail(request.email());
        entity.setFullName(request.fullName());
        entity.setRole("TRAINEE");
        entity.setPasswordHash(passwordHash);
        entity.setMustChangePassword(true);
        entity.setPasswordUpdatedAt(LocalDateTime.now());
        entity.setMentorId(mentorId);
        entity.setActive(true);
        try {
            userDao.insert(entity);
        } catch (UniqueConstraintException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        return new CreateTraineeResponse(
                entity.getId(),
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
        long safeTotal = userDao.countTrainees(mentorId, keyword, active);
        List<TraineeResponse> rows = userDao
                .listTrainees(mentorId, keyword, active, safeSortBy, safeSortDir, safeSize, (long) safePage * safeSize)
                .stream()
                .map(this::mapTrainee)
                .toList();
        return PagedResponse.of(rows, safePage, safeSize, safeTotal);
    }

    public void setActive(Long mentorId, Long traineeId, boolean active) {
        if (userDao.countTraineeByMentor(mentorId, traineeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found");
        }
        UserEntity entity = userDao.selectById(traineeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found"));
        entity.setActive(active);
        userDao.update(entity);
    }

    public ResetPasswordResponse resetPassword(Long mentorId, Long traineeId) {
        UserBasicProjection trainee = findTrainee(mentorId, traineeId);
        String tempPassword = generateTempPassword(12);
        String hash = passwordHashService.hash(tempPassword);
        UserEntity entity = userDao.selectById(traineeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found"));
        entity.setPasswordHash(hash);
        entity.setMustChangePassword(true);
        entity.setPasswordUpdatedAt(LocalDateTime.now());
        userDao.update(entity);
        return new ResetPasswordResponse(trainee.getId(), trainee.getEmail(), tempPassword, true);
    }

    private UserBasicProjection findTrainee(Long mentorId, Long traineeId) {
        return userDao.findTraineeBasic(mentorId, traineeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found"));
    }

    private TraineeResponse mapTrainee(TraineeListProjection row) {
        return new TraineeResponse(
                row.getId(),
                row.getEmail(),
                row.getFullName(),
                row.isActive(),
                row.getMentorId(),
                row.getCreatedAt(),
                row.getActiveAssignmentId(),
                row.getActiveCurriculumName(),
                row.getCompletedTaskCount(),
                row.getTotalTaskCount()
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
