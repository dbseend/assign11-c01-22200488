package edu.handong.web11.controller;

import edu.handong.web11.domain.Attachment;
import edu.handong.web11.repository.AttachmentRepository;
import edu.handong.web11.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {
    // 미리보기를 허용하는 이미지 MIME 타입 (jpg, jpeg, png, gif). 저장 시 서버가 확장자 기준으로 정한 값이다.
    private static final Set<String> PREVIEWABLE = Set.of("image/jpeg", "image/png", "image/gif");
    private final AttachmentRepository attachments;
    private final FileStorageService storage;

    public AttachmentController(AttachmentRepository attachments, FileStorageService storage) {
        this.attachments = attachments; this.storage = storage;
    }

    /*
     [첨부파일 다운로드]
     - Browser(다운로드 링크 클릭) → Controller(요청 수신) → Repository(파일 메타데이터 조회) → File Storage(실제 파일 읽기) → HTTP Response(파일 데이터 전달)
     - 업로드는 POST + MultipartFile(Client→Server), 다운로드는 GET + Resource(Server→Client)
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {

        // id로 DB에서 파일 메타데이터(원본명, 저장명 등)를 조회, 없으면 404 에러 출력
        Attachment file = attachments.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일이 없습니다."));

        // UUID로 서버에 저장된 실제 파일 위치를 확인 후, 파일 데이터를 Resource로 만든다.
        Resource resource = storage.load(file);

        // Content-Disposition 헤더 설정: attachment는 브라우저가 파일을 열지 않고 다운로드하게 하고,
        // filename에는 디스크의 UUID 이름이 아닌 원본 파일명을 UTF-8로 지정한다.
        String disposition = ContentDisposition.attachment()
            .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
            .build().toString();

        // ResponseEntity로 HTTP 응답을 구성한다.
        // Status 200 OK + Header(Content-Type, Content-Disposition) + Body(파일 데이터)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .body(resource);
    }

    /*
     [이미지 미리보기]
     - 상세 화면의 <img src="/attachments/{id}/view">가 이 경로를 호출한다.
     - 다운로드와 달리 Content-Disposition을 inline으로 지정해, 브라우저가 파일을 저장하지 않고 화면에 바로 표시하게 한다.
     - 오류 처리: 존재하지 않는 id → 404, 이미지가 아닌 파일(PDF 등) → 415, 디스크에 파일이 없음 → 404 (storage.load), 숫자가 아닌 id → 400
     */
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id) {
        // DB에서 메타데이터 조회 (없으면 404)
        Attachment file = attachments.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일이 없습니다."));

        // 이미지(jpg, jpeg, png, gif)인지 확인. PDF/TXT는 미리보기 대상이 아니므로 거부한다.
        if (!PREVIEWABLE.contains(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "이미지 파일만 미리보기할 수 있습니다.");

        // 실제 파일 로드 (디스크에 없으면 404)
        Resource resource = storage.load(file);

        // Content-Type은 DB에 저장된 값(image/jpeg 등)으로 정확히 지정, inline으로 화면 표시.
        //    nosniff: 브라우저가 내용을 추측해 다른 타입으로 해석하지 못하게 한다.
        String disposition = ContentDisposition.inline()
            .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
            .build().toString();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .header("X-Content-Type-Options", "nosniff")
            .body(resource);
    }
}
