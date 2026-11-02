
package com.urlshortener.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ShortenRequest {
    @NotBlank
    @Size(max = 2048)
    private String longUrl;
    private String customAlias;
    private Integer ttlHours;

    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    public String getCustomAlias() { return customAlias; }
    public void setCustomAlias(String customAlias) { this.customAlias = customAlias; }
    public Integer getTtlHours() { return ttlHours; }
    public void setTtlHours(Integer ttlHours) { this.ttlHours = ttlHours; }
}
