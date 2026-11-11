package com.rizkysiregar.feedzmore.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileImageUrlResolverTest {

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ProfileImageUrlResolver resolver;

    @Test
    void blankStaysNull() {
        assertNull(resolver.resolve(null));
        assertNull(resolver.resolve(" "));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void externalUrlIsReturnedAsIs() {
        assertEquals("https://lh3.googleusercontent.com/a/x", resolver.resolve("https://lh3.googleusercontent.com/a/x"));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void objectKeyIsPresigned() throws Exception {
        when(fileStorageService.getPresignUrl("u/PROFILE_PICTURE/a.jpg")).thenReturn("https://signed");

        assertEquals("https://signed", resolver.resolve("u/PROFILE_PICTURE/a.jpg"));
    }

    @Test
    void failedPresignGivesNull() throws Exception {
        when(fileStorageService.getPresignUrl("u/PROFILE_PICTURE/a.jpg")).thenThrow(new IllegalStateException("down"));

        assertNull(resolver.resolve("u/PROFILE_PICTURE/a.jpg"));
    }
}
