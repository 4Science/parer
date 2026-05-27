package it.ivscience.parer.worker;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.model.WorkUnitDescriptor;
import it.ivscience.parer.worker.s3.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BatchRunnerTest {

    @Mock  private SipPipeline   sipPipeline;
    @Mock  private S3Service s3Service;

    @InjectMocks
    private BatchRunner batchRunner;

    private static final String VALID_BUCKET = "my-bucket";
    private static final String VALID_KEY    = "parer/esecuzioni/2026/01/01/foo.process";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(batchRunner, "keyPrefix", "parer/esecuzioni/");
        ReflectionTestUtils.setField(batchRunner, "keySuffix", ".process");
        ReflectionTestUtils.setField(batchRunner, "logPrefix",  "parer/log/");
        ReflectionTestUtils.setField(batchRunner, "localLogFile",  "/tmp/parer.log");
    }

    @Test
    void missingBucketArgReturnsZero() {
        int exit = batchRunner.runBatch(new DefaultApplicationArguments("--s3-key=" + VALID_KEY));
        assertEquals(0, exit);
        verify(sipPipeline, never()).process(any());
    }

    @Test
    void missingKeyArgReturnsZero() {
        int exit = batchRunner.runBatch(new DefaultApplicationArguments("--s3-bucket=" + VALID_BUCKET));
        assertEquals(0, exit);
        verify(sipPipeline, never()).process(any());
    }

    @Test
    void wrongPrefixReturnsZero() {
        int exit = batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=" + VALID_BUCKET,
                "--s3-key=wrong/path/foo.process"));
        assertEquals(0, exit);
        verify(sipPipeline, never()).process(any());
    }

    @Test
    void wrongSuffixReturnsZero() {
        int exit = batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=" + VALID_BUCKET,
                "--s3-key=parer/esecuzioni/2026/01/01/foo.json"));
        assertEquals(0, exit);
        verify(sipPipeline, never()).process(any());
    }

    @Test
    void functionalExceptionFromPipelineReturnsZero() {
        doThrow(new FunctionalException("XSD fail")).when(sipPipeline).process(any(WorkUnitDescriptor.class));
        assertEquals(0, runValidBatch());
    }

    @Test
    void functionalExceptionFromPipelineWritesExecutionLog() {
        doThrow(new FunctionalException("XSD fail")).when(sipPipeline).process(any(WorkUnitDescriptor.class));
        runValidBatch();
        // VALID_KEY → sipKey="2026/01/01/foo" → objectId="foo"
        verify(s3Service).writeFile(
                eq(VALID_BUCKET),
                logKeyFor("foo"),
                eq("/tmp/parer.log"));
    }

    @Test
    void transientExceptionFromPipelineReturnsOne() {
        doThrow(new TransientException("FTP down")).when(sipPipeline).process(any(WorkUnitDescriptor.class));
        assertEquals(1, runValidBatch());
        verify(s3Service).writeFile(
                eq(VALID_BUCKET),
                logKeyFor("foo"),
                eq("/tmp/parer.log"));
    }

    @Test
    void unexpectedExceptionFromPipelineReturnsZero() {
        doThrow(new RuntimeException("unexpected")).when(sipPipeline).process(any(WorkUnitDescriptor.class));
        assertEquals(0, runValidBatch());
    }

    @Test
    void happyPathReturnsZeroAndInvokesPipeline() {
        int exit = runValidBatch();
        assertEquals(0, exit);
        verify(sipPipeline).process(any(WorkUnitDescriptor.class));
        verify(s3Service).writeFile(
                eq(VALID_BUCKET),
                logKeyFor("foo"),
                eq("/tmp/parer.log"));
    }

    @Test
    void descriptorFieldsPassedToPipeline() {
        runValidBatch();
        // VALID_KEY → sipKey="2026/01/01/foo" → rawId="foo" → objectId="foo" (no underscore)
        verify(sipPipeline).process(argThat(d ->
                "foo".equals(d.getObjectId())
                && VALID_BUCKET.equals(d.getBucket())
                && "2026/01/01/foo".equals(d.getKey())));
    }

    @Test
    void handleWithDotsKeptAsIs() {
        int exit = batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=" + VALID_BUCKET,
                "--s3-key=parer/esecuzioni/20.500.14008_80992.process"));
        assertEquals(0, exit);
        // underscore kept in sipId — S3 paths use underscore encoding
        verify(sipPipeline).process(argThat(d -> "20.500.14008_80992".equals(d.getObjectId())));
    }

    @Test
    void urlEncodedKeyIsDecodedBeforeParsing() {
        int exit = batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=" + VALID_BUCKET,
                "--s3-key=parer/esecuzioni/AMS_HISTORICA%5E2000%5E20.500.14008_82440.process"));

        assertEquals(0, exit);
        verify(sipPipeline).process(argThat(d ->
                "AMS_HISTORICA^2000^20.500.14008_82440".equals(d.getObjectId())
                && "AMS_HISTORICA^2000^20.500.14008_82440".equals(d.getKey())));
    }

    // ── baseName ──────────────────────────────────────────────────────────────

    @Test
    void baseNameExtractsLastComponentWithoutExtension() {
        assertEquals("OBJ-1",  BatchRunner.baseName("objects/OBJ-1"));
        assertEquals("OBJ-1",  BatchRunner.baseName("objects/OBJ-1/"));
        assertEquals("OBJ-1",  BatchRunner.baseName("objects/OBJ-1.xml"));
        assertEquals("OBJ-1",  BatchRunner.baseName("a/b/c/OBJ-1.process"));
        assertEquals("OBJ-1",  BatchRunner.baseName("OBJ-1"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int runValidBatch() {
        return batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=" + VALID_BUCKET,
                "--s3-key=" + VALID_KEY));
    }

    private static <T> T argThat(java.util.function.Predicate<T> pred) {
        return org.mockito.ArgumentMatchers.argThat(pred::test);
    }

    private static String logKeyFor(String objectId) {
        String regex = "parer/log/" + java.util.regex.Pattern.quote(objectId)
                + "-\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}Z\\.log";
        return org.mockito.ArgumentMatchers.argThat(key -> key != null && key.matches(regex));
    }
}