package it.ivscience.parer.content;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "sip_package",
    indexes = {
        @Index(name = "idx_sip_package_status",     columnList = "status"),
        @Index(name = "idx_sip_package_created_at", columnList = "created_at")
    }
)
public class SipPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sip_package_id_seq")
    @SequenceGenerator(name = "sip_package_id_seq", sequenceName = "sip_package_id_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "sip_id", nullable = false, unique = true, length = 255)
    private String sipId;

    @Column(name = "parer_id", unique = true, length = 255)
    private String parerId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    @Column(name = "s3_uri", columnDefinition = "TEXT")
    private String s3Uri;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @OneToMany(mappedBy = "sipPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SipFile> files = new ArrayList<>();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getSipId() { return sipId; }
    public void setSipId(String sipId) { this.sipId = sipId; }

    public String getParerId() { return parerId; }
    public void setParerId(String parerId) { this.parerId = parerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public String getS3Uri() { return s3Uri; }
    public void setS3Uri(String s3Uri) { this.s3Uri = s3Uri; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public List<SipFile> getFiles() { return files; }
    public void setFiles(List<SipFile> files) { this.files = files; }
}