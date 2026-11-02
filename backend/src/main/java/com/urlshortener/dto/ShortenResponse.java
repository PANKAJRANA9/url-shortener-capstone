
package com.urlshortener.dto;

public class ShortenResponse {
    private String shortCode;
    private String shortUrl;
    private String longUrl;
    private long expiresInHours;

    public ShortenResponse(String shortCode, String shortUrl, String longUrl, long expiresInHours) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.expiresInHours = expiresInHours;
    }
    public String getShortCode() { return shortCode; }
    public String getShortUrl() { return shortUrl; }
    public String getLongUrl() { return longUrl; }
    public long getExpiresInHours() { return expiresInHours; }
}
