package it.ivscience.parer.content.dao.impl;

import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.dao.AbstractHibernateDAO;
import it.ivscience.parer.content.dao.SipPackageDAO;

import java.time.OffsetDateTime;
import java.util.Optional;

public class SipPackageDAOImpl extends AbstractHibernateDAO<SipPackage> implements SipPackageDAO {

    @Override
    public Optional<SipPackage> findBySipId(String sipId) {
        return getSession()
                .createQuery("FROM SipPackage WHERE sipId = :sipId", SipPackage.class)
                .setParameter("sipId", sipId)
                .uniqueResultOptional();
    }

    @Override
    public void updateStatus(Integer id, String status, String statusReason) {
        getSession()
                .createMutationQuery(
                        "UPDATE SipPackage SET status = :status, statusReason = :reason, " +
                        "lastUpdatedAt = :now WHERE id = :id")
                .setParameter("status", status)
                .setParameter("reason", statusReason)
                .setParameter("now", OffsetDateTime.now())
                .setParameter("id", id)
                .executeUpdate();
    }
}