package com.bookr.reader.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.BookItem;
import com.bookr.reader.data.model.BookmarkItem;
import com.bookr.reader.data.model.FolderSourceItem;
import com.bookr.reader.data.model.NoteItem;
import com.bookr.reader.data.model.QuoteItem;
import com.bookr.reader.data.model.ReaderSettings;
import com.bookr.reader.data.model.ShelfItem;
import com.bookr.reader.parser.BookFormat;
import com.bookr.reader.parser.BookFormatDetector;
import com.bookr.reader.parser.BookParserManager;
import com.bookr.reader.parser.Fb2Metadata;
import com.bookr.reader.ui.adapter.BookAdapter;
import com.bookr.reader.ui.adapter.BookmarkAdapter;
import com.bookr.reader.ui.adapter.NoteAdapter;
import com.bookr.reader.ui.adapter.QuoteAdapter;
import com.bookr.reader.ui.adapter.ShelfAdapter;
import com.bookr.reader.ui.dialog.BackupRestoreDialog;
import com.bookr.reader.ui.dialog.BookDetailsBottomSheet;
import com.bookr.reader.ui.dialog.FolderSourcesDialog;
import com.bookr.reader.ui.dialog.NoteEditorDialog;
import com.bookr.reader.ui.dialog.ThemeEditorDialog;
import com.bookr.reader.util.FileUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements
        BookAdapter.OnBookClickListener,
        BookDetailsBottomSheet.OnBookActionListener {

    private static final String TAG = "BookR.Main";

    // 8 Main Sections
    public static final int SECTION_LIBRARY = 0;
    public static final int SECTION_CONTINUE = 1;
    public static final int SECTION_BOOKMARKS = 2;
    public static final int SECTION_QUOTES = 3;
    public static final int SECTION_NOTES = 4;
    public static final int SECTION_SHELVES = 5;
    public static final int SECTION_HISTORY = 6;
    public static final int SECTION_SETTINGS = 7;

    // Library Sub-tabs
    private static final int SUBTAB_ALL = 0;
    private static final int SUBTAB_RECENT = 1;
    private static final int SUBTAB_FAVORITES = 2;

    private int currentSection = SECTION_LIBRARY;
    private int currentSubTab = SUBTAB_ALL;
    private int currentSort = BookRepository.SORT_TITLE_ASC;
    private String currentSearchQuery = "";

    private BookRepository repository;
    private ReaderSettings settings;

    // Adapters
    private BookAdapter bookAdapter;
    private BookmarkAdapter bookmarkAdapter;
    private QuoteAdapter quoteAdapter;
    private NoteAdapter noteAdapter;
    private ShelfAdapter shelfAdapter;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private RecyclerView rvBooks;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvBookCount;
    private EditText etSearch;
    private ImageView btnClearSearch;
    private ThemeBackgroundView mainBgView;

    // Navigation pills
    private TextView navLibrary, navContinue, navBookmarks, navQuotes, navNotes, navShelves, navHistory, navSettings, navCustomization;

    // Subtabs & actions
    private LinearLayout subTabsContainer;
    private TextView tabAll, tabRecent, tabFavorites;
    private TextView btnSectionAction;

    private LinearLayout emptyState, bannerLoading;
    private TextView tvEmptyTitle, tvEmptyDesc, tvLoadingText;

    private ActivityResultLauncher<Intent> openFileLauncher;
    private ActivityResultLauncher<Intent> openFolderLauncher;
    private ActivityResultLauncher<Intent> exportBackupLauncher;
    private ActivityResultLauncher<Intent> importBackupLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = new BookRepository(this);
        settings = ReaderSettings.load(this);

        SharedPreferences sp = getSharedPreferences("main_prefs", Context.MODE_PRIVATE);
        currentSort = sp.getInt("sort_order", BookRepository.SORT_TITLE_ASC);

        initViews();
        setupLaunchers();
        setupListeners();

        handleIncomingIntent(getIntent());

        if (settings.isAutoScanOnStartup()) {
            autoScanConnectedFolders();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        settings = ReaderSettings.load(this);
        if (mainBgView != null) {
            mainBgView.setConfig(settings.getActiveTheme(), false);
            mainBgView.resumeAnimation();
        }
        loadCurrentSectionData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mainBgView != null) {
            mainBgView.pauseAnimation();
        }
    }

    private void initViews() {
        rvBooks = findViewById(R.id.rvBooks);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvBookCount = findViewById(R.id.tvBookCount);
        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        // 8 Nav pills + Customization
        mainBgView = findViewById(R.id.mainBgView);
        if (mainBgView != null) {
            mainBgView.setConfig(settings.getActiveTheme(), false);
        }

        navLibrary = findViewById(R.id.navLibrary);
        navContinue = findViewById(R.id.navContinue);
        navBookmarks = findViewById(R.id.navBookmarks);
        navQuotes = findViewById(R.id.navQuotes);
        navNotes = findViewById(R.id.navNotes);
        navShelves = findViewById(R.id.navShelves);
        navHistory = findViewById(R.id.navHistory);
        navSettings = findViewById(R.id.navSettings);
        navCustomization = findViewById(R.id.navCustomization);

        // Sub-tabs
        subTabsContainer = findViewById(R.id.subTabsContainer);
        tabAll = findViewById(R.id.tabAll);
        tabRecent = findViewById(R.id.tabRecent);
        tabFavorites = findViewById(R.id.tabFavorites);
        btnSectionAction = findViewById(R.id.btnSectionAction);

        emptyState = findViewById(R.id.emptyState);
        bannerLoading = findViewById(R.id.bannerLoading);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyDesc = findViewById(R.id.tvEmptyDesc);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        rvBooks.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapters
        bookAdapter = new BookAdapter(this, this);

        bookmarkAdapter = new BookmarkAdapter(this, null, new BookmarkAdapter.OnBookmarkClickListener() {
            @Override
            public void onBookmarkClick(BookmarkItem item) {
                openBookmarkTarget(item);
            }

            @Override
            public void onBookmarkDelete(BookmarkItem item) {
                repository.deleteBookmark(item.getId());
                loadBookmarks();
            }
        });

        quoteAdapter = new QuoteAdapter(this, new QuoteAdapter.OnQuoteActionListener() {
            @Override
            public void onCopyQuote(QuoteItem quote) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Quote", quote.getQuoteText()));
                    Toast.makeText(MainActivity.this, "Цитата скопирована", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onShareQuote(QuoteItem quote) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "«" + quote.getQuoteText() + "»\n\n— " + quote.getBookTitle() + (quote.getBookAuthor().isEmpty() ? "" : " (" + quote.getBookAuthor() + ")"));
                startActivity(Intent.createChooser(shareIntent, "Поделиться цитатой"));
            }

            @Override
            public void onDeleteQuote(QuoteItem quote) {
                repository.deleteQuote(quote.getId());
                loadQuotes();
            }
        });

        noteAdapter = new NoteAdapter(this, new NoteAdapter.OnNoteActionListener() {
            @Override
            public void onEditNote(NoteItem note) {
                NoteEditorDialog dialog = NoteEditorDialog.newInstance(note);
                dialog.setListener(MainActivity.this::loadNotes);
                dialog.show(getSupportFragmentManager(), "edit_note");
            }

            @Override
            public void onShareNote(NoteItem note) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, note.getNoteText() + "\n\n(К книге: " + note.getBookTitle() + ")");
                startActivity(Intent.createChooser(shareIntent, "Поделиться заметкой"));
            }

            @Override
            public void onDeleteNote(NoteItem note) {
                repository.deleteNote(note.getId());
                loadNotes();
            }
        });

        shelfAdapter = new ShelfAdapter(this, repository, new ShelfAdapter.OnShelfClickListener() {
            @Override
            public void onShelfClick(ShelfItem shelf) {
                showShelfBooksDialog(shelf);
            }

            @Override
            public void onShelfDelete(ShelfItem shelf) {
                repository.deleteShelf(shelf.getId());
                loadShelves();
            }
        });

        rvBooks.setAdapter(bookAdapter);

        swipeRefresh.setColorSchemeResources(R.color.white);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.glass_surface_dialog);
        swipeRefresh.setOnRefreshListener(this::loadCurrentSectionData);
    }

    private void setupLaunchers() {
        openFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignored) {}
                            importSingleBook(uri, true);
                        }
                    }
                }
        );

        openFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            try {
                                getContentResolver().takePersistableUriPermission(treeUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            } catch (Exception ignored) {}
                            scanFolder(treeUri);
                        }
                    }
                }
        );

        exportBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri targetUri = result.getData().getData();
                        if (targetUri != null) {
                            performBackupExport(targetUri);
                        }
                    }
                }
        );

        importBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri sourceUri = result.getData().getData();
                        if (sourceUri != null) {
                            performBackupImport(sourceUri);
                        }
                    }
                }
        );
    }

    private void setupListeners() {
        findViewById(R.id.btnAddBook).setOnClickListener(v -> pickBookFile());
        findViewById(R.id.btnScanFolder).setOnClickListener(v -> showFolderSourcesDialog());
        findViewById(R.id.btnBackup).setOnClickListener(v -> showBackupDialog());
        findViewById(R.id.btnSort).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.btnEmptyAdd).setOnClickListener(v -> pickBookFile());

        View btnCust = findViewById(R.id.btnCustomization);
        if (btnCust != null) {
            btnCust.setOnClickListener(v -> openCustomizationScreen());
        }
        if (navCustomization != null) {
            navCustomization.setOnClickListener(v -> openCustomizationScreen());
        }

        // Nav pill clicks
        navLibrary.setOnClickListener(v -> selectSection(SECTION_LIBRARY));
        navContinue.setOnClickListener(v -> selectSection(SECTION_CONTINUE));
        navBookmarks.setOnClickListener(v -> selectSection(SECTION_BOOKMARKS));
        navQuotes.setOnClickListener(v -> selectSection(SECTION_QUOTES));
        navNotes.setOnClickListener(v -> selectSection(SECTION_NOTES));
        navShelves.setOnClickListener(v -> selectSection(SECTION_SHELVES));
        navHistory.setOnClickListener(v -> selectSection(SECTION_HISTORY));
        navSettings.setOnClickListener(v -> selectSection(SECTION_SETTINGS));

        // Subtabs
        tabAll.setOnClickListener(v -> selectSubTab(SUBTAB_ALL));
        tabRecent.setOnClickListener(v -> selectSubTab(SUBTAB_RECENT));
        tabFavorites.setOnClickListener(v -> selectSubTab(SUBTAB_FAVORITES));

        // Search text watcher
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim() : "";
                btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                loadCurrentSectionData();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            currentSearchQuery = "";
            loadCurrentSectionData();
        });
    }

    private void selectSection(int section) {
        currentSection = section;

        setPillState(navLibrary, section == SECTION_LIBRARY);
        setPillState(navContinue, section == SECTION_CONTINUE);
        setPillState(navBookmarks, section == SECTION_BOOKMARKS);
        setPillState(navQuotes, section == SECTION_QUOTES);
        setPillState(navNotes, section == SECTION_NOTES);
        setPillState(navShelves, section == SECTION_SHELVES);
        setPillState(navHistory, section == SECTION_HISTORY);
        setPillState(navSettings, section == SECTION_SETTINGS);

        subTabsContainer.setVisibility(section == SECTION_LIBRARY ? View.VISIBLE : View.GONE);
        btnSectionAction.setVisibility(View.GONE);

        if (section == SECTION_NOTES) {
            subTabsContainer.setVisibility(View.VISIBLE);
            tabAll.setVisibility(View.GONE);
            tabRecent.setVisibility(View.GONE);
            tabFavorites.setVisibility(View.GONE);
            btnSectionAction.setVisibility(View.VISIBLE);
            btnSectionAction.setText("+ Новая заметка");
            btnSectionAction.setOnClickListener(v -> {
                NoteEditorDialog d = NoteEditorDialog.newInstance(null);
                d.setListener(this::loadNotes);
                d.show(getSupportFragmentManager(), "new_note");
            });
        } else if (section == SECTION_SHELVES) {
            subTabsContainer.setVisibility(View.VISIBLE);
            tabAll.setVisibility(View.GONE);
            tabRecent.setVisibility(View.GONE);
            tabFavorites.setVisibility(View.GONE);
            btnSectionAction.setVisibility(View.VISIBLE);
            btnSectionAction.setText("+ Новая полка");
            btnSectionAction.setOnClickListener(v -> promptCreateShelf());
        } else if (section == SECTION_LIBRARY) {
            tabAll.setVisibility(View.VISIBLE);
            tabRecent.setVisibility(View.VISIBLE);
            tabFavorites.setVisibility(View.VISIBLE);
        }

        loadCurrentSectionData();
    }

    private void selectSubTab(int subTab) {
        currentSubTab = subTab;
        tabAll.setBackgroundResource(subTab == SUBTAB_ALL ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        tabAll.setTextColor(getResources().getColor(subTab == SUBTAB_ALL ? R.color.white : R.color.text_secondary));

        tabRecent.setBackgroundResource(subTab == SUBTAB_RECENT ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        tabRecent.setTextColor(getResources().getColor(subTab == SUBTAB_RECENT ? R.color.white : R.color.text_secondary));

        tabFavorites.setBackgroundResource(subTab == SUBTAB_FAVORITES ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        tabFavorites.setTextColor(getResources().getColor(subTab == SUBTAB_FAVORITES ? R.color.white : R.color.text_secondary));

        loadBooks();
    }

    private void setPillState(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        tv.setTextColor(getResources().getColor(active ? R.color.white : R.color.text_secondary));
    }

    private void loadCurrentSectionData() {
        swipeRefresh.setRefreshing(false);
        switch (currentSection) {
            case SECTION_CONTINUE:
                loadContinueReading();
                break;
            case SECTION_BOOKMARKS:
                loadBookmarks();
                break;
            case SECTION_QUOTES:
                loadQuotes();
                break;
            case SECTION_NOTES:
                loadNotes();
                break;
            case SECTION_SHELVES:
                loadShelves();
                break;
            case SECTION_HISTORY:
                loadHistory();
                break;
            case SECTION_SETTINGS:
                showSettingsSection();
                break;
            case SECTION_LIBRARY:
            default:
                loadBooks();
                break;
        }
    }

    private void loadBooks() {
        rvBooks.setAdapter(bookAdapter);
        executor.execute(() -> {
            List<BookItem> list;
            switch (currentSubTab) {
                case SUBTAB_RECENT:
                    list = repository.getRecentBooks(currentSearchQuery);
                    break;
                case SUBTAB_FAVORITES:
                    list = repository.getFavoriteBooks(currentSearchQuery);
                    break;
                case SUBTAB_ALL:
                default:
                    list = repository.getAllBooks(currentSort, currentSearchQuery);
                    break;
            }

            mainHandler.post(() -> {
                bookAdapter.setBooks(list);
                tvBookCount.setText(String.format(Locale.getDefault(), "%d %s", list.size(), getBookCountNoun(list.size())));
                updateEmptyState(list.isEmpty(), R.string.empty_all_title, R.string.empty_all_desc);
            });
        });
    }

    private void loadContinueReading() {
        rvBooks.setAdapter(bookAdapter);
        executor.execute(() -> {
            List<BookItem> all = repository.getRecentBooks(currentSearchQuery);
            List<BookItem> inProgress = new ArrayList<>();
            for (BookItem b : all) {
                if (b.getProgressPercent() > 0 && b.getProgressPercent() < 99.5f) {
                    inProgress.add(b);
                }
            }

            mainHandler.post(() -> {
                bookAdapter.setBooks(inProgress);
                tvBookCount.setText(String.format(Locale.getDefault(), "В процессе: %d", inProgress.size()));
                updateEmptyState(inProgress.isEmpty(), R.string.empty_continue_title, R.string.empty_continue_desc);
            });
        });
    }

    private void loadBookmarks() {
        rvBooks.setAdapter(bookmarkAdapter);
        executor.execute(() -> {
            List<BookmarkItem> list = repository.getAllBookmarks();
            mainHandler.post(() -> {
                bookmarkAdapter.setItems(list);
                tvBookCount.setText(String.format(Locale.getDefault(), "Закладок: %d", list.size()));
                updateEmptyState(list.isEmpty(), R.string.bookmarks_title, R.string.no_bookmarks);
            });
        });
    }

    private void loadQuotes() {
        rvBooks.setAdapter(quoteAdapter);
        executor.execute(() -> {
            List<QuoteItem> list = repository.getAllQuotes(currentSearchQuery);
            mainHandler.post(() -> {
                quoteAdapter.setItems(list);
                tvBookCount.setText(String.format(Locale.getDefault(), "Цитат: %d", list.size()));
                updateEmptyState(list.isEmpty(), R.string.empty_quotes_title, R.string.empty_quotes_desc);
            });
        });
    }

    private void loadNotes() {
        rvBooks.setAdapter(noteAdapter);
        executor.execute(() -> {
            List<NoteItem> list = repository.getAllNotes(currentSearchQuery);
            mainHandler.post(() -> {
                noteAdapter.setItems(list);
                tvBookCount.setText(String.format(Locale.getDefault(), "Заметок: %d", list.size()));
                updateEmptyState(list.isEmpty(), R.string.empty_notes_title, R.string.empty_notes_desc);
            });
        });
    }

    private void loadShelves() {
        rvBooks.setAdapter(shelfAdapter);
        executor.execute(() -> {
            List<ShelfItem> list = repository.getAllShelves();
            mainHandler.post(() -> {
                shelfAdapter.setItems(list);
                tvBookCount.setText(String.format(Locale.getDefault(), "Коллекций: %d", list.size()));
                updateEmptyState(list.isEmpty(), R.string.empty_shelves_title, R.string.empty_shelves_desc);
            });
        });
    }

    private void loadHistory() {
        rvBooks.setAdapter(bookAdapter);
        executor.execute(() -> {
            List<BookItem> list = repository.getRecentBooks(currentSearchQuery);
            long totalReadingSeconds = 0;
            int completedCount = 0;
            for (BookItem b : list) {
                totalReadingSeconds += b.getReadingTimeSeconds();
                if (b.getProgressPercent() >= 99f) completedCount++;
            }
            final long hours = totalReadingSeconds / 3600;
            final long mins = (totalReadingSeconds % 3600) / 60;
            final int completed = completedCount;

            mainHandler.post(() -> {
                bookAdapter.setBooks(list);
                tvBookCount.setText(String.format(Locale.getDefault(), "Время чтения: %d ч %d мин • Прочитано: %d", hours, mins, completed));
                updateEmptyState(list.isEmpty(), R.string.empty_recent_title, R.string.empty_recent_desc);
            });
        });
    }

    private void showSettingsSection() {
        tvBookCount.setText(R.string.nav_settings);
        emptyState.setVisibility(View.GONE);
        rvBooks.setVisibility(View.GONE);

        // Show theme editor immediately or offer quick dialog
        ThemeEditorDialog.newInstance().show(getSupportFragmentManager(), "theme_editor_main");
    }

    private void updateEmptyState(boolean isEmpty, int titleRes, int descRes) {
        if (isEmpty) {
            emptyState.setVisibility(View.VISIBLE);
            rvBooks.setVisibility(View.GONE);
            tvEmptyTitle.setText(titleRes);
            tvEmptyDesc.setText(descRes);
        } else {
            emptyState.setVisibility(View.GONE);
            rvBooks.setVisibility(View.VISIBLE);
        }
    }

    private void openBookmarkTarget(BookmarkItem bookmark) {
        executor.execute(() -> {
            BookItem book = null;
            if (bookmark.getBookUuid() != null) {
                book = repository.getBookByUuid(bookmark.getBookUuid());
            }
            if (book == null && bookmark.getBookTitle() != null) {
                List<BookItem> matches = repository.getAllBooks(BookRepository.SORT_TITLE_ASC, bookmark.getBookTitle());
                if (!matches.isEmpty()) book = matches.get(0);
            }

            final BookItem finalBook = book;
            mainHandler.post(() -> {
                if (finalBook != null) {
                    onBookClick(finalBook);
                } else {
                    Toast.makeText(this, "Книга была удалена из библиотеки, но закладка сохранена", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void pickBookFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        openFileLauncher.launch(intent);
    }

    private void showFolderSourcesDialog() {
        FolderSourcesDialog dialog = FolderSourcesDialog.newInstance();
        dialog.setListener(new FolderSourcesDialog.OnFolderSourceInteractionListener() {
            @Override
            public void onAddFolderRequested() {
                pickFolder();
            }

            @Override
            public void onRescanFolderRequested(FolderSourceItem item) {
                scanFolder(Uri.parse(item.getTreeUri()));
            }

            @Override
            public void onRescanAllRequested() {
                autoScanConnectedFolders();
            }

            @Override
            public void onRestoreAccessRequested(FolderSourceItem item) {
                pickFolder();
            }
        });
        dialog.show(getSupportFragmentManager(), "folder_sources");
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        openFolderLauncher.launch(intent);
    }

    private void showBackupDialog() {
        BackupRestoreDialog dialog = BackupRestoreDialog.newInstance();
        dialog.setListener(new BackupRestoreDialog.OnBackupActionListener() {
            @Override
            public void onExportBackupRequested() {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "bookr_backup_" + System.currentTimeMillis() + ".json");
                exportBackupLauncher.launch(intent);
            }

            @Override
            public void onImportBackupRequested() {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                importBackupLauncher.launch(intent);
            }
        });
        dialog.show(getSupportFragmentManager(), "backup_restore");
    }

    private void performBackupExport(Uri targetUri) {
        executor.execute(() -> {
            try {
                String json = repository.exportBackupToJson();
                OutputStream os = getContentResolver().openOutputStream(targetUri);
                if (os != null) {
                    os.write(json.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                    mainHandler.post(() -> Toast.makeText(this, R.string.backup_success, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                mainHandler.post(() -> Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void performBackupImport(Uri sourceUri) {
        executor.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(sourceUri);
                if (is != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                    is.close();

                    repository.importBackupFromJson(sb.toString());
                    mainHandler.post(() -> {
                        Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show();
                        loadCurrentSectionData();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                mainHandler.post(() -> Toast.makeText(this, "Ошибка импорта резервной копии", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showSortDialog() {
        String[] options = {
                getString(R.string.sort_title_asc),
                getString(R.string.sort_title_desc),
                getString(R.string.sort_author),
                getString(R.string.sort_recent),
                getString(R.string.sort_date_added)
        };

        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle(R.string.action_sort)
                .setSingleChoiceItems(options, currentSort, (dialog, which) -> {
                    currentSort = which;
                    getSharedPreferences("main_prefs", Context.MODE_PRIVATE)
                            .edit().putInt("sort_order", currentSort).apply();
                    dialog.dismiss();
                    loadBooks();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void promptCreateShelf() {
        EditText et = new EditText(this);
        et.setHint("Название полки (например, Фантастика)");
        et.setTextColor(android.graphics.Color.WHITE);
        et.setHintTextColor(android.graphics.Color.GRAY);
        et.setBackgroundResource(R.drawable.bg_glass_input);
        et.setPadding(24, 24, 24, 24);

        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle("Новая коллекция")
                .setView(et)
                .setPositiveButton("Создать", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        ShelfItem s = new ShelfItem(name);
                        repository.addShelf(s);
                        loadShelves();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showShelfBooksDialog(ShelfItem shelf) {
        executor.execute(() -> {
            List<BookItem> books = repository.getBooksOnShelf(shelf.getId());
            mainHandler.post(() -> {
                String[] titles = new String[books.size()];
                for (int i = 0; i < books.size(); i++) titles[i] = books.get(i).getTitle();

                new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                        .setTitle("Полка: " + shelf.getName())
                        .setItems(titles.length == 0 ? new String[]{"На этой полке пока нет книг"} : titles, (d, which) -> {
                            if (!books.isEmpty()) onBookClick(books.get(which));
                        })
                        .setPositiveButton(R.string.btn_close, null)
                        .show();
            });
        });
    }

    private void importSingleBook(Uri uri, boolean openImmediately) {
        bannerLoading.setVisibility(View.VISIBLE);
        tvLoadingText.setText(R.string.loading_book);

        executor.execute(() -> {
            try {
                Fb2Metadata meta = BookParserManager.parseMetadata(this, uri);
                String fallbackTitle = FileUtils.getFileName(this, uri);
                long fileSize = FileUtils.getFileSize(this, uri);
                BookFormat format = BookFormatDetector.detectFormat(this, uri);

                BookItem book = new BookItem();
                book.setUri(uri.toString());
                book.setTitle(meta.getTitle().equals("Без названия") ? fallbackTitle : meta.getTitle());
                book.setAuthor(meta.getAuthor());
                book.setAnnotation(meta.getAnnotation());
                book.setFileSize(fileSize);
                book.setFormat(format.name());

                if (meta.getCoverImageBytes() != null) {
                    String coverPath = FileUtils.saveCoverToCache(this, meta.getCoverImageBytes(), book.getTitle());
                    book.setCoverPath(coverPath);
                }

                long bookId = repository.insertOrUpdateBook(book);
                book.setId(bookId);

                mainHandler.post(() -> {
                    bannerLoading.setVisibility(View.GONE);
                    loadBooks();
                    if (openImmediately) {
                        onBookClick(book);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to import single book", e);
                mainHandler.post(() -> {
                    bannerLoading.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.error_open_book, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void scanFolder(Uri treeUri) {
        bannerLoading.setVisibility(View.VISIBLE);
        tvLoadingText.setText(R.string.scanning_folders);

        executor.execute(() -> {
            DocumentFile rootDoc = null;
            try {
                rootDoc = DocumentFile.fromTreeUri(this, treeUri);
            } catch (Exception e) {
                Log.w(TAG, "Failed to read treeUri", e);
            }

            if (rootDoc == null || !rootDoc.isDirectory()) {
                mainHandler.post(() -> {
                    bannerLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Папка недоступна. Попробуйте выбрать её снова.", Toast.LENGTH_LONG).show();
                });
                return;
            }

            // Register in Folder Sources
            FolderSourceItem source = new FolderSourceItem();
            source.setTreeUri(treeUri.toString());
            source.setDisplayName(rootDoc.getName() != null ? rootDoc.getName() : "Папка книг");
            source.setAddedAt(System.currentTimeMillis());
            source.setAccessible(true);
            long sourceId = repository.addFolderSource(source);

            List<DocumentFile> files = new ArrayList<>();
            collectFiles(rootDoc, files);

            int count = 0;
            for (DocumentFile file : files) {
                try {
                    Uri fileUri = file.getUri();
                    Fb2Metadata meta = BookParserManager.parseMetadata(this, fileUri);
                    String fallbackTitle = file.getName() != null ? file.getName() : "book";
                    long fileSize = file.length();
                    BookFormat format = BookFormatDetector.detectFormat(this, fileUri);

                    BookItem book = new BookItem();
                    book.setUri(fileUri.toString());
                    book.setTitle(meta.getTitle().equals("Без названия") ? fallbackTitle : meta.getTitle());
                    book.setAuthor(meta.getAuthor());
                    book.setAnnotation(meta.getAnnotation());
                    book.setFileSize(fileSize);
                    book.setFormat(format.name());

                    if (meta.getCoverImageBytes() != null) {
                        String coverPath = FileUtils.saveCoverToCache(this, meta.getCoverImageBytes(), book.getTitle());
                        book.setCoverPath(coverPath);
                    }

                    repository.insertOrUpdateBook(book);
                    count++;

                    final int currentCount = count;
                    mainHandler.post(() -> tvLoadingText.setText(String.format(Locale.getDefault(), "Индексировано книг: %d", currentCount)));
                } catch (Exception ignored) {}
            }

            repository.updateFolderSourceCount(sourceId, count);

            final int finalCount = count;
            mainHandler.post(() -> {
                bannerLoading.setVisibility(View.GONE);
                loadBooks();
                Toast.makeText(this, String.format(Locale.getDefault(), "Найдено и добавлено книг: %d", finalCount), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void autoScanConnectedFolders() {
        executor.execute(() -> {
            List<FolderSourceItem> sources = repository.getAllFolderSources();
            for (FolderSourceItem src : sources) {
                try {
                    Uri treeUri = Uri.parse(src.getTreeUri());
                    DocumentFile rootDoc = DocumentFile.fromTreeUri(this, treeUri);
                    if (rootDoc != null && rootDoc.canRead()) {
                        repository.setFolderAccessible(src.getId(), true);
                    } else {
                        repository.setFolderAccessible(src.getId(), false);
                    }
                } catch (Exception e) {
                    repository.setFolderAccessible(src.getId(), false);
                }
            }
        });
    }

    private void collectFiles(DocumentFile dir, List<DocumentFile> result) {
        DocumentFile[] files = dir.listFiles();
        for (DocumentFile file : files) {
            if (file.isDirectory()) {
                collectFiles(file, result);
            } else {
                String name = file.getName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.endsWith(".fb2") || lower.endsWith(".fb2.zip") || lower.endsWith(".epub")
                            || lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".html")
                            || lower.endsWith(".xhtml") || lower.endsWith(".rtf") || lower.endsWith(".mobi")
                            || lower.endsWith(".azw3") || lower.endsWith(".zip")) {
                        result.add(file);
                    }
                }
            }
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                importSingleBook(data, true);
            }
        }
    }

    @Override
    public void onBookClick(BookItem book) {
        repository.updateLastOpened(book.getId());
        Intent intent = new Intent(this, ReaderActivity.class);
        Uri uri = Uri.parse(book.getUri());
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(ReaderActivity.EXTRA_BOOK_ID, book.getId());
        intent.putExtra(ReaderActivity.EXTRA_BOOK_URI, book.getUri());
        intent.putExtra(ReaderActivity.EXTRA_BOOK_TITLE, book.getTitle());
        startActivity(intent);
    }

    @Override
    public void onBookOptionsClick(BookItem book) {
        BookDetailsBottomSheet sheet = BookDetailsBottomSheet.newInstance(book);
        sheet.setListener(this);
        sheet.show(getSupportFragmentManager(), "book_options");
    }

    @Override
    public void onOpenBook(BookItem book) {
        onBookClick(book);
    }

    @Override
    public void onToggleFavorite(BookItem book) {
        repository.setFavorite(book.getId(), !book.isFavorite());
        loadBooks();
    }

    @Override
    public void onDeleteBook(BookItem book) {
        repository.deleteBook(book.getId());
        loadBooks();
    }

    private String getBookCountNoun(int count) {
        int rem100 = count % 100;
        int rem10 = count % 10;
        if (rem100 >= 11 && rem100 <= 19) return "книг";
        if (rem10 == 1) return "книга";
        if (rem10 >= 2 && rem10 <= 4) return "книги";
        return "книг";
    }

    private void openCustomizationScreen() {
        Intent intent = new Intent(this, CustomizationActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainBgView != null) {
            mainBgView.releaseMedia();
        }
        executor.shutdown();
    }
}
