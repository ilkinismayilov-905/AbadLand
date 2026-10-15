package az.aqroinvest.web;

import az.aqroinvest.domain.Document;
import az.aqroinvest.repo.DocumentRepository;
import az.aqroinvest.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** File download: owner investor or staff only; anyone else → 404. */
@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documents;
    private final StorageService storage;

    @GetMapping("/fayl/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id, Authentication auth) {
        Document d = documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean staff = auth.getAuthorities().stream().anyMatch(a -> !a.getAuthority().equals("ROLE_INVESTOR"));
        String owner = d.getApplication() != null ? d.getApplication().getInvestor().getEmail()
                : d.getReport() != null ? d.getReport().getProject().getInvestor().getEmail() : null;
        if (!staff && (owner == null || !owner.equalsIgnoreCase(auth.getName())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path p = storage.path(d);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, I18n.t("common.file.notFound"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StorageService.contentType(d)))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(d.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(p));
    }
}
