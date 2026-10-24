package com.fr3ts0n.ecu.gui.androbd;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Durable, destination-isolated FIFO. Unattributed v1 rows are held, never rerouted. */
final class LotoTGatewayQueue extends SQLiteOpenHelper
{
    static final int MAX_ROWS = 10_000;
    private static final String DB_NAME = "lotot_gateway.db";
    private static final int DB_VERSION = 2;

    static final class Entry
    {
        final long id;
        final String topic;
        final String payload;
        final int qos;
        final boolean retained;
        final long createdAt;
        Entry(long id, String topic, String payload, int qos, boolean retained, long createdAt)
        {
            this.id = id; this.topic = topic; this.payload = payload;
            this.qos = qos; this.retained = retained; this.createdAt = createdAt;
        }
    }

    LotoTGatewayQueue(Context context)
    {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE telemetry_queue (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "topic TEXT NOT NULL,payload TEXT NOT NULL,qos INTEGER NOT NULL,"
                + "retained INTEGER NOT NULL,created_at INTEGER NOT NULL,"
                + "destination TEXT NOT NULL DEFAULT '')");
        createV2(db);
    }

    private void createV2(SQLiteDatabase db)
    {
        db.execSQL("CREATE INDEX IF NOT EXISTS telemetry_destination_idx ON telemetry_queue(destination,id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS queue_stats (name TEXT PRIMARY KEY,value INTEGER NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO queue_stats VALUES ('dropped',0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        if (oldVersion < 2)
        {
            db.execSQL("ALTER TABLE telemetry_queue ADD COLUMN destination TEXT NOT NULL DEFAULT ''");
            createV2(db);
        }
    }

    synchronized long enqueue(String destination, String topic, String payload,
                              int qos, boolean retained, long createdAt)
    {
        if (destination == null || destination.isEmpty())
            throw new IllegalArgumentException("A queue destination is required");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try
        {
            ContentValues values = new ContentValues();
            values.put("destination", destination); values.put("topic", topic);
            values.put("payload", payload); values.put("qos", qos);
            values.put("retained", retained ? 1 : 0); values.put("created_at", createdAt);
            long id = db.insertOrThrow("telemetry_queue", null, values);
            int excess = Math.max(0, countLocked(db, null) - MAX_ROWS);
            if (excess > 0)
            {
                // Insertion order stays FIFO even when the device clock changes.
                db.execSQL("DELETE FROM telemetry_queue WHERE id IN (SELECT id FROM telemetry_queue ORDER BY id LIMIT ?)",
                        new Object[]{excess});
                db.execSQL("UPDATE queue_stats SET value=value+? WHERE name='dropped'", new Object[]{excess});
            }
            db.setTransactionSuccessful();
            return id;
        }
        finally { db.endTransaction(); }
    }

    synchronized List<Entry> peek(String destination, int limit)
    {
        ArrayList<Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("telemetry_queue",
                new String[]{"id","topic","payload","qos","retained","created_at"},
                "destination=?", new String[]{destination}, null, null, "id ASC",
                String.valueOf(Math.max(1, Math.min(limit, 200)))))
        {
            while (cursor.moveToNext()) entries.add(new Entry(cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getInt(3), cursor.getInt(4)!=0, cursor.getLong(5)));
        }
        return entries;
    }

    synchronized void delete(long id)
    { getWritableDatabase().delete("telemetry_queue", "id=?", new String[]{String.valueOf(id)}); }

    private int countLocked(SQLiteDatabase db, String destination)
    {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM telemetry_queue"
                + (destination == null ? "" : " WHERE destination=?"),
                destination == null ? null : new String[]{destination}))
        { return cursor.moveToFirst() ? cursor.getInt(0) : 0; }
    }
    synchronized int count() { return countLocked(getReadableDatabase(), null); }
    synchronized int count(String destination) { return countLocked(getReadableDatabase(), destination); }
    synchronized long droppedCount()
    {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM queue_stats WHERE name='dropped'", null))
        { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    synchronized long oldestCapturedAt()
    {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MIN(created_at) FROM telemetry_queue", null))
        { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
}
