package com.xingyu.musicvault.library;

import com.xingyu.musicvault.library.TrackDtos.TrackCreateRequest;
import com.xingyu.musicvault.library.TrackDtos.TrackResponse;
import com.xingyu.musicvault.library.TrackDtos.TrackUpdateRequest;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Path("/api/tracks")
@Produces(MediaType.APPLICATION_JSON)
public class TrackResource {
    private static final Set<String> ALLOWED_STATUSES = Set.of("pending", "matched", "missing", "ignored");

    @GET
    public List<TrackResponse> list() {
        return Track.<Track>listAll(Sort.descending("createdAt")).stream()
                .map(TrackResponse::from)
                .toList();
    }

    @GET
    @Path("/{id}")
    public TrackResponse get(@PathParam("id") Long id) {
        return TrackResponse.from(findTrack(id));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response create(TrackCreateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        Track track = new Track();
        track.title = requireTitle(request.title());
        track.normalizedTitle = normalizeTitle(track.title);
        track.metadataStatus = resolveStatus(request.metadataStatus(), "metadataStatus");
        track.lyricsStatus = resolveStatus(request.lyricsStatus(), "lyricsStatus");
        track.artworkStatus = resolveStatus(request.artworkStatus(), "artworkStatus");
        track.persist();

        return Response.status(Response.Status.CREATED).entity(TrackResponse.from(track)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public TrackResponse update(@PathParam("id") Long id, TrackUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        Track track = findTrack(id);
        if (request.title() != null) {
            track.title = requireTitle(request.title());
            track.normalizedTitle = normalizeTitle(track.title);
        }
        if (request.metadataStatus() != null) {
            track.metadataStatus = resolveStatus(request.metadataStatus(), "metadataStatus");
        }
        if (request.lyricsStatus() != null) {
            track.lyricsStatus = resolveStatus(request.lyricsStatus(), "lyricsStatus");
        }
        if (request.artworkStatus() != null) {
            track.artworkStatus = resolveStatus(request.artworkStatus(), "artworkStatus");
        }

        return TrackResponse.from(track);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Track track = findTrack(id);
        track.delete();
        return Response.noContent().build();
    }

    private Track findTrack(Long id) {
        Track track = Track.findById(id);
        if (track == null) {
            throw new NotFoundException("Track not found");
        }
        return track;
    }

    private String requireTitle(String value) {
        if (value == null) {
            throw new BadRequestException("title is required");
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("title must not be blank");
        }
        return trimmed;
    }

    private String resolveStatus(String value, String fieldName) {
        if (value == null) {
            return "pending";
        }

        String trimmed = value.trim();
        if (!ALLOWED_STATUSES.contains(trimmed)) {
            throw new BadRequestException(fieldName + " must be one of: pending, matched, missing, ignored");
        }
        return trimmed;
    }

    private String normalizeTitle(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }
}
