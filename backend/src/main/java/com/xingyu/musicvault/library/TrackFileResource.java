package com.xingyu.musicvault.library;

import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.library.TrackFileDtos.TrackFileResponse;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Path("/api/track-files")
@Produces(MediaType.APPLICATION_JSON)
public class TrackFileResource {
    @GET
    public PageResponse<TrackFileResponse> list(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @QueryParam("ext") String ext,
            @QueryParam("keyword") String keyword
    ) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);
        String normalizedExt = normalizeExt(ext);
        String normalizedKeyword = normalizeKeyword(keyword);

        PanacheQuery<TrackFile> query = buildQuery(normalizedExt, normalizedKeyword);
        long total = query.count();
        List<TrackFileResponse> items = query.page(Page.of(pageValue, sizeValue)).list().stream()
                .map(TrackFileResponse::from)
                .toList();
        return new PageResponse<>(items, pageValue, sizeValue, total);
    }

    @GET
    @Path("/{id}")
    public TrackFileResponse get(@PathParam("id") Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Track file not found");
        }
        return TrackFileResponse.from(trackFile);
    }

    private PanacheQuery<TrackFile> buildQuery(String ext, String keyword) {
        Sort sort = Sort.descending("createdAt");
        if (ext == null && keyword == null) {
            return TrackFile.findAll(sort);
        }
        if (ext != null && keyword == null) {
            return TrackFile.find("fileExt = :ext", sort, Map.of("ext", ext));
        }
        if (ext == null) {
            return TrackFile.find(
                    "lower(fileName) like :keyword or lower(filePath) like :keyword",
                    sort,
                    Map.of("keyword", "%" + keyword + "%")
            );
        }
        return TrackFile.find(
                "fileExt = :ext and (lower(fileName) like :keyword or lower(filePath) like :keyword)",
                sort,
                Map.of("ext", ext, "keyword", "%" + keyword + "%")
        );
    }

    private String normalizeExt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.length() > 16) {
            throw new BadRequestException("ext must be a valid file extension");
        }
        return normalized;
    }

    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new BadRequestException("page must be greater than or equal to 0");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        return size;
    }
}
