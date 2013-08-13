package com.orangelabs.rcs.provider.messaging;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

/**
 * File transfer content provider
 * 
 * @author Jean-Marc AUFFRET
 */
public class FileTransferProvider extends ContentProvider {
	/**
	 * Database table
	 */
    private static final String TABLE = "ft";

	// Create the constants used to differentiate between the different URI requests
	private static final int FILETRANSFERS = 1;
    private static final int FILETRANSFER_ID = 2;
    private static final int RCSAPI = 3;

	// Allocate the UriMatcher object
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        uriMatcher.addURI("com.orangelabs.rcs.ft", "ft", FILETRANSFERS);
        uriMatcher.addURI("com.orangelabs.rcs.ft", "ft/#", FILETRANSFER_ID);
		uriMatcher.addURI("org.gsma.joyn.provider.ft", "ft", RCSAPI);
    }

    /**
     * Database helper class
     */
    private SQLiteOpenHelper openHelper;
    
    /**
     * Database name
     */
    public static final String DATABASE_NAME = "ft.db";

    /**
     * Helper class for opening, creating and managing database version control
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final int DATABASE_VERSION = 1;

        public DatabaseHelper(Context ctx) {
            super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        	db.execSQL("CREATE TABLE " + TABLE + " ("
        			+ FileTransferData.KEY_ID + " integer primary key autoincrement,"
        			+ FileTransferData.KEY_SESSION_ID + " TEXT,"
        			+ FileTransferData.KEY_CONTACT + " TEXT,"
        			+ FileTransferData.KEY_NAME + " TEXT,"
        			+ FileTransferData.KEY_MIME_TYPE + " TEXT,"
        			+ FileTransferData.KEY_STATUS + " integer,"
        			+ FileTransferData.KEY_DIRECTION + " integer,"
        			+ FileTransferData.KEY_TIMESTAMP + " long,"
        			+ FileTransferData.KEY_SIZE + " long,"
        			+ FileTransferData.KEY_TOTAL_SIZE + " long);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        openHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        int match = uriMatcher.match(uri);
        switch(match) {
            case FILETRANSFERS:
                return "vnd.android.cursor.dir/com.orangelabs.rcs.ft";
            case FILETRANSFER_ID:
                return "vnd.android.cursor.item/com.orangelabs.rcs.ft";
			case RCSAPI:
                return "vnd.android.cursor.dir/com.orangelabs.rcs.ft";
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projectionIn, String selection, String[] selectionArgs, String sort) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE);

        // Generate the body of the query
        int match = uriMatcher.match(uri);
        switch(match) {
            case FILETRANSFERS:
                break;
            case FILETRANSFER_ID:
                qb.appendWhere(FileTransferData.KEY_ID + "=");
                qb.appendWhere(uri.getPathSegments().get(1));
                break;
        	case RCSAPI:
        		break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = openHelper.getReadableDatabase();
        Cursor c = qb.query(db, projectionIn, selection, selectionArgs, null, null, sort);

		// Register the contexts ContentResolver to be notified if
		// the cursor result set changes.
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        int count;
        SQLiteDatabase db = openHelper.getWritableDatabase();

        int match = uriMatcher.match(uri);
        switch (match) {
	        case FILETRANSFERS:
	            count = db.update(TABLE, values, where, null);
	            break;
            case FILETRANSFER_ID:
                String segment = uri.getPathSegments().get(1);
                int id = Integer.parseInt(segment);
                count = db.update(TABLE, values, FileTransferData.KEY_ID + "=" + id, null);
                break;
            default:
                throw new UnsupportedOperationException("Cannot update URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        SQLiteDatabase db = openHelper.getWritableDatabase();
        switch(uriMatcher.match(uri)){
	        case FILETRANSFERS:
	        case FILETRANSFER_ID:
	            // Insert the new row, will return the row number if successful
	        	// Use system clock to generate id : it should not be a common int otherwise it could be the 
	        	// same as an id present in MmsSms table (and that will create uniqueness problem when doing the tables merge) 
	        	int id = (int)System.currentTimeMillis();
	        	if (Integer.signum(id) == -1){
	        		// If generated id is <0, it is problematic for uris
	        		id = -id;
	        	}
	        	initialValues.put(FileTransferData.KEY_ID, id);
	    		long rowId = db.insert(TABLE, null, initialValues);
	    		uri = ContentUris.withAppendedId(FileTransferData.CONTENT_URI, rowId);
	        	break;
	        default:
	    		throw new SQLException("Failed to insert row into " + uri);
        }
		getContext().getContentResolver().notifyChange(uri, null);
        return uri;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = openHelper.getWritableDatabase();
        int count = 0;
        switch(uriMatcher.match(uri)){
	        case FILETRANSFERS:
	        	count = db.delete(TABLE, where, whereArgs);
	        	break;
	        case FILETRANSFER_ID:
	        	String segment = uri.getPathSegments().get(1);
				count = db.delete(TABLE, FileTransferData.KEY_ID + "="
						+ segment
						+ (!TextUtils.isEmpty(where) ? " AND ("	+ where + ')' : ""),
						whereArgs);
				
				break;
	        	
	        default:
	    		throw new SQLException("Failed to delete row " + uri);
        }
		getContext().getContentResolver().notifyChange(uri, null);
        return count;    
    }
}