package it.ivscience.parer.it;

import it.ivscience.parer.worker.BatchRunner;
import it.ivscience.parer.worker.SipPipeline;
import it.ivscience.parer.worker.ftp.FtpClient;
import it.ivscience.parer.worker.s3.S3Service;
import it.ivscience.parer.worker.sip.SipValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: verifies that the full Spring context loads correctly.
 * If this test fails, all other ITs will also fail — fix this first.
 */
class ContextLoadsIT extends AbstractPipelineIT {

    @Autowired private SipPipeline sipPipeline;
    @Autowired private BatchRunner batchRunner;
    @Autowired private DataSource dataSource;
    @Autowired private SipValidator sipValidator;
    @Autowired private FtpClient ftpClient;
    @Autowired private S3Service s3Service;

    @Test
    void allCriticalBeansArePresent() {
        assertThat(sipPipeline).isNotNull();
        assertThat(batchRunner).isNotNull();
        assertThat(dataSource).isNotNull();
        assertThat(sipValidator).isNotNull();
        assertThat(ftpClient).isNotNull();
        assertThat(s3Service).isNotNull();
    }

    @Test
    void dataSourceIsReachable() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }
}
