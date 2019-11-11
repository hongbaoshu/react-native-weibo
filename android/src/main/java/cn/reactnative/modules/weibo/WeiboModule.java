package cn.reactnative.modules.weibo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;

import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.common.util.UriUtil;
import com.facebook.datasource.BaseDataSubscriber;
import com.facebook.datasource.DataSource;
import com.facebook.datasource.DataSubscriber;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.drawable.OrientedDrawable;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.image.CloseableStaticBitmap;
import com.facebook.imagepipeline.image.EncodedImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.sina.weibo.sdk.WbSdk;
import com.sina.weibo.sdk.api.ImageObject;
import com.sina.weibo.sdk.api.TextObject;
import com.sina.weibo.sdk.api.VideoSourceObject;
import com.sina.weibo.sdk.api.WebpageObject;
import com.sina.weibo.sdk.api.WeiboMultiMessage;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WbAuthListener;
import com.sina.weibo.sdk.auth.WbConnectErrorMessage;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.share.WbShareCallback;
import com.sina.weibo.sdk.share.WbShareHandler;

import java.util.Date;

import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;

public class WeiboModule extends ReactContextBaseJavaModule implements ActivityEventListener, WbShareCallback {

    public WeiboModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    private SsoHandler mSsoHandler;
    private WbShareHandler mShareHandler;
    private Promise mSharePromise;
    private boolean wbSdkInstalled = false;

    private static final String RCTWBShareTypeNews = "news";
    private static final String RCTWBShareTypeImage = "image";
    private static final String RCTWBShareTypeText = "text";
    private static final String RCTWBShareTypeVideo = "video";

    private static final String RCTWBShareType = "type";
    private static final String RCTWBShareText = "text";
    private static final String RCTWBShareTitle = "title";
    private static final String RCTWBShareDescription = "description";
    private static final String RCTWBShareWebpageUrl = "webpageUrl";
    private static final String RCTWBSharePropVideoPath = "videoPath";
    private static final String RCTWBShareImageUrl = "imageUrl";


    @Override
    public String getName() {
        return "RCTWeiboAPI";
    }

    @ReactMethod
    public void register(final ReadableMap config, Promise promise) {
        if (wbSdkInstalled) return;
        String appId = config.getString("appId");
        if (appId.length() == 0) {
            promise.reject("-1", new Exception("no appId"));
            return;
        }
        String redirectURI = config.getString("redirectURI");
        String scope = config.getString("scope");

        final AuthInfo sinaAuthInfo = new AuthInfo(getReactApplicationContext(), appId, redirectURI, scope);
        WbSdk.install(getCurrentActivity(), sinaAuthInfo);
        mShareHandler = new WbShareHandler(getCurrentActivity());
        mShareHandler.registerApp();
        wbSdkInstalled = true;
        promise.resolve(true);
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

    @Override
    public void onWbShareSuccess() {
        mSharePromise.resolve(true);
    }

    @Override
    public void onWbShareCancel() {
        mSharePromise.reject("100", new Exception("user cancel"));
    }

    @Override
    public void onWbShareFail() {
        mSharePromise.reject("-1", new Exception("failed"));
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


    @ReactMethod
    public void share(final ReadableMap data, Promise promise) {
        mSharePromise = promise;
        if (data.hasKey(RCTWBShareImageUrl)) {
            String imageUrl = data.getString(RCTWBShareImageUrl);
            DataSubscriber<CloseableReference<CloseableImage>> dataSubscriber =
                    new BaseDataSubscriber<CloseableReference<CloseableImage>>() {
                        @Override
                        public void onNewResultImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                            // isFinished must be obtained before image, otherwise we might set intermediate result
                            // as final image.
                            boolean isFinished = dataSource.isFinished();
//                        float progress = dataSource.getProgress();
                            CloseableReference<CloseableImage> image = dataSource.getResult();
                            if (image != null) {
                                Drawable drawable = _createDrawable(image);
                                Bitmap bitmap = _drawable2Bitmap(drawable);
                                _share(data, bitmap);
                            } else if (isFinished) {
                                _share(data, null);
                            }
                            dataSource.close();
                        }

                        @Override
                        public void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                            dataSource.close();
                            _share(data, null);
                        }

                        @Override
                        public void onProgressUpdate(DataSource<CloseableReference<CloseableImage>> dataSource) {
                        }
                    };
            ResizeOptions resizeOptions = null;
            if (!data.hasKey(RCTWBShareType) || !data.getString(RCTWBShareType).equals(RCTWBShareTypeImage)) {
                resizeOptions = new ResizeOptions(80, 80);
            }

            this._downloadImage(imageUrl, resizeOptions, dataSubscriber);
        } else {
            this._share(data, null);
        }
    }

    public void shareNews(ReadableMap data, Bitmap bitmap) {
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();//初始化微博的分享消息
        TextObject textObject = new TextObject();
        if (data.hasKey(RCTWBShareText)) {
            textObject.text = data.getString(RCTWBShareText);
        }
        weiboMessage.textObject = textObject;
        addMessageDetail(weiboMessage, data, bitmap);
        mShareHandler.shareMessage(weiboMessage, false);
    }

    public void shareText(ReadableMap data) {
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();//初始化微博的分享消息
        TextObject textObject = new TextObject();
        textObject.text = data.getString(RCTWBShareText);
        weiboMessage.textObject = textObject;
        mShareHandler.shareMessage(weiboMessage, false);
    }

    public void shareImage(ReadableMap data, Bitmap bitmap) {
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();//初始化微博的分享消息
        ImageObject imageObject = new ImageObject();
        if (bitmap != null) {
            imageObject.setImageObject(bitmap);
        }
        weiboMessage.imageObject = imageObject;
        mShareHandler.shareMessage(weiboMessage, false);
    }

    public void shareVideo(ReadableMap data, Bitmap bitmap) {

        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();//初始化微博的分享消息
        TextObject textObject = new TextObject();
        if (data.hasKey(RCTWBShareText)) {
            textObject.text = data.getString(RCTWBShareText);
        }
        weiboMessage.textObject = textObject;
        VideoSourceObject videoObject = new VideoSourceObject();
        // if (data.hasKey(RCTWBShareWebpageUrl)) {
        //     videoObject.dataUrl = data.getString(RCTWBShareWebpageUrl);
        // }

        videoObject.videoPath = Uri.parse(data.getString(RCTWBSharePropVideoPath));
        weiboMessage.mediaObject = videoObject;
        addMessageDetail(weiboMessage, data, bitmap);
        mShareHandler.shareMessage(weiboMessage, false);
    }

    private void addMessageDetail(WeiboMultiMessage weiboMessage, ReadableMap data, Bitmap bitmap) {
        WebpageObject webpageObject = new WebpageObject();
        if (data.hasKey(RCTWBShareWebpageUrl)) {
            webpageObject.actionUrl = data.getString(RCTWBShareWebpageUrl);
        }
        if (data.hasKey(RCTWBShareDescription)) {
            weiboMessage.mediaObject.description = data.getString(RCTWBShareDescription);
        }
        if (data.hasKey(RCTWBShareTitle)) {
            weiboMessage.mediaObject.title = data.getString(RCTWBShareTitle);
        }
        if (bitmap != null) {
            weiboMessage.mediaObject.setThumbImage(bitmap);
        }
        weiboMessage.mediaObject = webpageObject;
        weiboMessage.mediaObject.identify = new Date().toString();
    }

    private void _share(ReadableMap data, Bitmap bitmap) {
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();//初始化微博的分享消息
        TextObject textObject = new TextObject();
        if (data.hasKey(RCTWBShareText)) {
            textObject.text = data.getString(RCTWBShareText);
        }
        weiboMessage.textObject = textObject;

        String type = RCTWBShareTypeNews;
        if (data.hasKey(RCTWBShareType)) {
            type = data.getString(RCTWBShareType);
        }

        if (type.equals(RCTWBShareTypeText)) {
        } else if (type.equals(RCTWBShareTypeImage)) {
            ImageObject imageObject = new ImageObject();
            if (bitmap != null) {
                imageObject.setImageObject(bitmap);
            }
            weiboMessage.imageObject = imageObject;
        } else {
            if (type.equals(RCTWBShareTypeNews)) {
                WebpageObject webpageObject = new WebpageObject();
                if (data.hasKey(RCTWBShareWebpageUrl)) {
                    webpageObject.actionUrl = data.getString(RCTWBShareWebpageUrl);
                }
                weiboMessage.mediaObject = webpageObject;
            } else if (type.equals(RCTWBShareTypeVideo)) {
                VideoSourceObject videoObject = new VideoSourceObject();
                // if (data.hasKey(RCTWBShareWebpageUrl)) {
                //     videoObject.dataUrl = data.getString(RCTWBShareWebpageUrl);
                // }

                videoObject.videoPath = Uri.parse(data.getString(RCTWBSharePropVideoPath));
                weiboMessage.mediaObject = videoObject;
            }
            if (data.hasKey(RCTWBShareDescription)) {
                weiboMessage.mediaObject.description = data.getString(RCTWBShareDescription);
            }
            if (data.hasKey(RCTWBShareTitle)) {
                weiboMessage.mediaObject.title = data.getString(RCTWBShareTitle);
            }
            if (bitmap != null) {
                weiboMessage.mediaObject.setThumbImage(bitmap);
            }
            weiboMessage.mediaObject.identify = new Date().toString();
        }

        mShareHandler.shareMessage(weiboMessage, false);
    }

    private void _downloadImage(String imageUrl, ResizeOptions resizeOptions, DataSubscriber<CloseableReference<CloseableImage>> dataSubscriber) {
        Uri uri = null;
        try {
            uri = Uri.parse(imageUrl);
            // Verify scheme is set, so that relative uri (used by static resources) are not handled.
            if (uri.getScheme() == null) {
                uri = null;
            }
        } catch (Exception e) {
            // ignore malformed uri, then attempt to extract resource ID.
        }
        if (uri == null) {
            uri = _getResourceDrawableUri(getReactApplicationContext(), imageUrl);
        } else {
        }

        ImageRequestBuilder builder = ImageRequestBuilder.newBuilderWithSource(uri);
        if (resizeOptions != null) {
            builder.setResizeOptions(resizeOptions);
        }
        ImageRequest imageRequest = builder.build();

        ImagePipeline imagePipeline = Fresco.getImagePipeline();
        DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);
        dataSource.subscribe(dataSubscriber, UiThreadImmediateExecutorService.getInstance());
    }

    private static @Nullable
    Uri _getResourceDrawableUri(Context context, @Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        name = name.toLowerCase().replace("-", "_");
        int resId = context.getResources().getIdentifier(
                name,
                "drawable",
                context.getPackageName());
        return new Uri.Builder()
                .scheme(UriUtil.LOCAL_RESOURCE_SCHEME)
                .path(String.valueOf(resId))
                .build();
    }

    private Drawable _createDrawable(CloseableReference<CloseableImage> image) {
        Preconditions.checkState(CloseableReference.isValid(image));
        CloseableImage closeableImage = image.get();
        if (closeableImage instanceof CloseableStaticBitmap) {
            CloseableStaticBitmap closeableStaticBitmap = (CloseableStaticBitmap) closeableImage;
            BitmapDrawable bitmapDrawable = new BitmapDrawable(
                    getReactApplicationContext().getResources(),
                    closeableStaticBitmap.getUnderlyingBitmap());
            if (closeableStaticBitmap.getRotationAngle() == 0 ||
                    closeableStaticBitmap.getRotationAngle() == EncodedImage.UNKNOWN_ROTATION_ANGLE) {
                return bitmapDrawable;
            } else {
                return new OrientedDrawable(bitmapDrawable, closeableStaticBitmap.getRotationAngle());
            }
        } else {
            throw new UnsupportedOperationException("Unrecognized image class: " + closeableImage);
        }
    }

    private Bitmap _drawable2Bitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof NinePatchDrawable) {
            Bitmap bitmap = Bitmap
                    .createBitmap(
                            drawable.getIntrinsicWidth(),
                            drawable.getIntrinsicHeight(),
                            drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                                    : Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            return bitmap;
        } else {
            return null;
        }
    }
}