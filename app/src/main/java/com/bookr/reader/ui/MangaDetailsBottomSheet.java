package com.bookr.reader.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.MangaChapterItem;
import com.bookr.reader.data.model.MangaItem;
import com.bookr.reader.network.MangaApiClient;
import com.bookr.reader.network.NetworkImageLoader;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MangaDetailsBottomSheet extends BottomSheetDialogFragment {

    public interface OnMangaStateChangedListener {
        void onMangaStateChanged();
    }

    private MangaItem manga;
    private OnMangaStateChangedListener stateListener;
    private MangaChapterAdapter chapterAdapter;
    private List<MangaChapterItem> loadedChapters = new ArrayList<>();
    private String currentLang = "ALL";

    private TextView tvStatus;
    private TextView btnContinue;
    private TextView btnFavorite;
    private TextView btnLangAll, btnLangRu, btnLangEn;

    public static MangaDetailsBottomSheet newInstance(MangaItem manga, OnMangaStateChangedListener listener) {
        MangaDetailsBottomSheet sheet = new MangaDetailsBottomSheet();
        sheet.manga = manga;
        sheet.stateListener = listener;
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_manga_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (manga == null) {
            dismiss();
            return;
        }

        BookRepository repo = new BookRepository(requireContext());
        repo.saveOrUpdateManga(manga);
        MangaItem dbManga = repo.getMangaByExternalId(manga.getMangaId());
        if (dbManga != null) {
            manga = dbManga;
        }

        ImageView ivCover = view.findViewById(R.id.ivSheetMangaCover);
        TextView tvTitle = view.findViewById(R.id.tvSheetMangaTitle);
        TextView tvAuthor = view.findViewById(R.id.tvSheetMangaAuthor);
        TextView tvGenres = view.findViewById(R.id.tvSheetMangaGenres);
        TextView tvDesc = view.findViewById(R.id.tvSheetMangaDesc);
        btnContinue = view.findViewById(R.id.btnSheetContinue);
        btnFavorite = view.findViewById(R.id.btnSheetFavorite);
        btnLangAll = view.findViewById(R.id.btnLangAll);
        btnLangRu = view.findViewById(R.id.btnLangRu);
        btnLangEn = view.findViewById(R.id.btnLangEn);
        TextView btnDownloadAll = view.findViewById(R.id.btnDownloadAllChapters);
        tvStatus = view.findViewById(R.id.tvSheetStatus);
        RecyclerView rvChapters = view.findViewById(R.id.rvMangaChapters);

        tvTitle.setText(manga.getTitle());
        tvAuthor.setText(manga.getAuthor() + " • " + manga.getStatus());
        tvGenres.setText(manga.getGenres());
        tvDesc.setText(manga.getDescription());

        String cover = manga.getLocalCoverPath() != null && !manga.getLocalCoverPath().isEmpty()
                ? manga.getLocalCoverPath() : manga.getCoverUrl();
        NetworkImageLoader.getInstance(requireContext())
                .loadImage(cover, ivCover, manga.getTitle(), manga.getAuthor());

        updateFavoriteBtn();
        updateContinueBtn();

        btnFavorite.setOnClickListener(v -> {
            boolean newFav = !manga.isFavorite();
            manga.setFavorite(newFav);
            repo.setMangaFavorite(manga.getMangaId(), newFav);
            updateFavoriteBtn();
            Toast.makeText(requireContext(),
                    newFav ? "Манга добавлена в закладки/избранное" : "Убрано из избранного",
                    Toast.LENGTH_SHORT).show();
            if (stateListener != null) stateListener.onMangaStateChanged();
        });

        rvChapters.setLayoutManager(new LinearLayoutManager(requireContext()));
        chapterAdapter = new MangaChapterAdapter(new MangaChapterAdapter.OnChapterActionListener() {
            @Override
            public void onReadChapter(MangaChapterItem chapter, int position) {
                openMangaReader(chapter, position, chapter.getReadPage());
            }

            @Override
            public void onDownloadChapter(MangaChapterItem chapter, int position) {
                downloadSingleChapter(chapter, position);
            }
        });
        rvChapters.setAdapter(chapterAdapter);

        btnContinue.setOnClickListener(v -> {
            if (loadedChapters.isEmpty()) return;
            MangaChapterItem target = loadedChapters.get(0);
            int targetIdx = 0;
            int targetPage = 0;
            if (manga.getLastChapterId() != null && !manga.getLastChapterId().isEmpty()) {
                for (int i = 0; i < loadedChapters.size(); i++) {
                    if (loadedChapters.get(i).getChapterId().equals(manga.getLastChapterId())) {
                        target = loadedChapters.get(i);
                        targetIdx = i;
                        targetPage = manga.getLastPage();
                        break;
                    }
                }
            }
            openMangaReader(target, targetIdx, targetPage);
        });

        btnLangAll.setOnClickListener(v -> switchLanguage("ALL"));
        btnLangRu.setOnClickListener(v -> switchLanguage("RU"));
        btnLangEn.setOnClickListener(v -> switchLanguage("EN"));

        btnDownloadAll.setOnClickListener(v -> downloadBatchChapters());

        loadChapters();
    }

    private void switchLanguage(String lang) {
        currentLang = lang;
        btnLangAll.setBackgroundResource("ALL".equals(lang) ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        btnLangRu.setBackgroundResource("RU".equals(lang) ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        btnLangEn.setBackgroundResource("EN".equals(lang) ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        loadChapters();
    }

    private void loadChapters() {
        tvStatus.setText("Загрузка списка глав...");
        MangaApiClient.getInstance().fetchChapters(requireContext(), manga, currentLang, new MangaApiClient.ChaptersCallback() {
            @Override
            public void onSuccess(List<MangaChapterItem> chapters) {
                if (!isAdded()) return;
                loadedChapters = chapters;
                chapterAdapter.setItems(chapters);
                tvStatus.setText("Доступно глав: " + chapters.size() + " • Нажмите на главу для чтения или скачайте офлайн");
                updateContinueBtn();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                tvStatus.setText("Ошибка загрузки глав: " + error);
            }
        });
    }

    private void downloadSingleChapter(MangaChapterItem chapter, int position) {
        tvStatus.setText("Скачивание: " + chapter.getDisplayTitle() + "...");
        MangaApiClient.getInstance().downloadChapterOffline(requireContext(), manga, chapter, false,
                new MangaApiClient.DownloadChapterCallback() {
                    @Override
                    public void onProgress(int currentPage, int totalPages) {
                        if (!isAdded()) return;
                        tvStatus.setText("Скачивание «" + chapter.getDisplayTitle() + "»: стр. " + currentPage + " / " + totalPages);
                    }

                    @Override
                    public void onSuccess(MangaChapterItem updatedChapter) {
                        if (!isAdded()) return;
                        chapterAdapter.notifyItemChanged(position);
                        tvStatus.setText("Глава «" + updatedChapter.getDisplayTitle() + "» сохранена офлайн ✓");
                        if (stateListener != null) stateListener.onMangaStateChanged();
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        tvStatus.setText(error);
                    }
                });
    }

    private void downloadBatchChapters() {
        if (loadedChapters.isEmpty()) return;
        final List<MangaChapterItem> toDownload = new ArrayList<>();
        for (MangaChapterItem ch : loadedChapters) {
            if (!ch.isDownloaded()) {
                toDownload.add(ch);
            }
            if (toDownload.size() >= 5) break; // Download up to 5 chapters per batch to stay fast
        }
        if (toDownload.isEmpty()) {
            Toast.makeText(requireContext(), "Все ближайшие главы уже скачаны офлайн!", Toast.LENGTH_SHORT).show();
            return;
        }
        downloadNextInQueue(toDownload, 0);
    }

    private void downloadNextInQueue(final List<MangaChapterItem> queue, final int idx) {
        if (!isAdded() || idx >= queue.size()) {
            if (isAdded()) {
                tvStatus.setText("Пакетное скачивание глав завершено ✓");
                chapterAdapter.notifyDataSetChanged();
                if (stateListener != null) stateListener.onMangaStateChanged();
            }
            return;
        }
        MangaChapterItem ch = queue.get(idx);
        MangaApiClient.getInstance().downloadChapterOffline(requireContext(), manga, ch, false,
                new MangaApiClient.DownloadChapterCallback() {
                    @Override
                    public void onProgress(int currentPage, int totalPages) {
                        if (!isAdded()) return;
                        tvStatus.setText("Пакет (" + (idx + 1) + "/" + queue.size() + ") "
                                + ch.getDisplayTitle() + ": стр. " + currentPage + "/" + totalPages);
                    }

                    @Override
                    public void onSuccess(MangaChapterItem chapter) {
                        downloadNextInQueue(queue, idx + 1);
                    }

                    @Override
                    public void onError(String error) {
                        downloadNextInQueue(queue, idx + 1);
                    }
                });
    }

    private void openMangaReader(MangaChapterItem chapter, int chapterIdx, int startPage) {
        Intent intent = new Intent(requireContext(), MangaReaderActivity.class);
        intent.putExtra("manga", manga);
        intent.putExtra("chapter", chapter);
        intent.putExtra("chapters_list", (Serializable) new ArrayList<>(loadedChapters));
        intent.putExtra("chapter_index", chapterIdx);
        intent.putExtra("start_page", startPage);
        startActivity(intent);
        dismiss();
    }

    private void updateFavoriteBtn() {
        if (manga.isFavorite()) {
            btnFavorite.setText("★ В закладках");
            btnFavorite.setTextColor(requireContext().getColor(R.color.white));
        } else {
            btnFavorite.setText("☆ В закладки");
            btnFavorite.setTextColor(requireContext().getColor(R.color.text_secondary));
        }
    }

    private void updateContinueBtn() {
        if (manga.getLastChapterTitle() != null && !manga.getLastChapterTitle().isEmpty()) {
            btnContinue.setText("Продолжить: Стр. " + (manga.getLastPage() + 1));
        } else {
            btnContinue.setText("Читать с 1 главы");
        }
    }
}
