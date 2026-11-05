package com.odontomax.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpConfigurarResponseDTO {

    private String secret;

    @JsonProperty("otpauth_url")
    private String otpauthUrl;

    @JsonProperty("qr_code")
    private String qrCode;

    @JsonProperty("backup_codes")
    private List<String> backupCodes;

    @JsonProperty("qrCode")
    public String getQrCodeCamel() { return qrCode; }

    @JsonProperty("otpauthUrl")
    public String getOtpauthUrlCamel() { return otpauthUrl; }

    @JsonProperty("backupCodes")
    public List<String> getBackupCodesCamel() { return backupCodes; }
}
