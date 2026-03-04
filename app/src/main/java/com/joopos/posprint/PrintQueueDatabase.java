package com.joopos.posprint;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {PrintJobEntity.class}, version = 3, exportSchema = false)
public abstract class PrintQueueDatabase extends RoomDatabase {
    private static volatile PrintQueueDatabase INSTANCE;

    public abstract PrintJobDao dao();

    public static PrintQueueDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (PrintQueueDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            PrintQueueDatabase.class,
                            "print_queue.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
