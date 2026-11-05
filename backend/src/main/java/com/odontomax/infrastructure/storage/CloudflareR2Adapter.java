package com.odontomax.infrastructure.storage;

import com.odontomax.domain.port.out.StoragePort;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CloudflareR2Adapter implements StoragePort {

    @Value("${cloudflare.r2.account-id:}")
    private String accountId;

    @Value("${cloudflare.r2.access-key:}")
    private String accessKey;

    @Value("${cloudflare.r2.secret-key:}")
    private String secretKey;

    @Value("${cloudflare.r2.bucket-name:radiografias-odontomax}")
    private String bucketName;

    @Value("${cloudflare.r2.public-domain:https://radiografias.odontomax.pe}")
    private String publicDomain;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    @PostConstruct
    public void init() {
        if (accountId != null && !accountId.isBlank() && !accountId.contains("tu-account-id")) {
            String endpointUrl = String.format("https://%s.r2.cloudflarestorage.com", accountId);
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

            software.amazon.awssdk.services.s3.S3Configuration s3Config = software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .checksumValidationEnabled(false)
                    .chunkedEncodingEnabled(false)
                    .build();

            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpointUrl))
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of("auto"))
                    .serviceConfiguration(s3Config)
                    .build();

            this.s3Presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpointUrl))
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of("auto"))
                    .serviceConfiguration(s3Config)
                    .build();
        }
    }

    private final java.util.Map<String, byte[]> localDevStorage = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.nio.file.Path localDiskDir = java.nio.file.Paths.get("uploads", "storage");

    @Override
    public String uploadFile(String key, InputStream inputStream, long contentLength, String contentType) {
        if (s3Client == null) {
            log.info("Almacenamiento Cloudflare R2 no configurado (entorno local dev). Guardando archivo localmente: {}", key);
            try {
                byte[] bytes = inputStream.readAllBytes();
                localDevStorage.put(key, bytes);
                try {
                    java.nio.file.Path targetPath = localDiskDir.resolve(key).normalize();
                    if (targetPath.startsWith(localDiskDir.normalize())) {
                        java.nio.file.Files.createDirectories(targetPath.getParent());
                        java.nio.file.Files.write(targetPath, bytes);
                    }
                } catch (Exception ex) {
                    log.warn("No se pudo persistir copia local a disco para {}: {}", key, ex.getMessage());
                }
            } catch (Exception e) {
                log.error("Error al leer archivo en almacenamiento local simulado: {}", e.getMessage(), e);
            }
            return key;
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

        // Retornar la clave canónica del objeto en el bucket privado
        return key;
    }

    @Override
    public String generatePresignedUrl(String key, int durationMinutes) {
        if (s3Presigner == null) {
            log.warn("Cloudflare R2 presigner no configurado (entorno local dev). Enlace de prueba para key: {}", key);
            return "https://storage.local.odontomax.test/" + key;
        }

        // Tope máximo de seguridad: 120 minutos (2 horas)
        int duration = Math.max(1, Math.min(durationMinutes > 0 ? durationMinutes : 15, 120));

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(duration))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void deleteFile(String key) {
        if (s3Client != null) {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
        } else {
            localDevStorage.remove(key);
            try {
                java.nio.file.Path targetPath = localDiskDir.resolve(key).normalize();
                java.nio.file.Files.deleteIfExists(targetPath);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public byte[] downloadFile(String key) {
        if (key == null || key.isBlank()) {
            return new byte[0];
        }

        if (s3Client == null) {
            byte[] inMemory = localDevStorage.get(key);
            if (inMemory != null && inMemory.length > 0) {
                return inMemory;
            }
            try {
                java.nio.file.Path targetPath = localDiskDir.resolve(key).normalize();
                if (java.nio.file.Files.exists(targetPath)) {
                    byte[] onDisk = java.nio.file.Files.readAllBytes(targetPath);
                    localDevStorage.put(key, onDisk);
                    return onDisk;
                }
            } catch (Exception ex) {
                log.warn("No se pudo leer archivo desde disco local simulado {}: {}", key, ex.getMessage());
            }
            return new byte[0];
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            return s3Client.getObject(getObjectRequest).readAllBytes();
        } catch (Exception e) {
            log.error("Error al descargar archivo desde Cloudflare R2 con key {}: {}", key, e.getMessage());
            return new byte[0];
        }
    }
}
