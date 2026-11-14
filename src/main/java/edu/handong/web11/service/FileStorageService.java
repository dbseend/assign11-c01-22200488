package edu.handong.web11.service;

import edu.handong.web11.domain.Attachment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "pdf", "txt");
    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir}") String path) throws IOException {
        uploadDir = Path.of(path).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    /*
     - 업로드된 파일 1개를 검증하고 디스크에 저장한 뒤, DB에 저장할 메타데이터(Attachment)를 반환하는 함수
     - 실제 파일은 디스크에 저장하고, 메타데이터(원본명, 저장명, 타입, 크기)는 DB에 저장하는 구조이다.
     - 이 함수에서는 실제 스토리지에 저장까지 수행되며 DB에 메타데이터 저장은 PostService에서 실행된다.
     */
    public Attachment store(MultipartFile file) {

        // [업로드된 파일 내용이 비어 있는지 검증]
        if (file.isEmpty()) throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");

        // [클라이언트가 보낸 원본 파일명에 검증]
        // 1. Path Traversal: "/", "\\", ".."가 포함되는 경우 거부
        // 2. 파일명 길이 제한: 240 이내
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank() || original.contains("/") || original.contains("\\") || original.contains(".."))
            throw new IllegalArgumentException("잘못된 파일 이름입니다.");
        if (original.length() > 240) throw new IllegalArgumentException("파일 이름이 너무 깁니다.");

        // [허용 파일 형식 검증] 업로드한 파일 형식이 jpg, jpeg, png, gif, pdf, txt 인지 확인
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(ext)) throw new IllegalArgumentException("허용 형식: jpg, jpeg, png, gif, pdf, txt");

        // [파일 크기 제한(10MB) 검증]
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("파일은 10MB 이하만 가능합니다.");

        // [저장 경로 생성] 원본명 대신 UUID로 고유한 저장명을 부여해, 같은 이름의 파일이 업로드되어도 저장 시 충돌(덮어쓰기)하지 않도록 한다.
        String stored = UUID.randomUUID() + "." + ext;

        // resolve()로 업로드 디렉터리 아래 경로를 만들고 normalize()로 정규화 진행,
        // startsWith()로 결과 경로가 업로드 디렉터리 안에 있는지 재확인한다.
        Path target = uploadDir.resolve(stored).normalize();
        if (!target.startsWith(uploadDir)) throw new IllegalArgumentException("잘못된 파일 경로입니다.");

        // [실제 파일 저장] getInputStream()으로 업로드 데이터를 읽어 Files.copy()로 디스크에 저장
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            // 요청 헤더의 Content-Type은 조작될 수 있으므로, 검증된 확장자를 기준으로 서버가 MIME 타입을 직접 결정한다.
            String contentType = switch (ext) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "pdf" -> "application/pdf";
                default -> "text/plain";
            };
            // DB 저장에 필요한 메타데이터(원본명, 저장명, 타입, 크기)를 반환
            return new Attachment(original, stored, contentType, file.getSize());
        } catch (IOException e) {
            // 저장 중 실패하면 생성 중인 불완전한 파일을 삭제한다.
            // 파일은 JPA 트랜잭션이 롤백해 주지 않으므로 직접 정리해야 한다.
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 실패", e);
        }
    }

    public Resource load(Attachment attachment) {
        Path target = uploadDir.resolve(attachment.getStoredFilename()).normalize();
        if (!target.startsWith(uploadDir) || !Files.isRegularFile(target))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 없습니다.");
        return new PathResource(target);
    }

    public void deleteIfExists(Attachment attachment) {
        try {
            Files.deleteIfExists(uploadDir.resolve(attachment.getStoredFilename()).normalize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제 실패", e);
        }
    }
}
