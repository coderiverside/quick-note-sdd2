package dev.quicknote.shared.problem;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import dev.quicknote.shared.correlation.CorrelationContext;

/**
 * Overrides Quarkus REST's built-in Jackson error handling.
 *
 * <p>The built-in mapper answers with its own JSON shape ({@code objectName}, {@code attributeName},
 * …), which names internal classes and is not a problem document. Server exception mappers outrank
 * {@code @Provider} mappers, so this is the only level at which it can be replaced.
 */
public class JsonProblemMappers {

    @ServerExceptionMapper(value = UnrecognizedPropertyException.class, priority = 1)
    public Response unknownProperty(UnrecognizedPropertyException exception, UriInfo uriInfo) {
        return problem("Unknown property '" + exception.getPropertyName() + "'.", uriInfo);
    }

    @ServerExceptionMapper(value = MismatchedInputException.class, priority = 1)
    public Response mismatchedInput(MismatchedInputException exception, UriInfo uriInfo) {
        return problem("The request body is not valid JSON for this operation.", uriInfo);
    }

    private static Response problem(String detail, UriInfo uriInfo) {
        return Response.status(ErrorCode.MALFORMED_REQUEST.status())
                .type(ProblemMappers.PROBLEM_JSON)
                .entity(ProblemDetail.of(
                        ErrorCode.MALFORMED_REQUEST,
                        detail,
                        uriInfo == null ? null : uriInfo.getPath(),
                        CorrelationContext.current()))
                .build();
    }
}
