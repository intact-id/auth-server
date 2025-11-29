package com.intact_id.client.face;

import com.intact_id.config.FeignConfig;
import com.intact_id.dto.response.face.FaceVerificationResponse;
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
}
