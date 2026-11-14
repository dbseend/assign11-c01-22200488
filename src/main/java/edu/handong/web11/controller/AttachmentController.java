package edu.handong.web11.controller;

import edu.handong.web11.domain.Attachment;
import edu.handong.web11.repository.AttachmentRepository;
import edu.handong.web11.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {
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

    // STUDENT TODO: GET /attachments/{id}/view (image preview).
}
