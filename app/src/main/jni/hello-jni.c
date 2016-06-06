#include <string.h>
#include <jni.h>
#include "sqlite3.h"
#include <android/log.h>

int stopFlag = 0;

jstring
Java_com_example_sqlitecorruption_HelloJni_setStopFlag( JNIEnv* env,
                                                       jobject thiz, jint flag )
{
    stopFlag = flag;
}

#define ONE "Native 1"
#define TWO "Native 2"
#define THREE "Native 3"
#define PICKID (id == 3 ? THREE : ((id == 2) ? TWO : ONE ))

#define PICKQUERY (id == 3 ? "%" THREE "%": ((id == 2) ? "%" TWO "%": "%" ONE "%"))
#define PICKUPDATE (id == 3 ? "Update from " THREE : ((id == 2) ? "Update from " TWO : "Update from " ONE ))
jstring
Java_com_example_sqlitecorruption_HelloJni_startRunningEdits( JNIEnv* env,
                                                      jobject thiz, jstring fullPath, jint id)
{
    sqlite3 * pDB = NULL;
    jstring resultString = NULL;
    const char *fullPathNative = (*env)->GetStringUTFChars(env, fullPath, 0);

    int openResult = sqlite3_open_v2(fullPathNative, &pDB, SQLITE_OPEN_READWRITE | SQLITE_OPEN_PRIVATECACHE, NULL );
    if (openResult == SQLITE_LOCKED || openResult == SQLITE_BUSY)
    {
        (*env)->ReleaseStringUTFChars(env, fullPath, fullPathNative);
        resultString = (*env)->NewStringUTF(env, "LOCKED");
        goto end;
    }

    if (openResult == SQLITE_CANTOPEN)
    {
        (*env)->ReleaseStringUTFChars(env, fullPath, fullPathNative);
        resultString = (*env)->NewStringUTF(env, "CAN'T OPEN");
        goto end;
    }
    const char * sqliteversion = sqlite3_libversion();
    __android_log_print(ANDROID_LOG_VERBOSE, PICKID, "SQLite version %s", sqliteversion);
    __android_log_print(ANDROID_LOG_VERBOSE, PICKID, "Native database %s", fullPathNative);
    (*env)->ReleaseStringUTFChars(env, fullPath, fullPathNative);
    char * errmsg = NULL;
    sqlite3_stmt * pStmtNotLikeMe = NULL;
    sqlite3_prepare(pDB, "SELECT COUNT(*) FROM test WHERE string not like ?;", -1, &pStmtNotLikeMe, NULL);
    sqlite3_bind_text(pStmtNotLikeMe, 1, PICKQUERY,  -1, NULL);
    sqlite3_stmt * pStmtLikeMe = NULL;
    sqlite3_prepare(pDB, "SELECT COUNT(*) FROM test WHERE string like ?;", -1, &pStmtLikeMe, NULL);
    sqlite3_bind_text(pStmtLikeMe, 1, PICKQUERY,  -1, NULL);

    sqlite3_stmt * pInsert = NULL;
    openResult = sqlite3_prepare(pDB, "INSERT INTO test (string) VALUES (?);", -1, &pInsert, NULL);
    openResult = sqlite3_bind_text(pInsert, 1, PICKID,  -1, NULL);


    sqlite3_stmt * pUpdate = NULL;
    openResult = sqlite3_prepare(pDB, "UPDATE test set string=? WHERE id=(select abs(random()) % ((SELECT COUNT (*) from test)  - 1) + 1);", -1, &pUpdate, NULL);
    openResult = sqlite3_bind_text(pUpdate, 1, PICKUPDATE,  -1, NULL);

    for (int m = 0; m < 10; m++) {

        if (stopFlag)
        {
            __android_log_write(ANDROID_LOG_VERBOSE, PICKID, "Stopping");
            resultString = (*env)->NewStringUTF(env, "Stopped");
            goto end;
        }
        int result;

        /*int result = sqlite3_exec(pDB, "BEGIN IMMEDIATE TRANSACTION;", NULL, NULL, &errmsg);

        if (result == SQLITE_LOCKED) {
            return (*env)->NewStringUTF(env, "LOCKED TRANSACTION");
        }
        if (result != SQLITE_OK) {
            return (*env)->NewStringUTF(env, sqlite3_errmsg(pDB));
        }*/

        __android_log_write(ANDROID_LOG_VERBOSE, PICKID, "Native commits...");

        int lastRowCount = 0;

        for (int i = 0; i < 20; i++)
        {
            //__android_log_write(ANDROID_LOG_VERBOSE, PICKID, "native insert...");
            result = sqlite3_step(pInsert);
            if (result == SQLITE_LOCKED || result == SQLITE_BUSY) {
                resultString = (*env)->NewStringUTF(env, "LOCKED");
                goto end;
            }
            if (result != SQLITE_DONE && result != SQLITE_OK) {
                errmsg = sqlite3_errmsg(pDB);
                resultString = (*env)->NewStringUTF(env, errmsg);
                goto end;
            }
            if (stopFlag)
            {
                resultString = (*env)->NewStringUTF(env, "Stopped");
                goto end;
            }
            sqlite3_reset(pInsert);
        }
        for (int i = 0; i < 20; i++) {
            //__android_log_write(ANDROID_LOG_VERBOSE, PICKID, "native update...");
            result = sqlite3_step(pUpdate);
            if (result == SQLITE_LOCKED || result == SQLITE_BUSY) {
                resultString = (*env)->NewStringUTF(env, "LOCKED");
                goto end;
            }
            if (result != SQLITE_DONE && result != SQLITE_OK) {
                errmsg = sqlite3_errmsg(pDB);
                resultString = (*env)->NewStringUTF(env, errmsg);
                goto end;
            }
            if (stopFlag)
            {
                resultString = (*env)->NewStringUTF(env, "Stopped");
                goto end;
            }
            sqlite3_reset(pUpdate);
        }

        result = sqlite3_step(pStmtNotLikeMe) ;
        if (result == SQLITE_LOCKED || result == SQLITE_BUSY) {
            resultString = (*env)->NewStringUTF(env, "LOCKED");
            goto end;
        }
        if (stopFlag)
        {
            resultString = (*env)->NewStringUTF(env, "Stopped");
            goto end;
        }
        int rowCount = sqlite3_column_int(pStmtNotLikeMe,0);

        sqlite3_reset(pStmtNotLikeMe);


        __android_log_print(ANDROID_LOG_VERBOSE, PICKID, "Native thinks that there are %d rows that it didn't change or add.", rowCount);

        result = sqlite3_step(pStmtLikeMe) ;
        if (result == SQLITE_LOCKED || result == SQLITE_BUSY) {
            resultString = (*env)->NewStringUTF(env, "LOCKED");
            goto end;
        }
        rowCount = sqlite3_column_int(pStmtLikeMe,0);

        sqlite3_reset(pStmtLikeMe);
        __android_log_print(ANDROID_LOG_VERBOSE, PICKID, "Native thinks that there are %d rows that it added or changed", rowCount);

        /*result = sqlite3_exec(pDB, "COMMIT;", NULL, NULL, &errmsg);
        if (result != SQLITE_OK) {
            return (*env)->NewStringUTF(env, sqlite3_errmsg(pDB));
        }*/
    }
    resultString = (*env)->NewStringUTF(env, "CONTINUE");
end:
    if (pStmtNotLikeMe != NULL)
        sqlite3_finalize(pStmtNotLikeMe);
    if (pStmtLikeMe != NULL)
        sqlite3_finalize(pStmtLikeMe);
    if (pInsert != NULL)
        sqlite3_finalize(pInsert);
    if (pUpdate != NULL)
        sqlite3_finalize(pUpdate);
    if (pDB != NULL)
        sqlite3_close(pDB);
    __android_log_write(ANDROID_LOG_VERBOSE, PICKID, "native returning...");
    return resultString;
}