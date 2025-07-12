package stirling.software.common.controller;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.Mockito;
import stirling.software.SPDF.config.EndpointConfiguration;
import stirling.software.SPDF.controller.api.misc.CompressController;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.GeneralUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import static org.mockito.Mockito.*;

public class CompressControllerPropertyTest {

    static CompressController controller;
    static CustomPDFDocumentFactory pdfDocumentFactory;
    static EndpointConfiguration endpointConfiguration;

    @BeforeContainer
    static void setup() throws IOException {
        pdfDocumentFactory = mock(CustomPDFDocumentFactory.class);
        endpointConfiguration = mock(EndpointConfiguration.class);

        controller = new CompressController(pdfDocumentFactory, endpointConfiguration);

        // Stub PDF loading
        when(pdfDocumentFactory.load(any(Path.class))).thenAnswer(invocation -> {
            PDDocument mockDoc = mock(PDDocument.class);
            doAnswer(saveInvocation -> {
                String targetPath = saveInvocation.getArgument(0);
                Files.write(Path.of(targetPath), "FAKE PDF DATA".getBytes());
                return null;
            }).when(mockDoc).save(anyString());
            return mockDoc;
        });
    }

    // An example of using Assumptions, Custom Constraints, Constraints
    @Property
    void compressImagesWithValidInputs(
        @ForAll("tempPdfFile") Path tempPdf,
        @ForAll @Positive double scaleFactor,
        @ForAll("validJpegQualities") float jpegQuality,
        @ForAll boolean convertToGrayscale) throws Exception {

        // Assumption: Avoid combinations where both scaleFactor and jpegQuality are too low — that would make the image unreadably bad
        Assume.that(scaleFactor + jpegQuality > 0.5);

        Path result = controller.compressImagesInPDF(tempPdf, scaleFactor, jpegQuality, convertToGrayscale);

        // Assert that a non-null file was created
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(Files.exists(result)).isTrue();
        Assertions.assertThat(Files.size(result)).isGreaterThan(0);
    }

    @Provide
    Arbitrary<Path> tempPdfFile() {
        return Arbitraries.just(createTempPdf());
    }

    private static Path createTempPdf() {
        try {
            Path tempFile = Files.createTempFile("test-pdf", ".pdf");
            Files.write(tempFile, "%PDF-1.4\n%EOF".getBytes()); // very minimal PDF
            return tempFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provide
    Arbitrary<Float> validJpegQualities() {
        return Arbitraries.floats()
            .between(0.1f, 1.0f); // JPEG quality range is 0.0 to 1.0
    }
}
