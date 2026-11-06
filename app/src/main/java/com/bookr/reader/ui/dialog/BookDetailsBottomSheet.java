package com.bookr.reader.ui.dialog;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.BookItem;
import com.bookr.reader.data.model.ShelfItem;
import com.bookr.reader.util.FileUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class BookDetailsBottomSheet extends BottomSheetDialogFragment {

    public interface OnBookActionListener {
        void onOpenBook(BookItem book);
        void onToggleFavorite(BookItem book);
        void onDeleteBook(BookItem book);
        default void onShelvesUpdated(BookItem book) {}
    }

    private static final String ARG_BOOK = "arg_book";
    private BookItem book;
    private OnBookActionListener listener;

    public static BookDetailsBottomSheet newInstance(BookItem book) {
        BookDetailsBottomSheet fragment = new BookDetailsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_BOOK, book);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(OnBookActionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            book = (BookItem) getArguments().getSerializable(ARG_BOOK);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_book_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (book == null) {
            dismiss();
            return;
        }

        ImageView ivCover = view.findViewById(R.id.ivOptionCover);
        ImageView ivPlaceholder = view.findViewById(R.id.ivOptionPlaceholder);
        TextView tvTitle = view.findViewById(R.id.tvOptionTitle);
        TextView tvAuthor = view.findViewById(R.id.tvOptionAuthor);
        TextView tvMeta = view.findViewById(R.id.tvOptionMeta);
        TextView tvAnnotation = view.findViewById(R.id.tvOptionAnnotation);

        tvTitle.setText(book.getTitle());
        String authorText = book.getAuthor();
        if (book.getSeries() != null && !book.getSeries().trim().isEmpty()) {
            String seriesStr = book.getSeries().trim();
            if (book.getSeriesNumber() > 0) {
                seriesStr += " #" + book.getSeriesNumber();
            }
            authorText = authorText + " • " + seriesStr;
        }
        tvAuthor.setText(authorText);

        StringBuilder metaStr = new StringBuilder();
        if (book.getFormat() != null && !book.getFormat().trim().isEmpty()) {
            metaStr.append(book.getFormat().trim().toUpperCase(Locale.ROOT));
        }
        if (book.getFileSize() > 0) {
            if (metaStr.length() > 0) metaStr.append(" • ");
            metaStr.append(FileUtils.formatFileSize(book.getFileSize()));
        }
        if (book.getProgressPercent() > 0) {
            if (metaStr.length() > 0) metaStr.append(" • ");
            metaStr.append(String.format(Locale.getDefault(), "Прочитано %.0f%%", book.getProgressPercent()));
        }
        if (book.getReadingTimeSeconds() >= 60) {
            if (metaStr.length() > 0) metaStr.append(" • ");
            long mins = book.getReadingTimeSeconds() / 60;
            if (mins >= 60) {
                metaStr.append(String.format(Locale.getDefault(), "%d ч %d мин", mins / 60, mins % 60));
            } else {
                metaStr.append(String.format(Locale.getDefault(), "%d мин", mins));
            }
        }
        if (metaStr.length() > 0) {
            tvMeta.setText(metaStr.toString());
            tvMeta.setVisibility(View.VISIBLE);
        } else {
            tvMeta.setVisibility(View.GONE);
        }

        if (book.getAnnotation() != null && !book.getAnnotation().trim().isEmpty()) {
            tvAnnotation.setText(android.text.Html.fromHtml(book.getAnnotation()));
            tvAnnotation.setVisibility(View.VISIBLE);
        } else {
            tvAnnotation.setVisibility(View.GONE);
        }

        if (book.getCoverPath() != null && new File(book.getCoverPath()).exists()) {
            try {
                Bitmap bmp = BitmapFactory.decodeFile(book.getCoverPath());
                if (bmp != null) {
                    ivCover.setImageBitmap(bmp);
                    ivCover.setVisibility(View.VISIBLE);
                    ivPlaceholder.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
            }
        }

        // Action: Read
        view.findViewById(R.id.actionOpen).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onOpenBook(book);
        });

        // Action: Favorite
        ImageView ivFavIcon = view.findViewById(R.id.ivActionFavIcon);
        TextView tvFavText = view.findViewById(R.id.tvActionFavText);
        if (book.isFavorite()) {
            ivFavIcon.setImageResource(R.drawable.ic_favorite);
            tvFavText.setText(R.string.action_remove_fav);
        } else {
            ivFavIcon.setImageResource(R.drawable.ic_favorite_border);
            tvFavText.setText(R.string.action_add_fav);
        }

        view.findViewById(R.id.actionFavorite).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onToggleFavorite(book);
        });

        // Action: Add to Shelf / Collection
        View actionAddToShelf = view.findViewById(R.id.actionAddToShelf);
        if (actionAddToShelf != null) {
            actionAddToShelf.setOnClickListener(v -> showAddToShelfDialog());
        }

        // Action: Delete from library
        view.findViewById(R.id.actionDelete).setOnClickListener(v -> {
            dismiss();
            showDeleteConfirmDialog();
        });
    }

    private void showAddToShelfDialog() {
        if (getContext() == null || book == null) return;
        BookRepository repo = new BookRepository(getContext());
        List<ShelfItem> shelves = repo.getAllShelves();
        if (shelves.isEmpty()) {
            EditText input = new EditText(getContext());
            input.setHint(R.string.shelf_name_hint);
            input.setTextColor(0xFFFFFFFF);
            input.setHintTextColor(0xFF636366);
            input.setPadding(48, 32, 48, 32);

            new AlertDialog.Builder(getContext(), R.style.Theme_BookR_Dialog)
                    .setTitle(R.string.create_shelf)
                    .setView(input)
                    .setPositiveButton(R.string.btn_save, (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            long shelfId = repo.createShelf(name);
                            if (shelfId > 0) {
                                repo.addBookToShelf(shelfId, book.getId());
                            } else {
                                for (ShelfItem s : repo.getAllShelves()) {
                                    if (name.equalsIgnoreCase(s.getName())) {
                                        repo.addBookToShelf(s.getId(), book.getId());
                                        break;
                                    }
                                }
                            }
                            if (listener != null) listener.onShelvesUpdated(book);
                            Toast.makeText(getContext(), "Добавлено на полку «" + name + "»", Toast.LENGTH_SHORT).show();
                            dismiss();
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
            return;
        }

        List<Long> bookShelfIds = repo.getShelfIdsForBook(book.getId());
        CharSequence[] names = new CharSequence[shelves.size()];
        boolean[] checked = new boolean[shelves.size()];
        for (int i = 0; i < shelves.size(); i++) {
            ShelfItem s = shelves.get(i);
            names[i] = s.getName();
            checked[i] = bookShelfIds.contains(s.getId());
        }

        new AlertDialog.Builder(getContext(), R.style.Theme_BookR_Dialog)
                .setTitle(R.string.add_to_shelf)
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    for (int i = 0; i < shelves.size(); i++) {
                        long shelfId = shelves.get(i).getId();
                        if (checked[i]) {
                            repo.addBookToShelf(shelfId, book.getId());
                        } else {
                            repo.removeBookFromShelf(shelfId, book.getId());
                        }
                    }
                    if (listener != null) listener.onShelvesUpdated(book);
                    Toast.makeText(getContext(), "Полки обновлены", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showDeleteConfirmDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext(), R.style.Theme_BookR_Dialog)
                .setTitle(R.string.delete_dialog_title)
                .setMessage(R.string.delete_dialog_msg)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    if (listener != null) listener.onDeleteBook(book);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
