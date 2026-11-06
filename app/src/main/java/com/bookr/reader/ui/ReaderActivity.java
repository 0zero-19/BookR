package com.bookr.reader.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.BookItem;
import com.bookr.reader.data.model.BookmarkItem;
import com.bookr.reader.data.model.NoteItem;
import com.bookr.reader.data.model.QuoteItem;
import com.bookr.reader.data.model.ReaderSettings;
import com.bookr.reader.data.model.ThemeConfig;
import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.parser.BookFormat;
import com.bookr.reader.parser.BookFormatDetector;
import com.bookr.reader.parser.BookParserManager;
import com.bookr.reader.parser.Fb2Book;
import com.bookr.reader.ui.adapter.PdfPageAdapter;
import com.bookr.reader.ui.dialog.BookmarksBottomSheet;
import com.bookr.reader.ui.dialog.FootnoteDialog;
import com.bookr.reader.ui.dialog.ReaderSettingsBottomSheet;
import com.bookr.reader.ui.dialog.TocBottomSheet;
import com.bookr.reader.util.FileUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends AppCompatActivity implements
        ReaderSettingsBottomSheet.OnSettingsChangeListener,
        TocBottomSheet.OnTocItemSelectedListener,
        BookmarksBottomSheet.OnBookmarkSelectedListener {

    private static final String TAG = "BookR.Reader";

    public static final String EXTRA_BOOK_ID = "extra_book_id";
    public static final String EXTRA_BOOK_URI = "extra_book_uri";
    public static final String EXTRA_BOOK_TITLE = "extra_book_title";
    public static final String EXTRA_TARGET_PERCENT = "extra_target_percent";
    public static final String EXTRA_TARGET_PAGE = "extra_target_page";

    private long bookId;
    private String bookUriString;
    private String bookTitle;
    private float targetPercent = -1f;
    private int targetPage = -1;
    private int activeSleepTimerMinutes = 0;

    private BookRepository repository;
    private ReaderSettings settings;
    private BookItem currentBook;
    private Fb2Book parsedBook;
    private BookFormat currentFormat = BookFormat.FB2;

    private ThemeBackgroundView readerBgView;
    private WebView wvReader;
    private RecyclerView rvPdfPages;
    private PdfPageAdapter pdfAdapter;
    private ParcelFileDescriptor pdfPfd;
    private LinearLayoutManager pdfLayoutManager;
    private PagerSnapHelper pdfSnapHelper;

    private int currentPageIndex = 0;
    private int totalPagesCount = 1;

    private View readerRoot;
    private LinearLayout topReaderBar;
    private LinearLayout bottomReaderBar;
    private LinearLayout searchOverlayBar;
    private LinearLayout loadingLayout;

    private TextView tvReaderBookTitle;
    private TextView tvReaderChapterTitle;
    private ImageView ivBookmarkIcon;
    private SeekBar seekProgress;
    private TextView tvProgressLeft;
    private TextView tvProgressRight;

    private EditText etReaderSearch;
    private TextView tvSearchMatches;
    private FrameLayout btnSearchPrev;
    private FrameLayout btnSearchNext;

    private boolean isControlsVisible = true;
    private boolean isBookLoaded = false;
    private float currentProgressPercent = 0f;
    private int currentScrollPosition = 0;

    private long sessionStartTime = 0;
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_reader);

        bookId = getIntent().getLongExtra(EXTRA_BOOK_ID, -1);
        bookUriString = getIntent().getStringExtra(EXTRA_BOOK_URI);
        if (bookUriString == null && getIntent().getData() != null) {
            bookUriString = getIntent().getData().toString();
        }
        bookTitle = getIntent().getStringExtra(EXTRA_BOOK_TITLE);
        targetPercent = getIntent().getFloatExtra(EXTRA_TARGET_PERCENT, -1f);
        targetPage = getIntent().getIntExtra(EXTRA_TARGET_PAGE, -1);

        repository = new BookRepository(this);
        settings = ReaderSettings.load(this);

        if (bookId > 0) {
            currentBook = repository.getBookById(bookId);
            if (currentBook != null) {
                if (currentBook.getTitle() != null) {
                    bookTitle = currentBook.getTitle();
                }
                currentProgressPercent = currentBook.getProgressPercent();
                currentScrollPosition = currentBook.getCurrentPosition();
            }
        }

        applyBrightness(settings.getBrightness());
        applyOrientation();
        applyFullscreen();

        initViews();
        setupWebView();
        setupListeners();

        sessionStartTime = System.currentTimeMillis();
        loadBookContent();
    }

    private void initViews() {
        readerRoot = findViewById(R.id.readerRoot);
        readerBgView = findViewById(R.id.readerBgView);
        wvReader = findViewById(R.id.wvReader);
        rvPdfPages = findViewById(R.id.rvPdfPages);
        topReaderBar = findViewById(R.id.topReaderBar);
        bottomReaderBar = findViewById(R.id.bottomReaderBar);
        searchOverlayBar = findViewById(R.id.searchOverlayBar);
        loadingLayout = findViewById(R.id.loadingLayout);

        tvReaderBookTitle = findViewById(R.id.tvReaderBookTitle);
        tvReaderChapterTitle = findViewById(R.id.tvReaderChapterTitle);
        ivBookmarkIcon = findViewById(R.id.ivBookmarkIcon);
        seekProgress = findViewById(R.id.seekProgress);
        tvProgressLeft = findViewById(R.id.tvProgressLeft);
        tvProgressRight = findViewById(R.id.tvProgressRight);

        etReaderSearch = findViewById(R.id.etReaderSearch);
        tvSearchMatches = findViewById(R.id.tvSearchMatches);
        btnSearchPrev = findViewById(R.id.btnSearchPrev);
        btnSearchNext = findViewById(R.id.btnSearchNext);

        if (bookTitle != null) {
            tvReaderBookTitle.setText(bookTitle);
        }

        applyThemeColors();
    }

    private void applyThemeColors() {
        ThemeConfig tc = settings.getActiveTheme();
        if (readerBgView != null) {
            readerBgView.setConfig(tc, true);
        }
        if (readerRoot != null) readerRoot.setBackgroundColor(Color.TRANSPARENT);
        if (wvReader != null) wvReader.setBackgroundColor(Color.TRANSPARENT);
        if (rvPdfPages != null) rvPdfPages.setBackgroundColor(Color.TRANSPARENT);

        int alpha = (int) (tc.getReaderBarsAlpha() * 2.55f);
        if (topReaderBar != null && topReaderBar.getBackground() != null) {
            topReaderBar.getBackground().setAlpha(alpha);
        }
        if (bottomReaderBar != null && bottomReaderBar.getBackground() != null) {
            bottomReaderBar.getBackground().setAlpha(alpha);
        }
    }

    private void applyOrientation() {
        int orientation = settings.getOrientationMode();
        switch (orientation) {
            case ReaderSettings.ORIENTATION_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case ReaderSettings.ORIENTATION_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case ReaderSettings.ORIENTATION_AUTO:
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
        }
    }

    private void applyFullscreen() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller == null) return;
        if (settings.isFullscreenMode()) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void setupWebView() {
        WebSettings webSettings = wvReader.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);

        applyThemeColors();
        wvReader.addJavascriptInterface(new BookRBridge(), "BookRBridge");

        wvReader.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isBookLoaded = true;
                loadingLayout.setVisibility(View.GONE);
                updateBookmarkIconState();

                // Restore target bookmark or saved reading position
                if (targetPercent >= 0f) {
                    final float pctToRestore = targetPercent;
                    targetPercent = -1f;
                    mainHandler.postDelayed(() -> {
                        wvReader.evaluateJavascript("scrollToPercent(" + pctToRestore + ");", null);
                    }, 250);
                } else if (bookId > 0) {
                    BookItem item = repository.getBookById(bookId);
                    if (item != null && item.getProgressPercent() > 0) {
                        mainHandler.postDelayed(() -> {
                            wvReader.evaluateJavascript("scrollToPercent(" + item.getProgressPercent() + ");", null);
                        }, 250);
                    }
                }
            }
        });

        // Search listener
        wvReader.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (numberOfMatches > 0) {
                tvSearchMatches.setText(String.format(Locale.getDefault(), "%d из %d", activeMatchOrdinal + 1, numberOfMatches));
            } else {
                tvSearchMatches.setText("0/0");
            }
        });
    }

    private void handleSelectedTextAction(int actionId, String selectedText) {
        String bookUuid = currentBook != null ? currentBook.getUuid() : String.valueOf(bookId);
        String title = bookTitle != null ? bookTitle : "Без названия";
        String author = currentBook != null ? currentBook.getAuthor() : "";
        String chapter = tvReaderChapterTitle.getText().toString();

        switch (actionId) {
            case 103: // В закладки
                BookmarkItem bm = new BookmarkItem();
                bm.setBookUuid(bookUuid);
                bm.setBookTitle(title);
                bm.setBookAuthor(author);
                bm.setChapterTitle(chapter);
                bm.setPositionStr(String.format(Locale.US, "%.1f", currentProgressPercent));
                bm.setPageNumber(currentPageIndex);
                bm.setSnippet(selectedText);
                bm.setBookmarkType(BookmarkItem.TYPE_QUOTE);
                bm.setCreatedAt(System.currentTimeMillis());
                repository.addBookmark(bm);
                updateBookmarkIconState();
                Toast.makeText(this, "Цитата добавлена в закладки", Toast.LENGTH_SHORT).show();
                break;

            case 101: // Цитата в отдельную коллекцию
                QuoteItem quote = new QuoteItem();
                quote.setBookUuid(bookUuid);
                quote.setBookTitle(title);
                quote.setBookAuthor(author);
                quote.setChapterTitle(chapter);
                quote.setQuoteText(selectedText);
                quote.setColorLabel("#FFFFFF");
                quote.setCreatedAt(System.currentTimeMillis());
                repository.addQuote(quote);
                Toast.makeText(this, "Цитата сохранена в коллекции", Toast.LENGTH_SHORT).show();
                break;

            case 102: // Создать заметку к фрагменту
                showAddNoteDialog(bookUuid, title, chapter, selectedText);
                break;

            case 104: // Поделиться
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                String shareBody = "«" + selectedText + "»\n\n— " + title + (author.isEmpty() ? "" : " (" + author + ")");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                startActivity(Intent.createChooser(shareIntent, "Поделиться цитатой"));
                break;
        }
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        Menu menu = mode.getMenu();
        if (menu != null) {
            MenuItem.OnMenuItemClickListener itemClickListener = item -> {
                int id = item.getItemId();
                wvReader.evaluateJavascript("(function() { return window.getSelection().toString(); })()", value -> {
                    String cleanText = unescapeJsString(value);
                    if (!cleanText.isEmpty()) {
                        mainHandler.post(() -> handleSelectedTextAction(id, cleanText));
                    }
                });
                mode.finish();
                return true;
            };

            MenuItem bm = menu.add(0, 103, 0, "В закладки");
            MenuItem quote = menu.add(0, 101, 1, "Цитата");
            MenuItem note = menu.add(0, 102, 2, "Заметка");
            MenuItem share = menu.add(0, 104, 3, "Поделиться");

            bm.setOnMenuItemClickListener(itemClickListener);
            quote.setOnMenuItemClickListener(itemClickListener);
            note.setOnMenuItemClickListener(itemClickListener);
            share.setOnMenuItemClickListener(itemClickListener);
        }
    }

    private void showAddNoteDialog(String bookUuid, String bookTitle, String chapter, String selectedText) {
        EditText etNote = new EditText(this);
        etNote.setHint("Введите вашу заметку...");
        etNote.setTextColor(Color.WHITE);
        etNote.setHintTextColor(Color.parseColor("#8E8E93"));
        etNote.setBackgroundResource(R.drawable.bg_glass_input);
        etNote.setPadding(24, 24, 24, 24);
        etNote.setMinLines(3);

        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle("Заметка к тексту")
                .setMessage("Фрагмент: «" + (selectedText.length() > 60 ? selectedText.substring(0, 57) + "..." : selectedText) + "»")
                .setView(etNote)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String noteText = etNote.getText().toString().trim();
                    if (!noteText.isEmpty()) {
                        NoteItem note = new NoteItem();
                        note.setBookUuid(bookUuid);
                        note.setBookTitle(bookTitle);
                        note.setChapterTitle(chapter);
                        note.setPositionStr(String.format(Locale.US, "%.1f", currentProgressPercent));
                        note.setPageNumber(currentPageIndex);
                        note.setSelectedText(selectedText);
                        note.setNoteText(noteText);
                        note.setCreatedAt(System.currentTimeMillis());
                        note.setUpdatedAt(System.currentTimeMillis());
                        repository.addNote(note);
                        Toast.makeText(this, "Заметка сохранена", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private String unescapeJsString(String rawValue) {
        if (rawValue == null || "null".equals(rawValue)) return "";
        try {
            Object parsed = new org.json.JSONTokener(rawValue).nextValue();
            return parsed != null ? parsed.toString().trim() : "";
        } catch (Exception e) {
            String s = rawValue.trim();
            if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length() - 1);
            }
            return s.replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();
        }
    }

    private void updateBookmarkIconState() {
        if (ivBookmarkIcon == null || bookId <= 0) return;
        List<BookmarkItem> bms = repository.getBookmarks(bookId);
        ivBookmarkIcon.setImageResource((bms != null && !bms.isEmpty())
                ? R.drawable.ic_bookmark
                : R.drawable.ic_bookmark_border);
    }

    private void setupListeners() {
        // Back
        findViewById(R.id.btnReaderBack).setOnClickListener(v -> finish());

        // Table of Contents
        findViewById(R.id.btnReaderToc).setOnClickListener(v -> {
            if (parsedBook != null && parsedBook.getToc() != null && !parsedBook.getToc().isEmpty()) {
                TocBottomSheet sheet = TocBottomSheet.newInstance(parsedBook.getToc());
                sheet.setListener(this);
                sheet.show(getSupportFragmentManager(), "reader_toc");
            } else {
                Toast.makeText(this, R.string.no_toc, Toast.LENGTH_SHORT).show();
            }
        });

        // Bookmarks
        findViewById(R.id.btnReaderBookmark).setOnClickListener(v -> {
            BookmarksBottomSheet sheet = BookmarksBottomSheet.newInstance(bookId);
            sheet.setListener(this);
            sheet.show(getSupportFragmentManager(), "reader_bookmarks");
        });

        // Settings
        findViewById(R.id.btnReaderSettings).setOnClickListener(v -> {
            ReaderSettingsBottomSheet sheet = ReaderSettingsBottomSheet.newInstance();
            sheet.setSelectedSleepMinutes(activeSleepTimerMinutes);
            sheet.setListener(this);
            sheet.show(getSupportFragmentManager(), "reader_settings");
        });

        // Search
        findViewById(R.id.btnReaderSearch).setOnClickListener(v -> {
            searchOverlayBar.setVisibility(View.VISIBLE);
            etReaderSearch.requestFocus();
        });

        findViewById(R.id.btnCloseSearch).setOnClickListener(v -> {
            searchOverlayBar.setVisibility(View.GONE);
            wvReader.clearMatches();
        });

        etReaderSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s != null ? s.toString().trim() : "";
                if (!query.isEmpty()) {
                    wvReader.findAllAsync(query);
                } else {
                    wvReader.clearMatches();
                    tvSearchMatches.setText("0/0");
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnSearchPrev.setOnClickListener(v -> wvReader.findNext(false));
        btnSearchNext.setOnClickListener(v -> wvReader.findNext(true));

        // Customization
        View btnCust = findViewById(R.id.btnReaderCustomization);
        if (btnCust != null) {
            btnCust.setOnClickListener(v -> openCustomizationScreen());
        }

        // Seekbar scrubbing
        seekProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (currentFormat == BookFormat.PDF && pdfAdapter != null) {
                        int total = pdfAdapter.getItemCount();
                        int page = Math.min(total - 1, progress);
                        rvPdfPages.scrollToPosition(page);
                        float pct = total > 1 ? (float) page / (total - 1) * 100f : 0f;
                        tvProgressLeft.setText(String.format(Locale.getDefault(), "%.0f%%", pct));
                        tvProgressRight.setText(String.format(Locale.getDefault(), "Стр. %d из %d", page + 1, total));
                    } else {
                        if (settings.isPagedMode()) {
                            wvReader.evaluateJavascript("goToPage(" + progress + ", false);", null);
                        } else {
                            float percent = progress / 10f;
                            tvProgressLeft.setText(String.format(Locale.getDefault(), "%.0f%%", percent));
                            wvReader.evaluateJavascript("scrollToPercent(" + percent + ");", null);
                        }
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void loadBookContent() {
        if (bookUriString == null) {
            Toast.makeText(this, R.string.error_open_book, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadingLayout.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                Uri uri = Uri.parse(bookUriString);
                currentFormat = BookFormatDetector.detectFormat(this, uri);
                Log.d(TAG, "Opening book format: " + currentFormat + " for URI: " + uri);

                if (currentFormat == BookFormat.PDF) {
                    loadPdf(uri);
                } else {
                    loadTextBasedBook(uri);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error opening book directly, attempting local cache fallback", e);
                // Attempt local cached copy fallback if SAF permission was revoked
                try {
                    Uri uri = Uri.parse(bookUriString);
                    File cached = FileUtils.copyToLocalBookCache(this, uri, "book_recovery");
                    Uri cachedUri = Uri.fromFile(cached);
                    currentFormat = BookFormatDetector.detectFormat(this, cachedUri);
                    if (currentFormat == BookFormat.PDF) {
                        loadPdf(cachedUri);
                    } else {
                        loadTextBasedBook(cachedUri);
                    }
                } catch (Exception ex2) {
                    Log.e(TAG, "Recovery fallback also failed", ex2);
                    mainHandler.post(() -> {
                        loadingLayout.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.error_open_book, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }
        });
    }

    private void loadTextBasedBook(Uri uri) throws Exception {
        parsedBook = BookParserManager.parseBook(this, uri);

        mainHandler.post(() -> {
            wvReader.setVisibility(View.VISIBLE);
            rvPdfPages.setVisibility(View.GONE);

            if (parsedBook.getMetadata().getTitle() != null && !parsedBook.getMetadata().getTitle().isEmpty()) {
                tvReaderBookTitle.setText(parsedBook.getMetadata().getTitle());
            }

            // Render HTML in WebView
            String html = parsedBook.generateHtml(settings);
            wvReader.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        });
    }

    private void loadPdf(Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            pdfPfd = ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
        } else {
            try {
                pdfPfd = getContentResolver().openFileDescriptor(uri, "r");
            } catch (Exception e) {
                File temp = FileUtils.copyToLocalBookCache(this, uri, "temp_pdf.pdf");
                pdfPfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        }

        if (pdfPfd == null) throw new Exception("Cannot open file descriptor for PDF");

        mainHandler.post(() -> {
            wvReader.setVisibility(View.GONE);
            rvPdfPages.setVisibility(View.VISIBLE);
            loadingLayout.setVisibility(View.GONE);

            if (settings.isPagedMode()) {
                pdfLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
                if (pdfSnapHelper == null) pdfSnapHelper = new PagerSnapHelper();
                pdfSnapHelper.attachToRecyclerView(rvPdfPages);
            } else {
                pdfLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
                if (pdfSnapHelper != null) pdfSnapHelper.attachToRecyclerView(null);
            }
            rvPdfPages.setLayoutManager(pdfLayoutManager);
            pdfAdapter = new PdfPageAdapter(this, pdfPfd, pageIndex -> toggleControls());
            rvPdfPages.setAdapter(pdfAdapter);
            isBookLoaded = true;
            updateBookmarkIconState();

            int totalPages = pdfAdapter.getItemCount();
            seekProgress.setMax(Math.max(1, totalPages - 1));

            rvPdfPages.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    int pos = pdfLayoutManager.findFirstVisibleItemPosition();
                    int total = pdfAdapter.getItemCount();
                    if (pos >= 0 && total > 0) {
                        currentPageIndex = pos + 1;
                        totalPagesCount = total;
                        currentScrollPosition = pos;
                        currentProgressPercent = total > 1 ? ((float) pos / (float) (total - 1) * 100f) : 100f;
                        seekProgress.setMax(Math.max(1, total - 1));
                        seekProgress.setProgress(pos);
                        tvProgressLeft.setText(String.format(Locale.getDefault(), "%.0f%%", currentProgressPercent));
                        tvProgressRight.setText(String.format(Locale.getDefault(), "Стр. %d из %d", pos + 1, total));

                        if (bookId > 0) {
                            repository.updateProgress(bookId, currentProgressPercent, pos, total);
                        }
                    }
                }
            });

            // Restore target bookmark or saved PDF progress
            if (targetPage > 0 && totalPages > 0) {
                int p = Math.min(totalPages - 1, Math.max(0, targetPage - 1));
                targetPage = -1;
                rvPdfPages.scrollToPosition(p);
            } else if (targetPercent >= 0f && totalPages > 1) {
                int p = Math.min(totalPages - 1, Math.max(0, Math.round(targetPercent / 100f * (totalPages - 1))));
                targetPercent = -1f;
                rvPdfPages.scrollToPosition(p);
            } else if (bookId > 0) {
                BookItem item = repository.getBookById(bookId);
                if (item != null && item.getCurrentPosition() > 0) {
                    rvPdfPages.scrollToPosition(Math.min(Math.max(0, totalPages - 1), item.getCurrentPosition()));
                }
            }
        });
    }

    private void toggleControls() {
        isControlsVisible = !isControlsVisible;
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);

        if (isControlsVisible) {
            topReaderBar.startAnimation(fadeIn);
            bottomReaderBar.startAnimation(fadeIn);
            topReaderBar.setVisibility(View.VISIBLE);
            bottomReaderBar.setVisibility(View.VISIBLE);
        } else {
            topReaderBar.startAnimation(fadeOut);
            bottomReaderBar.startAnimation(fadeOut);
            topReaderBar.setVisibility(View.GONE);
            bottomReaderBar.setVisibility(View.GONE);
            if (searchOverlayBar.getVisibility() == View.VISIBLE) {
                searchOverlayBar.setVisibility(View.GONE);
                wvReader.clearMatches();
            }
        }
    }

    @Override
    public void onSettingsChanged(ReaderSettings newSettings) {
        this.settings = newSettings;
        applyThemeColors();
        applyOrientation();
        applyFullscreen();

        // Update CSS without page reload for text books
        if (currentFormat != BookFormat.PDF && wvReader != null) {
            String css = Fb2Book.generateCss(settings).replace("\n", "\\n").replace("'", "\\'");
            boolean isPaged = settings.isPagedMode();
            boolean anim = settings.getActiveTheme() != null && settings.getActiveTheme().isPageTurnAnimation();
            wvReader.evaluateJavascript("isPaged = " + isPaged + "; animEnabled = " + anim + "; updateStyle('" + css + "');", null);
        } else if (currentFormat == BookFormat.PDF && rvPdfPages != null) {
            int curPos = pdfLayoutManager != null ? pdfLayoutManager.findFirstVisibleItemPosition() : 0;
            if (settings.isPagedMode()) {
                pdfLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
                if (pdfSnapHelper == null) pdfSnapHelper = new PagerSnapHelper();
                pdfSnapHelper.attachToRecyclerView(rvPdfPages);
            } else {
                pdfLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
                if (pdfSnapHelper != null) pdfSnapHelper.attachToRecyclerView(null);
            }
            rvPdfPages.setLayoutManager(pdfLayoutManager);
            if (curPos >= 0) rvPdfPages.scrollToPosition(curPos);
        }
    }

    @Override
    public void onBrightnessChanged(float brightness) {
        applyBrightness(brightness);
    }

    @Override
    public void onSleepTimerSelected(int minutes) {
        setSleepTimer(minutes);
    }

    private void applyBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : brightness;
        getWindow().setAttributes(lp);
    }

    @Override
    public void onTocItemSelected(TocItem item) {
        tvReaderChapterTitle.setText(item.getTitle());
        if (currentFormat == BookFormat.PDF) {
            rvPdfPages.scrollToPosition(item.getIndex());
        } else {
            wvReader.evaluateJavascript("scrollToAnchor('" + item.getElementId() + "');", null);
        }
    }

    @Override
    public void onBookmarkSelected(BookmarkItem bookmark) {
        if (currentFormat == BookFormat.PDF && pdfAdapter != null) {
            int total = pdfAdapter.getItemCount();
            int page = bookmark.getPageNumber() > 0
                    ? Math.max(0, bookmark.getPageNumber() - 1)
                    : Math.round(bookmark.getProgressPercent() / 100f * Math.max(0, total - 1));
            rvPdfPages.scrollToPosition(Math.min(Math.max(0, total - 1), page));
        } else {
            wvReader.evaluateJavascript("scrollToPercent(" + bookmark.getProgressPercent() + ");", null);
        }
    }

    @Override
    public void onAddBookmarkClicked() {
        String chapter = tvReaderChapterTitle.getText().toString();
        if (chapter.isEmpty()) chapter = "Раздел";
        String snippet = String.format(Locale.getDefault(), "Прогресс чтения: %.0f%%", currentProgressPercent);

        String bookUuid = currentBook != null ? currentBook.getUuid() : String.valueOf(bookId);
        String title = bookTitle != null ? bookTitle : "Без названия";
        String author = currentBook != null ? currentBook.getAuthor() : "";

        BookmarkItem bm = new BookmarkItem();
        bm.setBookUuid(bookUuid);
        bm.setBookTitle(title);
        bm.setBookAuthor(author);
        if (currentBook != null && currentBook.getSeries() != null && !currentBook.getSeries().isEmpty()) {
            bm.setSeriesInfo(currentBook.getSeries() + (currentBook.getSeriesNumber() > 0 ? " #" + currentBook.getSeriesNumber() : ""));
        }
        bm.setChapterTitle(chapter);
        bm.setPositionStr(String.format(Locale.US, "%.1f", currentProgressPercent));
        bm.setPageNumber(currentPageIndex);
        bm.setSnippet(snippet);
        bm.setCreatedAt(System.currentTimeMillis());

        if (currentFormat == BookFormat.PDF && pdfLayoutManager != null) {
            int page = pdfLayoutManager.findFirstVisibleItemPosition() + 1;
            bm.setPageNumber(page);
            bm.setSnippet("Страница " + page);
        }

        repository.addBookmark(bm);
        updateBookmarkIconState();
        Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show();
    }

    public void setSleepTimer(int minutes) {
        this.activeSleepTimerMinutes = minutes;
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }

        if (minutes > 0) {
            Toast.makeText(this, String.format(Locale.getDefault(), "Таймер сна: %d мин", minutes), Toast.LENGTH_SHORT).show();
            sleepTimerRunnable = () -> {
                activeSleepTimerMinutes = 0;
                Toast.makeText(ReaderActivity.this, "Таймер сна завершён", Toast.LENGTH_SHORT).show();
                finish();
            };
            sleepTimerHandler.postDelayed(sleepTimerRunnable, minutes * 60L * 1000L);
        } else {
            Toast.makeText(this, "Таймер сна отключён", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (settings.isVolumeKeysPageTurn()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (currentFormat == BookFormat.PDF && rvPdfPages != null) {
                    if (settings.isPagedMode() && pdfLayoutManager != null) {
                        int pos = pdfLayoutManager.findFirstVisibleItemPosition();
                        if (pos < pdfAdapter.getItemCount() - 1) rvPdfPages.smoothScrollToPosition(pos + 1);
                    } else {
                        rvPdfPages.smoothScrollBy(0, (int) (rvPdfPages.getHeight() * 0.85f));
                    }
                } else if (wvReader != null) {
                    if (settings.isPagedMode()) {
                        wvReader.evaluateJavascript("nextPage(true);", null);
                    } else {
                        wvReader.pageDown(false);
                    }
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (currentFormat == BookFormat.PDF && rvPdfPages != null) {
                    if (settings.isPagedMode() && pdfLayoutManager != null) {
                        int pos = pdfLayoutManager.findFirstVisibleItemPosition();
                        if (pos > 0) rvPdfPages.smoothScrollToPosition(pos - 1);
                    } else {
                        rvPdfPages.smoothScrollBy(0, -(int) (rvPdfPages.getHeight() * 0.85f));
                    }
                } else if (wvReader != null) {
                    if (settings.isPagedMode()) {
                        wvReader.evaluateJavascript("prevPage(true);", null);
                    } else {
                        wvReader.pageUp(false);
                    }
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void openCustomizationScreen() {
        Intent intent = new Intent(this, CustomizationActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        settings = ReaderSettings.load(this);
        applyThemeColors();
        applyOrientation();
        applyFullscreen();
        if (readerBgView != null) {
            readerBgView.resumeAnimation();
        }
        if (isBookLoaded && currentFormat != BookFormat.PDF && wvReader != null) {
            String css = Fb2Book.generateCss(settings).replace("\n", "\\n").replace("'", "\\'");
            wvReader.evaluateJavascript("updateStyle('" + css + "');", null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (readerBgView != null) {
            readerBgView.pauseAnimation();
        }
        saveReadingProgress();
        recordReadingTime();
    }

    private void recordReadingTime() {
        if (bookId > 0 && sessionStartTime > 0) {
            long durationSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000L;
            if (durationSeconds > 0) {
                repository.addReadingTime(bookId, durationSeconds);
            }
            sessionStartTime = System.currentTimeMillis();
        }
    }

    private void saveReadingProgress() {
        if (isBookLoaded && bookId > 0 && currentProgressPercent >= 0) {
            int pos = settings.isPagedMode() ? currentPageIndex : currentScrollPosition;
            int total = settings.isPagedMode() ? totalPagesCount : 0;
            if (currentFormat == BookFormat.PDF && pdfLayoutManager != null && pdfAdapter != null) {
                int pdfPos = pdfLayoutManager.findFirstVisibleItemPosition();
                if (pdfPos >= 0) pos = pdfPos;
                total = pdfAdapter.getItemCount();
            }
            repository.updateProgress(bookId, currentProgressPercent, pos, total);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveReadingProgress();
        recordReadingTime();
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        }
        executor.shutdown();
        if (readerBgView != null) {
            readerBgView.releaseMedia();
        }
        if (wvReader != null) {
            wvReader.destroy();
        }
        if (pdfAdapter != null) {
            pdfAdapter.release();
        }
    }

    /**
     * JavaScript Bridge interface receiving events from inside the WebView.
     */
    public class BookRBridge {

        @JavascriptInterface
        public void onPageChanged(int page, int total, float percent) {
            mainHandler.post(() -> {
                currentPageIndex = page;
                totalPagesCount = total;
                currentProgressPercent = percent;

                if (total > 1) {
                    seekProgress.setMax(total - 1);
                    seekProgress.setProgress(page - 1);
                } else {
                    seekProgress.setMax(100);
                    seekProgress.setProgress((int) percent);
                }
                tvProgressLeft.setText(String.format(Locale.getDefault(), "%.0f%%", percent));
                tvProgressRight.setText(String.format(Locale.getDefault(), "Стр. %d из %d", page, total));

                if (bookId > 0) {
                    repository.updateProgress(bookId, percent, page, total);
                }
            });
        }

        @JavascriptInterface
        public void onScrollPosition(int scrollY, float percent) {
            mainHandler.post(() -> {
                currentScrollPosition = scrollY;
                currentProgressPercent = percent;

                seekProgress.setMax(1000);
                seekProgress.setProgress((int) (percent * 10));
                tvProgressLeft.setText(String.format(Locale.getDefault(), "%.0f%%", percent));
                tvProgressRight.setText(String.format(Locale.getDefault(), "%.1f%%", percent));

                if (bookId > 0) {
                    repository.updateProgress(bookId, percent, scrollY, 0);
                }
            });
        }

        @JavascriptInterface
        public void onFootnoteClicked(String noteId) {
            mainHandler.post(() -> {
                if (parsedBook != null) {
                    String noteText = parsedBook.getFootnote(noteId);
                    FootnoteDialog.newInstance(noteText != null ? noteText : "Сноска: " + noteId)
                            .show(getSupportFragmentManager(), "footnote_dialog");
                }
            });
        }

        @JavascriptInterface
        public void onTapLeft() {
            mainHandler.post(() -> {
                if (settings.isPagedMode()) {
                    wvReader.evaluateJavascript("prevPage(true);", null);
                } else {
                    wvReader.pageUp(false);
                }
            });
        }

        @JavascriptInterface
        public void onTapRight() {
            mainHandler.post(() -> {
                if (settings.isPagedMode()) {
                    wvReader.evaluateJavascript("nextPage(true);", null);
                } else {
                    wvReader.pageDown(false);
                }
            });
        }

        @JavascriptInterface
        public void onTapCenter() {
            mainHandler.post(ReaderActivity.this::toggleControls);
        }
    }
}
