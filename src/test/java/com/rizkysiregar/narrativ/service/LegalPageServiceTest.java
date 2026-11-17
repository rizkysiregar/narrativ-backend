package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.service.LegalPageService.Document;
import com.rizkysiregar.narrativ.service.LegalPageService.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalPageServiceTest {

    private LegalPageService service(String email) {
        LegalPageService service = new LegalPageService(email);
        service.load();
        return service;
    }

    @Test
    void everyDocumentExistsInBothLanguagesWithItsContactAddress() {
        LegalPageService service = service("help@narrativ.test");

        for (Document document : Document.values()) {
            for (Language language : Language.values()) {
                String page = service.render(document, language);

                assertTrue(page.contains("<html lang=\"" + language.code() + "\""), document + " " + language);
                assertTrue(page.contains("mailto:help@narrativ.test"), document + " " + language);
                assertFalse(page.contains("{{"), "unreplaced placeholder in " + document + " " + language);
            }
        }
    }

    @Test
    void theContactAddressIsEscaped() {
        String page = service("\"><script>alert(1)</script>").render(Document.TERMS, Language.EN);

        assertFalse(page.contains("<script>"));
    }

    @Test
    void linksBetweenDocumentsKeepTheLanguage() {
        String page = service("a@b.test").render(Document.PRIVACY_POLICY, Language.EN);

        assertTrue(page.contains("href=\"delete-account?lang=en\""));
        // the switch offers the other language, not the current one
        assertTrue(page.contains("href=\"privacy-policy?lang=id\""));
        assertFalse(page.contains("href=\"privacy-policy?lang=en\""));
    }

    @Test
    void theThemeIsForcedOnlyWhenAsked() {
        LegalPageService service = service("a@b.test");

        String dark = service.render(Document.TERMS, Language.ID, LegalPageService.Theme.DARK);

        assertTrue(dark.contains("data-theme=\"dark\""));
        assertTrue(dark.contains("href=\"privacy-policy?lang=id&amp;theme=dark\""));
        assertTrue(service.render(Document.TERMS, Language.ID).contains("data-theme=\"auto\""));
        assertEquals(LegalPageService.Theme.AUTO, LegalPageService.Theme.parse("<script>"));
        assertEquals(LegalPageService.Theme.DARK, LegalPageService.Theme.parse(" Dark "));
    }

    @Test
    void languageComesFromTheParameterThenAcceptLanguageThenIndonesian() {
        assertEquals(Language.EN, LegalPageService.resolveLanguage("en", "id"));
        assertEquals(Language.ID, LegalPageService.resolveLanguage("ID", "en"));
        assertEquals(Language.EN, LegalPageService.resolveLanguage(null, "en-US,en;q=0.9"));
        assertEquals(Language.ID, LegalPageService.resolveLanguage(null, "fr-FR,id;q=0.5"));
        assertEquals(Language.ID, LegalPageService.resolveLanguage("de", "fr"));
        assertEquals(Language.ID, LegalPageService.resolveLanguage(null, null));
    }

    @Test
    void aMissingContactAddressDoesNotBreakTheService() {
        assertTrue(service("").render(Document.TERMS, Language.ID).contains("<h1>"));
    }
}
