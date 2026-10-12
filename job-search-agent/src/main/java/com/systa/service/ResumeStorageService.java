package com.systa.service;

import com.systa.config.ResumeProperties;
import com.systa.exception.ResumeStorageException;
import com.systa.model.ResumeDownload;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/** Stores resume files in the S3 bucket and hands out links to download them. */
@Service
public class ResumeStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ResumeProperties resumeProperties;

    public ResumeStorageService(final S3Client s3Client, final S3Presigner s3Presigner,
                                 final ResumeProperties resumeProperties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.resumeProperties = resumeProperties;
    }

    public void store(final String key, final byte[] content, final String contentType) {
        try {
            s3Client.putObject(request -> request
                            .bucket(resumeProperties.s3().bucket())
                            .key(key)
                            .contentType(contentType),
                    RequestBody.fromBytes(content));
        } catch (final SdkException e) {
            throw new ResumeStorageException("Could not store the resume in S3 - key=" + key, e);
        }
    }

    /** A link the browser can fetch directly from S3, saving the file under the given name. */
    public ResumeDownload downloadLink(final String key, final String fileName, final String contentType) {
        try {
            final PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presign -> presign
                    .signatureDuration(resumeProperties.downloadUrlTtl())
                    .getObjectRequest(request -> request
                            .bucket(resumeProperties.s3().bucket())
                            .key(key)
                            .responseContentType(contentType)
                            .responseContentDisposition("attachment; filename=\"" + fileName + "\"")));
            return new ResumeDownload(presigned.url().toString(), presigned.expiration());
        } catch (final SdkException e) {
            throw new ResumeStorageException("Could not create a download link for the resume - key=" + key, e);
        }
    }
}
