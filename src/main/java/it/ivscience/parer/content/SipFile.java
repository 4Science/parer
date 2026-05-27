package it.ivscience.parer.content;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "sip_file",
    uniqueConstraints = @UniqueConstraint(name = "uk_sip_file_key", columnNames = {"sip_package_id", "s3_uri"}),
    indexes = {
        @Index(name = "idx_sip_file_sip_package_id", columnList = "sip_package_id"),
        @Index(name = "idx_sip_file_upload_status",  columnList = "upload_status")
    }
)
public class SipFile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sip_file_id_seq")
    @SequenceGenerator(name = "sip_file_id_seq", sequenceName = "sip_file_id_seq", allocationSize = 1)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sip_package_id", nullable = false)
    private SipPackage sipPackage;

    @Column(name = "s3_uri", columnDefinition = "TEXT")
    private String s3Uri;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "upload_status", nullable = false, length = 50)
    private String uploadStatus;

    @Column(name = "remote_file_name", length = 255)
    private String remoteFileName;

    @Column(name = "checksum", length = 255)
    private String checksum;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public SipPackage getSipPackage() { return sipPackage; }
    public void setSipPackage(SipPackage sipPackage) { this.sipPackage = sipPackage; }

    public String getS3Uri() { return s3Uri; }
    public void setS3Uri(String s3Uri) { this.s3Uri = s3Uri; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(String uploadStatus) { this.uploadStatus = uploadStatus; }

    public String getRemoteFileName() { return remoteFileName; }
    public void setRemoteFileName(String remoteFileName) { this.remoteFileName = remoteFileName; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }
}
