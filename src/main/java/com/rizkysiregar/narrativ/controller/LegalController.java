package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.annotation.IgnoreResponseBinding;
import com.rizkysiregar.narrativ.service.LegalPageService;
import com.rizkysiregar.narrativ.service.LegalPageService.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Public, read-only HTML pages: no login, because the Play Console and a person who has no account yet have to
 * read them. Only these three GET paths are open (see SecurityConfig); there is no wildcard.
 */
@RestController
@RequestMapping("/legal")
@RequiredArgsConstructor
public class LegalController {

    // the pages are static text: no script, no external file, no framing, no form
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    private final LegalPageService legalPageService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Privacy policy (HTML)", description = "Public. lang=id|en (defaults to Accept-Language, then Indonesian), theme=dark|light (defaults to the reader's setting)")
    @GetMapping("/privacy-policy")
    @IgnoreResponseBinding
    public ResponseEntity<String> privacyPolicy(@RequestParam(required = false) String lang,
                                                @RequestParam(required = false) String theme,
                                                @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return page(Document.PRIVACY_POLICY, lang, theme, acceptLanguage);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Terms and conditions (HTML)", description = "Public. lang=id|en (defaults to Accept-Language, then Indonesian), theme=dark|light (defaults to the reader's setting)")
    @GetMapping("/terms")
    @IgnoreResponseBinding
    public ResponseEntity<String> terms(@RequestParam(required = false) String lang,
                                                @RequestParam(required = false) String theme,
                                        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return page(Document.TERMS, lang, theme, acceptLanguage);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "How to delete an account (HTML)", description = "Public. lang=id|en (defaults to Accept-Language, then Indonesian), theme=dark|light (defaults to the reader's setting)")
    @GetMapping("/delete-account")
    @IgnoreResponseBinding
    public ResponseEntity<String> deleteAccount(@RequestParam(required = false) String lang,
                                                @RequestParam(required = false) String theme,
                                                @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return page(Document.DELETE_ACCOUNT, lang, theme, acceptLanguage);
    }

    private ResponseEntity<String> page(Document document, String lang, String theme, String acceptLanguage) {
        var language = LegalPageService.resolveLanguage(lang, acceptLanguage);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                // the page differs per language, so a cache must not serve one to a reader of the other
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                .body(legalPageService.render(document, language, LegalPageService.Theme.parse(theme)));
    }
}
