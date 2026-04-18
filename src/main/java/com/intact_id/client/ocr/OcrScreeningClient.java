package com.intact_id.client.ocr;

import com.intact_id.config.FeignConfig;
import com.intact_id.dto.response.ocr.OcrExtractionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "ocr-service", url = "${ocr-service.url}", configuration = FeignConfig.class)
public interface OcrScreeningClient {

    @PostMapping(value = "/ocr/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    OcrExtractionResponse extractDocument(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret,
            @RequestPart("file") MultipartFile file);
}
