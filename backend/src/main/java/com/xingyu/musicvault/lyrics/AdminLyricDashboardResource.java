package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.lyrics.LyricDashboardDtos.DailyRecommendationItem;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.DailyRecommendationResponse;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.LyricOverviewResponse;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.RandomRecommendationRequest;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.RandomRecommendationResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin/lyrics")
@Produces(MediaType.APPLICATION_JSON)
public class AdminLyricDashboardResource {
    @Inject
    LyricDashboardService service;

    @GET
    @Path("/overview")
    public LyricOverviewResponse overview() {
        return service.overview();
    }

    @GET
    @Path("/recommendations/daily")
    public DailyRecommendationResponse daily() {
        return service.daily();
    }

    @POST
    @Path("/recommendations/{id}/start")
    public DailyRecommendationItem start(@PathParam("id") Long id) {
        return service.start(id);
    }

    @POST
    @Path("/recommendations/{id}/skip")
    public DailyRecommendationResponse skip(@PathParam("id") Long id) {
        return service.skip(id);
    }

    @POST
    @Path("/recommendations/{id}/replace")
    public DailyRecommendationItem replace(@PathParam("id") Long id) {
        return service.replace(id);
    }

    @POST
    @Path("/recommendations/random")
    @Consumes(MediaType.APPLICATION_JSON)
    public RandomRecommendationResponse random(RandomRecommendationRequest request) {
        return service.random(request == null || request.count() == null ? 5 : request.count());
    }
}
