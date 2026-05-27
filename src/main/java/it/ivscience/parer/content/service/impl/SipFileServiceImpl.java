package it.ivscience.parer.content.service.impl;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.dao.SipFileDAO;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.worker.s3.S3FileMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

public class SipFileServiceImpl implements SipFileService {

    @Autowired
    private SipFileDAO sipFileDAO;

    private String createdStatus;
    private String successStatus;
    public void setCreatedStatus(String v) { this.createdStatus = v; }
    public void setSuccessStatus(String v)  { this.successStatus = v; }

    @Override
    @Transactional
    public SipFile create(SipPackage sipPackage, S3FileMetadata meta) {
        SipFile sf = new SipFile();
        sf.setSipPackage(sipPackage);
        sf.setS3Uri(meta.getS3Uri());
        sf.setFileName(meta.getFileName());
        sf.setFileSizeBytes(meta.getSizeBytes());
        sf.setMimeType(meta.getMimeType());
        sf.setUploadStatus(createdStatus);
        sf.setChecksum(meta.getChecksum());
        return sipFileDAO.create(sf);
    }

    @Override
    @Transactional
    public SipFile save(SipFile sipFile) {
        return sipFileDAO.save(sipFile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SipFile> findBySipPackage(SipPackage sipPackage) {
        return sipFileDAO.findBySipPackageId(sipPackage.getId());
    }

    @Override
    @Transactional
    public void markSent(SipFile sipFile, OffsetDateTime sentAt, String remoteFileName, String checksum) {
        sipFileDAO.updateUploadStatus(sipFile.getId(), successStatus, sentAt, remoteFileName, checksum);
    }
}