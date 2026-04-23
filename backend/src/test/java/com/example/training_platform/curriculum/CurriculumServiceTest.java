package com.example.training_platform.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.example.training_platform.dao.AssignmentDao;
import com.example.training_platform.dao.CurriculumDao;
import com.example.training_platform.dao.LearningMaterialDao;
import com.example.training_platform.dao.TaskTemplateDao;
import com.example.training_platform.dao.projection.CurriculumSourceProjection;
import com.example.training_platform.dao.projection.MaterialCopyProjection;
import com.example.training_platform.dao.projection.TaskTemplateCopyProjection;
import com.example.training_platform.curriculum.dto.CreateCurriculumRequest;
import com.example.training_platform.curriculum.dto.CreateCurriculumVersionRequest;
import com.example.training_platform.curriculum.dto.CreateTaskTemplateRequest;
import com.example.training_platform.curriculum.dto.UpdateCurriculumRequest;
import com.example.training_platform.curriculum.dto.UpdateTaskTemplateRequest;
import com.example.training_platform.entity.CurriculumEntity;
import com.example.training_platform.entity.LearningMaterialEntity;
import com.example.training_platform.entity.TaskTemplateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.seasar.doma.jdbc.Sql;
import org.seasar.doma.jdbc.SqlKind;
import org.seasar.doma.jdbc.SqlLogType;
import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceTest {

    private static final long MENTOR_ID = 3L;
    private static final long CURRICULUM_ID = 40L;

    @Mock
    private CurriculumDao curriculumDao;
    @Mock
    private LearningMaterialDao learningMaterialDao;
    @Mock
    private TaskTemplateDao taskTemplateDao;
    @Mock
    private AssignmentDao assignmentDao;
    @Mock
    private LocalPdfStorageService pdfStorage;

    private CurriculumService curriculumService;

    @BeforeEach
    void setUp() {
        curriculumService = new CurriculumService(
                curriculumDao,
                learningMaterialDao,
                taskTemplateDao,
                assignmentDao,
                pdfStorage
        );
    }

    @Test
    void create_insertsAndSetsCurriculumGroupId() {
        doAnswer(invocation -> {
            CurriculumEntity e = invocation.getArgument(0);
            e.setId(200L);
            return null;
        }).when(curriculumDao).insert(any(CurriculumEntity.class));
        when(curriculumDao.selectByIdAndCreator(200L, MENTOR_ID)).thenReturn(Optional.of(draftCurriculum(200L)));

        var response = curriculumService.create(MENTOR_ID, new CreateCurriculumRequest("Java 101", "desc"));

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.status()).isEqualTo("DRAFT");
        verify(curriculumDao).insert(any(CurriculumEntity.class));
        verify(curriculumDao).update(any(CurriculumEntity.class));
    }

    @Test
    void list_throwsBadRequestWhenStatusInvalid() {
        assertThatThrownBy(() -> curriculumService.list(MENTOR_ID, null, "ARCHIVED", 0, 10, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void list_normalizesInputsAndPassesToDao() {
        when(curriculumDao.countByCreatorWithFilter(MENTOR_ID, "Java", "PUBLISHED")).thenReturn(1L);
        when(curriculumDao.listByCreatorWithFilter(MENTOR_ID, "Java", "PUBLISHED", "updated_at", "asc", 100, 0))
                .thenReturn(List.of(draftCurriculum(11L)));

        var page = curriculumService.list(MENTOR_ID, " Java ", " published ", -1, 999, "unsupported", "ASC");

        assertThat(page.items()).hasSize(1);
        verify(curriculumDao).countByCreatorWithFilter(MENTOR_ID, "Java", "PUBLISHED");
        verify(curriculumDao).listByCreatorWithFilter(MENTOR_ID, "Java", "PUBLISHED", "updated_at", "asc", 100, 0);
    }

    @Test
    void getDetail_throwsNotFoundWhenCurriculumMissing() {
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.getDetail(MENTOR_ID, CURRICULUM_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void getDetail_returnsMaterialsAndTemplates() {
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(draftCurriculum(CURRICULUM_ID)));
        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(1L);
        material.setCurriculumId(CURRICULUM_ID);
        material.setFileName("doc.pdf");
        material.setStoragePath("materials/1.pdf");
        material.setSortOrder(1);
        material.setFileSizeBytes(123L);
        material.setUploadedAt(LocalDateTime.now());
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(2L);
        template.setCurriculumId(CURRICULUM_ID);
        template.setTitle("Task");
        template.setSortOrder(1);
        template.setEstimatedDays(2);
        when(learningMaterialDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(material));
        when(taskTemplateDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(template));

        var detail = curriculumService.getDetail(MENTOR_ID, CURRICULUM_ID);

        assertThat(detail.curriculum().id()).isEqualTo(CURRICULUM_ID);
        assertThat(detail.materials()).hasSize(1);
        assertThat(detail.taskTemplates()).hasSize(1);
    }

    @Test
    void update_throwsBadRequestWhenNoFieldsProvided() {
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(draftCurriculum(CURRICULUM_ID)));

        assertThatThrownBy(() -> curriculumService.update(MENTOR_ID, CURRICULUM_ID, new UpdateCurriculumRequest(null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
        verify(curriculumDao, never()).update(any(CurriculumEntity.class));
    }

    @Test
    void update_throwsConflictWhenCurriculumNotDraft() {
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(publishedCurriculum(CURRICULUM_ID)));

        assertThatThrownBy(() -> curriculumService.update(MENTOR_ID, CURRICULUM_ID, new UpdateCurriculumRequest("X", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void update_updatesNameAndDescription() {
        CurriculumEntity draft = draftCurriculum(CURRICULUM_ID);
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(draft));

        var response = curriculumService.update(MENTOR_ID, CURRICULUM_ID, new UpdateCurriculumRequest("New name", "New desc"));

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.description()).isEqualTo("New desc");
        verify(curriculumDao).update(draft);
    }

    @Test
    void forkPublishedVersion_throwsBadRequestWhenVersionBlank() {
        assertThatThrownBy(() -> curriculumService.forkPublishedVersion(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateCurriculumVersionRequest("   ", null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void forkPublishedVersion_throwsNotFoundWhenSourceMissing() {
        when(curriculumDao.selectSourceForFork(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.forkPublishedVersion(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateCurriculumVersionRequest("2.0", null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void forkPublishedVersion_throwsConflictWhenSourceNotPublished() {
        CurriculumSourceProjection source = publishedSourceProjection();
        source.setStatus("DRAFT");
        when(curriculumDao.selectSourceForFork(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> curriculumService.forkPublishedVersion(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateCurriculumVersionRequest("2.0", null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void forkPublishedVersion_throwsConflictWhenVersionAlreadyExists() {
        when(curriculumDao.selectSourceForFork(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(publishedSourceProjection()));
        when(curriculumDao.countByGroupAndVersion(1L, "2.0")).thenReturn(1L);

        assertThatThrownBy(() -> curriculumService.forkPublishedVersion(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateCurriculumVersionRequest("2.0", null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void forkPublishedVersion_throwsConflictWhenInsertHitsUniqueRace() {
        when(curriculumDao.selectSourceForFork(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(publishedSourceProjection()));
        when(curriculumDao.countByGroupAndVersion(1L, "2.0")).thenReturn(0L);
        doThrow(uniqueConstraintEx()).when(curriculumDao).insert(any(CurriculumEntity.class));

        assertThatThrownBy(() -> curriculumService.forkPublishedVersion(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateCurriculumVersionRequest("2.0", null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void forkPublishedVersion_throwsInternalServerErrorWhenCopiedMaterialMissingId() {
        when(curriculumDao.selectSourceForFork(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(publishedSourceProjection()));
        when(curriculumDao.countByGroupAndVersion(1L, "2.0")).thenReturn(0L);
        doAnswer(invocation -> {
            CurriculumEntity e = invocation.getArgument(0);
            e.setId(333L);
            return null;
        }).when(curriculumDao).insert(any(CurriculumEntity.class));

        MaterialCopyProjection material = new MaterialCopyProjection();
        material.setId(9L);
        material.setSortOrder(1);
        material.setFileName("f.pdf");
        material.setStoragePath("materials/a.pdf");
        material.setFileSizeBytes(11L);
        when(learningMaterialDao.listForCopy(CURRICULUM_ID)).thenReturn(List.of(material));

        assertThatThrownBy(() -> curriculumService.forkPublishedVersion(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateCurriculumVersionRequest("2.0", "copy", null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @Test
    void forkPublishedVersion_throwsInternalServerErrorWhenTemplateReferencesUnmappedMaterial() {
        when(curriculumDao.selectSourceForFork(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(publishedSourceProjection()));
        when(curriculumDao.countByGroupAndVersion(1L, "2.0")).thenReturn(0L);
        doAnswer(invocation -> {
            CurriculumEntity e = invocation.getArgument(0);
            e.setId(333L);
            return null;
        }).when(curriculumDao).insert(any(CurriculumEntity.class));

        when(learningMaterialDao.listForCopy(CURRICULUM_ID)).thenReturn(List.of());
        TaskTemplateCopyProjection template = new TaskTemplateCopyProjection();
        template.setLearningMaterialId(999L);
        template.setSortOrder(1);
        template.setTitle("T");
        template.setDescription("D");
        template.setEstimatedDays(2);
        when(taskTemplateDao.listForCopy(CURRICULUM_ID)).thenReturn(List.of(template));

        assertThatThrownBy(() -> curriculumService.forkPublishedVersion(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateCurriculumVersionRequest("2.0", null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @Test
    void forkPublishedVersion_copiesMaterialsAndRemapsTemplateLearningMaterial() {
        when(curriculumDao.selectSourceForFork(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(publishedSourceProjection()));
        when(curriculumDao.countByGroupAndVersion(1L, "2.0")).thenReturn(0L);

        doAnswer(invocation -> {
            CurriculumEntity e = invocation.getArgument(0);
            e.setId(333L);
            return null;
        }).when(curriculumDao).insert(any(CurriculumEntity.class));

        MaterialCopyProjection material = new MaterialCopyProjection();
        material.setId(9L);
        material.setSortOrder(1);
        material.setFileName("f.pdf");
        material.setStoragePath("materials/a.pdf");
        material.setFileSizeBytes(11L);
        material.setUploadedAt(LocalDateTime.now());
        when(learningMaterialDao.listForCopy(CURRICULUM_ID)).thenReturn(List.of(material));

        AtomicLong idGen = new AtomicLong(1000L);
        doAnswer(invocation -> {
            LearningMaterialEntity entity = invocation.getArgument(0);
            entity.setId(idGen.incrementAndGet());
            return null;
        }).when(learningMaterialDao).insert(any(LearningMaterialEntity.class));

        TaskTemplateCopyProjection template = new TaskTemplateCopyProjection();
        template.setLearningMaterialId(9L);
        template.setSortOrder(1);
        template.setTitle("Task");
        template.setDescription("desc");
        template.setEstimatedDays(3);
        when(taskTemplateDao.listForCopy(CURRICULUM_ID)).thenReturn(List.of(template));

        CurriculumEntity copied = draftCurriculum(333L);
        copied.setVersionLabel("2.0");
        when(curriculumDao.selectByIdAndCreator(333L, MENTOR_ID)).thenReturn(Optional.of(copied));

        curriculumService.forkPublishedVersion(MENTOR_ID, CURRICULUM_ID, new CreateCurriculumVersionRequest("2.0", null, null));

        ArgumentCaptor<TaskTemplateEntity> captor = ArgumentCaptor.forClass(TaskTemplateEntity.class);
        verify(taskTemplateDao).insert(captor.capture());
        assertThat(captor.getValue().getLearningMaterialId()).isEqualTo(1001L);
    }

    @Test
    void addMaterial_throwsBadRequestWhenFileMissing() {
        stubAssertDraftPass();
        assertThatThrownBy(() -> curriculumService.addMaterial(MENTOR_ID, CURRICULUM_ID, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void addMaterial_throwsBadRequestWhenReadFails() throws IOException {
        stubAssertDraftPass();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("cannot read"));

        assertThatThrownBy(() -> curriculumService.addMaterial(MENTOR_ID, CURRICULUM_ID, file, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void addMaterial_throwsBadRequestForNonPdf() {
        stubAssertDraftPass();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> curriculumService.addMaterial(MENTOR_ID, CURRICULUM_ID, file, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void addMaterial_happyPathUsesAutoSortAndSanitizedFileName() {
        stubAssertDraftPass();
        when(learningMaterialDao.maxSortOrderByCurriculumId(CURRICULUM_ID)).thenReturn(7);
        when(pdfStorage.savePdf(eq(CURRICULUM_ID), any())).thenReturn("materials/new.pdf");
        doAnswer(invocation -> {
            LearningMaterialEntity entity = invocation.getArgument(0);
            entity.setId(500L);
            return null;
        }).when(learningMaterialDao).insert(any(LearningMaterialEntity.class));

        MockMultipartFile file = new MockMultipartFile("file", "../../unsafe.pdf", "application/pdf", "%PDF-1.7".getBytes());

        var response = curriculumService.addMaterial(MENTOR_ID, CURRICULUM_ID, file, null);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.sortOrder()).isEqualTo(8);
        assertThat(response.fileName()).isEqualTo("unsafe.pdf");
    }

    @Test
    void addMaterial_rollsBackSavedFileOnUniqueConflict() {
        stubAssertDraftPass();
        when(learningMaterialDao.maxSortOrderByCurriculumId(CURRICULUM_ID)).thenReturn(1);
        when(pdfStorage.savePdf(eq(CURRICULUM_ID), any())).thenReturn("materials/new.pdf");
        doThrow(uniqueConstraintEx()).when(learningMaterialDao).insert(any(LearningMaterialEntity.class));

        MockMultipartFile file = new MockMultipartFile("file", "ok.pdf", "application/pdf", "%PDF-1.4".getBytes());

        assertThatThrownBy(() -> curriculumService.addMaterial(MENTOR_ID, CURRICULUM_ID, file, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
        verify(pdfStorage).deleteIfExists("materials/new.pdf");
    }

    @Test
    void addMaterial_rollsBackSavedFileOnRuntimeFailure() {
        stubAssertDraftPass();
        when(learningMaterialDao.maxSortOrderByCurriculumId(CURRICULUM_ID)).thenReturn(1);
        when(pdfStorage.savePdf(eq(CURRICULUM_ID), any())).thenReturn("materials/new.pdf");
        doThrow(new RuntimeException("db down")).when(learningMaterialDao).insert(any(LearningMaterialEntity.class));

        MockMultipartFile file = new MockMultipartFile("file", "ok.pdf", "application/pdf", "%PDF-1.4".getBytes());

        assertThatThrownBy(() -> curriculumService.addMaterial(MENTOR_ID, CURRICULUM_ID, file, null))
                .isInstanceOf(RuntimeException.class);
        verify(pdfStorage).deleteIfExists("materials/new.pdf");
    }

    @Test
    void deleteMaterial_throwsNotFoundWhenMissing() {
        stubAssertDraftPass();
        when(learningMaterialDao.selectByIdAndCurriculumId(5L, CURRICULUM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.deleteMaterial(MENTOR_ID, CURRICULUM_ID, 5L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void deleteMaterial_deletesFileOnlyWhenNoReferenceLeft() {
        stubAssertDraftPass();
        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(5L);
        material.setStoragePath("materials/shared.pdf");
        when(learningMaterialDao.selectByIdAndCurriculumId(5L, CURRICULUM_ID)).thenReturn(Optional.of(material));
        when(learningMaterialDao.countByStoragePath("materials/shared.pdf")).thenReturn(0L);

        curriculumService.deleteMaterial(MENTOR_ID, CURRICULUM_ID, 5L);

        verify(learningMaterialDao).delete(material);
        verify(pdfStorage).deleteIfExists("materials/shared.pdf");
    }

    @Test
    void createTaskTemplate_throwsBadRequestWhenLearningMaterialIdNonPositive() {
        stubAssertDraftPass();

        var request = new CreateTaskTemplateRequest("Task", "Desc", 1, 1, 0L);
        assertThatThrownBy(() -> curriculumService.createTaskTemplate(MENTOR_ID, CURRICULUM_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void createTaskTemplate_throwsBadRequestWhenMaterialOutsideCurriculum() {
        stubAssertDraftPass();
        when(learningMaterialDao.selectByIdAndCurriculumId(9L, CURRICULUM_ID)).thenReturn(Optional.empty());

        var request = new CreateTaskTemplateRequest("Task", "Desc", 1, 1, 9L);
        assertThatThrownBy(() -> curriculumService.createTaskTemplate(MENTOR_ID, CURRICULUM_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void createTaskTemplate_throwsConflictOnDuplicateSort() {
        stubAssertDraftPass();
        doThrow(uniqueConstraintEx()).when(taskTemplateDao).insert(any(TaskTemplateEntity.class));

        var request = new CreateTaskTemplateRequest("Task", "Desc", 1, 1, null);
        assertThatThrownBy(() -> curriculumService.createTaskTemplate(MENTOR_ID, CURRICULUM_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void createTaskTemplate_happyPathAutoSort() {
        stubAssertDraftPass();
        when(taskTemplateDao.maxSortOrderByCurriculumId(CURRICULUM_ID)).thenReturn(2);
        doAnswer(invocation -> {
            TaskTemplateEntity entity = invocation.getArgument(0);
            entity.setId(77L);
            return null;
        }).when(taskTemplateDao).insert(any(TaskTemplateEntity.class));

        var response = curriculumService.createTaskTemplate(
                MENTOR_ID,
                CURRICULUM_ID,
                new CreateTaskTemplateRequest("Task", "Desc", 2, null, null)
        );

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.sortOrder()).isEqualTo(3);
    }

    @Test
    void updateTaskTemplate_throwsBadRequestWhenNoFieldsToUpdate() {
        stubAssertDraftPass();
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(8L);
        template.setCurriculumId(CURRICULUM_ID);
        when(taskTemplateDao.selectByIdAndCurriculumId(8L, CURRICULUM_ID)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> curriculumService.updateTaskTemplate(
                MENTOR_ID,
                CURRICULUM_ID,
                8L,
                new UpdateTaskTemplateRequest(null, null, null, null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void updateTaskTemplate_clearsLearningMaterialWhenSentinelZero() {
        stubAssertDraftPass();
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(8L);
        template.setCurriculumId(CURRICULUM_ID);
        template.setLearningMaterialId(100L);
        template.setSortOrder(1);
        template.setTitle("Task");
        template.setEstimatedDays(1);
        when(taskTemplateDao.selectByIdAndCurriculumId(8L, CURRICULUM_ID)).thenReturn(Optional.of(template));

        curriculumService.updateTaskTemplate(
                MENTOR_ID,
                CURRICULUM_ID,
                8L,
                new UpdateTaskTemplateRequest(null, null, null, null, 0L)
        );

        assertThat(template.getLearningMaterialId()).isNull();
        verify(taskTemplateDao).update(template);
    }

    @Test
    void updateTaskTemplate_throwsConflictOnDuplicateSort() {
        stubAssertDraftPass();
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(8L);
        template.setCurriculumId(CURRICULUM_ID);
        when(taskTemplateDao.selectByIdAndCurriculumId(8L, CURRICULUM_ID)).thenReturn(Optional.of(template));
        doThrow(uniqueConstraintEx()).when(taskTemplateDao).update(template);

        assertThatThrownBy(() -> curriculumService.updateTaskTemplate(
                MENTOR_ID,
                CURRICULUM_ID,
                8L,
                new UpdateTaskTemplateRequest("X", null, null, 9, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void deleteTaskTemplate_throwsNotFoundWhenDeleteReturnsZero() {
        stubAssertDraftPass();
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(8L);
        when(taskTemplateDao.selectByIdAndCurriculumId(8L, CURRICULUM_ID)).thenReturn(Optional.of(template));
        when(taskTemplateDao.delete(template)).thenReturn(0);

        assertThatThrownBy(() -> curriculumService.deleteTaskTemplate(MENTOR_ID, CURRICULUM_ID, 8L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void deleteTaskTemplate_happyPath() {
        stubAssertDraftPass();
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(8L);
        when(taskTemplateDao.selectByIdAndCurriculumId(8L, CURRICULUM_ID)).thenReturn(Optional.of(template));
        when(taskTemplateDao.delete(template)).thenReturn(1);

        curriculumService.deleteTaskTemplate(MENTOR_ID, CURRICULUM_ID, 8L);

        verify(taskTemplateDao).delete(template);
    }

    @Test
    void deleteDraft_throwsConflictWhenReferencedByAssignment() {
        stubAssertDraftPass();
        when(assignmentDao.countByCurriculum(CURRICULUM_ID)).thenReturn(1L);

        assertThatThrownBy(() -> curriculumService.deleteDraft(MENTOR_ID, CURRICULUM_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void deleteDraft_happyPathDeletesUnreferencedFilesAndRows() {
        stubAssertDraftPass();
        when(assignmentDao.countByCurriculum(CURRICULUM_ID)).thenReturn(0L);

        TaskTemplateEntity t = new TaskTemplateEntity();
        t.setId(1L);
        when(taskTemplateDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(t));

        LearningMaterialEntity m = new LearningMaterialEntity();
        m.setId(2L);
        when(learningMaterialDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(m));
        when(learningMaterialDao.listStoragePathsByCurriculumId(CURRICULUM_ID))
                .thenReturn(List.of("materials/a.pdf", "materials/a.pdf", "materials/b.pdf"));
        when(learningMaterialDao.countByStoragePath("materials/a.pdf")).thenReturn(0L);
        when(learningMaterialDao.countByStoragePath("materials/b.pdf")).thenReturn(1L);

        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(draftCurriculum(CURRICULUM_ID)));
        when(curriculumDao.delete(any(CurriculumEntity.class))).thenReturn(1);

        curriculumService.deleteDraft(MENTOR_ID, CURRICULUM_ID);

        verify(taskTemplateDao).batchDelete(List.of(t));
        verify(learningMaterialDao).batchDelete(List.of(m));
        verify(pdfStorage).deleteIfExists("materials/a.pdf");
        verify(pdfStorage, never()).deleteIfExists("materials/b.pdf");
    }

    @Test
    void deleteDraft_throwsConflictWhenDeleteCurriculumReturnsZero() {
        stubAssertDraftPass();
        when(assignmentDao.countByCurriculum(CURRICULUM_ID)).thenReturn(0L);
        when(taskTemplateDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of());
        when(learningMaterialDao.listStoragePathsByCurriculumId(CURRICULUM_ID)).thenReturn(List.of());
        when(learningMaterialDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of());
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(draftCurriculum(CURRICULUM_ID)));
        when(curriculumDao.delete(any(CurriculumEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> curriculumService.deleteDraft(MENTOR_ID, CURRICULUM_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void publish_throwsBadRequestWhenNoMaterials() {
        stubAssertDraftPass();
        when(learningMaterialDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> curriculumService.publish(MENTOR_ID, CURRICULUM_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void publish_throwsBadRequestWhenNoTemplates() {
        stubAssertDraftPass();
        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(1L);
        when(learningMaterialDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(material));
        when(taskTemplateDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> curriculumService.publish(MENTOR_ID, CURRICULUM_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void publish_happyPathSetsPublishedStatusAndPublishedAt() {
        stubAssertDraftPass();
        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(1L);
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(2L);
        when(learningMaterialDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(material));
        when(taskTemplateDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(template));

        CurriculumEntity curriculum = draftCurriculum(CURRICULUM_ID);
        CurriculumEntity finalState = draftCurriculum(CURRICULUM_ID);
        finalState.setStatus("PUBLISHED");
        finalState.setPublishedAt(LocalDateTime.now());
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID))
                .thenReturn(Optional.of(curriculum), Optional.of(curriculum), Optional.of(finalState));

        var response = curriculumService.publish(MENTOR_ID, CURRICULUM_ID);

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(curriculum.getPublishedAt()).isNotNull();
        verify(curriculumDao).update(curriculum);
    }

    @Test
    void publish_keepsExistingPublishedAtWhenAlreadySet() {
        stubAssertDraftPass();
        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(1L);
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setId(2L);
        when(learningMaterialDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(material));
        when(taskTemplateDao.listByCurriculumId(CURRICULUM_ID)).thenReturn(List.of(template));

        LocalDateTime existing = LocalDateTime.now().minusDays(1);
        CurriculumEntity curriculum = draftCurriculum(CURRICULUM_ID);
        curriculum.setPublishedAt(existing);
        CurriculumEntity finalState = draftCurriculum(CURRICULUM_ID);
        finalState.setStatus("PUBLISHED");
        finalState.setPublishedAt(existing);
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID))
                .thenReturn(Optional.of(curriculum), Optional.of(curriculum), Optional.of(finalState));

        curriculumService.publish(MENTOR_ID, CURRICULUM_ID);

        assertThat(curriculum.getPublishedAt()).isEqualTo(existing);
    }

    private CurriculumEntity draftCurriculum(Long id) {
        CurriculumEntity c = new CurriculumEntity();
        c.setId(id);
        c.setCurriculumGroupId(id);
        c.setVersionLabel("1.0");
        c.setName("Curriculum");
        c.setDescription("Desc");
        c.setStatus("DRAFT");
        return c;
    }

    private CurriculumEntity publishedCurriculum(Long id) {
        CurriculumEntity c = draftCurriculum(id);
        c.setStatus("PUBLISHED");
        c.setPublishedAt(LocalDateTime.now());
        return c;
    }

    private void stubAssertDraftPass() {
        when(curriculumDao.selectByIdAndCreator(CURRICULUM_ID, MENTOR_ID)).thenReturn(Optional.of(draftCurriculum(CURRICULUM_ID)));
    }

    private CurriculumSourceProjection publishedSourceProjection() {
        CurriculumSourceProjection source = new CurriculumSourceProjection();
        source.setCurriculumGroupId(1L);
        source.setName("Base curriculum");
        source.setDescription("Desc");
        source.setStatus("PUBLISHED");
        return source;
    }

    private UniqueConstraintException uniqueConstraintEx() {
        @SuppressWarnings("rawtypes")
        Sql sql = mock(Sql.class);
        when(sql.getKind()).thenReturn(SqlKind.INSERT);
        return new UniqueConstraintException(SqlLogType.RAW, sql, new RuntimeException("duplicate"));
    }
}
