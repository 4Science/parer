package it.ivscience.parer.worker.s3;

public class S3FileMetadata {

    private String bucket;
    private String key;
    private String fileName;
    private long sizeBytes;
    private String mimeType;
    private String checksum;

    public S3FileMetadata(String bucket, String key, String fileName, long sizeBytes, String mimeType, String checksum) {
        this.bucket = bucket;
        this.key = key;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.mimeType = mimeType;
        this.checksum = checksum;
    }

    public String getBucket() { return bucket; }
    public String getKey() { return key; }
    public String getFileName() { return fileName; }
    public long getSizeBytes() { return sizeBytes; }
    public String getMimeType() { return mimeType; }
    public String getChecksum() { return checksum; }

    public String getS3Uri() {
        return "s3://" + bucket + "/" + key;
    }
}