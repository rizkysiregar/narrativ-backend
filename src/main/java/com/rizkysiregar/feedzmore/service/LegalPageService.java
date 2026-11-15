package com.rizkysiregar.feedzmore.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The public legal pages (privacy policy, terms, account deletion), served as plain HTML so the app can show them
 * in a WebView and the Play Console can link to them. The texts are classpath files, one per language; the only
 * input from a request is which of the known documents and languages to show, so nothing a caller sends ends up
 * in the page.
 */
@Service
@Slf4j
public class LegalPageService {

    public enum Document {
        PRIVACY_POLICY("privacy-policy", "Kebijakan Privasi", "Privacy Policy"),
        TERMS("terms", "Syarat & Ketentuan", "Terms & Conditions"),
        DELETE_ACCOUNT("delete-account", "Penghapusan Akun", "Account Deletion");

        private final String path;
        private final String titleId;
        private final String titleEn;

        Document(String path, String titleId, String titleEn) {
            this.path = path;
            this.titleId = titleId;
            this.titleEn = titleEn;
        }

        public String path() {
            return path;
        }

        String title(Language language) {
            return language == Language.EN ? titleEn : titleId;
        }
    }

    public enum Language {
        ID("id", "Bahasa Indonesia"),
        EN("en", "English");

        private final String code;
        private final String label;

        Language(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() {
            return code;
        }
    }

    /** AUTO follows the reader's system setting; the app is dark only, so it asks for DARK. */
    public enum Theme {
        AUTO, DARK, LIGHT;

        public static Theme parse(String value) {
            if (value != null) {
                for (Theme theme : values()) {
                    if (theme.name().equalsIgnoreCase(value.trim())) {
                        return theme;
                    }
                }
            }
            return AUTO;
        }
    }

    private static final String PLACEHOLDER_CONTACT = "{{contactEmail}}";

    private final String contactEmail;
    private final Map<Document, Map<Language, String>> fragments = new EnumMap<>(Document.class);

    public LegalPageService(@Value("${app.legal.contact-email:}") String contactEmail) {
        this.contactEmail = contactEmail == null ? "" : contactEmail.trim();
    }

    @PostConstruct
    void load() {
        for (Document document : Document.values()) {
            Map<Language, String> byLanguage = new EnumMap<>(Language.class);
            for (Language language : Language.values()) {
                byLanguage.put(language, read("legal/" + document.path + "." + language.code + ".html"));
            }
            fragments.put(document, byLanguage);
        }
        if (contactEmail.isEmpty()) {
            log.warn("LEGAL.CONTACT.EMAIL.MISSING hint=\"set LEGAL_CONTACT_EMAIL, the legal pages show no contact address\"");
        }
    }

    /** The full HTML page of a document in a language. */
    public String render(Document document, Language language) {
        return render(document, language, Theme.AUTO);
    }

    public String render(Document document, Language language, Theme theme) {
        String body = fragments.get(document).get(language)
                .replace(PLACEHOLDER_CONTACT, HtmlUtils.htmlEscape(contactEmail));
        // links between the documents are relative; keep the reader's language and theme when following them
        String query = "?lang=" + language.code + (theme == Theme.AUTO ? "" : "&amp;theme=" + theme.name().toLowerCase(Locale.ROOT));
        for (Document target : Document.values()) {
            body = body.replace("href=\"" + target.path + "\"", "href=\"" + target.path + query + "\"");
        }

        return """
                <!DOCTYPE html>
                <html lang="%s" data-theme="%s">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="dark light">
                <meta name="robots" content="index,follow">
                <title>%s | Feedzmore</title>
                <style>%s</style>
                </head>
                <body>
                <main>
                <nav class="lang">%s</nav>
                %s
                </main>
                </body>
                </html>
                """.formatted(language.code, theme.name().toLowerCase(Locale.ROOT),
                HtmlUtils.htmlEscape(document.title(language)), STYLE, languageSwitch(document, language, theme), body);
    }

    /** The language from the {@code lang} parameter, else from Accept-Language, else Indonesian. */
    public static Language resolveLanguage(String lang, String acceptLanguage) {
        Language requested = parse(lang);
        if (requested != null) {
            return requested;
        }
        if (acceptLanguage != null) {
            for (String part : acceptLanguage.split(",")) {
                Language fromHeader = parse(part.split(";")[0].trim().split("-")[0]);
                if (fromHeader != null) {
                    return fromHeader;
                }
            }
        }
        return Language.ID;
    }

    private static Language parse(String value) {
        if (value == null) {
            return null;
        }
        String code = value.trim().toLowerCase(Locale.ROOT);
        for (Language language : Language.values()) {
            if (language.code.equals(code)) {
                return language;
            }
        }
        return null;
    }

    private String languageSwitch(Document document, Language current, Theme theme) {
        StringBuilder links = new StringBuilder();
        for (Language language : Language.values()) {
            if (links.length() > 0) {
                links.append(" · ");
            }
            if (language == current) {
                links.append("<strong>").append(language.label).append("</strong>");
            } else {
                links.append("<a href=\"").append(document.path).append("?lang=").append(language.code)
                        .append(theme == Theme.AUTO ? "" : "&amp;theme=" + theme.name().toLowerCase(Locale.ROOT)).append("\">")
                        .append(language.label).append("</a>");
            }
        }
        return links.toString();
    }

    private static String read(String location) {
        try (var in = new ClassPathResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing legal page " + location, e);
        }
    }

    // inline on purpose: the pages are served with a Content-Security-Policy that allows no external file
    private static final String STYLE = """
            :root{color-scheme:dark light;--bg:#0d0b14;--fg:#eae6f2;--muted:#9d96b0;--accent:#b79ced;--rule:#2b2639}
            @media (prefers-color-scheme:light){:root{--bg:#ffffff;--fg:#1c1830;--muted:#5d5873;--accent:#6b4fb3;--rule:#e4e0ee}}
            html[data-theme=dark]{--bg:#0d0b14;--fg:#eae6f2;--muted:#9d96b0;--accent:#b79ced;--rule:#2b2639}
            html[data-theme=light]{--bg:#ffffff;--fg:#1c1830;--muted:#5d5873;--accent:#6b4fb3;--rule:#e4e0ee}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.65 -apple-system,Roboto,"Segoe UI",Helvetica,Arial,sans-serif}
            main{max-width:42rem;margin:0 auto;padding:1rem 1.25rem 3rem}
            h1{font-size:1.6rem;line-height:1.25;margin:.75rem 0 .25rem}
            h2{font-size:1.15rem;margin:2rem 0 .5rem;padding-top:.5rem;border-top:1px solid var(--rule)}
            h3{font-size:1rem;margin:1.25rem 0 .25rem}
            p,li{margin:.5rem 0}
            ul,ol{padding-left:1.25rem}
            a{color:var(--accent)}
            .meta{color:var(--muted);font-size:.875rem;margin-top:0}
            .lang{font-size:.875rem;color:var(--muted)}
            .lang strong{color:var(--fg)}
            """;
}
