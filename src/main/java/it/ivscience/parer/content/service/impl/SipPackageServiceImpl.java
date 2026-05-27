package it.ivscience.parer.content.service.impl;

import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.dao.SipPackageDAO;
import it.ivscience.parer.content.service.SipPackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

public class SipPackageServiceImpl implements SipPackageService {

    @Autowired
    private SipPackageDAO sipPackageDAO;

    private String createdStatus;
    private String successStatus;
    public void setCreatedStatus(String v) { this.createdStatus = v; }
    public void setSuccessStatus(String v)  { this.successStatus = v; }

    @Override
    @Transactional
    public SipPackage create(String sipId, String s3Uri) {
        SipPackage pkg = new SipPackage();
        pkg.setSipId(sipId);
        pkg.setS3Uri(s3Uri);
        pkg.setStatus(createdStatus);
        return sipPackageDAO.create(pkg);
    }

    @Override
    @Transactional
    public SipPackage save(SipPackage sipPackage) {
        return sipPackageDAO.save(sipPackage);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SipPackage> findBySipId(String sipId) {
        return sipPackageDAO.findBySipId(sipId);
    }

    @Override
    @Transactional
    public void updateStatus(SipPackage sipPackage, String status, String reason) {
        sipPackageDAO.updateStatus(sipPackage.getId(), status, reason);
    }

    @Override
    @Transactional
    public void markSuccess(SipPackage sipPackage) {
        sipPackage.setStatus(successStatus);
        sipPackage.setStatusReason(null);
        sipPackage.setSubmittedAt(OffsetDateTime.now());
        sipPackageDAO.save(sipPackage);
    }
}