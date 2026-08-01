package com.fr3ts0n.ecu.gui.androbd;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Durable FIFO for telemetry snapshots awaiting MQTT acknowledgement. */
final class LotoTGatewayQueue extends SQLiteOpenHelper
{
    static final int MAX_ROWS = 10_000;
    private static final String DB_NAME = "lotot_gateway.db";
    private static final int DB_VERSION = 1;

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
            this.id = id;
            this.topic = topic;
            this.payload = payload;
            this.qos = qos;
            this.retained = retained;
            this.createdAt = createdAt;
        }
    }

    LotoTGatewayQueue(Context context)
    {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE telemetry_queue ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "topic TEXT NOT NULL,"
                + "payload TEXT NOT NULL,"
                + "qos INTEGER NOT NULL,"
                + "retained INTEGER NOT NULL,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX telemetry_queue_created_idx ON telemetry_queue(created_at, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        db.execSQL("DROP TABLE IF EXISTS telemetry_queue");
        onCreate(db);
    }

    synchronized long enqueue(String topic, String payload, int qos, boolean retained, long createdAt)
    {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("topic", topic);
        values.put("payload", payload);
        values.put("qos", qos);
        values.put("retained", retained ? 1 : 0);
        values.put("created_at", createdAt);
        long id = db.insertOrThrow("telemetry_queue", null, values);
        trimLocked(db);
        return id;
    }

    synchronized List<Entry> peek(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        ArrayList<Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("telemetry_queue",
                new String[]{"id", "topic", "payload", "qos", "retained", "created_at"},
                null, null, null, null, "created_at ASC, id ASC", String.valueOf(safeLimit)))
        {
            while (cursor.moveToNext())
                entries.add(new Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getInt(3), cursor.getInt(4) != 0, cursor.getLong(5)));
        }
        return entries;
    }

    synchronized void delete(long id)
    {
        getWritableDatabase().delete("telemetry_queue", "id=?", new String[]{String.valueOf(id)});
    }

    synchronized int count()
    {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM telemetry_queue", null))
        {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    synchronized void clear()
    {
        getWritableDatabase().delete("telemetry_queue", null, null);
    }

    private void trimLocked(SQLiteDatabase db)
    {
        db.execSQL("DELETE FROM telemetry_queue WHERE id IN (SELECT id FROM telemetry_queue "
                + "ORDER BY created_at ASC, id ASC LIMIT MAX(0, "
                + "(SELECT COUNT(*) FROM telemetry_queue) - " + MAX_ROWS + "))");
    }
}
