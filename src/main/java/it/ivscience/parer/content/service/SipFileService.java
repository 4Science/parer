package it.ivscience.parer.content.service;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.s3.S3FileMetadata;

import java.time.OffsetDateTime;
import java.util.List;

public interface SipFileService {

    SipFile create(SipPackage sipPackage, S3FileMetadata meta);

    SipFile save(SipFile sipFile);

    List<SipFile> findBySipPackage(SipPackage sipPackage);

    void markSent(SipFile sipFile, OffsetDateTime sentAt, String remoteFileName, String checksum);
}