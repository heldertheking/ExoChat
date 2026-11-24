package com.heldertheking.quela.pagination;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/// A [Spliterator] that lazily walks over paginated data, fetching the next page through a [PageFetcher]
/// only when the previous one has been fully consumed.
///
/// The main use case is a list query that needs an in-memory post-filter (a predicate that cannot be expressed
/// in SQL): wrap the jOOQ query in a [PageFetcher], build a stream with
/// [java.util.stream.StreamSupport#stream(Spliterator, boolean)], apply the predicate and `limit(pageSize)`.
/// Pages are then fetched one by one until enough elements survived the filter, and not a single query more.
///
/// ```
///     var pages = new PageSpliterator<Record, Result<Record>>(offset -> {
///         var result = dsl.select().from(USER)
///                 .where(filter.getMergedCondition())
///                 .orderBy(filter.getSortFields())
///                 .offset(offset)
///                 .limit(filter.getPageSize())
///                 .fetch();
///         return result.isEmpty() ? Optional.empty() : Optional.of(result);
///     });
///     var items = StreamSupport.stream(pages, false)
///             .filter(inMemoryPredicate)
///             .limit(filter.getPageSize())
///             .toList();
/// ```
///
/// Not thread safe and not splittable; use it with sequential streams only.
///
/// @param <T> the type of elements returned by the spliterator
/// @param <C> the type of the container that holds a page of elements
public class PageSpliterator<T, C extends Iterable<T>> implements Spliterator<T> {

    private final PageFetcher<T, C> pageFetcher;
    private Spliterator<T> currentPage = Spliterators.emptySpliterator();
    private int offset;

    public PageSpliterator(PageFetcher<T, C> pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        while (true) {
            if (currentPage.tryAdvance(action)) {
                offset++;
                return true;
            }
            var next = pageFetcher.nextPage(offset);
            if (next.isEmpty()) {
                return false;
            }
            currentPage = next.get().spliterator();
        }
    }

    @Override
    public @Nullable Spliterator<T> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
        return NONNULL | ORDERED;
    }

    /// Fetches pages from the underlying data source.
    @FunctionalInterface
    public interface PageFetcher<T, C extends Iterable<T>> {
        /// Fetches the next page.
        ///
        /// @param offset number of elements that were already consumed from previous pages
        /// @return the next page, or an empty optional if there are no more pages. A page must not be empty
        ///         unless it is the last one; an empty page must be signalled with an empty optional.
        Optional<C> nextPage(int offset);
    }
}
