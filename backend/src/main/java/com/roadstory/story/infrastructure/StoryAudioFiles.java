package com.roadstory.story.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstory.story.domain.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads operator-provisioned recordings for the narration supplied by the published Story. */
@Component
public class StoryAudioFiles {
    private static final String CONTENT_TYPE = "audio/mp4";
    private static final int MAX_AUDIO_BYTES = 20 * 1024 * 1024;
    private static final int MAX_METADATA_BYTES = 1024;
    private static final int MAX_DURATION_MS = 3_600_000;
    private static final Pattern STORY_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final Path directory;
    private final Map<Path, FileDigest> digests = new ConcurrentHashMap<>();

    public StoryAudioFiles(@Value("${roadstory.audio.directory:}") String directory) {
        this.directory = directory.isBlank() ? null : Path.of(directory).toAbsolutePath().normalize();
    }

    public Optional<AudioFile> find(Story story) {
        if (directory == null || !STORY_ID.matcher(story.id()).matches() || story.narrationText() == null) {
            return Optional.empty();
        }
        Path file = directory.resolve(story.id() + ".m4a");
        try {
            FileStamp stamp = fileStamp(file);
            if (stamp == null) return Optional.empty();
            int durationMs = readDuration(directory.resolve(story.id() + ".json"), story.narrationText());
            if (durationMs == 0) return Optional.empty();

            String sha256 = fileDigest(file, stamp);
            var descriptor = new AudioDescriptor("/v1/stories/" + story.id() + "/audio", CONTENT_TYPE,
                stamp.size(), sha256, durationMs);
            return Optional.of(new AudioFile(descriptor, file, stamp));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static int readDuration(Path sidecar, String narration) throws IOException {
        if (!Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)) return 0;
        try (InputStream input = Files.newInputStream(sidecar, LinkOption.NOFOLLOW_LINKS)) {
            byte[] metadata = input.readNBytes(MAX_METADATA_BYTES + 1);
            if (metadata.length > MAX_METADATA_BYTES) return 0;
            JsonNode fields = JSON.readTree(metadata);
            if (fields == null || !fields.isObject()) return 0;
            JsonNode duration = fields.path("durationMs");
            JsonNode narrationSha256 = fields.path("narrationSha256");
            if (!duration.isIntegralNumber() || !duration.canConvertToInt() || !narrationSha256.isTextual()) return 0;
            int durationMs = duration.intValue();
            String currentSha256 = HexFormat.of().formatHex(sha256().digest(narration.getBytes(StandardCharsets.UTF_8)));
            return durationMs > 0 && durationMs <= MAX_DURATION_MS && currentSha256.equals(narrationSha256.textValue())
                ? durationMs : 0;
        }
    }

    private String fileDigest(Path file, FileStamp stamp) throws IOException {
        FileDigest cached = digests.get(file);
        if (stamp.fileKey() != null && cached != null && cached.stamp().equals(stamp)) return cached.sha256();

        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int bytes; (bytes = input.read(buffer)) != -1;) {
                total += bytes;
                if (total > stamp.size()) throw new IOException("Story audio changed while reading");
                digest.update(buffer, 0, bytes);
            }
            if (total != stamp.size() || !stamp.equals(fileStamp(file))) {
                throw new IOException("Story audio changed while reading");
            }
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        if (stamp.fileKey() != null) digests.put(file, new FileDigest(stamp, sha256));
        return sha256;
    }

    private static FileStamp fileStamp(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() < 1 || attributes.size() > MAX_AUDIO_BYTES) return null;
        return new FileStamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record AudioDescriptor(String url, String contentType, long byteLength, String sha256, int durationMs) {}

    public static final class AudioFile {
        private final AudioDescriptor descriptor;
        private final Path file;
        private final FileStamp stamp;

        private AudioFile(AudioDescriptor descriptor, Path file, FileStamp stamp) {
            this.descriptor = descriptor;
            this.file = file;
            this.stamp = stamp;
        }

        public AudioDescriptor descriptor() {
            return descriptor;
        }

        public InputStream open() throws IOException {
            if (!stamp.equals(fileStamp(file))) throw new IOException("Story audio changed before download");
            InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
            try {
                if (!stamp.equals(fileStamp(file))) throw new IOException("Story audio changed before download");
                return input;
            } catch (IOException exception) {
                input.close();
                throw exception;
            }
        }
    }

    private record FileStamp(long size, FileTime modifiedAt, Object fileKey) {}
    private record FileDigest(FileStamp stamp, String sha256) {}
}
