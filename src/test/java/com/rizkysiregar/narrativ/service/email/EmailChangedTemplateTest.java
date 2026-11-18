package com.rizkysiregar.narrativ.service.email;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renders the real template the way EmailService does (Spring expressions), so a broken expression fails here and not in a mailbox. */
class EmailChangedTemplateTest {

    @Test
    void rendersTheMaskedNewAddressTheTimeAndBothLanguages() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        Context context = new Context();
        Map.of("username", "rizky", "newEmail", "n****w@mail.com", "changedAt", "18 Nov 2026 10:00 WIB")
                .forEach(context::setVariable);

        String html = engine.process("email/email-changed.html", context);

        assertTrue(html.contains("@rizky"));
        assertTrue(html.contains("n****w@mail.com"));
        assertTrue(html.contains("18 Nov 2026 10:00 WIB"));
        assertTrue(html.contains("Not you?") && html.contains("Bukan Anda?"));
        assertFalse(html.contains("th:"));
    }
}
