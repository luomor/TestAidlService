// IAidlInterface.aidl
package com.luomor.testaidlservice;

// Declare any non-default types here with import statements
import com.luomor.testaidlservice.IAidlCallBack;
interface IAidlInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void registerCallBack(IAidlCallBack iAidlCallBack);
    void unregisterCallBack(IAidlCallBack iAidlCallBack);
    void sendMessage(String message);
    List<String> getMessages();
}
