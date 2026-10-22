package com.bookr.reader.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.BookmarkItem;
import com.bookr.reader.ui.adapter.BookmarkAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class BookmarksBottomSheet extends BottomSheetDialogFragment {

    public interface OnBookmarkSelectedListener {
        void onBookmarkSelected(BookmarkItem bookmark);
        void onAddBookmarkClicked();
    }

    private static final String ARG_BOOK_ID = "arg_book_id";
    private long bookId;
    private OnBookmarkSelectedListener listener;
    private BookRepository repository;
    private BookmarkAdapter adapter;
    private RecyclerView rvBookmarks;
    private TextView tvEmpty;

    public static BookmarksBottomSheet newInstance(long bookId) {
        BookmarksBottomSheet fragment = new BookmarksBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_BOOK_ID, bookId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(OnBookmarkSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bookId = getArguments().getLong(ARG_BOOK_ID);
        }
        repository = new BookRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_bookmarks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvBookmarks = view.findViewById(R.id.rvBookmarks);
        tvEmpty = view.findViewById(R.id.tvEmptyBookmarks);

        view.findViewById(R.id.btnAddCurrentBookmark).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onAddBookmarkClicked();
        });

        rvBookmarks.setLayoutManager(new LinearLayoutManager(getContext()));
        loadBookmarks();
    }

    private void loadBookmarks() {
        List<BookmarkItem> bookmarks = repository.getBookmarks(bookId);
        if (bookmarks.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvBookmarks.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvBookmarks.setVisibility(View.VISIBLE);
            adapter = new BookmarkAdapter(requireContext(), bookmarks, new BookmarkAdapter.OnBookmarkClickListener() {
                @Override
                public void onBookmarkClick(BookmarkItem item) {
                    dismiss();
                    if (listener != null) listener.onBookmarkSelected(item);
                }

                @Override
                public void onBookmarkDelete(BookmarkItem item) {
                    repository.deleteBookmark(item.getId());
                    loadBookmarks();
                }
            });
            rvBookmarks.setAdapter(adapter);
        }
    }
}
