package com.rizkysiregar.narrativ.config;

import com.rizkysiregar.narrativ.adviser.CustomResponseAdvise;
import com.rizkysiregar.narrativ.annotation.IgnoreResponseBinding;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

/**
 * Makes the OpenAPI document describe what clients really receive. {@link CustomResponseAdvise} wraps a controller's
 * answer in {@code {code, message, data}} after the method returned, which springdoc cannot see, so every success
 * response is wrapped here the same way. Errors are always a {@code BaseResponse} without data, documented once as
 * the {@code ErrorResponse} schema and attached as the {@code default} response. Clients generate their types from
 * this document, so it has to match the wire format.
 */
@Component
public class ResponseEnvelopeCustomizer implements OperationCustomizer {

    public static final String ERROR_SCHEMA = "ErrorResponse";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!advised(handlerMethod) || operation.getResponses() == null) {
            return operation;
        }
        if (wrapped(handlerMethod)) {
            operation.getResponses().forEach((status, response) -> {
                if (status.startsWith("2") && response.getContent() != null) {
                    response.getContent().values().forEach(media -> media.setSchema(envelope(media.getSchema())));
                }
            });
        }
        operation.getResponses().addApiResponse("default", new ApiResponse()
                .description("Error")
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA)))));
        return operation;
    }

    /** The schema of every error body, registered as a component by {@link SwaggerConfig}. */
    public static Schema<?> errorSchema() {
        return new ObjectSchema()
                .addProperty("code", new StringSchema().description("HTTP-like status code of the message, e.g. 401"))
                .addProperty("message", new StringSchema().description("Message in the language of Accept-Language"))
                .addProperty("errorCode", new StringSchema().description("Stable identifier of the error, e.g. UNAUTHORIZED"))
                .addProperty("args", new ArraySchema().items(new StringSchema())
                        .description("Values filled into the message"))
                .required(List.of("code", "message"));
    }

    // the advice only handles controllers of this package
    private static boolean advised(HandlerMethod handlerMethod) {
        Class<?> controller = handlerMethod.getMethod().getDeclaringClass();
        return controller.isAnnotationPresent(RestController.class)
                && (controller.getPackageName() + ".").startsWith(CustomResponseAdvise.CONTROLLER_PACKAGE + ".");
    }

    // what CustomResponseAdvise passes through unchanged
    private static boolean wrapped(HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(IgnoreResponseBinding.class)) {
            return false;
        }
        Class<?> type = handlerMethod.getMethod().getReturnType();
        return !(ResponseEntity.class.isAssignableFrom(type) || Resource.class.isAssignableFrom(type)
                || type == byte[].class || type == String.class || type == void.class);
    }

    private static Schema<?> envelope(Schema<?> data) {
        Schema<?> envelope = new ObjectSchema()
                .addProperty("code", new StringSchema())
                .addProperty("message", new StringSchema())
                .required(List.of("code", "message"));
        if (data != null) {
            envelope.addProperty("data", data);
        }
        return envelope;
    }
}
