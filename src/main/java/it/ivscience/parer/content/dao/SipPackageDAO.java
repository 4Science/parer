package it.ivscience.parer.content.dao;

import it.ivscience.parer.content.SipPackage;

import java.util.Optional;

public interface SipPackageDAO extends GenericDAO<SipPackage> {

    Optional<SipPackage> findBySipId(String sipId);

    void updateStatus(Integer id, String status, String statusReason);
}