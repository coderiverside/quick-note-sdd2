package dev.quicknote.shared.idempotency;

import java.net.URI;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import dev.quicknote.notes.api.dto.NoteResponse;
import dev.quicknote.notes.application.NoteService;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.security.CurrentUserProvider;

/**
 * Makes note creation safe to retry.
 *
 * <p>Creation is the only non-idempotent operation in the contract — every state change is a PATCH
 * attribute write, label attach and detach are PUT and DELETE on a sub-resource, and trashing is a
 * DELETE. So the key applies here and nowhere else.
 */
@Provider
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final String CREATE_PATH = "v1/notes";

    @Inject
    IdempotencyStore store;

    @Inject
    NoteService notes;

    @Inject
    CurrentUserProvider currentUser;

    @Override
    public void filter(ContainerRequestContext request) {
        String key = keyOf(request);
        if (key == null) {
            return;
        }
        Optional<IdempotencyRecord> replay = store.find(currentUser.subject(), key);
        if (replay.isEmpty()) {
            return;
        }
        // A retry returns exactly what the first attempt returned, and creates nothing.
        IdempotencyRecord record = replay.get();
        NoteResponse body = NoteResponse.from(notes.get(currentUser.id(), NoteId.of(record.noteId)));
        request.abortWith(Response.status(record.responseStatus)
                .location(URI.create("/v1/notes/" + record.noteId))
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String key = keyOf(request);
        if (key == null || response.getStatus() != Response.Status.CREATED.getStatusCode()) {
            return;
        }
        if (response.getEntity() instanceof NoteResponse note) {
            store.record(currentUser.subject(), key, note.id(), response.getStatus());
        }
    }

    /** Non-null only for a create request that actually carries a key. */
    private static String keyOf(ContainerRequestContext request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        if (!CREATE_PATH.equals(request.getUriInfo().getPath().replaceAll("^/|/$", ""))) {
            return null;
        }
        String key = request.getHeaderString(HEADER);
        return key == null || key.isBlank() ? null : key.trim();
    }
}
