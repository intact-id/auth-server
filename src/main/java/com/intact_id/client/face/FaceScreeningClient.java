package com.intact_id.client.face;

import com.intact_id.config.FeignConfig;
import com.intact_id.dto.response.face.FaceVerificationResponse;
import com.intact_id.dto.response.face.LivenessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "face-screening-service", url = "${face-service.url}", configuration = FeignConfig.class)
public interface FaceScreeningClient {

    @PostMapping(value = "/api/face/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FaceVerificationResponse verifyFace(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret,
            @RequestPart("selfie") MultipartFile selfie,
            @RequestPart("id_photo") MultipartFile idPhoto);

    @PostMapping(value = "/api/face/liveness/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    LivenessResponse checkLiveness(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret,
            @RequestPart("photo") MultipartFile photo);

    /**
     * Active liveness: frames captured during the widget's head-turn challenge.
     * labels is comma-separated, one per frame, in the same order (neutral, turn_left, turn_right).
     * Returns the same shape as checkLiveness.
     */
    @PostMapping(value = "/api/face/liveness/sequence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    LivenessResponse checkLivenessSequence(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret,
            @RequestPart("frames") MultipartFile[] frames,
            @RequestPart("labels") String labels);
}
