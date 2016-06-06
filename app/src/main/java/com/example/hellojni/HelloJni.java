/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.sqlitecorruption;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;


public class HelloJni extends Activity
{
    String fullpath = "";
    SQLiteOpenHelper dbHelper = null;

    private String[] progressChars = new String[] {"◢", "◣", "◤", "◥"};
    private void advanceOutputChar(TextView output, String id)
    {
        if (output.getTag() == null || (int)output.getTag() >= (progressChars.length - 1))
        {
            output.setTag(0);
        }
        else
        {
            output.setTag((int)output.getTag() + 1);
        }
        output.setText(id + " " + progressChars[(int)output.getTag()]);
    }
    private class MyDbErrorHandler implements DatabaseErrorHandler {

        private String cachePath;
        public  MyDbErrorHandler(String path)
        {
           cachePath = path;
        }
        private void copyFileUsingFileChannels(File source, File dest)
                throws IOException {
            FileChannel inputChannel = null;
            FileChannel outputChannel = null;
            try {
                inputChannel = new FileInputStream(source).getChannel();
                outputChannel = new FileOutputStream(dest).getChannel();
                outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
            } finally {
                if (inputChannel != null)
                    inputChannel.close();
                if (outputChannel != null)
                    outputChannel.close();
            }
        }

        @Override
        public void onCorruption(SQLiteDatabase db) {
            setStopFlag(1);
            HelloJni.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView v = (TextView) HelloJni.this.findViewById(R.id.Progress1);
                    v.setText("Corruption!");
                    v = (TextView) HelloJni.this.findViewById(R.id.Progress2);
                    v.setText("Corruption!");
                    v = (Button) HelloJni.this.findViewById(R.id.startTest);
                    v.setText("Start Running Tests");
                    v.setTag(false);
                }
            });
            Log.e("CORRUPTION!", "Corruption!");

            try {

                Log.e("CORRUPTION!", "Copying corrupted database to " + cachePath + "/corruptedDatabase");
                //create file
                File destFile = new File(cachePath + "/corruptedDatabase");
                copyFileUsingFileChannels(new File(db.getPath()), destFile);
            }
            catch (IOException ex)
            {
                Log.e("CORRUPTION!", "Copy Problem!", ex);
            }
        }
    }

    private class MySQLiteOpenHelper extends SQLiteOpenHelper
    {
        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
            db.enableWriteAheadLogging();
        }

        public MySQLiteOpenHelper(Context context, String cachePath)
        {
            super(context, cachePath + "/corruptionTest", null, 1, new MyDbErrorHandler(cachePath));
        }
        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
            return;
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL("CREATE TABLE test (id INTEGER PRIMARY KEY, string TEXT);");

            return;
        }
    }

    private String getMyCachePath(Context context)
    {
        String cachePath = context.getCacheDir().getPath(); // you still need a default value if not mounted

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment.isExternalStorageRemovable()) {
            if (HelloJni.this.getExternalCacheDir() != null) {
                cachePath = HelloJni.this.getExternalCacheDir().getPath(); // most likely your null value
            }
        }
        return cachePath;
    }
    boolean stopJavaThreads = false;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        final Button b = (Button)this.findViewById(R.id.startTest);

        final String cachePath = getMyCachePath(this);
        File f;
        f = new File(cachePath + "/corruptionTest");
        if (f.exists())
            f.delete();
        dbHelper = new MySQLiteOpenHelper(this, f.getPath());
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (b.getTag() != null && (boolean)b.getTag() == true)
                {
                    b.setTag(false);
                    setStopFlag(1);
                    stopJavaThreads = true;
                    b.setText("Start Running Tests");
                    return;
                }

                setStopFlag(0);
                File f;
                f = new File(cachePath + "/corruptionTest-wal");
                if (f.exists())
                    f.delete();
                f = new File(cachePath + "/corruptionTest-shm");
                if (f.exists())
                    f.delete();
                f = new File(cachePath + "/corruptionTest-journal");
                if (f.exists())
                    f.delete();
                f = new File(cachePath + "/corruptionTest");
                if (f.exists())
                    f.delete();

                stopJavaThreads = false;
                SQLiteDatabase d = dbHelper.getWritableDatabase();
                fullpath = d.getPath();

                //Start out a with a new database.
                d.enableWriteAheadLogging();
                d.execSQL("DROP TABLE test;");
                d.execSQL("CREATE TABLE test (id INTEGER PRIMARY KEY, string TEXT);");
                d.execSQL("INSERT INTO test (string) VALUES ('Starting row');");

                d.close();

                RadioButton rb = (RadioButton) HelloJni.this.findViewById(R.id.radio_java);
                if (rb.isChecked())
                {
                    new JavaThread(fullpath, "Java 1", (TextView) HelloJni.this.findViewById(R.id.Progress1)).start();
                    try {
                        Thread.sleep(100, 100);
                    } catch (InterruptedException ie) {
                    }
                    new JavaThread(fullpath, "Java 2", (TextView) HelloJni.this.findViewById(R.id.Progress2)).start();
                }

                rb = (RadioButton) HelloJni.this.findViewById(R.id.radio_java_native);
                if (rb.isChecked()) {
                    new JavaThread(fullpath, "Java 1", (TextView) HelloJni.this.findViewById(R.id.Progress1)).start();
                    try {
                        Thread.sleep(15000, 100);
                    } catch (InterruptedException ie) {
                    }
                    new NativeThread(fullpath, 1, (TextView) HelloJni.this.findViewById(R.id.Progress2)).start();

                }

                rb = (RadioButton) HelloJni.this.findViewById(R.id.radio_native);
                if (rb.isChecked()) {
                    new NativeThread(fullpath, 1, (TextView) HelloJni.this.findViewById(R.id.Progress1)).start();
                    try {
                        Thread.sleep(100, 100);
                    } catch (InterruptedException ie) {
                    }
                    new NativeThread(fullpath, 2, (TextView) HelloJni.this.findViewById(R.id.Progress2)).start();
                }
                b.setText("Stop Tests");
                b.setTag(true);
            }
        });
    }

    private class JavaThread extends Thread
    {
        String fullpath = "";
        String id = "";
        TextView outputView = null;
        public JavaThread(String FullPath, String identifier, TextView output)
        {
            fullpath = FullPath;
            outputView = output;
            id = identifier;
        }
        @Override
        public void run() {
            HelloJni.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    advanceOutputChar(outputView, id);
                }
            });
            try {
                Log.e("Java SQLite path", id + " SQLite path: " + fullpath);

                SQLiteDatabase d = dbHelper.getWritableDatabase();
                int i = 0;

                while (true) {
                    for (int m = 0; m < 10; m++) {
                        if (HelloJni.this.isFinishing() || HelloJni.this.isDestroyed())
                            break;
                        if (HelloJni.this.stopJavaThreads) {
                            Log.e(id, id + " Stopping");
                            d.close();
                            return;
                        }

                        try {
                            if (!d.isOpen()) {
                                Log.e("SQLite path", id + " reopening database ");
                                d = dbHelper.getWritableDatabase();
                            }
                            //d.beginTransaction();
                            final String strcol = id + " row number " + i;
                            for (int j = 0; j < 20; j++) {
                                //Log.e("SQLite path", "Java insert");
                                d.execSQL("INSERT INTO test (string) VALUES ('" + strcol + "');");
                            }

                            i++;
                            for (int j = 0; j < 20; j++) {
                                //Log.e("SQLite path", "Java update");
                                d.execSQL("UPDATE test set string='Update from " + id + "' WHERE id=(select abs(random()) % ((SELECT COUNT (*) from test)  - 1) + 1);");
                            }

                            //d.endTransaction();
                            if (i % 20 == 0) {
                                Cursor c = d.rawQuery("SELECT COUNT(*) FROM test WHERE string not like '%" + id + "%';", null);
                                if (c.moveToFirst()) {
                                    int rowCount = c.getInt(0);
                                    Log.e("Java rowcount", id + " thinks that there are " + rowCount + " rows that it didn't change or add.");
                                }
                                c.close();

                                c = d.rawQuery("SELECT COUNT(*) FROM test WHERE string like '%" + id + "%';", null);
                                if (c.moveToFirst()) {
                                    int rowCount = c.getInt(0);
                                    Log.e("Java rowcount", id + " thinks that there are " + rowCount + " rows that it added or changed.");
                                }
                                c.close();
                            }
                        } catch (SQLiteDatabaseLockedException ex) {
                            //ignore any locked database exceptions
                            if (d.isOpen())
                                d.close();
                            HelloJni.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    advanceOutputChar(outputView, id);
                                }
                            });
                        } catch (SQLiteCantOpenDatabaseException coex) {
                            //ignore any locked database exceptions
                            if (d.isOpen())
                                d.close();
                            HelloJni.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    advanceOutputChar(outputView, id);
                                }
                            });
                        } catch (SQLiteDatabaseCorruptException cx) {
                            //ignore any locked database exceptions
                            if (d.isOpen())
                                d.close();
                            Log.e(id, "Database Corruption Exception", cx);
                            return;
                        } catch (Exception e) {
                            if (d.isOpen())
                                d.close();
                            Log.e(id, "Exception", e);
                        }
                    }
                    d.close();
                }
            }
            catch (Exception e)
            {
                Log.e(id, "Exception", e);
                final String errMsg = e.getMessage();
                HelloJni.this.setStopFlag(1);
                HelloJni.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        outputView.setText("Couldn't start tests " + errMsg);
                    }
                });
            }
        }
    }

    private class NativeThread extends Thread
    {
        String fullpath = "";
        TextView outputView = null;
        int id = 1;
        String idstring = "";
        public NativeThread(String FullPath, int identifier, TextView output)
        {
            fullpath = FullPath;
            outputView = output;
            id = identifier;
            idstring = "Native " + id;
        }
        @Override
        public void run() {
            HelloJni.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    advanceOutputChar(outputView, idstring);
                }
            });
            String result = startRunningEdits(fullpath, id);
            while (result != null
                    && (result.toUpperCase().contains("LOCKED") || result.startsWith("CONTINUE"))) {

                final String errResult = result;
                try {
                    Thread.sleep(50, 100);
                } catch (InterruptedException ie) {
                }
                HelloJni.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        advanceOutputChar(outputView, idstring);
                    }
                });
                result = startRunningEdits(fullpath, id);
            }

            if (result == null) {
                HelloJni.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        outputView.setText("native result is null");
                    }
                });
            }
            else
            {
                final String errResult = result;
                HelloJni.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        outputView.setText(errResult);
                    }
                });
            }

            return;
        }
    }

    public native String  startRunningEdits(String fullpath, int id);
    public native void  setStopFlag(int flag);

    static {
        //System.loadLibrary("sqlite");
        System.loadLibrary("hello-jni");
    }
}
