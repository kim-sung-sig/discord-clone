package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultChannelMessageQueryServiceTest {
    @Test
    void rejectsWhenReadGuardDeniesQueryAccess() {
        ChannelMessageQueryService service = new DefaultChannelMessageQueryService(
            query -> {
                throw new MessageMutationRejectedException("cannot read channel messages");
            },
            new RecordingReadModels(List.of(), null, List.of())
        );

        assertThatThrownBy(() -> service.read(query()))
            .isInstanceOf(MessageMutationRejectedException.class)
            .hasMessage("cannot read channel messages");
    }

    @Test
    void returnsReadModelsWithoutDeletedRows() {
        MessageReadModel visible = readModel("visible", false);
        MessageReadModel deleted = readModel("deleted", true);
        ChannelMessageQueryService service = new DefaultChannelMessageQueryService(
            query -> {
            },
            new RecordingReadModels(List.of(deleted, visible), "cursor-next", List.of())
        );

        MessageReadPage page = service.read(query());

        assertThat(page.messages()).containsExactly(visible);
        assertThat(page.nextCursor()).isEqualTo("cursor-next");
    }

    @Test
    void searchesReadModelsWithGuardAndLimit() {
        MessageReadModel result = readModel("searchable", false);
        RecordingReadModels readModels = new RecordingReadModels(List.of(), null, List.of(result));
        ChannelMessageQueryService service = new DefaultChannelMessageQueryService(
            query -> {
            },
            readModels
        );

        List<MessageReadModel> results = service.search(query(), "searchable", 75);

        assertThat(results).containsExactly(result);
        assertThat(readModels.searchedLimit).isEqualTo(50);
    }

    private static ChannelMessageQuery query() {
        return new ChannelMessageQuery(
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            null,
            50
        );
    }

    private static MessageReadModel readModel(String content, boolean deleted) {
        Instant now = Instant.parse("2026-06-30T00:00:00Z");
        return new MessageReadModel(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            content,
            List.of(),
            false,
            deleted,
            false,
            now,
            now
        );
    }

    private static final class RecordingReadModels implements ChannelMessageReadModelPort {
        private final List<MessageReadModel> page;
        private final String nextCursor;
        private final List<MessageReadModel> search;
        private int searchedLimit;

        private RecordingReadModels(
            List<MessageReadModel> page,
            String nextCursor,
            List<MessageReadModel> search
        ) {
            this.page = page;
            this.nextCursor = nextCursor;
            this.search = search;
        }

        @Override
        public MessageReadPage readModels(ChannelMessageTarget target, String beforeCursor, int limit) {
            return new MessageReadPage(page, nextCursor);
        }

        @Override
        public List<MessageReadModel> searchModels(ChannelMessageTarget target, String query, int limit) {
            searchedLimit = limit;
            return search;
        }
    }
}
