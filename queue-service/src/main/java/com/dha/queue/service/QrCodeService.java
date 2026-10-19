package com.dha.queue.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders QueueEntry.qrCode (a plain string like "DHA-A003-FAD00173") as an
 * actual scannable QR PNG, returned as a data: URI so the frontend can drop
 * it straight into an <img src>  with no separate image endpoint (which
 * would need its own auth, given qrCode is meant to be private to the
 * ticket holder — see QueueService.broadcastQueueUpdate).
 */
@Service
@Slf4j
public class QrCodeService {

    private static final int SIZE_PX = 240;
    // A ticket's QR content never changes, so each image is rendered once and
    // reused (citizens poll their ticket every few seconds). Bounded LRU: ~2-3KB
    // per image, so at most a few MB.
    private static final int CACHE_SIZE = 2000;
    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public String generateDataUri(String content) {
        if (content == null) return null;
        String cached = cache.get(content);
        if (cached != null) return cached;
        String uri = render(content);
        if (uri != null) cache.put(content, uri);
        return uri;
    }

    private String render(String content) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1
            );
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX, hints);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            // A broken QR image shouldn't take down the whole ticket response —
            // the frontend falls back to its placeholder when this is null.
            log.error("QR code generation failed for content={}: {}", content, e.getMessage());
            return null;
        }
    }
}
