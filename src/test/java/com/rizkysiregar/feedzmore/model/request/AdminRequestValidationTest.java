package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.TemplateCategory;
import com.rizkysiregar.feedzmore.model.enums.TemplateMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(Object request) {
        return validator.validate(request).isEmpty();
    }

    @Test
    void parameterKeys_areLowerCaseWordsWithDotsUnderscoresAndDashes() {
        for (String key : new String[]{"maintenance.mode", "feature_x", "min-app-version", "a1b", "x".repeat(100)}) {
            assertTrue(valid(new CreateParameterRequest(key, "v", "d")), key);
        }
        for (String key : new String[]{"", "ab", "Maintenance", "1abc", "has space", "with/slash", "x".repeat(101)}) {
            assertEquals(false, valid(new CreateParameterRequest(key, "v", "d")), key);
        }
    }

    @Test
    void parameterValueAndDescription_areRequiredAndFitTheColumn() {
        assertEquals(false, valid(new CreateParameterRequest("key.one", "", "d")));
        assertEquals(false, valid(new CreateParameterRequest("key.one", "v", " ")));
        assertEquals(false, valid(new CreateParameterRequest("key.one", "v".repeat(256), "d")));
        assertEquals(false, valid(new CreateParameterRequest("key.one", "v", "d".repeat(256))));
        assertTrue(valid(new CreateParameterRequest("key.one", "v".repeat(255), "d".repeat(255))));
        assertEquals(false, valid(new UpdateParameterRequest("", null)));
        assertTrue(valid(new UpdateParameterRequest(null, null)));
    }

    @Test
    void templateRequests_checkCodeTitleBodyAndTopic() {
        assertTrue(valid(new CreateNotificationTemplateRequest("CASUAL_HELLO", "t", "b", TemplateMode.STATIC, TemplateCategory.CASUAL, "global", null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("no", "t", "b", TemplateMode.STATIC, TemplateCategory.CASUAL, null, null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("CASUAL HELLO", "t", "b", TemplateMode.STATIC, TemplateCategory.CASUAL, null, null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("CASUAL_HELLO", "", "b", TemplateMode.STATIC, TemplateCategory.CASUAL, null, null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("CASUAL_HELLO", "t".repeat(101), "b", TemplateMode.STATIC, TemplateCategory.CASUAL, null, null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("CASUAL_HELLO", "t", "b".repeat(256), TemplateMode.STATIC, TemplateCategory.CASUAL, null, null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("CASUAL_HELLO", "t", "b", null, TemplateCategory.CASUAL, null, null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("CASUAL_HELLO", "t", "b", TemplateMode.STATIC, null, null, null)));
        assertEquals(false, valid(new CreateNotificationTemplateRequest("CASUAL_HELLO", "t", "b", TemplateMode.STATIC, TemplateCategory.CASUAL, "bad topic!", null)));
        assertTrue(valid(new UpdateNotificationTemplateRequest(null, null, null, null, null, null)));
        assertEquals(false, valid(new UpdateNotificationTemplateRequest("", null, null, null, null, null)));
    }

    @Test
    void broadcast_needsATitleABodyAndAValidFcmTopic() {
        assertTrue(valid(broadcast("global", "Halo", "Isi")));
        assertTrue(valid(broadcast("news-2026_11.a~b%c", "Halo", "Isi")));
        assertEquals(false, valid(broadcast("", "Halo", "Isi")));
        assertEquals(false, valid(broadcast("/topics/global", "Halo", "Isi")));
        assertEquals(false, valid(broadcast("has space", "Halo", "Isi")));
        assertEquals(false, valid(broadcast("t".repeat(101), "Halo", "Isi")));
        assertEquals(false, valid(broadcast("global", "", "Isi")));
        assertEquals(false, valid(broadcast("global", "T".repeat(101), "Isi")));
        assertEquals(false, valid(broadcast("global", "Halo", "")));
        assertEquals(false, valid(broadcast("global", "Halo", "b".repeat(501))));
        assertEquals(false, valid(broadcast(null, null, null)));
    }

    private BroadcastNotificationRequest broadcast(String topic, String title, String body) {
        BroadcastNotificationRequest request = new BroadcastNotificationRequest();
        request.setTopic(topic);
        request.setTitle(title);
        request.setBody(body);
        return request;
    }
}
