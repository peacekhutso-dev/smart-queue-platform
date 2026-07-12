package com.dha.document.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Shrinks uploaded files before storage (resize/compress/strip metadata) —
 * the "Document Optimization" pipeline stage, sitting between file
 * validation and storage. Optimization failure must never block a
 * legitimate upload, so every path fails open: on any error, the original
 * bytes are returned unchanged and the failure is only logged.
 *
 * PDF handling here is deliberately the cheap win only: PDDocument.save with
 * CompressParameters.DEFAULT_COMPRESSION recompresses the PDF's internal
 * object/content streams, but does not touch embedded image bytes — actual
 * image recompression inside a PDF (the bigger win) would require manually
 * walking PDPage/PDResources/PDImageXObject and re-encoding each embedded
 * image via PDFBox's JPEGFactory, which is out of scope for now.
 */
@Service
@Slf4j
public class DocumentOptimizationService {

    private static final int MAX_DIMENSION = 2000;
    private static final float JPEG_QUALITY = 0.75f;

    public byte[] optimizeImage(byte[] input, String contentType) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.Builder<?> builder = Thumbnails.of(new ByteArrayInputStream(input))
                    .size(MAX_DIMENSION, MAX_DIMENSION)
                    .keepAspectRatio(true);

            if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) {
                builder.outputFormat("jpg").outputQuality(JPEG_QUALITY);
            }

            builder.toOutputStream(out);
            byte[] result = out.toByteArray();
            return result.length > 0 && result.length < input.length ? result : input;
        } catch (Exception e) {
            log.warn("Image optimization failed, storing original bytes: {}", e.getMessage());
            return input;
        }
    }

    public byte[] optimizePdf(byte[] input) {
        try (PDDocument document = Loader.loadPDF(input);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out, CompressParameters.DEFAULT_COMPRESSION);
            byte[] result = out.toByteArray();
            return result.length > 0 && result.length < input.length ? result : input;
        } catch (Exception e) {
            log.warn("PDF optimization failed, storing original bytes: {}", e.getMessage());
            return input;
        }
    }
}
