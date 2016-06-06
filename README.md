# Android_N_SQLite_Corruption
An example project that shows SQLite corruption when using SQLite simultaneously from Java and Native code.


## The problem:

As of Android N, you must not use Java's android.database.sqlite classes and native code to make changes to the same database file. You will corrupt the database.


## The cause: 

The SQLite "How to corrupt A SQLite Database File" at https://www.sqlite.org/howtocorrupt.html describes it as "Multiple copies of SQLite linked into the same application." 

## How things used to work: 

In Android versions previous to Android N, a native library could rely on the system libsqlite.so. This WAS NOT OFFICIALLY SUPPORTED, and sqlite was not one of the stable apis. https://developer.android.com/ndk/guides/stable_apis.html. Regardless, the solution worked, and only one copy of the sqlite library was loaded. Locking the database was respected in both Java and native code.

## How things work in Android N: 

Any attempt to load the system sqlite library will cause an alert dialog warning the user of unsupported behavior. Although it does load a copy of the sqlite library, it is not the same one in use from android.database.sqlite, as locking is not respected.

## How to use the test project: 

* Run the application on an Android N x86 emulator. 
* Select One Java and One Native thread.
* Push the button.
* Follow the logcat stream
* On Android N, locking also means that starting up the two threads is more error prone. You may get several fluke errors before you get a test that runs long enough to show the true corruption.
* Once a stable run is underway, it will eventually stop with a SQLiteDatabaseCorruptException.

## To see a successful run:
* Load the application on an earlier Android x86 emulator
* Select One Java and One Native thread.
* Push the button.
* Follow the logcat stream
* You will see quite a few locked database errors, which is good. That means that both Java and Native are respecting the locking 


## Solutions: 

* Google could add SQLite to the list of stable APIs. That would make it possible to link against the system sqlite, and ensure that native and Java SQLite clients can use the single copy of the SQLite library.
* Any application that includes native sqlite access could forbid access through android.database.sqlite classes. The application would have to ship its own version of those android.database.sqlite classes. That's what SQLCipher does. Unfortunately, there is no way prevent a developer from mistakenly writing access through android.database.sqlite classes, and exposing themselves to corruption.
* Use JNI instead of native access. Everywhere you would call a native sqlite3 call, use JNI to make the corresponding call from SQLiteDatabase. The problem with that is that there is not a 1-to-1 mapping between the calls in native code and the calls in android.database.sqlite.




