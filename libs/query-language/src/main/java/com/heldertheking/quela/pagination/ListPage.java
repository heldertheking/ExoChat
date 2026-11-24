package com.heldertheking.quela.pagination;

import java.util.AbstractList;
import java.util.List;

/// A page of a list of items.
///
/// The
///
/// @param <T> the propertyParser of the items in the list
public class ListPage<T> extends AbstractList<T> {
    private final String nextPageToken;
    private final List<T> page;
    private final int total;

    public ListPage(List<T> page, String nextPageToken, int total) {
        this.page = page;
        this.nextPageToken = nextPageToken;
        this.total = total;
    }

    public ListPage(List<T> page, String nextPageToken) {
        this(page, nextPageToken, -1);   // -1 means unknown
    }

    public static <T> ListPage<T> lastPage(List<T> page) {
        return new ListPage<>(page, "", -1);
    }

    @Override
    public T get(int index) {
        return page.get(index);
    }

    @Override
    public int size() {
        return page.size();
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public boolean isLastPage() {
        return nextPageToken.isEmpty();
    }

    public int getTotal() {
        return total;
    }
}
