package com.roadstory.user;

import com.roadstory.common.error.ApiException;
import com.roadstory.user.UserLibraryService.UserLibrary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users/me")
public class UserController {
    private final UserLibraryService libraries;

    UserController(UserLibraryService libraries) {
        this.libraries = libraries;
    }

    @GetMapping("/library")
    public UserLibrary getLibrary(Authentication authentication) {
        return libraries.library(accountId(authentication));
    }

    @GetMapping("/preferences")
    public UserPreferences getPreferences(Authentication authentication) {
        return libraries.preferences(accountId(authentication));
    }

    @PutMapping("/preferences")
    public UserPreferences updatePreferences(Authentication authentication, @Valid @RequestBody UserPreferences updated) {
        return libraries.updatePreferences(accountId(authentication), updated);
    }

    @PutMapping("/saved-stories/{storyId}")
    public ResponseEntity<Void> saveStory(Authentication authentication, @PathVariable String storyId) {
        libraries.saveStory(accountId(authentication), storyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/saved-stories/{storyId}")
    public ResponseEntity<Void> unsaveStory(Authentication authentication, @PathVariable String storyId) {
        libraries.removeStory(accountId(authentication), storyId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/saved-courses/{courseId}")
    public ResponseEntity<Void> saveCourse(Authentication authentication, @PathVariable String courseId) {
        libraries.saveCourse(accountId(authentication), courseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/saved-courses/{courseId}")
    public ResponseEntity<Void> unsaveCourse(Authentication authentication, @PathVariable String courseId) {
        libraries.removeCourse(accountId(authentication), courseId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/listening-history/{storyId}")
    public ResponseEntity<Void> recordListening(Authentication authentication, @PathVariable String storyId,
                                                @Valid @RequestBody ListeningRequest request) {
        String accountId = accountId(authentication);
        Instant playedAt;
        try {
            playedAt = Instant.parse(request.playedAt());
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "청취 시각이 올바르지 않습니다.");
        }
        libraries.recordListening(accountId, storyId, playedAt);
        return ResponseEntity.noContent().build();
    }

    private static String accountId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        return authentication.getName();
    }

    public record ListeningRequest(@NotBlank String playedAt) {}
}
