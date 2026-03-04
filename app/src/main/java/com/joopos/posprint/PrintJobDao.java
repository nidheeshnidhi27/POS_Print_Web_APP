package com.joopos.posprint;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface PrintJobDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(PrintJobEntity job);

    @Query("UPDATE print_jobs SET status=1 WHERE jobId=:jobId")
    void markSuccess(String jobId);

    @Query("UPDATE print_jobs SET attempts=attempts+1 WHERE jobId=:jobId")
    void incAttempts(String jobId);

    @Query("DELETE FROM print_jobs WHERE jobId=:jobId")
    void delete(String jobId);

    @Query("SELECT * FROM print_jobs WHERE jobId=:jobId LIMIT 1")
    PrintJobEntity get(String jobId);

    @Query("SELECT * FROM print_jobs WHERE status=0 ORDER BY createdAt DESC")
    java.util.List<PrintJobEntity> listPending();

    @Query("UPDATE print_jobs SET orderId=:orderId, printerIp=:printerIp WHERE jobId=:jobId")
    void updateMeta(String jobId, String orderId, String printerIp);

    @Query("SELECT * FROM print_jobs WHERE status=0 AND baseUrl=:baseUrl LIMIT 1")
    PrintJobEntity findPendingByBaseUrl(String baseUrl);

    @Query("SELECT COUNT(*) FROM print_jobs WHERE status=0 AND baseUrl=:baseUrl")
    int countPendingByBaseUrl(String baseUrl);

    @Query("SELECT * FROM print_jobs WHERE status=0 AND orderId=:orderId AND printType=:printType LIMIT 1")
    PrintJobEntity findPendingByOrderAndType(String orderId, String printType);
}
