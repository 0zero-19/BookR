package com.bookr.reader.ui.dialog;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bookr.reader.R;
import com.bookr.reader.data.model.BookItem;
import com.bookr.reader.util.FileUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.Locale;

public class BookDetailsBottomSheet extends BottomSheetDialogFragment {

    public interface OnBookActionListener {
        void onOpenBook(BookItem book);
        void onToggleFavorite(BookItem book);
        void onDeleteBook(BookItem book);
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
        tvAuthor.setText(book.getAuthor());

        StringBuilder metaStr = new StringBuilder();
        if (book.getFileSize() > 0) {
            metaStr.append(FileUtils.formatFileSize(book.getFileSize()));
        }
        if (book.getProgressPercent() > 0) {
            if (metaStr.length() > 0) metaStr.append(" • ");
            metaStr.append(String.format(Locale.getDefault(), "Прочитано %.0f%%", book.getProgressPercent()));
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

        // Action: Delete from library
        view.findViewById(R.id.actionDelete).setOnClickListener(v -> {
            dismiss();
            showDeleteConfirmDialog();
        });
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
