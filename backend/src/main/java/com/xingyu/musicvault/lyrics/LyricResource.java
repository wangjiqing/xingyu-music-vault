package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.lyrics.LyricDtos.LyricScanRequest;
import com.xingyu.musicvault.lyrics.LyricDtos.LyricScanResponse;
import com.xingyu.musicvault.lyrics.LyricDtos.SongLyricResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Produces(MediaType.APPLICATION_JSON)
@Path("/api")
public class LyricResource {
    @Inject
    LyricService lyricService;

    @POST
    @Path("/lyrics/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    public LyricScanResponse scan(LyricScanRequest request) {
        String path = request == null ? null : request.path();
        boolean overwritePrimary = request != null && Boolean.TRUE.equals(request.overwritePrimary());
        return lyricService.scan(path, overwritePrimary);
    }

    @GET
    @Path("/songs/{songId}/lyrics")
    public SongLyricResponse getSongLyric(@PathParam("songId") Long songId) {
        return lyricService.getSongLyric(songId);
    }
}
