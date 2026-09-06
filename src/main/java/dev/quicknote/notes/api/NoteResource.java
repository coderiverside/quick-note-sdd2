package dev.quicknote.notes.api;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;

import dev.quicknote.notes.api.dto.CreateNoteRequest;
import dev.quicknote.notes.api.dto.NoteResponse;
import dev.quicknote.notes.api.dto.PageResponse;
import dev.quicknote.notes.api.dto.UpdateNoteRequest;
import dev.quicknote.notes.application.NoteService;
import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.NoteQuery;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.security.CurrentUserProvider;
import dev.quicknote.shared.pagination.PageParams;
import dev.quicknote.shared.problem.DomainException;

/**
 * The notes endpoints.
 *
 * <p>No verb appears in any path: state changes are attribute writes on the note, and permanent
 * deletion lives under {@code /v1/trash}. Handlers run on virtual threads, so ordinary imperative
 * code and {@code @Transactional} semantics hold while still scaling past a worker pool.
 */
@Path("/v1/notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@Authenticated
public class NoteResource {

    @Inject
    NoteService notes;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    public PageResponse<NoteResponse> list(
            @QueryParam("state") String state,
            @QueryParam("labelId") String labelId,
            @QueryParam("q") String search,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {

        PageParams params = PageParams.of(page, size);
        NoteState noteState = NoteService.stateFromFilter(state);
        NoteQuery query = NoteQuery.of(currentUser.id(), noteState, params.page(), params.size());
        if (labelId != null && !labelId.isBlank()) {
            query = query.withLabel(LabelId.of(parseUuid(labelId, "labelId")));
        }
        if (search != null && !search.isBlank()) {
            query = query.withSearch(search);
        }

        Page<Note> result = notes.list(query);
        return PageResponse.from(result, NoteResponse::from, "/v1/notes");
    }

    @POST
    public Response create(@Valid CreateNoteRequest request) {
        Note note = notes.create(
                currentUser.id(),
                request.title(),
                request.body(),
                request.color(),
                Boolean.TRUE.equals(request.pinned()),
                toLabelIds(request.labelIds()));
        return Response.created(URI.create("/v1/notes/" + note.id()))
                .entity(NoteResponse.from(note))
                .build();
    }

    @GET
    @Path("/{noteId}")
    public NoteResponse get(@PathParam("noteId") String noteId) {
        return NoteResponse.from(notes.get(currentUser.id(), noteId(noteId)));
    }

    @PATCH
    @Path("/{noteId}")
    public NoteResponse update(@PathParam("noteId") String noteId, UpdateNoteRequest request) {
        if (request == null || request.isEmpty()) {
            throw new DomainException.MalformedRequest("An update must change at least one property.");
        }
        NoteId id = noteId(noteId);

        Note updated = notes.applyState(currentUser.id(), id, request.pinned(), request.archived(), request.trashed());

        if (request.touchesContent()) {
            updated = notes.updateContent(
                    currentUser.id(), id, toPatchValue(request.title()), toPatchValue(request.body()), request.color());
        }
        return NoteResponse.from(updated);
    }

    @DELETE
    @Path("/{noteId}")
    public Response trash(@PathParam("noteId") String noteId) {
        notes.trash(currentUser.id(), noteId(noteId));
        return Response.noContent().build();
    }

    // --- label attachment: idempotent sub-resource writes, no verbs ---------

    @PUT
    @Path("/{noteId}/labels")
    public NoteResponse replaceLabels(@PathParam("noteId") String noteId, ReplaceLabelsRequest request) {
        Set<LabelId> labels = toLabelIds(request == null ? List.of() : request.labelIds());
        return NoteResponse.from(notes.replaceLabels(currentUser.id(), noteId(noteId), labels));
    }

    @PUT
    @Path("/{noteId}/labels/{labelId}")
    public Response attachLabel(@PathParam("noteId") String noteId, @PathParam("labelId") String labelId) {
        notes.attachLabel(currentUser.id(), noteId(noteId), LabelId.of(parseUuid(labelId, "labelId")));
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{noteId}/labels/{labelId}")
    public Response detachLabel(@PathParam("noteId") String noteId, @PathParam("labelId") String labelId) {
        notes.detachLabel(currentUser.id(), noteId(noteId), LabelId.of(parseUuid(labelId, "labelId")));
        return Response.noContent().build();
    }

    /** Body of {@code PUT /v1/notes/{noteId}/labels}. */
    public record ReplaceLabelsRequest(List<UUID> labelIds) {}

    // --- helpers -----------------------------------------------------------

    /** A patch value: absent leaves the field alone, an explicit JSON null clears it. */
    private static String toPatchValue(com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null) {
            return null; // property absent: leave the field as it is
        }
        if (value.isNull()) {
            return Note.CLEAR; // explicit null: empty the field
        }
        return value.asText();
    }

    private static NoteId noteId(String raw) {
        return NoteId.of(parseUuid(raw, "noteId"));
    }

    /** Malformed identifiers are rejected before any lookup, so they cannot probe for existence. */
    private static UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException.MalformedIdentifier(field);
        }
    }

    private static Set<LabelId> toLabelIds(List<UUID> raw) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream().map(LabelId::of).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
