package stirling.software.common.service;

import jakarta.servlet.ServletContext;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import stirling.software.SPDF.model.ApiEndpoint;
import stirling.software.SPDF.service.ApiDocService;

import java.util.*;
import java.util.regex.*;

import static org.mockito.Mockito.*;

class ApiDocsServiceTest {

    static ApiDocService service;

    static ServletContext servletContext;
    static UserServiceInterface userService;

    @BeforeContainer
    static void setup() {
        servletContext = mock(ServletContext.class);
        userService = mock(UserServiceInterface.class);

        service = new ApiDocService(servletContext, userService);

        // Stub sample API endpoints
        ApiEndpoint endpoint1 = mock(ApiEndpoint.class);
        when(endpoint1.getDescription()).thenReturn("Input:PDF Output:IMAGE Type:MIConvert");
        when(endpoint1.areParametersValid(any())).thenReturn(true);

        ApiEndpoint endpoint2 = mock(ApiEndpoint.class);
        when(endpoint2.getDescription()).thenReturn("Input:JSON Output:CSV Type:SIConvert");
        when(endpoint2.areParametersValid(any())).thenReturn(false);

        service.getApiDocumentation().put("convertToImage", endpoint1);
    }


    // Property test for getExtensionTypes
    @Property
    void getExtensionTypesShouldMatchOutputToFileTypes(
        @ForAll("operationNames") String operationName,
        @ForAll boolean output
    ) {
        List<String> result = service.getExtensionTypes(output, operationName);

        if (!service.getApiDocumentation().containsKey(operationName)) {
            Assertions.assertThat(result).isNull();
        } else {
            ApiEndpoint endpoint = service.getApiDocumentation().get(operationName);
            String description = endpoint.getDescription();
            Pattern pattern = Pattern.compile(output ? "Output:(\\w+)" : "Input:(\\w+)");
            Matcher matcher = pattern.matcher(description);
            if (matcher.find()) {
                String type = matcher.group(1).toUpperCase();
                if (service.getOutputToFileTypes().containsKey(type)) {
                    Assertions.assertThat(result).isEqualTo(service.getOutputToFileTypes().get(type));
                } else {
                    Assertions.assertThat(result).isNull();
                }
            } else {
                Assertions.assertThat(result).isNull();
            }
        }
    }

    // Property test for isValidOperation
    // Example of shrinking, show then revert simple bug
    @Property
    void isValidOperationShouldRespectDocumentation(
        @ForAll("operationNames") String operationName,
        @ForAll("parameterMaps") Map<String, Object> parameters
    ) {
        boolean result = service.isValidOperation(operationName, parameters);

        if (!service.getApiDocumentation().containsKey(operationName)) {
            Assertions.assertThat(result).isFalse();
        } else {
            ApiEndpoint endpoint = service.getApiDocumentation().get(operationName);
            boolean expected = endpoint.areParametersValid(parameters);
            Assertions.assertThat(result).isEqualTo(expected);
        }
    }

    // Property test for isMultiInput
    @Property
    void isMultiInputShouldReturnTrueIfTypeStartsWithMI(@ForAll("operationNames") String operationName) {
        boolean result = service.isMultiInput(operationName);

        if (!service.getApiDocumentation().containsKey(operationName)) {
            Assertions.assertThat(result).isFalse();
        } else {
            String desc = service.getApiDocumentation().get(operationName).getDescription();
            Matcher matcher = Pattern.compile("Type:(\\w+)").matcher(desc);
            if (matcher.find()) {
                String type = matcher.group(1);
                Assertions.assertThat(result).isEqualTo(type.startsWith("MI"));
            } else {
                Assertions.assertThat(result).isFalse();
            }
        }
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<String> operationNames() {
        List<String> known = List.of("convertToImage", "convertJsonToCsv");
        Arbitrary<String> knownOps = Arbitraries.of(known);
        Arbitrary<String> randomOps = Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(3)
            .ofMaxLength(15);
        return Arbitraries.oneOf(knownOps, randomOps);
    }

    @Provide
    Arbitrary<Map<String, Object>> parameterMaps() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<Object> values = Arbitraries.oneOf(
            Arbitraries.integers().between(0, 100),
            Arbitraries.strings().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false)
        );
        return Arbitraries.maps(keys, values).ofMinSize(0).ofMaxSize(5);
    }
}
