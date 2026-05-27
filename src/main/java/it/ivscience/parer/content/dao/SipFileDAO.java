package it.ivscience.parer.content.dao;

import it.ivscience.parer.content.SipFile;

import java.time.OffsetDateTime;
import java.util.List;

public interface SipFileDAO extends GenericDAO<SipFile> {

    List<SipFile> findBySipPackageId(Integer sipPackageId);

    void updateUploadStatus(Integer id, String uploadStatus, OffsetDateTime sentAt, String remoteFileName, String checksum);
}