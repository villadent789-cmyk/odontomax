package com.odontomax.domain.port.out;

import java.io.InputStream;

public interface StoragePort {
    /**
     * Sube un archivo binario a Cloudflare R2 en bucket privado y retorna la clave canónica de almacenamiento (storageKey)
     */
    String uploadFile(String key, InputStream inputStream, long contentLength, String contentType);

    /**
     * Genera una URL firmada temporal (Presigned URL) para visualización o descarga segura con tiempo de expiración
     * @param key Clave canónica del archivo en el bucket privado
     * @param durationMinutes Tiempo de validez del enlace antes de expirar
     */
    String generatePresignedUrl(String key, int durationMinutes);

    /**
     * Elimina un archivo de Cloudflare R2
     */
    void deleteFile(String key);

    /**
     * Descarga los bytes del archivo almacenado
     */
    default byte[] downloadFile(String key) {
        return new byte[0];
    }
}
