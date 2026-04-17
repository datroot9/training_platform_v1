package com.example.training_platform.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PdfFileValidatorTest {

    @Test
    void acceptsPdfMagicBytes() {
        byte[] pdf = "%PDF-1.4\n".getBytes();
        assertThat(PdfFileValidator.isPdf(pdf)).isTrue();
    }

    @Test
    void rejectsNonPdf() {
        assertThat(PdfFileValidator.isPdf("hello".getBytes())).isFalse();
        assertThat(PdfFileValidator.isPdf(new byte[]{'%', 'P', 'D'})).isFalse();
        assertThat(PdfFileValidator.isPdf(null)).isFalse();
    }
}
