package com.icapps.niddler.interceptor;

import com.icapps.niddler.core.NiddlerRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;

/**
 * Created by maartenvangiel on 14/11/2016.
 */
public class NiddlerOkHttpRequest implements NiddlerRequest {

    private final Request mRequest;
    private final String mRequestId;
    private final String mMessageId;
    private final long mTimestamp;

    public NiddlerOkHttpRequest(Request request, String requestId) {
        this.mRequest = request;
        this.mRequestId = requestId;
        this.mMessageId = UUID.randomUUID().toString();
        this.mTimestamp = System.currentTimeMillis();
    }

    @Override
    public String getMessageId() {
        return mMessageId;
    }

    @Override
    public String getRequestId() {
        return mRequestId;
    }

    @Override
    public long getTimestamp() {
        return mTimestamp;
    }

    @Override
    public String getUrl() {
        return mRequest.url().toString();
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return mRequest.headers().toMultimap();
    }

    @Override
    public String getMethod() {
        return mRequest.method();
    }

    @Override
    public void writeBody(OutputStream stream) {
        try {
            final BufferedSink buffer = Okio.buffer(Okio.sink(stream));

            final RequestBody body = mRequest.body();
            if (body != null) {
                mRequest.body().writeTo(buffer);
                buffer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
