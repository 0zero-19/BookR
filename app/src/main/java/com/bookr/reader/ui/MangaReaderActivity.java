package com.bookr.reader.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.MangaBookmarkItem;
import com.bookr.reader.data.model.MangaChapterItem;
import com.bookr.reader.data.model.MangaItem;
import com.bookr.reader.data.model.ReaderSettings;
import com.bookr.reader.network.MangaApiClient;
import com.bookr.reader.network.NetworkImageLoader;

import java.util.ArrayList;
import java.util.List;

public class MangaReaderActivity extends AppCompatActivity {

    public static final int MODE_WEBTOON = 0;
    public static final int MODE_LTR = 1;
    public static final int MODE_RTL = 2;

    public static final int FILTER_NORMAL = 0;
    public static final int FILTER_EYE_CARE = 1;
    public static final int FILTER_CONTRAST = 2;
    public static final int FILTER_INVERT = 3;

    private MangaItem manga;
    private MangaChapterItem currentChapter;
    private List<MangaChapterItem> allChapters = new ArrayList<>();
    private int currentChapterIndex = 0;

    private List<String> pageItems = new ArrayList<>();
    private int currentPageIndex = 0;
    private int readingMode = MODE_WEBTOON;
    private int colorFilterMode = FILTER_NORMAL;
    private boolean dataSaver = false;
    private boolean hudVisible = true;

    private BookRepository repository;
    private ReaderSettings readerSettings;
    private SharedPreferences prefs;

    private RecyclerView rvMangaPages;
    private LinearLayoutManager webtoonLayoutManager;
    private WebtoonPageAdapter webtoonAdapter;

    private FrameLayout flPagedContainer;
    private MangaPageView ivPagedManga;
    private ProgressBar progressManga;
    private LinearLayout topBarManga;
    private LinearLayout bottomBarManga;

    private TextView tvMangaTitle;
    private TextView tvChapterTitle;
    private TextView btnMangaMode;
    private TextView btnMangaFilter;
    private TextView tvPageCounter;
    private TextView tvSourceBadge;
    private SeekBar seekBarManga;
    private TextView btnSaveChapterOffline;

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_reader);

        repository = new BookRepository(this);
        readerSettings = ReaderSettings.load(this);
        prefs = getSharedPreferences("bookr_manga_prefs", MODE_PRIVATE);

        readingMode = prefs.getInt("manga_reading_mode", MODE_WEBTOON);
        colorFilterMode = prefs.getInt("manga_filter_mode", FILTER_NORMAL);
        dataSaver = prefs.getBoolean("manga_data_saver", false);

        manga = (MangaItem) getIntent().getSerializableExtra("manga");
        currentChapter = (MangaChapterItem) getIntent().getSerializableExtra("chapter");
        Object listObj = getIntent().getSerializableExtra("chapters_list");
        if (listObj instanceof List) {
            allChapters = (List<MangaChapterItem>) listObj;
        }
        currentChapterIndex = getIntent().getIntExtra("chapter_index", 0);
        currentPageIndex = Math.max(0, getIntent().getIntExtra("start_page", 0));

        if (manga == null || currentChapter == null) {
            finish();
            return;
        }

        initViews();
        updateModeUI();
        loadChapterPages(currentPageIndex);
    }

    private void initViews() {
        rvMangaPages = findViewById(R.id.rvMangaPages);
        flPagedContainer = findViewById(R.id.flPagedContainer);
        ivPagedManga = findViewById(R.id.ivPagedManga);
        progressManga = findViewById(R.id.progressManga);
        topBarManga = findViewById(R.id.topBarManga);
        bottomBarManga = findViewById(R.id.bottomBarManga);

        tvMangaTitle = findViewById(R.id.tvReaderMangaTitle);
        tvChapterTitle = findViewById(R.id.tvReaderChapterTitle);
        btnMangaMode = findViewById(R.id.btnMangaMode);
        btnMangaFilter = findViewById(R.id.btnMangaFilter);
        tvPageCounter = findViewById(R.id.tvMangaPageCounter);
        tvSourceBadge = findViewById(R.id.tvMangaSourceBadge);
        seekBarManga = findViewById(R.id.seekBarManga);
        btnSaveChapterOffline = findViewById(R.id.btnSaveChapterOffline);

        findViewById(R.id.btnMangaBack).setOnClickListener(v -> finish());

        webtoonLayoutManager = new LinearLayoutManager(this);
        rvMangaPages.setLayoutManager(webtoonLayoutManager);
        webtoonAdapter = new WebtoonPageAdapter();
        rvMangaPages.setAdapter(webtoonAdapter);

        rvMangaPages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (readingMode == MODE_WEBTOON && !pageItems.isEmpty()) {
                    int pos = webtoonLayoutManager.findFirstVisibleItemPosition();
                    if (pos >= 0 && pos < pageItems.size() && pos != currentPageIndex) {
                        currentPageIndex = pos;
                        updatePageIndicator();
                        saveReadingProgress();
                    }
                }
            }
        });

        ivPagedManga.setOnTapZoneListener(new MangaPageView.OnTapZoneListener() {
            @Override
            public void onLeftTap() {
                if (readingMode == MODE_RTL) {
                    nextPage();
                } else {
                    prevPage();
                }
            }

            @Override
            public void onCenterTap() {
                toggleHud();
            }

            @Override
            public void onRightTap() {
                if (readingMode == MODE_RTL) {
                    prevPage();
                } else {
                    nextPage();
                }
            }
        });

        btnMangaMode.setOnClickListener(v -> {
            readingMode = (readingMode + 1) % 3;
            prefs.edit().putInt("manga_reading_mode", readingMode).apply();
            updateModeUI();
            renderCurrentState();
        });

        btnMangaFilter.setOnClickListener(v -> showFilterAndQualityDialog());

        findViewById(R.id.btnMangaBookmark).setOnClickListener(v -> showAddMangaBookmarkDialog());

        findViewById(R.id.btnPrevChapter).setOnClickListener(v -> switchChapter(-1));
        findViewById(R.id.btnNextChapter).setOnClickListener(v -> switchChapter(1));

        btnSaveChapterOffline.setOnClickListener(v -> downloadCurrentChapterOffline());

        seekBarManga.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && progress >= 0 && progress < pageItems.size()) {
                    goToPage(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void loadChapterPages(final int initialPage) {
        tvMangaTitle.setText(manga.getTitle());
        tvChapterTitle.setText(currentChapter.getDisplayTitle());
        progressManga.setVisibility(View.VISIBLE);

        MangaApiClient.getInstance().fetchChapterPages(this, manga, currentChapter, dataSaver,
                new MangaApiClient.PagesCallback() {
                    @Override
                    public void onSuccess(List<String> pageUrlsOrPaths, boolean isOffline) {
                        progressManga.setVisibility(View.GONE);
                        pageItems = pageUrlsOrPaths;
                        currentPageIndex = Math.min(Math.max(0, initialPage), Math.max(0, pageItems.size() - 1));

                        if (isOffline || currentChapter.isDownloaded()) {
                            tvSourceBadge.setText("ОФЛАЙН ✓");
                            btnSaveChapterOffline.setText("Скачано офлайн ✓");
                        } else {
                            tvSourceBadge.setText(dataSaver ? "ОНЛАЙН • DATA SAVER" : "ОНЛАЙН • HD");
                            btnSaveChapterOffline.setText("Скачать главу");
                        }

                        seekBarManga.setMax(Math.max(0, pageItems.size() - 1));
                        renderCurrentState();
                        saveReadingProgress();
                    }

                    @Override
                    public void onError(String error) {
                        progressManga.setVisibility(View.GONE);
                        Toast.makeText(MangaReaderActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateModeUI() {
        if (readingMode == MODE_WEBTOON) {
            btnMangaMode.setText("Вебтун ↕");
            rvMangaPages.setVisibility(View.VISIBLE);
            flPagedContainer.setVisibility(View.GONE);
        } else if (readingMode == MODE_LTR) {
            btnMangaMode.setText("Слева→Направо");
            rvMangaPages.setVisibility(View.GONE);
            flPagedContainer.setVisibility(View.VISIBLE);
        } else {
            btnMangaMode.setText("Японский RTL ←");
            rvMangaPages.setVisibility(View.GONE);
            flPagedContainer.setVisibility(View.VISIBLE);
        }
    }

    private void renderCurrentState() {
        if (pageItems.isEmpty()) return;
        updatePageIndicator();

        if (readingMode == MODE_WEBTOON) {
            webtoonAdapter.notifyDataSetChanged();
            rvMangaPages.scrollToPosition(currentPageIndex);
        } else {
            loadPagedBitmap(currentPageIndex);
        }
    }

    private void loadPagedBitmap(final int pageIdx) {
        if (pageIdx < 0 || pageIdx >= pageItems.size()) return;
        String urlOrPath = pageItems.get(pageIdx);
        applyColorFilterToView(ivPagedManga);
        NetworkImageLoader.getInstance(this).loadMangaPage(
                urlOrPath,
                pageIdx + 1,
                pageItems.size(),
                manga.getTitle(),
                currentChapter.getDisplayTitle(),
                bitmap -> {
                    if (bitmap != null && pageIdx == currentPageIndex) {
                        ivPagedManga.setImageBitmap(bitmap);
                    }
                }
        );

        // Preload next page into cache
        if (pageIdx + 1 < pageItems.size()) {
            NetworkImageLoader.getInstance(this).loadMangaPage(
                    pageItems.get(pageIdx + 1),
                    pageIdx + 2,
                    pageItems.size(),
                    manga.getTitle(),
                    currentChapter.getDisplayTitle(),
                    null
            );
        }
    }

    private void goToPage(int pageIdx) {
        if (pageItems.isEmpty()) return;
        currentPageIndex = Math.max(0, Math.min(pageIdx, pageItems.size() - 1));
        updatePageIndicator();
        saveReadingProgress();

        if (readingMode == MODE_WEBTOON) {
            webtoonLayoutManager.scrollToPositionWithOffset(currentPageIndex, 0);
        } else {
            loadPagedBitmap(currentPageIndex);
        }
    }

    private void nextPage() {
        if (currentPageIndex + 1 < pageItems.size()) {
            goToPage(currentPageIndex + 1);
        } else {
            switchChapter(1);
        }
    }

    private void prevPage() {
        if (currentPageIndex > 0) {
            goToPage(currentPageIndex - 1);
        } else {
            switchChapter(-1);
        }
    }

    private void switchChapter(int delta) {
        if (allChapters == null || allChapters.isEmpty()) {
            Toast.makeText(this, "Других глав в списке нет", Toast.LENGTH_SHORT).show();
            return;
        }
        int nextIdx = currentChapterIndex + delta;
        if (nextIdx < 0) {
            Toast.makeText(this, "Это первая глава", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nextIdx >= allChapters.size()) {
            Toast.makeText(this, "Вы дочитали последнюю доступную главу!", Toast.LENGTH_SHORT).show();
            return;
        }
        currentChapterIndex = nextIdx;
        currentChapter = allChapters.get(currentChapterIndex);
        currentPageIndex = 0;
        loadChapterPages(0);
    }

    private void updatePageIndicator() {
        int total = Math.max(1, pageItems.size());
        tvPageCounter.setText("Стр. " + (currentPageIndex + 1) + " / " + total);
        seekBarManga.setProgress(currentPageIndex);
    }

    private void saveReadingProgress() {
        if (manga == null || currentChapter == null) return;
        boolean completed = !pageItems.isEmpty() && currentPageIndex >= pageItems.size() - 1;
        repository.updateChapterProgress(currentChapter.getChapterId(), currentPageIndex, completed);
        repository.updateMangaProgress(manga.getMangaId(), currentChapter.getChapterId(),
                currentChapter.getDisplayTitle(), currentPageIndex);
    }

    private void toggleHud() {
        hudVisible = !hudVisible;
        topBarManga.setVisibility(hudVisible ? View.VISIBLE : View.GONE);
        bottomBarManga.setVisibility(hudVisible ? View.VISIBLE : View.GONE);
    }

    private void showAddMangaBookmarkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog);
        builder.setTitle("Закладка манги • Стр. " + (currentPageIndex + 1));

        final EditText input = new EditText(this);
        input.setHint("Заметка к странице (необязательно)...");
        input.setTextColor(getColor(R.color.white));
        input.setHintTextColor(getColor(R.color.text_muted));
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);

        builder.setPositiveButton("Сохранить закладку", (d, w) -> {
            String note = input.getText().toString().trim();
            MangaBookmarkItem bm = new MangaBookmarkItem(
                    manga.getMangaId(),
                    manga.getTitle(),
                    currentChapter.getChapterId(),
                    currentChapter.getDisplayTitle(),
                    currentPageIndex,
                    note,
                    manga.getCoverUrl()
            );
            repository.addMangaBookmark(bm);
            Toast.makeText(this, "Закладка сохранена (Стр. " + (currentPageIndex + 1) + ")", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showFilterAndQualityDialog() {
        String[] options = new String[]{
                "Фильтр: Обычный оригинал",
                "Фильтр: Защита глаз (Мягкий тёплый)",
                "Фильтр: Высокий контраст ч/б",
                "Фильтр: Ночная инверсия (AMOLED)",
                dataSaver ? "Экономия трафика (Data Saver): ВКЛ ✓" : "Экономия трафика (Data Saver): ВЫКЛ"
        };
        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle("Настройки экрана и трафика")
                .setItems(options, (d, which) -> {
                    if (which < 4) {
                        colorFilterMode = which;
                        prefs.edit().putInt("manga_filter_mode", colorFilterMode).apply();
                        renderCurrentState();
                    } else {
                        dataSaver = !dataSaver;
                        prefs.edit().putBoolean("manga_data_saver", dataSaver).apply();
                        loadChapterPages(currentPageIndex);
                    }
                })
                .show();
    }

    private void applyColorFilterToView(ImageView iv) {
        if (colorFilterMode == FILTER_NORMAL) {
            iv.clearColorFilter();
        } else if (colorFilterMode == FILTER_EYE_CARE) {
            ColorMatrix cm = new ColorMatrix(new float[]{
                    0.92f, 0, 0, 0, 0,
                    0, 0.88f, 0, 0, 0,
                    0, 0, 0.78f, 0, 0,
                    0, 0, 0, 1, 0
            });
            iv.setColorFilter(new ColorMatrixColorFilter(cm));
        } else if (colorFilterMode == FILTER_CONTRAST) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0f);
            float c = 1.25f;
            float t = -30f;
            ColorMatrix contrast = new ColorMatrix(new float[]{
                    c, 0, 0, 0, t,
                    0, c, 0, 0, t,
                    0, 0, c, 0, t,
                    0, 0, 0, 1, 0
            });
            cm.postConcat(contrast);
            iv.setColorFilter(new ColorMatrixColorFilter(cm));
        } else if (colorFilterMode == FILTER_INVERT) {
            ColorMatrix cm = new ColorMatrix(new float[]{
                    -1f, 0, 0, 0, 255f,
                    0, -1f, 0, 0, 255f,
                    0, 0, -1f, 0, 255f,
                    0, 0, 0, 1f, 0
            });
            iv.setColorFilter(new ColorMatrixColorFilter(cm));
        }
    }

    private void downloadCurrentChapterOffline() {
        btnSaveChapterOffline.setText("Загрузка...");
        MangaApiClient.getInstance().downloadChapterOffline(this, manga, currentChapter, dataSaver,
                new MangaApiClient.DownloadChapterCallback() {
                    @Override
                    public void onProgress(int currentPage, int totalPages) {
                        btnSaveChapterOffline.setText(currentPage + " / " + totalPages);
                    }

                    @Override
                    public void onSuccess(MangaChapterItem chapter) {
                        currentChapter = chapter;
                        tvSourceBadge.setText("ОФЛАЙН ✓");
                        btnSaveChapterOffline.setText("Скачано офлайн ✓");
                        Toast.makeText(MangaReaderActivity.this,
                                "Глава сохранена для чтения без интернета!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        btnSaveChapterOffline.setText("Скачать главу");
                        Toast.makeText(MangaReaderActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (readerSettings.isVolumeKeysPageTurn()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                nextPage();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                prevPage();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private class WebtoonPageAdapter extends RecyclerView.Adapter<WebtoonPageAdapter.PageHolder> {

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(0, 0, 0, 8);
            return new PageHolder(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            String urlOrPath = pageItems.get(position);
            applyColorFilterToView(holder.imageView);
            holder.imageView.setTag(urlOrPath);

            // Set initial placeholder page immediately so scroll height is stable
            Bitmap placeholder = NetworkImageLoader.generateMangaPageBitmap(
                    manga.getTitle(), currentChapter.getDisplayTitle(), position + 1, pageItems.size());
            holder.imageView.setImageBitmap(placeholder);

            NetworkImageLoader.getInstance(MangaReaderActivity.this).loadMangaPage(
                    urlOrPath,
                    position + 1,
                    pageItems.size(),
                    manga.getTitle(),
                    currentChapter.getDisplayTitle(),
                    bitmap -> {
                        if (bitmap != null && urlOrPath.equals(holder.imageView.getTag())) {
                            holder.imageView.setImageBitmap(bitmap);
                        }
                    }
            );

            holder.imageView.setOnClickListener(v -> toggleHud());
        }

        @Override
        public int getItemCount() {
            return pageItems.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;

            PageHolder(@NonNull ImageView itemView) {
                super(itemView);
                this.imageView = itemView;
            }
        }
    }
}
