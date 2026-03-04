package com.joopos.posprint;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "print_jobs",
        indices = {
                @Index(value = {"orderId", "printType"}, unique = true)
        }
)
public class PrintJobEntity {
    @PrimaryKey
    @NonNull
    public String jobId;

    @ColumnInfo
    public String baseUrl;

    @ColumnInfo
    public String orderId;

    @ColumnInfo
    public String printType;

    @ColumnInfo
    public String printerIp;

    @ColumnInfo
    public int attempts;

    @ColumnInfo
    public int status; // 0=pending, 1=success

    @ColumnInfo
    public long createdAt;
}
