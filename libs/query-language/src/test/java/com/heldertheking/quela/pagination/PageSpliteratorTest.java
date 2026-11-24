package com.heldertheking.quela.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class PageSpliteratorTest {

    @Test
    void is_empty_if_there_are_no_pages() {
        var spliterator = new PageSpliterator<Integer, List<Integer>>(_ -> Optional.empty());
        assertThat(StreamSupport.stream(spliterator, false)).isEmpty();
    }

    @Test
    void iterates_all_elements_of_a_single_page() {
        var spliterator = new PageSpliterator<Integer, List<Integer>>(
                offset -> offset == 0 ? Optional.of(List.of(1, 2, 3)) : Optional.empty());
        assertThat(StreamSupport.stream(spliterator, false)).containsExactly(1, 2, 3);
    }

    @Test
    void fetches_pages_one_by_one_with_the_number_of_consumed_elements_as_offset() {
        var spliterator = new PageSpliterator<Integer, List<Integer>>(offset -> switch (offset) {
            case 0 -> Optional.of(List.of(1, 2, 3, 4, 5, 6));
            case 6 -> Optional.of(List.of(7, 8));
            case 8 -> Optional.of(List.of(9));
            case 9 -> Optional.empty();
            default -> throw new IllegalStateException("unexpected offset " + offset);
        });
        assertThat(StreamSupport.stream(spliterator, false)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    void does_not_fetch_further_pages_if_the_stream_stops_early() {
        var spliterator = new PageSpliterator<Integer, List<Integer>>(offset -> switch (offset) {
            case 0 -> Optional.of(List.of(1, 2, 3));
            case 3 -> Optional.of(List.of(4, 5, 6));
            default -> throw new IllegalStateException("unexpected offset " + offset);
        });
        assertThat(StreamSupport.stream(spliterator, false).limit(5)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void keeps_fetching_while_a_post_filter_drops_whole_pages() {
        var spliterator = new PageSpliterator<Integer, List<Integer>>(offset -> switch (offset) {
            case 0 -> Optional.of(List.of(1, 3, 5));
            case 3 -> Optional.of(List.of(7, 9, 11));
            case 6 -> Optional.of(List.of(12, 14));
            default -> Optional.empty();
        });
        assertThat(StreamSupport.stream(spliterator, false).filter(i -> i % 2 == 0).limit(2))
                .containsExactly(12, 14);
    }
}
