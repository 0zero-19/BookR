package com.bookr.reader;

import android.app.Application;
import com.bookr.reader.data.BookDatabaseHelper;

public class BookRApp extends Application {
    private static BookRApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Warm up the database
        BookDatabaseHelper.getInstance(this).getWritableDatabase();
    }

    public static BookRApp getInstance() {
        return instance;
    }
}
