package com.xingyu.musicvault.library;

import com.xingyu.musicvault.library.TrackFileDtos.TrackFileResponse;
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

@Path("/api/track-files")
@Produces(MediaType.APPLICATION_JSON)
public class TrackFileResource {
    @GET
    public List<TrackFileResponse> list(@QueryParam("ext") String ext) {
        if (ext == null || ext.isBlank()) {
            return TrackFile.<TrackFile>listAll(Sort.descending("createdAt")).stream()
                    .map(TrackFileResponse::from)
                    .toList();
        }

        String normalizedExt = normalizeExt(ext);
        return TrackFile.<TrackFile>list("fileExt", Sort.descending("createdAt"), normalizedExt).stream()
                .map(TrackFileResponse::from)
                .toList();
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

    private String normalizeExt(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.length() > 16) {
            throw new BadRequestException("ext must be a valid file extension");
        }
        return normalized;
    }
}
