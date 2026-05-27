package it.ivscience.parer.content.service;

import it.ivscience.parer.content.SipPackage;

import java.util.Optional;

public interface SipPackageService {

    SipPackage create(String sipId, String s3Uri);

    SipPackage save(SipPackage sipPackage);

    Optional<SipPackage> findBySipId(String sipId);

    void updateStatus(SipPackage sipPackage, String status, String reason);

    void markSuccess(SipPackage sipPackage);
}