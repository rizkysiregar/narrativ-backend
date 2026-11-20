package com.rizkysiregar.narrativ.adviser;

import com.rizkysiregar.narrativ.annotation.IgnoreResponseBinding;
import com.rizkysiregar.narrativ.controller.BaseController;
import com.rizkysiregar.narrativ.model.response.BaseResponse;
import com.rizkysiregar.narrativ.util.BeanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice(basePackages = {CustomResponseAdvise.CONTROLLER_PACKAGE})
@Slf4j
public class CustomResponseAdvise extends BaseController implements ResponseBodyAdvice<Object> {

    // ResponseEnvelopeCustomizer shows the same wrapping in the OpenAPI document, keep the two in step
    public static final String CONTROLLER_PACKAGE = "com.rizkysiregar.narrativ.controller";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }


    /*
    @Override
    public Object beforeBodyWrite(Object object, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (returnType.getContainingClass().isAnnotationPresent(RestController.class)) {
            if (!Objects.requireNonNull(returnType.getMethod()).isAnnotationPresent(IgnoreResponseBinding.class)) {

                if (object instanceof List || object instanceof Map) {
                    log.info("Response is instance of List or Map");
                    return buildSuccessResponse(object);
                } else if (object instanceof ResponseEntity<?>) {
                    log.info("Response is instance of response entity");
                    return object;
                } else if (object instanceof InputStreamResource) {
                    log.info("Response is instance of InputStreamResource");
                    return object;
                } else if (object instanceof byte[]) {
                    log.info("Response is instance of byte[]");
                    return object;
                } else if (object instanceof String) {
                    log.info("Response is instance of String[]");
                    return object;
                } else {
                    BaseResponse<?> baseResponse = BeanMapper.map(object, BaseResponse.class);
                    if (Objects.isNull(baseResponse.getCode()))
                        return buildSuccessResponse(object);
                    return object;
                }
            }
        }
        return object;
    }

     */


    @Override
    public Object beforeBodyWrite(
            Object object,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        if (returnType.getContainingClass().isAnnotationPresent(RestController.class)) {
            if (!Objects.requireNonNull(returnType.getMethod()).isAnnotationPresent(IgnoreResponseBinding.class)) {

                if (object instanceof List || object instanceof Map) {
//                    log.info("Response is instance of List or Map");
                    return buildSuccessResponse(object);
                } else if (object instanceof ResponseEntity<?>) {
//                    log.info("Response is instance of ResponseEntity");
                    return object; // Don't wrap ResponseEntity
                } else if (object instanceof org.springframework.core.io.Resource) {
//                    log.info("Response is instance of Resource");
                    return object; // Don't wrap Resource, needed for image host api
                } else if (object instanceof byte[]) {
//                    log.info("Response is instance of byte[]");
                    return object;
                } else if (object instanceof String) {
//                    log.info("Response is instance of String");
                    return object;
                } else {
                    // Wrap all other responses in BaseResponse
                    BaseResponse<?> baseResponse = BeanMapper.map(object, BaseResponse.class);
                    if (Objects.isNull(baseResponse.getCode())) {
                        return buildSuccessResponse(object);
                    }
                    return object;
                }
            }
        }
        return object;
    }


}
