package cn.reactnative.modules.weibo;

import android.app.Activity;
import android.content.Intent;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.sina.weibo.sdk.WbSdk;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WbAuthListener;
import com.sina.weibo.sdk.auth.WbConnectErrorMessage;
import com.sina.weibo.sdk.auth.sso.SsoHandler;

public class WeiboModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    public WeiboModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    private SsoHandler mSsoHandler;
    private String appId;

    private boolean wbSdkInstalled = false;

    @Override
    public String getName() {
        return "RCTWeiboAPI";
    }

    @ReactMethod
    public void register(final ReadableMap config, Promise promise) {
        if (!wbSdkInstalled) {
            if (config.hasKey("appId")) {
                this.appId = config.getString("appId");
            } else {
                promise.reject("-1", new Exception("no appId"));
            }
            String redirectURI = "";
            if (config.hasKey("redirectURI")) {
                redirectURI = config.getString("redirectURI");
            }
            String scope = "";
            if (config.hasKey("scope")) {
                scope = config.getString("scope");
            }

            final AuthInfo sinaAuthInfo = new AuthInfo(getReactApplicationContext(), this.appId, redirectURI, scope);
            WbSdk.install(getCurrentActivity(), sinaAuthInfo);
            wbSdkInstalled = true;
            promise.resolve(true);
        }
    }

    @ReactMethod
    public void login(Promise promise) {
        if (mSsoHandler == null) {
            mSsoHandler = new SsoHandler(getCurrentActivity());
            SelfWbAuthListener listener = new SelfWbAuthListener(promise);
            mSsoHandler.authorize(listener);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
            mSsoHandler = null;
        }
    }

    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        this.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    private class SelfWbAuthListener implements WbAuthListener {
        private Promise promise;

        SelfWbAuthListener(Promise promise) {
            super();
            this.promise = promise;
        }

        @Override
        public void onSuccess(final Oauth2AccessToken token) {
            if (token.isSessionValid()) {
                WritableMap result = Arguments.createMap();
                result.putString("accessToken", token.getToken());
                result.putDouble("expirationDate", token.getExpiresTime());
                result.putString("userID", token.getUid());
                result.putString("refreshToken", token.getRefreshToken());
                promise.resolve(result);
            } else {
                promise.reject("-1", new Exception("token invalid"));
            }
        }


        @Override
        public void onFailure(WbConnectErrorMessage e) {
            promise.reject(e.getErrorCode(), new Exception("auth failed:" + e.getErrorMessage()));
        }

        @Override
        public void cancel() {
            promise.reject("100", new Exception("user cancel"));
        }
    }
}