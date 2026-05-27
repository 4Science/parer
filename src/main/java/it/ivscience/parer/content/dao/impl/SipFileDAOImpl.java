package it.ivscience.parer.content.dao.impl;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.dao.AbstractHibernateDAO;
import it.ivscience.parer.content.dao.SipFileDAO;

import java.time.OffsetDateTime;
import java.util.List;

public class SipFileDAOImpl extends AbstractHibernateDAO<SipFile> implements SipFileDAO {

    @Override
    public List<SipFile> findBySipPackageId(Integer sipPackageId) {
        return getSession()
                .createQuery("FROM SipFile WHERE sipPackage.id = :id ORDER BY id", SipFile.class)
                .setParameter("id", sipPackageId)
                .getResultList();
    }

    @Override
    public void updateUploadStatus(Integer id, String uploadStatus, OffsetDateTime sentAt, String remoteFileName, String checksum) {
        getSession()
                .createMutationQuery(
                        "UPDATE SipFile SET uploadStatus = :status, sentAt = :sentAt, remoteFileName = :remoteFileName, checksum = :checksum WHERE id = :id")
                .setParameter("status", uploadStatus)
                .setParameter("sentAt", sentAt)
                .setParameter("remoteFileName", remoteFileName)
                .setParameter("checksum", checksum)
                .setParameter("id", id)
                .executeUpdate();
    }
}