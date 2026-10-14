package az.aqroinvest.service;

import az.aqroinvest.domain.Application;
import az.aqroinvest.domain.DocType;
import az.aqroinvest.domain.Document;
import az.aqroinvest.repo.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static az.aqroinvest.service.AuditService.BAKU;

/** Uploads: PDF/JPG/PNG only (extension + magic bytes), ≤ 5 MB, stored under app.upload-dir with a UUID name. */
@Service
@RequiredArgsConstructor
public class StorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final DocumentRepository documents;

    @Value("${app.upload-dir}")
    private String uploadDir;

    /** @return Azerbaijani error or null if the file is acceptable. Empty file → null (caller decides if required). */
    public String check(MultipartFile f) {
        if (f == null || f.isEmpty()) return null;
        if (f.getSize() > MAX_BYTES) return "Fayl 5 MB-dan böyük ola bilməz";
        String ext = extension(f.getOriginalFilename());
        if (!Arrays.asList("pdf", "jpg", "jpeg", "png").contains(ext)) return "Yalnız PDF, JPG və ya PNG fayl yükləyin";
        byte[] head = new byte[8];
        try (InputStream in = f.getInputStream()) {
            int n = in.readNBytes(head, 0, 8);
            boolean ok = switch (ext) {
                case "pdf" -> n >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
                case "png" -> n >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
                default -> n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            };
            return ok ? null : "Faylın məzmunu " + ext.toUpperCase() + " formatına uyğun deyil";
        } catch (IOException e) {
            return "Faylı oxumaq mümkün olmadı";
        }
    }

    public Document store(MultipartFile f, DocType type, Application application) {
        return store(f, type, application, null);
    }

    public Document store(MultipartFile f, DocType type, Application application, az.aqroinvest.domain.ProgressReport report) {
        String error = check(f);
        if (error != null) throw new BusinessException(error);
        String stored = UUID.randomUUID() + "." + extension(f.getOriginalFilename());
        try {
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            f.transferTo(dir.resolve(stored).toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException("Faylı yadda saxlamaq mümkün olmadı");
        }
        Document d = new Document();
        d.setApplication(application);
        d.setReport(report);
        d.setType(type);
        d.setOriginalName(safeName(f.getOriginalFilename()));
        d.setStoredName(stored);
        d.setSize(f.getSize());
        d.setUploadedAt(LocalDateTime.now(BAKU));
        return documents.save(d);
    }

    /** Path of a stored file, or null if missing (seed rows have no file). */
    public Path path(Document d) {
        Path p = Path.of(uploadDir).resolve(d.getStoredName()).normalize();
        return p.startsWith(Path.of(uploadDir).normalize()) && Files.isRegularFile(p) ? p : null;
    }

    public static String contentType(Document d) {
        return switch (extension(d.getStoredName())) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    static String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeName(String name) {
        String n = name == null ? "fayl" : Path.of(name).getFileName().toString();
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }
}
