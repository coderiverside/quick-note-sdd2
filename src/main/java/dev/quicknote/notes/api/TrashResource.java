package dev.quicknote.notes.api;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;

import dev.quicknote.notes.api.dto.NoteResponse;
import dev.quicknote.notes.api.dto.PageResponse;
import dev.quicknote.notes.application.TrashService;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.security.CurrentUserProvider;
import dev.quicknote.shared.pagination.PageParams;
import dev.quicknote.shared.problem.DomainException;

/**
 * The trash, modelled as a resource rather than as verbs on a note.
 *
 * <p>That is what lets permanent deletion be a plain DELETE without any ambiguity: {@code DELETE
 * /v1/notes/{id}} moves a note here, {@code DELETE /v1/trash/{id}} destroys it, and restoring is an
 * attribute write on the note itself.
 */
@Path("/v1/trash")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@Authenticated
public class TrashResource {

    @Inject
    TrashService trash;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    public PageResponse<NoteResponse> list(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        PageParams params = PageParams.of(page, size);
        Page<Note> result = trash.list(currentUser.id(), params.page(), params.size());
        return PageResponse.from(result, NoteResponse::from, "/v1/trash");
    }

    @DELETE
    public Response empty() {
        trash.empty(currentUser.id());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{noteId}")
    public Response purge(@PathParam("noteId") String noteId) {
        trash.purge(currentUser.id(), noteId(noteId));
        return Response.noContent().build();
    }

    private static NoteId noteId(String raw) {
        try {
            return NoteId.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            throw new DomainException.MalformedIdentifier("noteId");
        }
    }
}
