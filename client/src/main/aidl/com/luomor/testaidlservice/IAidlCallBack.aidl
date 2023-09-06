// IAidlCallBack.aidl
package com.luomor.testaidlservice;

// Declare any non-default types here with import statements

interface IAidlCallBack {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void onMessageSuccess(String message);
}
