package com.bookr.reader.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.bookr.reader.data.model.BookItem;
import com.bookr.reader.data.model.BookmarkItem;
import com.bookr.reader.data.model.CustomThemeItem;
import com.bookr.reader.data.model.FolderSourceItem;
import com.bookr.reader.data.model.MangaBookmarkItem;
import com.bookr.reader.data.model.MangaChapterItem;
import com.bookr.reader.data.model.MangaItem;
import com.bookr.reader.data.model.NoteItem;
import com.bookr.reader.data.model.OpdsCatalogItem;
import com.bookr.reader.data.model.QuoteItem;
import com.bookr.reader.data.model.ShelfItem;
import com.bookr.reader.data.model.ThemeConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookRepository {
    private static final String TAG = "BookR.Repository";

    public static final int SORT_TITLE_ASC = 0;
    public static final int SORT_TITLE_DESC = 1;
    public static final int SORT_AUTHOR = 2;
    public static final int SORT_RECENT = 3;
    public static final int SORT_DATE_ADDED = 4;

    private final BookDatabaseHelper dbHelper;
    private final Context context;

    public BookRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = BookDatabaseHelper.getInstance(context);
    }

    // --- BOOKS ---

    public synchronized long insertOrUpdateBook(BookItem book) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        BookItem existing = getBookByUri(book.getUri());
        if (existing == null && book.getUuid() != null) {
            existing = getBookByUuid(book.getUuid());
        }

        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_BOOK_URI, book.getUri());
        cv.put(BookDatabaseHelper.COL_BOOK_TITLE, book.getTitle());
        cv.put(BookDatabaseHelper.COL_BOOK_AUTHOR, book.getAuthor());
        cv.put(BookDatabaseHelper.COL_BOOK_SERIES, book.getSeries());
        cv.put(BookDatabaseHelper.COL_BOOK_SERIES_NUM, book.getSeriesNumber());
        cv.put(BookDatabaseHelper.COL_BOOK_FORMAT, book.getFormat());
        cv.put(BookDatabaseHelper.COL_BOOK_ANNOTATION, book.getAnnotation());
        if (book.getCoverPath() != null) {
            cv.put(BookDatabaseHelper.COL_BOOK_COVER_PATH, book.getCoverPath());
        }
        if (book.getCachedPath() != null) {
            cv.put(BookDatabaseHelper.COL_BOOK_CACHED_PATH, book.getCachedPath());
        }
        cv.put(BookDatabaseHelper.COL_BOOK_FILE_SIZE, book.getFileSize());

        if (existing != null) {
            book.setId(existing.getId());
            book.setUuid(existing.getUuid());
            db.update(BookDatabaseHelper.TABLE_BOOKS, cv,
                    BookDatabaseHelper.COL_BOOK_ID + "=?",
                    new String[]{String.valueOf(existing.getId())});
            return existing.getId();
        } else {
            String uuid = book.getUuid() != null ? book.getUuid() : UUID.randomUUID().toString();
            cv.put(BookDatabaseHelper.COL_BOOK_UUID, uuid);
            cv.put(BookDatabaseHelper.COL_BOOK_DATE_ADDED, System.currentTimeMillis());
            cv.put(BookDatabaseHelper.COL_BOOK_LAST_OPENED, 0);
            cv.put(BookDatabaseHelper.COL_BOOK_PROGRESS_PERCENT, 0f);
            cv.put(BookDatabaseHelper.COL_BOOK_CURRENT_POSITION, 0);
            cv.put(BookDatabaseHelper.COL_BOOK_TOTAL_PAGES, 0);
            cv.put(BookDatabaseHelper.COL_BOOK_IS_FAVORITE, 0);
            cv.put(BookDatabaseHelper.COL_BOOK_IS_HIDDEN, 0);
            cv.put(BookDatabaseHelper.COL_BOOK_READING_TIME, 0);
            long id = db.insert(BookDatabaseHelper.TABLE_BOOKS, null, cv);
            book.setId(id);
            book.setUuid(uuid);
            return id;
        }
    }

    public synchronized BookItem getBookById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null,
                BookDatabaseHelper.COL_BOOK_ID + "=?",
                new String[]{String.valueOf(id)},
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            BookItem item = cursorToBook(cursor);
            cursor.close();
            return item;
        }
        if (cursor != null) cursor.close();
        return null;
    }

    public synchronized BookItem getBookByUri(String uri) {
        if (uri == null) return null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null,
                BookDatabaseHelper.COL_BOOK_URI + "=?",
                new String[]{uri},
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            BookItem item = cursorToBook(cursor);
            cursor.close();
            return item;
        }
        if (cursor != null) cursor.close();
        return null;
    }

    public synchronized BookItem getBookByUuid(String uuid) {
        if (uuid == null) return null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null,
                BookDatabaseHelper.COL_BOOK_UUID + "=?",
                new String[]{uuid},
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            BookItem item = cursorToBook(cursor);
            cursor.close();
            return item;
        }
        if (cursor != null) cursor.close();
        return null;
    }

    public synchronized List<BookItem> getAllBooks(int sortOrder, String query) {
        StringBuilder selection = new StringBuilder(BookDatabaseHelper.COL_BOOK_IS_HIDDEN + " = 0");
        List<String> argsList = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            selection.append(" AND (")
                    .append(BookDatabaseHelper.COL_BOOK_TITLE).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_BOOK_AUTHOR).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_BOOK_SERIES).append(" LIKE ?)");
            String likeArg = "%" + query.trim() + "%";
            argsList.add(likeArg);
            argsList.add(likeArg);
            argsList.add(likeArg);
        }

        String orderBy = getOrderByClause(sortOrder);
        String[] selectionArgs = argsList.isEmpty() ? null : argsList.toArray(new String[0]);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null, selection.toString(), selectionArgs, null, null, orderBy);
        List<BookItem> result = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                result.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        return result;
    }

    public synchronized List<BookItem> getRecentBooks(String query) {
        StringBuilder selection = new StringBuilder(BookDatabaseHelper.COL_BOOK_LAST_OPENED + " > 0 AND " + BookDatabaseHelper.COL_BOOK_IS_HIDDEN + " = 0");
        List<String> argsList = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            selection.append(" AND (")
                    .append(BookDatabaseHelper.COL_BOOK_TITLE).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_BOOK_AUTHOR).append(" LIKE ?)");
            String likeArg = "%" + query.trim() + "%";
            argsList.add(likeArg);
            argsList.add(likeArg);
        }

        String orderBy = BookDatabaseHelper.COL_BOOK_LAST_OPENED + " DESC";
        String[] selectionArgs = argsList.isEmpty() ? null : argsList.toArray(new String[0]);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null, selection.toString(), selectionArgs, null, null, orderBy);
        List<BookItem> result = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                result.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        return result;
    }

    public synchronized List<BookItem> getFavoriteBooks(String query) {
        StringBuilder selection = new StringBuilder(BookDatabaseHelper.COL_BOOK_IS_FAVORITE + " = 1 AND " + BookDatabaseHelper.COL_BOOK_IS_HIDDEN + " = 0");
        List<String> argsList = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            selection.append(" AND (")
                    .append(BookDatabaseHelper.COL_BOOK_TITLE).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_BOOK_AUTHOR).append(" LIKE ?)");
            String likeArg = "%" + query.trim() + "%";
            argsList.add(likeArg);
            argsList.add(likeArg);
        }

        String orderBy = BookDatabaseHelper.COL_BOOK_TITLE + " COLLATE NOCASE ASC";
        String[] selectionArgs = argsList.isEmpty() ? null : argsList.toArray(new String[0]);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null, selection.toString(), selectionArgs, null, null, orderBy);
        List<BookItem> result = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                result.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        return result;
    }

    public synchronized List<BookItem> getReadingBooks(String query) {
        StringBuilder selection = new StringBuilder(
                BookDatabaseHelper.COL_BOOK_IS_HIDDEN + " = 0 AND "
                + "((" + BookDatabaseHelper.COL_BOOK_PROGRESS_PERCENT + " > 0 AND " + BookDatabaseHelper.COL_BOOK_PROGRESS_PERCENT + " < 98) "
                + "OR (" + BookDatabaseHelper.COL_BOOK_LAST_OPENED + " > 0 AND " + BookDatabaseHelper.COL_BOOK_PROGRESS_PERCENT + " < 98))");
        List<String> argsList = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            selection.append(" AND (")
                    .append(BookDatabaseHelper.COL_BOOK_TITLE).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_BOOK_AUTHOR).append(" LIKE ?)");
            String likeArg = "%" + query.trim() + "%";
            argsList.add(likeArg);
            argsList.add(likeArg);
        }
        String orderBy = BookDatabaseHelper.COL_BOOK_LAST_OPENED + " DESC";
        String[] selectionArgs = argsList.isEmpty() ? null : argsList.toArray(new String[0]);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null, selection.toString(), selectionArgs, null, null, orderBy);
        List<BookItem> result = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                result.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        return result;
    }

    public synchronized List<BookItem> getCompletedBooks(String query) {
        StringBuilder selection = new StringBuilder(
                BookDatabaseHelper.COL_BOOK_IS_HIDDEN + " = 0 AND "
                + BookDatabaseHelper.COL_BOOK_PROGRESS_PERCENT + " >= 98");
        List<String> argsList = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            selection.append(" AND (")
                    .append(BookDatabaseHelper.COL_BOOK_TITLE).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_BOOK_AUTHOR).append(" LIKE ?)");
            String likeArg = "%" + query.trim() + "%";
            argsList.add(likeArg);
            argsList.add(likeArg);
        }
        String orderBy = BookDatabaseHelper.COL_BOOK_LAST_OPENED + " DESC";
        String[] selectionArgs = argsList.isEmpty() ? null : argsList.toArray(new String[0]);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKS, null, selection.toString(), selectionArgs, null, null, orderBy);
        List<BookItem> result = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                result.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        return result;
    }

    public synchronized void setFavorite(long bookId, boolean isFavorite) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_BOOK_IS_FAVORITE, isFavorite ? 1 : 0);
        db.update(BookDatabaseHelper.TABLE_BOOKS, cv,
                BookDatabaseHelper.COL_BOOK_ID + "=?",
                new String[]{String.valueOf(bookId)});
    }

    public synchronized void setHidden(long bookId, boolean isHidden) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_BOOK_IS_HIDDEN, isHidden ? 1 : 0);
        db.update(BookDatabaseHelper.TABLE_BOOKS, cv,
                BookDatabaseHelper.COL_BOOK_ID + "=?",
                new String[]{String.valueOf(bookId)});
    }

    public synchronized void updateProgress(long bookId, float progress, int position, int totalPages) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_BOOK_PROGRESS_PERCENT, progress);
        cv.put(BookDatabaseHelper.COL_BOOK_CURRENT_POSITION, position);
        if (totalPages > 0) {
            cv.put(BookDatabaseHelper.COL_BOOK_TOTAL_PAGES, totalPages);
        }
        cv.put(BookDatabaseHelper.COL_BOOK_LAST_OPENED, System.currentTimeMillis());
        db.update(BookDatabaseHelper.TABLE_BOOKS, cv,
                BookDatabaseHelper.COL_BOOK_ID + "=?",
                new String[]{String.valueOf(bookId)});
    }

    public synchronized void addReadingTime(long bookId, long additionalSeconds) {
        BookItem item = getBookById(bookId);
        if (item != null) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(BookDatabaseHelper.COL_BOOK_READING_TIME, item.getReadingTimeSeconds() + additionalSeconds);
            db.update(BookDatabaseHelper.TABLE_BOOKS, cv,
                    BookDatabaseHelper.COL_BOOK_ID + "=?",
                    new String[]{String.valueOf(bookId)});
        }
    }

    public synchronized void updateLastOpened(long bookId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_BOOK_LAST_OPENED, System.currentTimeMillis());
        db.update(BookDatabaseHelper.TABLE_BOOKS, cv,
                BookDatabaseHelper.COL_BOOK_ID + "=?",
                new String[]{String.valueOf(bookId)});
    }

    public synchronized void deleteBook(long bookId) {
        BookItem item = getBookById(bookId);
        if (item != null) {
            if (item.getCoverPath() != null) {
                File coverFile = new File(item.getCoverPath());
                if (coverFile.exists()) coverFile.delete();
            }
            if (item.getCachedPath() != null) {
                File cacheFile = new File(item.getCachedPath());
                if (cacheFile.exists()) cacheFile.delete();
            }
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Notice: bookmarks and notes are NOT deleted when book is removed, keeping user thoughts safe!
        db.delete(BookDatabaseHelper.TABLE_SHELF_BOOKS,
                BookDatabaseHelper.COL_SB_BOOK_ID + "=?",
                new String[]{String.valueOf(bookId)});
        db.delete(BookDatabaseHelper.TABLE_BOOKS,
                BookDatabaseHelper.COL_BOOK_ID + "=?",
                new String[]{String.valueOf(bookId)});
    }

    // --- UNIVERSAL BOOKMARKS ---

    public synchronized long addBookmark(BookmarkItem bookmark) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_BM_BOOK_UUID, bookmark.getBookUuid());
        cv.put(BookDatabaseHelper.COL_BM_BOOK_TITLE, bookmark.getBookTitle());
        cv.put(BookDatabaseHelper.COL_BM_BOOK_AUTHOR, bookmark.getBookAuthor());
        cv.put(BookDatabaseHelper.COL_BM_SERIES_INFO, bookmark.getSeriesInfo());
        cv.put(BookDatabaseHelper.COL_BM_CHAPTER_TITLE, bookmark.getChapterTitle());
        cv.put(BookDatabaseHelper.COL_BM_POSITION_STR, bookmark.getPositionStr());
        cv.put(BookDatabaseHelper.COL_BM_PAGE_NUMBER, bookmark.getPageNumber());
        cv.put(BookDatabaseHelper.COL_BM_SNIPPET, bookmark.getSnippet());
        cv.put(BookDatabaseHelper.COL_BM_COMMENT, bookmark.getComment());
        cv.put(BookDatabaseHelper.COL_BM_TYPE, bookmark.getBookmarkType());
        cv.put(BookDatabaseHelper.COL_BM_CREATED_AT, bookmark.getCreatedAt() > 0 ? bookmark.getCreatedAt() : System.currentTimeMillis());
        return db.insert(BookDatabaseHelper.TABLE_BOOKMARKS, null, cv);
    }

    public synchronized List<BookmarkItem> getAllBookmarks() {
        return getAllBookmarks(null);
    }

    public synchronized List<BookmarkItem> getAllBookmarks(String query) {
        String selection = null;
        String[] args = null;
        if (query != null && !query.trim().isEmpty()) {
            selection = BookDatabaseHelper.COL_BM_BOOK_TITLE + " LIKE ? OR "
                    + BookDatabaseHelper.COL_BM_BOOK_AUTHOR + " LIKE ? OR "
                    + BookDatabaseHelper.COL_BM_CHAPTER_TITLE + " LIKE ? OR "
                    + BookDatabaseHelper.COL_BM_SNIPPET + " LIKE ? OR "
                    + BookDatabaseHelper.COL_BM_COMMENT + " LIKE ?";
            String q = "%" + query.trim() + "%";
            args = new String[]{q, q, q, q, q};
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKMARKS, null,
                selection, args, null, null,
                BookDatabaseHelper.COL_BM_CREATED_AT + " DESC");
        List<BookmarkItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToBookmark(cursor));
            }
            cursor.close();
        }
        return list;
    }

    public synchronized List<BookmarkItem> getBookmarksForBook(String bookUuid) {
        if (bookUuid == null) return new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_BOOKMARKS, null,
                BookDatabaseHelper.COL_BM_BOOK_UUID + "=?",
                new String[]{bookUuid},
                null, null,
                BookDatabaseHelper.COL_BM_CREATED_AT + " DESC");
        List<BookmarkItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToBookmark(cursor));
            }
            cursor.close();
        }
        return list;
    }

    public synchronized List<BookmarkItem> getBookmarks(long bookId) {
        BookItem book = getBookById(bookId);
        if (book != null && book.getUuid() != null) {
            return getBookmarksForBook(book.getUuid());
        }
        return getAllBookmarks();
    }

    public synchronized void deleteBookmark(long bookmarkId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_BOOKMARKS,
                BookDatabaseHelper.COL_BM_ID + "=?",
                new String[]{String.valueOf(bookmarkId)});
    }

    // --- NOTES ---

    public synchronized long addNote(NoteItem note) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_NOTE_BOOK_UUID, note.getBookUuid());
        cv.put(BookDatabaseHelper.COL_NOTE_BOOK_TITLE, note.getBookTitle());
        cv.put(BookDatabaseHelper.COL_NOTE_CHAPTER_TITLE, note.getChapterTitle());
        cv.put(BookDatabaseHelper.COL_NOTE_POSITION_STR, note.getPositionStr());
        cv.put(BookDatabaseHelper.COL_NOTE_PAGE_NUMBER, note.getPageNumber());
        cv.put(BookDatabaseHelper.COL_NOTE_SELECTED_TEXT, note.getSelectedText());
        cv.put(BookDatabaseHelper.COL_NOTE_TEXT, note.getNoteText());
        long now = System.currentTimeMillis();
        cv.put(BookDatabaseHelper.COL_NOTE_CREATED_AT, note.getCreatedAt() > 0 ? note.getCreatedAt() : now);
        cv.put(BookDatabaseHelper.COL_NOTE_UPDATED_AT, now);
        return db.insert(BookDatabaseHelper.TABLE_NOTES, null, cv);
    }

    public synchronized void updateNote(long id, String newText) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_NOTE_TEXT, newText);
        cv.put(BookDatabaseHelper.COL_NOTE_UPDATED_AT, System.currentTimeMillis());
        db.update(BookDatabaseHelper.TABLE_NOTES, cv,
                BookDatabaseHelper.COL_NOTE_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public synchronized void deleteNote(long noteId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_NOTES,
                BookDatabaseHelper.COL_NOTE_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public synchronized List<NoteItem> getAllNotes(String query) {
        String selection = null;
        String[] args = null;
        if (query != null && !query.trim().isEmpty()) {
            selection = BookDatabaseHelper.COL_NOTE_TEXT + " LIKE ? OR "
                    + BookDatabaseHelper.COL_NOTE_BOOK_TITLE + " LIKE ? OR "
                    + BookDatabaseHelper.COL_NOTE_SELECTED_TEXT + " LIKE ?";
            String q = "%" + query.trim() + "%";
            args = new String[]{q, q, q};
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_NOTES, null, selection, args, null, null,
                BookDatabaseHelper.COL_NOTE_UPDATED_AT + " DESC");
        List<NoteItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToNote(cursor));
            }
            cursor.close();
        }
        return list;
    }

    // --- QUOTES ---

    public synchronized long addQuote(QuoteItem quote) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_QUOTE_BOOK_UUID, quote.getBookUuid());
        cv.put(BookDatabaseHelper.COL_QUOTE_BOOK_TITLE, quote.getBookTitle());
        cv.put(BookDatabaseHelper.COL_QUOTE_BOOK_AUTHOR, quote.getBookAuthor());
        cv.put(BookDatabaseHelper.COL_QUOTE_CHAPTER, quote.getChapterTitle());
        cv.put(BookDatabaseHelper.COL_QUOTE_TEXT, quote.getQuoteText());
        cv.put(BookDatabaseHelper.COL_QUOTE_COLOR_LABEL, quote.getColorLabel());
        cv.put(BookDatabaseHelper.COL_QUOTE_CREATED_AT, quote.getCreatedAt() > 0 ? quote.getCreatedAt() : System.currentTimeMillis());
        return db.insert(BookDatabaseHelper.TABLE_QUOTES, null, cv);
    }

    public synchronized void deleteQuote(long quoteId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_QUOTES,
                BookDatabaseHelper.COL_QUOTE_ID + "=?",
                new String[]{String.valueOf(quoteId)});
    }

    public synchronized List<QuoteItem> getAllQuotes(String query) {
        String selection = null;
        String[] args = null;
        if (query != null && !query.trim().isEmpty()) {
            selection = BookDatabaseHelper.COL_QUOTE_TEXT + " LIKE ? OR "
                    + BookDatabaseHelper.COL_QUOTE_BOOK_TITLE + " LIKE ? OR "
                    + BookDatabaseHelper.COL_QUOTE_BOOK_AUTHOR + " LIKE ?";
            String q = "%" + query.trim() + "%";
            args = new String[]{q, q, q};
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_QUOTES, null, selection, args, null, null,
                BookDatabaseHelper.COL_QUOTE_CREATED_AT + " DESC");
        List<QuoteItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToQuote(cursor));
            }
            cursor.close();
        }
        return list;
    }

    // --- SHELVES ---

    public synchronized long createShelf(String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_SHELF_NAME, name);
        cv.put(BookDatabaseHelper.COL_SHELF_CREATED_AT, System.currentTimeMillis());
        return db.insertWithOnConflict(BookDatabaseHelper.TABLE_SHELVES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized long addShelf(ShelfItem shelf) {
        return createShelf(shelf != null ? shelf.getName() : "Полка");
    }

    public synchronized List<ShelfItem> getAllShelves() {
        return getAllShelves(null);
    }

    public synchronized List<ShelfItem> getAllShelves(String query) {
        String selection = null;
        String[] args = null;
        if (query != null && !query.trim().isEmpty()) {
            selection = BookDatabaseHelper.COL_SHELF_NAME + " LIKE ?";
            args = new String[]{"%" + query.trim() + "%"};
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_SHELVES, null, selection, args, null, null,
                BookDatabaseHelper.COL_SHELF_NAME + " COLLATE NOCASE ASC");
        List<ShelfItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ShelfItem s = new ShelfItem();
                s.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_SHELF_ID)));
                s.setName(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_SHELF_NAME)));
                s.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_SHELF_CREATED_AT)));
                s.setBookCount(getShelfBooksCount(s.getId()));
                list.add(s);
            }
            cursor.close();
        }
        return list;
    }

    public synchronized int getShelfBooksCount(long shelfId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT COUNT(DISTINCT b." + BookDatabaseHelper.COL_BOOK_ID + ") FROM "
                + BookDatabaseHelper.TABLE_BOOKS + " b INNER JOIN "
                + BookDatabaseHelper.TABLE_SHELF_BOOKS + " sb ON b."
                + BookDatabaseHelper.COL_BOOK_ID + " = sb." + BookDatabaseHelper.COL_SB_BOOK_ID
                + " WHERE sb." + BookDatabaseHelper.COL_SB_SHELF_ID + "=? AND b."
                + BookDatabaseHelper.COL_BOOK_IS_HIDDEN + "=0";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(shelfId)});
        int count = 0;
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    count = c.getInt(0);
                }
            } finally {
                c.close();
            }
        }
        return count;
    }

    public synchronized void deleteShelf(long shelfId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_SHELF_BOOKS, BookDatabaseHelper.COL_SB_SHELF_ID + "=?", new String[]{String.valueOf(shelfId)});
        db.delete(BookDatabaseHelper.TABLE_SHELVES, BookDatabaseHelper.COL_SHELF_ID + "=?", new String[]{String.valueOf(shelfId)});
    }

    public synchronized void addBookToShelf(long shelfId, long bookId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(BookDatabaseHelper.TABLE_SHELF_BOOKS,
                new String[]{BookDatabaseHelper.COL_SB_ID},
                BookDatabaseHelper.COL_SB_SHELF_ID + "=? AND " + BookDatabaseHelper.COL_SB_BOOK_ID + "=?",
                new String[]{String.valueOf(shelfId), String.valueOf(bookId)},
                null, null, null);
        boolean exists = false;
        if (c != null) {
            exists = c.moveToFirst();
            c.close();
        }
        if (!exists) {
            ContentValues cv = new ContentValues();
            cv.put(BookDatabaseHelper.COL_SB_SHELF_ID, shelfId);
            cv.put(BookDatabaseHelper.COL_SB_BOOK_ID, bookId);
            db.insertWithOnConflict(BookDatabaseHelper.TABLE_SHELF_BOOKS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public synchronized void removeBookFromShelf(long shelfId, long bookId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_SHELF_BOOKS,
                BookDatabaseHelper.COL_SB_SHELF_ID + "=? AND " + BookDatabaseHelper.COL_SB_BOOK_ID + "=?",
                new String[]{String.valueOf(shelfId), String.valueOf(bookId)});
    }

    public synchronized List<Long> getShelfIdsForBook(long bookId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(BookDatabaseHelper.TABLE_SHELF_BOOKS,
                new String[]{BookDatabaseHelper.COL_SB_SHELF_ID},
                BookDatabaseHelper.COL_SB_BOOK_ID + "=?",
                new String[]{String.valueOf(bookId)},
                null, null, null);
        List<Long> ids = new ArrayList<>();
        if (c != null) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
            c.close();
        }
        return ids;
    }

    public synchronized List<BookItem> getBooksOnShelf(long shelfId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT DISTINCT b.* FROM " + BookDatabaseHelper.TABLE_BOOKS + " b "
                + "INNER JOIN " + BookDatabaseHelper.TABLE_SHELF_BOOKS + " sb "
                + "ON b." + BookDatabaseHelper.COL_BOOK_ID + " = sb." + BookDatabaseHelper.COL_SB_BOOK_ID
                + " WHERE sb." + BookDatabaseHelper.COL_SB_SHELF_ID + "=? AND b."
                + BookDatabaseHelper.COL_BOOK_IS_HIDDEN + "=0 "
                + "ORDER BY b." + BookDatabaseHelper.COL_BOOK_TITLE + " COLLATE NOCASE ASC";
        Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(shelfId)});
        List<BookItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        return list;
    }

    // --- FOLDER SOURCES ---

    public synchronized long addFolderSource(FolderSourceItem source) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor existing = db.query(BookDatabaseHelper.TABLE_FOLDER_SOURCES,
                new String[]{BookDatabaseHelper.COL_FS_ID},
                BookDatabaseHelper.COL_FS_TREE_URI + "=?",
                new String[]{source.getTreeUri()},
                null, null, null);
        long existingId = -1;
        if (existing != null) {
            if (existing.moveToFirst()) {
                existingId = existing.getLong(0);
            }
            existing.close();
        }

        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_FS_TREE_URI, source.getTreeUri());
        cv.put(BookDatabaseHelper.COL_FS_DISPLAY_NAME, source.getDisplayName());
        cv.put(BookDatabaseHelper.COL_FS_ADDED_AT, source.getAddedAt());
        cv.put(BookDatabaseHelper.COL_FS_LAST_SCANNED, source.getLastScanned());
        cv.put(BookDatabaseHelper.COL_FS_BOOKS_COUNT, source.getBooksCount());
        cv.put(BookDatabaseHelper.COL_FS_IS_ACCESSIBLE, source.isAccessible() ? 1 : 0);

        if (existingId > 0) {
            db.update(BookDatabaseHelper.TABLE_FOLDER_SOURCES, cv,
                    BookDatabaseHelper.COL_FS_ID + "=?",
                    new String[]{String.valueOf(existingId)});
            return existingId;
        }
        return db.insertWithOnConflict(BookDatabaseHelper.TABLE_FOLDER_SOURCES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized List<FolderSourceItem> getAllFolderSources() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_FOLDER_SOURCES, null, null, null, null, null,
                BookDatabaseHelper.COL_FS_ADDED_AT + " DESC");
        List<FolderSourceItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                FolderSourceItem item = new FolderSourceItem();
                item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_FS_ID)));
                item.setTreeUri(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_FS_TREE_URI)));
                item.setDisplayName(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_FS_DISPLAY_NAME)));
                item.setAddedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_FS_ADDED_AT)));
                item.setLastScanned(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_FS_LAST_SCANNED)));
                item.setBooksCount(cursor.getInt(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_FS_BOOKS_COUNT)));
                item.setAccessible(cursor.getInt(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_FS_IS_ACCESSIBLE)) == 1);
                list.add(item);
            }
            cursor.close();
        }
        return list;
    }

    public synchronized void updateFolderSourceCount(long id, int count) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_FS_BOOKS_COUNT, count);
        cv.put(BookDatabaseHelper.COL_FS_LAST_SCANNED, System.currentTimeMillis());
        cv.put(BookDatabaseHelper.COL_FS_IS_ACCESSIBLE, 1);
        db.update(BookDatabaseHelper.TABLE_FOLDER_SOURCES, cv,
                BookDatabaseHelper.COL_FS_ID + "=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setFolderAccessible(long id, boolean accessible) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_FS_IS_ACCESSIBLE, accessible ? 1 : 0);
        db.update(BookDatabaseHelper.TABLE_FOLDER_SOURCES, cv,
                BookDatabaseHelper.COL_FS_ID + "=?", new String[]{String.valueOf(id)});
    }

    public synchronized void deleteFolderSource(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_FOLDER_SOURCES,
                BookDatabaseHelper.COL_FS_ID + "=?", new String[]{String.valueOf(id)});
    }

    // --- CUSTOM THEMES ---

    public synchronized long addCustomTheme(CustomThemeItem theme) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_THEME_NAME, theme.getName());
        cv.put(BookDatabaseHelper.COL_THEME_BG, theme.getBgColor());
        cv.put(BookDatabaseHelper.COL_THEME_TEXT, theme.getTextColor());
        cv.put(BookDatabaseHelper.COL_THEME_SURFACE, theme.getSurfaceColor());
        cv.put(BookDatabaseHelper.COL_THEME_STROKE, theme.getStrokeColor());
        cv.put(BookDatabaseHelper.COL_THEME_CREATED_AT, theme.getCreatedAt());
        return db.insertWithOnConflict(BookDatabaseHelper.TABLE_CUSTOM_THEMES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized long saveThemeConfig(ThemeConfig config) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_THEME_NAME, config.getName());
        cv.put(BookDatabaseHelper.COL_THEME_BG, config.getAppBgColor());
        cv.put(BookDatabaseHelper.COL_THEME_TEXT, config.getTextColor());
        cv.put(BookDatabaseHelper.COL_THEME_SURFACE, config.getCardBgColor());
        cv.put(BookDatabaseHelper.COL_THEME_STROKE, config.getBorderColor());
        cv.put(BookDatabaseHelper.COL_THEME_JSON, config.toJson().toString());
        cv.put(BookDatabaseHelper.COL_THEME_CREATED_AT, config.getCreatedAt());

        if (config.getId() > 0) {
            db.update(BookDatabaseHelper.TABLE_CUSTOM_THEMES, cv,
                    BookDatabaseHelper.COL_THEME_ID + "=?", new String[]{String.valueOf(config.getId())});
            return config.getId();
        } else {
            long newId = db.insertWithOnConflict(BookDatabaseHelper.TABLE_CUSTOM_THEMES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            config.setId(newId);
            return newId;
        }
    }

    public synchronized List<ThemeConfig> getAllThemeConfigs() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_CUSTOM_THEMES, null, null, null, null, null,
                BookDatabaseHelper.COL_THEME_NAME + " COLLATE NOCASE ASC");
        List<ThemeConfig> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String jsonData = null;
                int jsonIdx = cursor.getColumnIndex(BookDatabaseHelper.COL_THEME_JSON);
                if (jsonIdx >= 0) {
                    jsonData = cursor.getString(jsonIdx);
                }

                ThemeConfig config;
                if (jsonData != null && !jsonData.trim().isEmpty()) {
                    config = ThemeConfig.fromJson(jsonData);
                } else {
                    config = new ThemeConfig();
                    config.setName(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_NAME)));
                    config.setAppBgColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_BG)));
                    config.setTextColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_TEXT)));
                    config.setCardBgColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_SURFACE)));
                    config.setBorderColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_STROKE)));
                    config.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_CREATED_AT)));
                }
                config.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_ID)));
                list.add(config);
            }
            cursor.close();
        }
        return list;
    }

    public synchronized List<CustomThemeItem> getAllCustomThemes() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(BookDatabaseHelper.TABLE_CUSTOM_THEMES, null, null, null, null, null,
                BookDatabaseHelper.COL_THEME_NAME + " COLLATE NOCASE ASC");
        List<CustomThemeItem> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                CustomThemeItem item = new CustomThemeItem();
                item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_ID)));
                item.setName(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_NAME)));
                item.setBgColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_BG)));
                item.setTextColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_TEXT)));
                item.setSurfaceColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_SURFACE)));
                item.setStrokeColor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_STROKE)));
                item.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_THEME_CREATED_AT)));
                list.add(item);
            }
            cursor.close();
        }
        return list;
    }

    public synchronized void deleteCustomTheme(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_CUSTOM_THEMES,
                BookDatabaseHelper.COL_THEME_ID + "=?", new String[]{String.valueOf(id)});
    }

    // --- MANGA, CHAPTERS, MANGA BOOKMARKS & OPDS CATALOGS ---

    public synchronized long saveOrUpdateManga(MangaItem manga) {
        if (manga == null || manga.getMangaId().isEmpty()) return -1;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        MangaItem existing = getMangaByExternalId(manga.getMangaId());

        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MANGA_EXTERNAL_ID, manga.getMangaId());
        cv.put(BookDatabaseHelper.COL_MANGA_TITLE, manga.getTitle());
        cv.put(BookDatabaseHelper.COL_MANGA_AUTHOR, manga.getAuthor());
        cv.put(BookDatabaseHelper.COL_MANGA_DESC, manga.getDescription());
        if (manga.getCoverUrl() != null) {
            cv.put(BookDatabaseHelper.COL_MANGA_COVER_URL, manga.getCoverUrl());
        }
        if (manga.getLocalCoverPath() != null) {
            cv.put(BookDatabaseHelper.COL_MANGA_LOCAL_COVER, manga.getLocalCoverPath());
        }
        cv.put(BookDatabaseHelper.COL_MANGA_STATUS, manga.getStatus());
        cv.put(BookDatabaseHelper.COL_MANGA_GENRES, manga.getGenres());
        cv.put(BookDatabaseHelper.COL_MANGA_UPDATED_AT, System.currentTimeMillis());

        if (existing != null) {
            if (manga.getLastChapterId() != null) {
                cv.put(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_ID, manga.getLastChapterId());
                cv.put(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_TITLE, manga.getLastChapterTitle());
                cv.put(BookDatabaseHelper.COL_MANGA_LAST_PAGE, manga.getLastPage());
            }
            db.update(BookDatabaseHelper.TABLE_MANGA, cv,
                    BookDatabaseHelper.COL_MANGA_ID + "=?",
                    new String[]{String.valueOf(existing.getId())});
            manga.setId(existing.getId());
            manga.setFavorite(existing.isFavorite());
            manga.setDownloaded(existing.isDownloaded());
            return existing.getId();
        } else {
            cv.put(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_ID, manga.getLastChapterId());
            cv.put(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_TITLE, manga.getLastChapterTitle());
            cv.put(BookDatabaseHelper.COL_MANGA_LAST_PAGE, manga.getLastPage());
            cv.put(BookDatabaseHelper.COL_MANGA_IS_FAVORITE, manga.isFavorite() ? 1 : 0);
            cv.put(BookDatabaseHelper.COL_MANGA_IS_DOWNLOADED, manga.isDownloaded() ? 1 : 0);
            long id = db.insertWithOnConflict(BookDatabaseHelper.TABLE_MANGA, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            manga.setId(id);
            return id;
        }
    }

    public synchronized MangaItem getMangaByExternalId(String mangaId) {
        if (mangaId == null || mangaId.isEmpty()) return null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(BookDatabaseHelper.TABLE_MANGA, null,
                BookDatabaseHelper.COL_MANGA_EXTERNAL_ID + "=?",
                new String[]{mangaId}, null, null, null);
        if (c != null && c.moveToFirst()) {
            MangaItem item = cursorToManga(c);
            c.close();
            item.setDownloadedChaptersCount(getDownloadedChaptersCount(mangaId));
            return item;
        }
        if (c != null) c.close();
        return null;
    }

    public synchronized void setMangaFavorite(String mangaId, boolean favorite) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MANGA_IS_FAVORITE, favorite ? 1 : 0);
        cv.put(BookDatabaseHelper.COL_MANGA_UPDATED_AT, System.currentTimeMillis());
        db.update(BookDatabaseHelper.TABLE_MANGA, cv,
                BookDatabaseHelper.COL_MANGA_EXTERNAL_ID + "=?",
                new String[]{mangaId});
    }

    public synchronized void setMangaDownloaded(String mangaId, boolean downloaded) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MANGA_IS_DOWNLOADED, downloaded ? 1 : 0);
        cv.put(BookDatabaseHelper.COL_MANGA_UPDATED_AT, System.currentTimeMillis());
        db.update(BookDatabaseHelper.TABLE_MANGA, cv,
                BookDatabaseHelper.COL_MANGA_EXTERNAL_ID + "=?",
                new String[]{mangaId});
    }

    public synchronized void updateMangaProgress(String mangaId, String chapterId, String chapterTitle, int pageIndex) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_ID, chapterId);
        cv.put(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_TITLE, chapterTitle);
        cv.put(BookDatabaseHelper.COL_MANGA_LAST_PAGE, pageIndex);
        cv.put(BookDatabaseHelper.COL_MANGA_UPDATED_AT, System.currentTimeMillis());
        db.update(BookDatabaseHelper.TABLE_MANGA, cv,
                BookDatabaseHelper.COL_MANGA_EXTERNAL_ID + "=?",
                new String[]{mangaId});
    }

    public synchronized List<MangaItem> getDownloadedOrFavoriteManga(boolean onlyDownloaded, boolean onlyFavorite, String query) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        StringBuilder sel = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        if (onlyDownloaded) {
            sel.append(" AND ").append(BookDatabaseHelper.COL_MANGA_IS_DOWNLOADED).append("=1");
        }
        if (onlyFavorite) {
            sel.append(" AND ").append(BookDatabaseHelper.COL_MANGA_IS_FAVORITE).append("=1");
        }
        if (query != null && !query.trim().isEmpty()) {
            sel.append(" AND (").append(BookDatabaseHelper.COL_MANGA_TITLE).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_MANGA_AUTHOR).append(" LIKE ?)");
            String q = "%" + query.trim() + "%";
            args.add(q);
            args.add(q);
        }
        Cursor c = db.query(BookDatabaseHelper.TABLE_MANGA, null,
                sel.toString(), args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, BookDatabaseHelper.COL_MANGA_UPDATED_AT + " DESC");
        List<MangaItem> list = new ArrayList<>();
        if (c != null) {
            while (c.moveToNext()) {
                MangaItem item = cursorToManga(c);
                item.setDownloadedChaptersCount(getDownloadedChaptersCount(item.getMangaId()));
                list.add(item);
            }
            c.close();
        }
        return list;
    }

    public synchronized List<MangaItem> getReadingManga(String query) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        StringBuilder sel = new StringBuilder("(" + BookDatabaseHelper.COL_MANGA_LAST_PAGE + " > 0 OR " + BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_ID + " IS NOT NULL)");
        List<String> args = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            sel.append(" AND (").append(BookDatabaseHelper.COL_MANGA_TITLE).append(" LIKE ? OR ")
                    .append(BookDatabaseHelper.COL_MANGA_AUTHOR).append(" LIKE ?)");
            String q = "%" + query.trim() + "%";
            args.add(q);
            args.add(q);
        }
        Cursor c = db.query(BookDatabaseHelper.TABLE_MANGA, null,
                sel.toString(), args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, BookDatabaseHelper.COL_MANGA_UPDATED_AT + " DESC");
        List<MangaItem> list = new ArrayList<>();
        if (c != null) {
            while (c.moveToNext()) {
                MangaItem item = cursorToManga(c);
                item.setDownloadedChaptersCount(getDownloadedChaptersCount(item.getMangaId()));
                list.add(item);
            }
            c.close();
        }
        return list;
    }

    public synchronized int getDownloadedChaptersCount(String mangaId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + BookDatabaseHelper.TABLE_MANGA_CHAPTERS
                        + " WHERE " + BookDatabaseHelper.COL_MC_MANGA_ID + "=? AND "
                        + BookDatabaseHelper.COL_MC_IS_DOWNLOADED + "=1",
                new String[]{mangaId});
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        return count;
    }

    public synchronized void saveOrUpdateChapter(MangaChapterItem ch) {
        if (ch == null || ch.getChapterId().isEmpty()) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        MangaChapterItem existing = getChapterByExternalId(ch.getChapterId());

        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MC_CHAPTER_ID, ch.getChapterId());
        cv.put(BookDatabaseHelper.COL_MC_MANGA_ID, ch.getMangaId());
        cv.put(BookDatabaseHelper.COL_MC_CHAPTER_NUM, ch.getChapterNumber());
        cv.put(BookDatabaseHelper.COL_MC_VOLUME_NUM, ch.getVolumeNumber());
        cv.put(BookDatabaseHelper.COL_MC_TITLE, ch.getTitle());
        cv.put(BookDatabaseHelper.COL_MC_LANGUAGE, ch.getLanguage());
        if (ch.getPagesCount() > 0) {
            cv.put(BookDatabaseHelper.COL_MC_PAGES_COUNT, ch.getPagesCount());
        }
        cv.put(BookDatabaseHelper.COL_MC_PUBLISHED_AT, ch.getPublishedAt());

        if (existing != null) {
            ch.setId(existing.getId());
            ch.setDownloaded(existing.isDownloaded());
            ch.setLocalDirPath(existing.getLocalDirPath());
            ch.setReadPage(existing.getReadPage());
            ch.setCompleted(existing.isCompleted());
            db.update(BookDatabaseHelper.TABLE_MANGA_CHAPTERS, cv,
                    BookDatabaseHelper.COL_MC_ID + "=?",
                    new String[]{String.valueOf(existing.getId())});
        } else {
            cv.put(BookDatabaseHelper.COL_MC_IS_DOWNLOADED, ch.isDownloaded() ? 1 : 0);
            cv.put(BookDatabaseHelper.COL_MC_LOCAL_DIR, ch.getLocalDirPath());
            cv.put(BookDatabaseHelper.COL_MC_READ_PAGE, ch.getReadPage());
            cv.put(BookDatabaseHelper.COL_MC_IS_COMPLETED, ch.isCompleted() ? 1 : 0);
            long id = db.insertWithOnConflict(BookDatabaseHelper.TABLE_MANGA_CHAPTERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            ch.setId(id);
        }
    }

    public synchronized MangaChapterItem getChapterByExternalId(String chapterId) {
        if (chapterId == null || chapterId.isEmpty()) return null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(BookDatabaseHelper.TABLE_MANGA_CHAPTERS, null,
                BookDatabaseHelper.COL_MC_CHAPTER_ID + "=?",
                new String[]{chapterId}, null, null, null);
        if (c != null && c.moveToFirst()) {
            MangaChapterItem item = cursorToMangaChapter(c);
            c.close();
            return item;
        }
        if (c != null) c.close();
        return null;
    }

    public synchronized List<MangaChapterItem> getChaptersForManga(String mangaId) {
        List<MangaChapterItem> list = new ArrayList<>();
        if (mangaId == null || mangaId.isEmpty()) return list;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(BookDatabaseHelper.TABLE_MANGA_CHAPTERS, null,
                BookDatabaseHelper.COL_MC_MANGA_ID + "=?",
                new String[]{mangaId}, null, null,
                "CAST(" + BookDatabaseHelper.COL_MC_CHAPTER_NUM + " AS REAL) ASC");
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToMangaChapter(c));
            }
            c.close();
        }
        return list;
    }

    public synchronized void markChapterDownloaded(String mangaId, String chapterId, String localDirPath, int pagesCount) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MC_IS_DOWNLOADED, 1);
        cv.put(BookDatabaseHelper.COL_MC_LOCAL_DIR, localDirPath);
        if (pagesCount > 0) {
            cv.put(BookDatabaseHelper.COL_MC_PAGES_COUNT, pagesCount);
        }
        db.update(BookDatabaseHelper.TABLE_MANGA_CHAPTERS, cv,
                BookDatabaseHelper.COL_MC_CHAPTER_ID + "=?",
                new String[]{chapterId});
        setMangaDownloaded(mangaId, true);
    }

    public synchronized void updateChapterProgress(String chapterId, int readPage, boolean completed) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MC_READ_PAGE, readPage);
        cv.put(BookDatabaseHelper.COL_MC_IS_COMPLETED, completed ? 1 : 0);
        db.update(BookDatabaseHelper.TABLE_MANGA_CHAPTERS, cv,
                BookDatabaseHelper.COL_MC_CHAPTER_ID + "=?",
                new String[]{chapterId});
    }

    public synchronized long addMangaBookmark(MangaBookmarkItem bm) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_MBM_MANGA_ID, bm.getMangaId());
        cv.put(BookDatabaseHelper.COL_MBM_MANGA_TITLE, bm.getMangaTitle());
        cv.put(BookDatabaseHelper.COL_MBM_CHAPTER_ID, bm.getChapterId());
        cv.put(BookDatabaseHelper.COL_MBM_CHAPTER_TITLE, bm.getChapterTitle());
        cv.put(BookDatabaseHelper.COL_MBM_PAGE_INDEX, bm.getPageIndex());
        cv.put(BookDatabaseHelper.COL_MBM_NOTE, bm.getNote());
        cv.put(BookDatabaseHelper.COL_MBM_COVER_URL, bm.getCoverUrl());
        cv.put(BookDatabaseHelper.COL_MBM_CREATED_AT, bm.getCreatedAt() > 0 ? bm.getCreatedAt() : System.currentTimeMillis());
        long id = db.insert(BookDatabaseHelper.TABLE_MANGA_BOOKMARKS, null, cv);
        bm.setId(id);
        return id;
    }

    public synchronized List<MangaBookmarkItem> getAllMangaBookmarks(String query) {
        String selection = null;
        String[] args = null;
        if (query != null && !query.trim().isEmpty()) {
            selection = BookDatabaseHelper.COL_MBM_MANGA_TITLE + " LIKE ? OR "
                    + BookDatabaseHelper.COL_MBM_CHAPTER_TITLE + " LIKE ? OR "
                    + BookDatabaseHelper.COL_MBM_NOTE + " LIKE ?";
            String q = "%" + query.trim() + "%";
            args = new String[]{q, q, q};
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(BookDatabaseHelper.TABLE_MANGA_BOOKMARKS, null,
                selection, args, null, null,
                BookDatabaseHelper.COL_MBM_CREATED_AT + " DESC");
        List<MangaBookmarkItem> list = new ArrayList<>();
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToMangaBookmark(c));
            }
            c.close();
        }
        return list;
    }

    public synchronized void deleteMangaBookmark(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_MANGA_BOOKMARKS,
                BookDatabaseHelper.COL_MBM_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public synchronized long addOpdsCatalog(OpdsCatalogItem item) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BookDatabaseHelper.COL_OPDS_TITLE, item.getTitle());
        cv.put(BookDatabaseHelper.COL_OPDS_URL, item.getUrl());
        cv.put(BookDatabaseHelper.COL_OPDS_DESC, item.getDescription());
        cv.put(BookDatabaseHelper.COL_OPDS_CREATED_AT, item.getCreatedAt() > 0 ? item.getCreatedAt() : System.currentTimeMillis());
        long id = db.insertWithOnConflict(BookDatabaseHelper.TABLE_OPDS_CATALOGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        item.setId(id);
        return id;
    }

    public synchronized List<OpdsCatalogItem> getAllOpdsCatalogs() {
        List<OpdsCatalogItem> list = new ArrayList<>();
        list.add(new OpdsCatalogItem(
                "Project Gutenberg (OPDS)",
                "https://m.gutenberg.org/ebooks.opds/",
                "Более 70 000 бесплатных книг мировой классики (EPUB, MOBI, TXT)",
                true));
        list.add(new OpdsCatalogItem(
                "Standard Ebooks (OPDS)",
                "https://standardebooks.org/feeds/opds",
                "Тщательно отформатированная классическая литература с иллюстрациями",
                true));
        list.add(new OpdsCatalogItem(
                "Feedbooks Public Domain",
                "https://catalog.feedbooks.com/publicdomain/browse/top.atom",
                "Бесплатные книги общественного достояния со всего мира",
                true));

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(BookDatabaseHelper.TABLE_OPDS_CATALOGS, null, null, null, null, null,
                BookDatabaseHelper.COL_OPDS_CREATED_AT + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                OpdsCatalogItem item = new OpdsCatalogItem();
                item.setId(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_OPDS_ID)));
                item.setTitle(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_OPDS_TITLE)));
                item.setUrl(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_OPDS_URL)));
                item.setDescription(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_OPDS_DESC)));
                item.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_OPDS_CREATED_AT)));
                item.setBuiltIn(false);
                list.add(item);
            }
            c.close();
        }
        return list;
    }

    public synchronized void deleteOpdsCatalog(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(BookDatabaseHelper.TABLE_OPDS_CATALOGS,
                BookDatabaseHelper.COL_OPDS_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    private MangaItem cursorToManga(Cursor c) {
        MangaItem m = new MangaItem();
        m.setId(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_ID)));
        m.setMangaId(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_EXTERNAL_ID)));
        m.setTitle(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_TITLE)));
        m.setAuthor(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_AUTHOR)));
        m.setDescription(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_DESC)));
        m.setCoverUrl(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_COVER_URL)));
        m.setLocalCoverPath(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_LOCAL_COVER)));
        m.setStatus(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_STATUS)));
        m.setGenres(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_GENRES)));
        m.setLastChapterId(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_ID)));
        m.setLastChapterTitle(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_LAST_CHAPTER_TITLE)));
        m.setLastPage(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_LAST_PAGE)));
        m.setFavorite(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_IS_FAVORITE)) == 1);
        m.setDownloaded(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_IS_DOWNLOADED)) == 1);
        m.setUpdatedAt(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MANGA_UPDATED_AT)));
        return m;
    }

    private MangaChapterItem cursorToMangaChapter(Cursor c) {
        MangaChapterItem ch = new MangaChapterItem();
        ch.setId(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_ID)));
        ch.setChapterId(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_CHAPTER_ID)));
        ch.setMangaId(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_MANGA_ID)));
        ch.setChapterNumber(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_CHAPTER_NUM)));
        ch.setVolumeNumber(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_VOLUME_NUM)));
        ch.setTitle(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_TITLE)));
        ch.setLanguage(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_LANGUAGE)));
        ch.setPagesCount(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_PAGES_COUNT)));
        ch.setDownloaded(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_IS_DOWNLOADED)) == 1);
        ch.setLocalDirPath(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_LOCAL_DIR)));
        ch.setReadPage(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_READ_PAGE)));
        ch.setCompleted(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_IS_COMPLETED)) == 1);
        ch.setPublishedAt(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MC_PUBLISHED_AT)));
        return ch;
    }

    private MangaBookmarkItem cursorToMangaBookmark(Cursor c) {
        MangaBookmarkItem bm = new MangaBookmarkItem();
        bm.setId(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_ID)));
        bm.setMangaId(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_MANGA_ID)));
        bm.setMangaTitle(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_MANGA_TITLE)));
        bm.setChapterId(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_CHAPTER_ID)));
        bm.setChapterTitle(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_CHAPTER_TITLE)));
        bm.setPageIndex(c.getInt(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_PAGE_INDEX)));
        bm.setNote(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_NOTE)));
        bm.setCoverUrl(c.getString(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_COVER_URL)));
        bm.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(BookDatabaseHelper.COL_MBM_CREATED_AT)));
        return bm;
    }

    // --- BACKUP & RESTORE ---

    public synchronized String exportBackupToJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 3);
        root.put("timestamp", System.currentTimeMillis());

        // Bookmarks
        JSONArray bmArray = new JSONArray();
        for (BookmarkItem bm : getAllBookmarks()) {
            JSONObject obj = new JSONObject();
            obj.put("bookUuid", bm.getBookUuid());
            obj.put("bookTitle", bm.getBookTitle());
            obj.put("bookAuthor", bm.getBookAuthor());
            obj.put("seriesInfo", bm.getSeriesInfo());
            obj.put("chapterTitle", bm.getChapterTitle());
            obj.put("positionStr", bm.getPositionStr());
            obj.put("pageNumber", bm.getPageNumber());
            obj.put("snippet", bm.getSnippet());
            obj.put("comment", bm.getComment());
            obj.put("type", bm.getBookmarkType());
            obj.put("createdAt", bm.getCreatedAt());
            bmArray.put(obj);
        }
        root.put("bookmarks", bmArray);

        // Notes
        JSONArray notesArray = new JSONArray();
        for (NoteItem n : getAllNotes(null)) {
            JSONObject obj = new JSONObject();
            obj.put("bookUuid", n.getBookUuid());
            obj.put("bookTitle", n.getBookTitle());
            obj.put("chapterTitle", n.getChapterTitle());
            obj.put("positionStr", n.getPositionStr());
            obj.put("pageNumber", n.getPageNumber());
            obj.put("selectedText", n.getSelectedText());
            obj.put("noteText", n.getNoteText());
            obj.put("createdAt", n.getCreatedAt());
            obj.put("updatedAt", n.getUpdatedAt());
            notesArray.put(obj);
        }
        root.put("notes", notesArray);

        // Quotes
        JSONArray quotesArray = new JSONArray();
        for (QuoteItem q : getAllQuotes(null)) {
            JSONObject obj = new JSONObject();
            obj.put("bookUuid", q.getBookUuid());
            obj.put("bookTitle", q.getBookTitle());
            obj.put("bookAuthor", q.getBookAuthor());
            obj.put("chapterTitle", q.getChapterTitle());
            obj.put("quoteText", q.getQuoteText());
            obj.put("colorLabel", q.getColorLabel());
            obj.put("createdAt", q.getCreatedAt());
            quotesArray.put(obj);
        }
        root.put("quotes", quotesArray);

        // Shelves
        JSONArray shelvesArray = new JSONArray();
        for (ShelfItem s : getAllShelves()) {
            JSONObject obj = new JSONObject();
            obj.put("name", s.getName());
            obj.put("createdAt", s.getCreatedAt());
            shelvesArray.put(obj);
        }
        root.put("shelves", shelvesArray);

        // Manga Bookmarks
        JSONArray mbmArray = new JSONArray();
        for (MangaBookmarkItem mbm : getAllMangaBookmarks(null)) {
            JSONObject obj = new JSONObject();
            obj.put("mangaId", mbm.getMangaId());
            obj.put("mangaTitle", mbm.getMangaTitle());
            obj.put("chapterId", mbm.getChapterId());
            obj.put("chapterTitle", mbm.getChapterTitle());
            obj.put("pageIndex", mbm.getPageIndex());
            obj.put("note", mbm.getNote());
            obj.put("coverUrl", mbm.getCoverUrl());
            obj.put("createdAt", mbm.getCreatedAt());
            mbmArray.put(obj);
        }
        root.put("mangaBookmarks", mbmArray);

        return root.toString(2);
    }

    public synchronized void importBackupFromJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);

        List<BookmarkItem> existingBookmarks = getAllBookmarks();
        if (root.has("bookmarks")) {
            JSONArray bmArray = root.getJSONArray("bookmarks");
            for (int i = 0; i < bmArray.length(); i++) {
                JSONObject obj = bmArray.getJSONObject(i);
                BookmarkItem bm = new BookmarkItem();
                bm.setBookUuid(obj.optString("bookUuid"));
                bm.setBookTitle(obj.optString("bookTitle"));
                bm.setBookAuthor(obj.optString("bookAuthor"));
                bm.setSeriesInfo(obj.optString("seriesInfo"));
                bm.setChapterTitle(obj.optString("chapterTitle"));
                bm.setPositionStr(obj.optString("positionStr"));
                bm.setPageNumber(obj.optInt("pageNumber", 0));
                bm.setSnippet(obj.optString("snippet"));
                bm.setComment(obj.optString("comment"));
                bm.setBookmarkType(obj.optInt("type", 0));
                bm.setCreatedAt(obj.optLong("createdAt", System.currentTimeMillis()));

                boolean duplicate = false;
                for (BookmarkItem ex : existingBookmarks) {
                    if (ex.getCreatedAt() == bm.getCreatedAt()
                            && String.valueOf(ex.getBookUuid()).equals(String.valueOf(bm.getBookUuid()))
                            && String.valueOf(ex.getPositionStr()).equals(String.valueOf(bm.getPositionStr()))) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    addBookmark(bm);
                }
            }
        }

        List<NoteItem> existingNotes = getAllNotes(null);
        if (root.has("notes")) {
            JSONArray notesArray = root.getJSONArray("notes");
            for (int i = 0; i < notesArray.length(); i++) {
                JSONObject obj = notesArray.getJSONObject(i);
                NoteItem n = new NoteItem();
                n.setBookUuid(obj.optString("bookUuid"));
                n.setBookTitle(obj.optString("bookTitle"));
                n.setChapterTitle(obj.optString("chapterTitle"));
                n.setPositionStr(obj.optString("positionStr"));
                n.setPageNumber(obj.optInt("pageNumber", 0));
                n.setSelectedText(obj.optString("selectedText"));
                n.setNoteText(obj.optString("noteText"));
                n.setCreatedAt(obj.optLong("createdAt", System.currentTimeMillis()));
                n.setUpdatedAt(obj.optLong("updatedAt", System.currentTimeMillis()));

                boolean duplicate = false;
                for (NoteItem ex : existingNotes) {
                    if (ex.getCreatedAt() == n.getCreatedAt()
                            && String.valueOf(ex.getBookUuid()).equals(String.valueOf(n.getBookUuid()))
                            && String.valueOf(ex.getNoteText()).equals(String.valueOf(n.getNoteText()))) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    addNote(n);
                }
            }
        }

        List<QuoteItem> existingQuotes = getAllQuotes(null);
        if (root.has("quotes")) {
            JSONArray quotesArray = root.getJSONArray("quotes");
            for (int i = 0; i < quotesArray.length(); i++) {
                JSONObject obj = quotesArray.getJSONObject(i);
                QuoteItem q = new QuoteItem();
                q.setBookUuid(obj.optString("bookUuid"));
                q.setBookTitle(obj.optString("bookTitle"));
                q.setBookAuthor(obj.optString("bookAuthor"));
                q.setChapterTitle(obj.optString("chapterTitle"));
                q.setQuoteText(obj.optString("quoteText"));
                q.setColorLabel(obj.optString("colorLabel", "#FFFFFF"));
                q.setCreatedAt(obj.optLong("createdAt", System.currentTimeMillis()));

                boolean duplicate = false;
                for (QuoteItem ex : existingQuotes) {
                    if (ex.getCreatedAt() == q.getCreatedAt()
                            && String.valueOf(ex.getQuoteText()).equals(String.valueOf(q.getQuoteText()))) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    addQuote(q);
                }
            }
        }

        if (root.has("shelves")) {
            JSONArray shelvesArray = root.getJSONArray("shelves");
            for (int i = 0; i < shelvesArray.length(); i++) {
                JSONObject obj = shelvesArray.getJSONObject(i);
                String name = obj.optString("name");
                if (name != null && !name.trim().isEmpty()) {
                    createShelf(name.trim());
                }
            }
        }

        if (root.has("mangaBookmarks")) {
            List<MangaBookmarkItem> existingMbm = getAllMangaBookmarks(null);
            JSONArray mbmArray = root.getJSONArray("mangaBookmarks");
            for (int i = 0; i < mbmArray.length(); i++) {
                JSONObject obj = mbmArray.getJSONObject(i);
                MangaBookmarkItem mbm = new MangaBookmarkItem(
                        obj.optString("mangaId"),
                        obj.optString("mangaTitle"),
                        obj.optString("chapterId"),
                        obj.optString("chapterTitle"),
                        obj.optInt("pageIndex", 0),
                        obj.optString("note"),
                        obj.optString("coverUrl")
                );
                mbm.setCreatedAt(obj.optLong("createdAt", System.currentTimeMillis()));
                boolean duplicate = false;
                for (MangaBookmarkItem ex : existingMbm) {
                    if (ex.getMangaId().equals(mbm.getMangaId())
                            && ex.getChapterId().equals(mbm.getChapterId())
                            && ex.getPageIndex() == mbm.getPageIndex()) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    addMangaBookmark(mbm);
                }
            }
        }
    }

    private String getOrderByClause(int sortOrder) {
        switch (sortOrder) {
            case SORT_TITLE_DESC:
                return BookDatabaseHelper.COL_BOOK_TITLE + " COLLATE NOCASE DESC";
            case SORT_AUTHOR:
                return BookDatabaseHelper.COL_BOOK_AUTHOR + " COLLATE NOCASE ASC, " + BookDatabaseHelper.COL_BOOK_TITLE + " COLLATE NOCASE ASC";
            case SORT_RECENT:
                return BookDatabaseHelper.COL_BOOK_LAST_OPENED + " DESC, " + BookDatabaseHelper.COL_BOOK_DATE_ADDED + " DESC";
            case SORT_DATE_ADDED:
                return BookDatabaseHelper.COL_BOOK_DATE_ADDED + " DESC";
            case SORT_TITLE_ASC:
            default:
                return BookDatabaseHelper.COL_BOOK_TITLE + " COLLATE NOCASE ASC";
        }
    }

    private BookItem cursorToBook(Cursor cursor) {
        BookItem item = new BookItem();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_ID)));
        int uuidCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BOOK_UUID);
        if (uuidCol != -1) item.setUuid(cursor.getString(uuidCol));

        item.setUri(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_URI)));

        int cacheCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BOOK_CACHED_PATH);
        if (cacheCol != -1) item.setCachedPath(cursor.getString(cacheCol));

        item.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_TITLE)));
        item.setAuthor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_AUTHOR)));

        int seriesCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BOOK_SERIES);
        if (seriesCol != -1) item.setSeries(cursor.getString(seriesCol));

        int seriesNumCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BOOK_SERIES_NUM);
        if (seriesNumCol != -1) item.setSeriesNumber(cursor.getInt(seriesNumCol));

        int formatCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BOOK_FORMAT);
        if (formatCol != -1) item.setFormat(cursor.getString(formatCol));

        item.setAnnotation(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_ANNOTATION)));
        item.setCoverPath(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_COVER_PATH)));
        item.setDateAdded(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_DATE_ADDED)));
        item.setLastOpened(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_LAST_OPENED)));
        item.setProgressPercent(cursor.getFloat(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_PROGRESS_PERCENT)));
        item.setCurrentPosition(cursor.getInt(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_CURRENT_POSITION)));
        item.setTotalPages(cursor.getInt(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_TOTAL_PAGES)));
        item.setFileSize(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_FILE_SIZE)));
        item.setFavorite(cursor.getInt(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BOOK_IS_FAVORITE)) == 1);

        int hiddenCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BOOK_IS_HIDDEN);
        if (hiddenCol != -1) item.setHidden(cursor.getInt(hiddenCol) == 1);

        int timeCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BOOK_READING_TIME);
        if (timeCol != -1) item.setReadingTimeSeconds(cursor.getLong(timeCol));

        return item;
    }

    private BookmarkItem cursorToBookmark(Cursor cursor) {
        BookmarkItem item = new BookmarkItem();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BM_ID)));
        int uuidCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_BOOK_UUID);
        if (uuidCol != -1) item.setBookUuid(cursor.getString(uuidCol));

        int titleCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_BOOK_TITLE);
        if (titleCol != -1) item.setBookTitle(cursor.getString(titleCol));

        int authorCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_BOOK_AUTHOR);
        if (authorCol != -1) item.setBookAuthor(cursor.getString(authorCol));

        int seriesCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_SERIES_INFO);
        if (seriesCol != -1) item.setSeriesInfo(cursor.getString(seriesCol));

        item.setChapterTitle(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BM_CHAPTER_TITLE)));

        int posStrCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_POSITION_STR);
        if (posStrCol != -1) item.setPositionStr(cursor.getString(posStrCol));

        int pageNumCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_PAGE_NUMBER);
        if (pageNumCol != -1) item.setPageNumber(cursor.getInt(pageNumCol));

        item.setSnippet(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BM_SNIPPET)));

        int commentCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_COMMENT);
        if (commentCol != -1) item.setComment(cursor.getString(commentCol));

        int typeCol = cursor.getColumnIndex(BookDatabaseHelper.COL_BM_TYPE);
        if (typeCol != -1) item.setBookmarkType(cursor.getInt(typeCol));

        item.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_BM_CREATED_AT)));
        return item;
    }

    private NoteItem cursorToNote(Cursor cursor) {
        NoteItem item = new NoteItem();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_ID)));
        item.setBookUuid(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_BOOK_UUID)));
        item.setBookTitle(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_BOOK_TITLE)));
        item.setChapterTitle(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_CHAPTER_TITLE)));
        item.setPositionStr(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_POSITION_STR)));
        item.setPageNumber(cursor.getInt(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_PAGE_NUMBER)));
        item.setSelectedText(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_SELECTED_TEXT)));
        item.setNoteText(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_TEXT)));
        item.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_CREATED_AT)));
        item.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_NOTE_UPDATED_AT)));
        return item;
    }

    private QuoteItem cursorToQuote(Cursor cursor) {
        QuoteItem item = new QuoteItem();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_ID)));
        item.setBookUuid(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_BOOK_UUID)));
        item.setBookTitle(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_BOOK_TITLE)));
        item.setBookAuthor(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_BOOK_AUTHOR)));
        item.setChapterTitle(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_CHAPTER)));
        item.setQuoteText(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_TEXT)));
        item.setColorLabel(cursor.getString(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_COLOR_LABEL)));
        item.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(BookDatabaseHelper.COL_QUOTE_CREATED_AT)));
        return item;
    }
}
