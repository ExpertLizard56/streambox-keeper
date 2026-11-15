package io.github.expertlizard56.streamingboxkeeper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * What the main screen shows, for another app on the box to read (a health monitor such as Home Watch's box app):
 * content://io.github.expertlizard56.streamingboxkeeper.status gives rows of name and value. Read only, and nothing
 * private in it: the version, what was last done and how the TV remote is.
 *   name             value
 *   version          0.1.0
 *   lastTidy         when the last cleanup ran and what it freed ("Not Yet" until then)
 *   lastRestart      the same for the weekly restart
 *   lastAppUpdate    the last app it updated
 *   lastSettingsFix  when Keep my settings last put settings back after a firmware update, and which; empty until then
 *   tvRemote         the TV remote in words (LinkService.describe)
 *   tvRemoteProblem  what's wrong with it, or empty
 */
public class StatusProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        android.content.Context context = getContext();
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences("keeper", android.content.Context.MODE_PRIVATE);
        MatrixCursor rows = new MatrixCursor(new String[] {"name", "value"});
        rows.addRow(new Object[] {"version", Jobs.installedVersion(context, context.getPackageName())});
        for (String key : new String[] {"lastTidy", "lastRestart", "lastAppUpdate"}) {
            rows.addRow(new Object[] {key, prefs.getString(key, "Not Yet")});
        }
        rows.addRow(new Object[] {"lastSettingsFix", prefs.getString("lastSettingsFix", "")});
        rows.addRow(new Object[] {"tvRemote", LinkService.describe(context)});
        String problem = LinkService.problem(context);
        rows.addRow(new Object[] {"tvRemoteProblem", problem == null ? "" : problem});
        return rows;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}
