package dev.quicknote.notes.api;

import java.net.URI;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;

import dev.quicknote.notes.api.dto.LabelRequest;
import dev.quicknote.notes.api.dto.LabelResponse;
import dev.quicknote.notes.api.dto.PageResponse;
import dev.quicknote.notes.application.LabelService;
import dev.quicknote.notes.domain.Label;
import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.security.CurrentUserProvider;
import dev.quicknote.shared.pagination.PageParams;
import dev.quicknote.shared.problem.DomainException;

/** The label endpoints. Labels are permanent when deleted; they do not go to the trash. */
@Path("/v1/labels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@Authenticated
public class LabelResource {

    @Inject
    LabelService labels;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    public PageResponse<LabelResponse> list(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        PageParams params = PageParams.of(page, size);
        Page<Label> result = labels.list(currentUser.id(), params.page(), params.size());
        return PageResponse.from(result, LabelResponse::from, "/v1/labels");
    }

    @POST
    public Response create(@Valid LabelRequest request) {
        Label label = labels.create(currentUser.id(), request.name());
        return Response.created(URI.create("/v1/labels/" + label.id()))
                .entity(LabelResponse.from(label))
                .build();
    }

    @PATCH
    @Path("/{labelId}")
    public LabelResponse rename(@PathParam("labelId") String labelId, @Valid LabelRequest request) {
        return LabelResponse.from(labels.rename(currentUser.id(), labelId(labelId), request.name()));
    }

    @DELETE
    @Path("/{labelId}")
    public Response delete(@PathParam("labelId") String labelId) {
        labels.delete(currentUser.id(), labelId(labelId));
        return Response.noContent().build();
    }

    private static LabelId labelId(String raw) {
        try {
            return LabelId.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            throw new DomainException.MalformedIdentifier("labelId");
        }
    }
}
