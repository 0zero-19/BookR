package com.bookr.reader.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class BookDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "bookr_library.db";
    private static final int DATABASE_VERSION = 3;

    // Books table
    public static final String TABLE_BOOKS = "books";
    public static final String COL_BOOK_ID = "id";
    public static final String COL_BOOK_UUID = "uuid";
    public static final String COL_BOOK_URI = "uri";
    public static final String COL_BOOK_CACHED_PATH = "cached_path";
    public static final String COL_BOOK_TITLE = "title";
    public static final String COL_BOOK_AUTHOR = "author";
    public static final String COL_BOOK_SERIES = "series";
    public static final String COL_BOOK_SERIES_NUM = "series_number";
    public static final String COL_BOOK_FORMAT = "format";
    public static final String COL_BOOK_ANNOTATION = "annotation";
    public static final String COL_BOOK_COVER_PATH = "cover_path";
    public static final String COL_BOOK_DATE_ADDED = "date_added";
    public static final String COL_BOOK_LAST_OPENED = "last_opened";
    public static final String COL_BOOK_PROGRESS_PERCENT = "progress_percent";
    public static final String COL_BOOK_CURRENT_POSITION = "current_position";
    public static final String COL_BOOK_TOTAL_PAGES = "total_pages";
    public static final String COL_BOOK_FILE_SIZE = "file_size";
    public static final String COL_BOOK_IS_FAVORITE = "is_favorite";
    public static final String COL_BOOK_IS_HIDDEN = "is_hidden";
    public static final String COL_BOOK_READING_TIME = "reading_time_seconds";

    // Universal Bookmarks table (independent from book files!)
    public static final String TABLE_BOOKMARKS = "bookmarks";
    public static final String COL_BM_ID = "id";
    public static final String COL_BM_BOOK_UUID = "book_uuid";
    public static final String COL_BM_BOOK_TITLE = "book_title";
    public static final String COL_BM_BOOK_AUTHOR = "book_author";
    public static final String COL_BM_SERIES_INFO = "series_info";
    public static final String COL_BM_CHAPTER_TITLE = "chapter_title";
    public static final String COL_BM_POSITION_STR = "position_str";
    public static final String COL_BM_PAGE_NUMBER = "page_number";
    public static final String COL_BM_SNIPPET = "snippet";
    public static final String COL_BM_COMMENT = "comment";
    public static final String COL_BM_TYPE = "bookmark_type"; // 0=NORMAL, 1=IMPORTANT, 2=QUOTE
    public static final String COL_BM_CREATED_AT = "created_at";

    // Notes table
    public static final String TABLE_NOTES = "notes";
    public static final String COL_NOTE_ID = "id";
    public static final String COL_NOTE_BOOK_UUID = "book_uuid";
    public static final String COL_NOTE_BOOK_TITLE = "book_title";
    public static final String COL_NOTE_CHAPTER_TITLE = "chapter_title";
    public static final String COL_NOTE_POSITION_STR = "position_str";
    public static final String COL_NOTE_PAGE_NUMBER = "page_number";
    public static final String COL_NOTE_SELECTED_TEXT = "selected_text";
    public static final String COL_NOTE_TEXT = "note_text";
    public static final String COL_NOTE_CREATED_AT = "created_at";
    public static final String COL_NOTE_UPDATED_AT = "updated_at";

    // Quotes table
    public static final String TABLE_QUOTES = "quotes";
    public static final String COL_QUOTE_ID = "id";
    public static final String COL_QUOTE_BOOK_UUID = "book_uuid";
    public static final String COL_QUOTE_BOOK_TITLE = "book_title";
    public static final String COL_QUOTE_BOOK_AUTHOR = "book_author";
    public static final String COL_QUOTE_CHAPTER = "chapter_title";
    public static final String COL_QUOTE_TEXT = "quote_text";
    public static final String COL_QUOTE_COLOR_LABEL = "color_label";
    public static final String COL_QUOTE_CREATED_AT = "created_at";

    // Collections / Shelves
    public static final String TABLE_SHELVES = "shelves";
    public static final String COL_SHELF_ID = "id";
    public static final String COL_SHELF_NAME = "name";
    public static final String COL_SHELF_CREATED_AT = "created_at";

    public static final String TABLE_SHELF_BOOKS = "shelf_books";
    public static final String COL_SB_ID = "id";
    public static final String COL_SB_SHELF_ID = "shelf_id";
    public static final String COL_SB_BOOK_ID = "book_id";

    // Connected folder sources
    public static final String TABLE_FOLDER_SOURCES = "folder_sources";
    public static final String COL_FS_ID = "id";
    public static final String COL_FS_TREE_URI = "tree_uri";
    public static final String COL_FS_DISPLAY_NAME = "display_name";
    public static final String COL_FS_ADDED_AT = "added_at";
    public static final String COL_FS_LAST_SCANNED = "last_scanned";
    public static final String COL_FS_BOOKS_COUNT = "books_count";
    public static final String COL_FS_IS_ACCESSIBLE = "is_accessible";

    // Custom themes
    public static final String TABLE_CUSTOM_THEMES = "custom_themes";
    public static final String COL_THEME_ID = "id";
    public static final String COL_THEME_NAME = "name";
    public static final String COL_THEME_BG = "bg_color";
    public static final String COL_THEME_TEXT = "text_color";
    public static final String COL_THEME_SURFACE = "surface_color";
    public static final String COL_THEME_STROKE = "stroke_color";
    public static final String COL_THEME_JSON = "json_data";
    public static final String COL_THEME_CREATED_AT = "created_at";

    private static BookDatabaseHelper instance;

    public static synchronized BookDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new BookDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private BookDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createBooksTable = "CREATE TABLE " + TABLE_BOOKS + " ("
                + COL_BOOK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_BOOK_UUID + " TEXT UNIQUE, "
                + COL_BOOK_URI + " TEXT UNIQUE, "
                + COL_BOOK_CACHED_PATH + " TEXT, "
                + COL_BOOK_TITLE + " TEXT, "
                + COL_BOOK_AUTHOR + " TEXT, "
                + COL_BOOK_SERIES + " TEXT, "
                + COL_BOOK_SERIES_NUM + " INTEGER DEFAULT 0, "
                + COL_BOOK_FORMAT + " TEXT, "
                + COL_BOOK_ANNOTATION + " TEXT, "
                + COL_BOOK_COVER_PATH + " TEXT, "
                + COL_BOOK_DATE_ADDED + " INTEGER, "
                + COL_BOOK_LAST_OPENED + " INTEGER, "
                + COL_BOOK_PROGRESS_PERCENT + " REAL DEFAULT 0, "
                + COL_BOOK_CURRENT_POSITION + " INTEGER DEFAULT 0, "
                + COL_BOOK_TOTAL_PAGES + " INTEGER DEFAULT 0, "
                + COL_BOOK_FILE_SIZE + " INTEGER DEFAULT 0, "
                + COL_BOOK_IS_FAVORITE + " INTEGER DEFAULT 0, "
                + COL_BOOK_IS_HIDDEN + " INTEGER DEFAULT 0, "
                + COL_BOOK_READING_TIME + " INTEGER DEFAULT 0"
                + ");";

        String createBookmarksTable = "CREATE TABLE " + TABLE_BOOKMARKS + " ("
                + COL_BM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_BM_BOOK_UUID + " TEXT, "
                + COL_BM_BOOK_TITLE + " TEXT, "
                + COL_BM_BOOK_AUTHOR + " TEXT, "
                + COL_BM_SERIES_INFO + " TEXT, "
                + COL_BM_CHAPTER_TITLE + " TEXT, "
                + COL_BM_POSITION_STR + " TEXT, "
                + COL_BM_PAGE_NUMBER + " INTEGER DEFAULT 0, "
                + COL_BM_SNIPPET + " TEXT, "
                + COL_BM_COMMENT + " TEXT, "
                + COL_BM_TYPE + " INTEGER DEFAULT 0, "
                + COL_BM_CREATED_AT + " INTEGER"
                + ");";

        String createNotesTable = "CREATE TABLE " + TABLE_NOTES + " ("
                + COL_NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NOTE_BOOK_UUID + " TEXT, "
                + COL_NOTE_BOOK_TITLE + " TEXT, "
                + COL_NOTE_CHAPTER_TITLE + " TEXT, "
                + COL_NOTE_POSITION_STR + " TEXT, "
                + COL_NOTE_PAGE_NUMBER + " INTEGER DEFAULT 0, "
                + COL_NOTE_SELECTED_TEXT + " TEXT, "
                + COL_NOTE_TEXT + " TEXT, "
                + COL_NOTE_CREATED_AT + " INTEGER, "
                + COL_NOTE_UPDATED_AT + " INTEGER"
                + ");";

        String createQuotesTable = "CREATE TABLE " + TABLE_QUOTES + " ("
                + COL_QUOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_QUOTE_BOOK_UUID + " TEXT, "
                + COL_QUOTE_BOOK_TITLE + " TEXT, "
                + COL_QUOTE_BOOK_AUTHOR + " TEXT, "
                + COL_QUOTE_CHAPTER + " TEXT, "
                + COL_QUOTE_TEXT + " TEXT, "
                + COL_QUOTE_COLOR_LABEL + " TEXT, "
                + COL_QUOTE_CREATED_AT + " INTEGER"
                + ");";

        String createShelvesTable = "CREATE TABLE " + TABLE_SHELVES + " ("
                + COL_SHELF_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SHELF_NAME + " TEXT UNIQUE, "
                + COL_SHELF_CREATED_AT + " INTEGER"
                + ");";

        String createShelfBooksTable = "CREATE TABLE " + TABLE_SHELF_BOOKS + " ("
                + COL_SB_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SB_SHELF_ID + " INTEGER, "
                + COL_SB_BOOK_ID + " INTEGER"
                + ");";

        String createFolderSourcesTable = "CREATE TABLE " + TABLE_FOLDER_SOURCES + " ("
                + COL_FS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_FS_TREE_URI + " TEXT UNIQUE, "
                + COL_FS_DISPLAY_NAME + " TEXT, "
                + COL_FS_ADDED_AT + " INTEGER, "
                + COL_FS_LAST_SCANNED + " INTEGER, "
                + COL_FS_BOOKS_COUNT + " INTEGER DEFAULT 0, "
                + COL_FS_IS_ACCESSIBLE + " INTEGER DEFAULT 1"
                + ");";

        String createCustomThemesTable = "CREATE TABLE " + TABLE_CUSTOM_THEMES + " ("
                + COL_THEME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_THEME_NAME + " TEXT UNIQUE, "
                + COL_THEME_BG + " TEXT, "
                + COL_THEME_TEXT + " TEXT, "
                + COL_THEME_SURFACE + " TEXT, "
                + COL_THEME_STROKE + " TEXT, "
                + COL_THEME_JSON + " TEXT, "
                + COL_THEME_CREATED_AT + " INTEGER"
                + ");";

        db.execSQL(createBooksTable);
        db.execSQL(createBookmarksTable);
        db.execSQL(createNotesTable);
        db.execSQL(createQuotesTable);
        db.execSQL(createShelvesTable);
        db.execSQL(createShelfBooksTable);
        db.execSQL(createFolderSourcesTable);
        db.execSQL(createCustomThemesTable);

        db.execSQL("CREATE INDEX idx_books_last_opened ON " + TABLE_BOOKS + " (" + COL_BOOK_LAST_OPENED + " DESC);");
        db.execSQL("CREATE INDEX idx_bookmarks_uuid ON " + TABLE_BOOKMARKS + " (" + COL_BM_BOOK_UUID + ");");
        db.execSQL("CREATE INDEX idx_notes_uuid ON " + TABLE_NOTES + " (" + COL_NOTE_BOOK_UUID + ");");
        db.execSQL("CREATE INDEX idx_quotes_uuid ON " + TABLE_QUOTES + " (" + COL_QUOTE_BOOK_UUID + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_BOOK_UUID + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_BOOK_CACHED_PATH + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_BOOK_SERIES + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_BOOK_SERIES_NUM + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_BOOK_FORMAT + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_BOOK_IS_HIDDEN + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_BOOK_READING_TIME + " INTEGER DEFAULT 0;");
            } catch (Exception ignored) {}

            try {
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_BOOK_UUID + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_BOOK_TITLE + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_BOOK_AUTHOR + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_SERIES_INFO + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_POSITION_STR + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_PAGE_NUMBER + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_COMMENT + " TEXT;");
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BM_TYPE + " INTEGER DEFAULT 0;");
            } catch (Exception ignored) {}

            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NOTES + " ("
                        + COL_NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_NOTE_BOOK_UUID + " TEXT, "
                        + COL_NOTE_BOOK_TITLE + " TEXT, "
                        + COL_NOTE_CHAPTER_TITLE + " TEXT, "
                        + COL_NOTE_POSITION_STR + " TEXT, "
                        + COL_NOTE_PAGE_NUMBER + " INTEGER DEFAULT 0, "
                        + COL_NOTE_SELECTED_TEXT + " TEXT, "
                        + COL_NOTE_TEXT + " TEXT, "
                        + COL_NOTE_CREATED_AT + " INTEGER, "
                        + COL_NOTE_UPDATED_AT + " INTEGER);");

                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_QUOTES + " ("
                        + COL_QUOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_QUOTE_BOOK_UUID + " TEXT, "
                        + COL_QUOTE_BOOK_TITLE + " TEXT, "
                        + COL_QUOTE_BOOK_AUTHOR + " TEXT, "
                        + COL_QUOTE_CHAPTER + " TEXT, "
                        + COL_QUOTE_TEXT + " TEXT, "
                        + COL_QUOTE_COLOR_LABEL + " TEXT, "
                        + COL_QUOTE_CREATED_AT + " INTEGER);");

                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SHELVES + " ("
                        + COL_SHELF_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_SHELF_NAME + " TEXT UNIQUE, "
                        + COL_SHELF_CREATED_AT + " INTEGER);");

                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SHELF_BOOKS + " ("
                        + COL_SB_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_SB_SHELF_ID + " INTEGER, "
                        + COL_SB_BOOK_ID + " INTEGER);");

                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FOLDER_SOURCES + " ("
                        + COL_FS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_FS_TREE_URI + " TEXT UNIQUE, "
                        + COL_FS_DISPLAY_NAME + " TEXT, "
                        + COL_FS_ADDED_AT + " INTEGER, "
                        + COL_FS_LAST_SCANNED + " INTEGER, "
                        + COL_FS_BOOKS_COUNT + " INTEGER DEFAULT 0, "
                        + COL_FS_IS_ACCESSIBLE + " INTEGER DEFAULT 1);");

                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CUSTOM_THEMES + " ("
                        + COL_THEME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_THEME_NAME + " TEXT UNIQUE, "
                        + COL_THEME_BG + " TEXT, "
                        + COL_THEME_TEXT + " TEXT, "
                        + COL_THEME_SURFACE + " TEXT, "
                        + COL_THEME_STROKE + " TEXT, "
                        + COL_THEME_JSON + " TEXT, "
                        + COL_THEME_CREATED_AT + " INTEGER);");
            } catch (Exception ignored) {}
        }

        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_CUSTOM_THEMES + " ADD COLUMN " + COL_THEME_JSON + " TEXT;");
            } catch (Exception ignored) {}
        }
    }
}
