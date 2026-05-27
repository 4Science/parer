package it.ivscience.parer.worker.model;

public class WorkUnitDescriptor {

    private String objectId;
    private String bucket;
    private String key;

    public WorkUnitDescriptor() {}

    public WorkUnitDescriptor(String objectId, String bucket, String key) {
        this.objectId = objectId;
        this.bucket   = bucket;
        this.key      = key;
    }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    @Override
    public String toString() {
        return "WorkUnitDescriptor{objectId='" + objectId + "', bucket='" + bucket + "', key='" + key + "'}";
    }
}
