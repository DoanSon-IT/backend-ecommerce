package com.sondv.phone.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {
    private static final Logger logger = LoggerFactory.getLogger(CloudinaryService.class);

    @Value("${CLOUDINARY_UPLOAD_URL}")
    private String CLOUDINARY_UPLOAD_URL;

    @Value("${CLOUDINARY_UPLOAD_PRESET}")
    private String CLOUDINARY_UPLOAD_PRESET;

    private final RestTemplate restTemplate;

    public CloudinaryService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String uploadImageToCloudinary(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                logger.warn("File rỗng hoặc không tồn tại!");
                return null;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            ByteArrayResource byteArrayResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            body.add("file", byteArrayResource);
            body.add("upload_preset", CLOUDINARY_UPLOAD_PRESET);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(CLOUDINARY_UPLOAD_URL, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.info("Upload ảnh thành công: {}", response.getBody().get("url"));
                return (String) response.getBody().get("url");
            } else {
                logger.error("Upload ảnh thất bại, mã phản hồi: {}", response.getStatusCode());
            }
        } catch (IOException e) {
            logger.error("Lỗi khi đọc file upload: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Lỗi không xác định khi upload ảnh: {}", e.getMessage(), e);
        }

        return null;
    }
}