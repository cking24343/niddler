package com.icapps.niddler.core;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.icapps.niddler.core.debug.NiddlerDebugger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import static com.icapps.niddler.core.NiddlerDebuggerImpl.DebugAction.extractId;
import static com.icapps.niddler.core.NiddlerDebuggerImpl.DebuggerConfiguration.sendHandleResponse;

/**
 * @author Nicola Verbeeck
 * @version 1
 */
final class NiddlerDebuggerImpl implements NiddlerDebugger {

	private static final String TAG = "NiddlerDebugger";

	private static final String DEBUG_TYPE_KEY = "controlType";
	private static final String DEBUG_PAYLOAD = "payload";
	private static final String KEY_MESSAGE_ID = "messageId";

	private static final String MESSAGE_MUTE_ACTIONS = "muteActions";
	private static final String MESSAGE_UNMUTE_ACTIONS = "unmuteActions";
	private static final String MESSAGE_ADD_BLACKLIST = "addBlacklist";
	private static final String MESSAGE_REMOVE_BLACKLIST = "removeBlacklist";
	private static final String MESSAGE_ADD_DEFAULT_RESPONSE = "addDefaultResponse";
	private static final String MESSAGE_REMOVE_REQUEST_ACTION = "removeRequestAction";
	private static final String MESSAGE_DEBUG_REPLY = "debugReply";
	private static final String MESSAGE_ADD_REQUEST = "addRequest";
	private static final String MESSAGE_REMOVE_REQUEST = "removeRequest";
	private static final String MESSAGE_ADD_RESPONSE = "addResponse";
	private static final String MESSAGE_REMOVE_RESPONSE = "removeResponse";
	private static final String MESSAGE_ACTIVATE_ACTION = "activateAction";
	private static final String MESSAGE_DEACTIVATE_ACTION = "deactivateAction";

	@NonNull
	private final DebuggerConfiguration mDebuggerConfiguration;
	@Nullable
	private transient ServerConnection mServerConnection;
	@NonNull
	private final Map<String, CompletableFuture<DebugResponse>> mWaitingResponses;

	NiddlerDebuggerImpl() {
		mDebuggerConfiguration = new DebuggerConfiguration();
		mWaitingResponses = new HashMap<>();
	}

	void onDebuggerAttached(@NonNull final ServerConnection connection) {
		mDebuggerConfiguration.connectionLost();
		mServerConnection = connection;
	}

	void onDebuggerConnectionClosed() {
		mDebuggerConfiguration.connectionLost();
		mServerConnection = null;
		synchronized (mWaitingResponses) {
			for (final Map.Entry<String, CompletableFuture<DebugResponse>> entry : mWaitingResponses.entrySet()) {
				entry.getValue().offer(null);
			}
			mWaitingResponses.clear();
		}
	}

	private void onDebuggerConfigurationMessage(@NonNull final String messageType, final JSONObject body) {
		try {
			switch (messageType) {
				case MESSAGE_MUTE_ACTIONS:
					mDebuggerConfiguration.muteActions(true);
					break;
				case MESSAGE_UNMUTE_ACTIONS:
					mDebuggerConfiguration.muteActions(false);
					break;
				case MESSAGE_ADD_BLACKLIST:
					mDebuggerConfiguration.addBlacklist(body.getString("regex"));
					break;
				case MESSAGE_REMOVE_BLACKLIST:
					mDebuggerConfiguration.removeBlacklist(body.getString("regex"));
					break;
				case MESSAGE_ADD_DEFAULT_RESPONSE:
					mDebuggerConfiguration.addRequestAction(new DefaultResponseAction(body));
					break;
				case MESSAGE_REMOVE_REQUEST_ACTION:
					mDebuggerConfiguration.removeRequestAction(extractId(body));
					break;
				case MESSAGE_DEBUG_REPLY:
					onDebugResponse(body.getString(KEY_MESSAGE_ID), parseResponse(body));
					break;
				case MESSAGE_ADD_REQUEST:
					mDebuggerConfiguration.addRequestAction(new DebugRequestAction(body));
					break;
				case MESSAGE_REMOVE_REQUEST:
					mDebuggerConfiguration.removeRequestAction(extractId(body));
					break;
				case MESSAGE_ADD_RESPONSE:
					mDebuggerConfiguration.addResponseAction(new DebugRequestResponseAction(body));
					break;
				case MESSAGE_REMOVE_RESPONSE:
					mDebuggerConfiguration.removeResponseAction(extractId(body));
					break;
				case MESSAGE_ACTIVATE_ACTION:
					mDebuggerConfiguration.setActionActive(extractId(body), true);
					break;
				case MESSAGE_DEACTIVATE_ACTION:
					mDebuggerConfiguration.setActionActive(extractId(body), false);
					break;
			}
		} catch (final JSONException e) {
			if (Log.isLoggable(TAG, Log.WARN)) {
				Log.w(TAG, "Invalid debugger json message:", e);
			}
		}
	}

	@Override
	public boolean isActive() {
		return mDebuggerConfiguration.active();
	}

	@Override
	public boolean isBlacklisted(@NonNull final CharSequence url) {
		return mDebuggerConfiguration.inBlacklisted(url);
	}

	@Nullable
	@Override
	public DebugResponse handleRequest(@NonNull final NiddlerRequest request) {
		final ServerConnection conn = mServerConnection;
		if (conn == null) {
			return null;
		}
		try {
			final CompletableFuture<DebugResponse> future = mDebuggerConfiguration.handleRequest(request, this);
			if (future == null) {
				return null;
			}
			return future.get();
		} catch (final Throwable ignored) {
			return null;
		}
	}

	@Nullable
	@Override
	public DebugResponse handleResponse(@NonNull final NiddlerRequest request, @NonNull final NiddlerResponse response) {
		final ServerConnection connection = mServerConnection;
		if (connection == null) {
			return null;
		}
		try {
			final CompletableFuture<DebugResponse> future = mDebuggerConfiguration.handleResponse(request, response, this);
			if (future == null) {
				return null;
			}
			return future.get();
		} catch (final Throwable ignored) {
			return null;
		}
	}

	void onControlMessage(@NonNull final JSONObject object, final ServerConnection connection) throws JSONException {
		if (mServerConnection != connection) {
			return;
		}

		onDebuggerConfigurationMessage(object.getString(DEBUG_TYPE_KEY), object.getJSONObject(DEBUG_PAYLOAD));
	}

	@Nullable
	CompletableFuture<DebugResponse> sendHandleRequest(@NonNull final NiddlerRequest request, @Nullable final NiddlerResponse response) {
		final ServerConnection connection = mServerConnection;
		if (connection == null) {
			return null;
		}
		return sendHandleResponse(request, response, connection, mWaitingResponses);
	}

	private void onDebugResponse(@NonNull final String messageId, @NonNull final DebugResponse response) {
		final CompletableFuture<DebugResponse> future;
		synchronized (mWaitingResponses) {
			future = mWaitingResponses.get(messageId);
			if (future != null) {
				mWaitingResponses.remove(messageId);
			}
		}
		if (future != null) {
			future.offer(response);
		}
	}

	static final class DebuggerConfiguration {

		private final ReentrantReadWriteLock mReadWriteLock = new ReentrantReadWriteLock(false);
		private final Lock mWriteLock = mReadWriteLock.writeLock();
		private final Lock mReadLock = mReadWriteLock.readLock();

		private List<Pattern> mBlacklist = new ArrayList<>();
		private List<RequestAction> mRequestActions = new ArrayList<>();
		private List<ResponseAction> mResponseActions = new ArrayList<>();
		private boolean mIsActive = false;
		private boolean mActionsMuted = false;

		boolean active() {
			mReadLock.lock();
			boolean isActive = mIsActive;
			mReadLock.unlock();
			return isActive;
		}

		void muteActions(final boolean muted) {
			mWriteLock.lock();
			mActionsMuted = muted;
			mWriteLock.unlock();
		}

		void connectionLost() {
			mWriteLock.lock();
			mBlacklist.clear();
			mRequestActions.clear();
			mResponseActions.clear();
			mIsActive = false;
			mWriteLock.unlock();
		}

		void addBlacklist(@NonNull final String regex) {
			mWriteLock.lock();
			if (mIsActive) {
				mBlacklist.add(Pattern.compile(regex));
			}
			mWriteLock.unlock();
		}

		void removeBlacklist(@NonNull final String regex) {
			mWriteLock.lock();
			final Iterator<Pattern> it = mBlacklist.iterator();
			while (it.hasNext()) {
				if (it.next().pattern().equals(regex)) {
					it.remove();
				}
			}
			mWriteLock.unlock();
		}

		boolean inBlacklisted(@NonNull final CharSequence url) {
			mReadLock.lock();

			if (mIsActive) {
				for (final Pattern pattern : mBlacklist) {
					if (pattern.matcher(url).matches()) {
						mReadLock.unlock();
						return true;
					}
				}
			}

			mReadLock.unlock();
			return false;
		}

		void addRequestAction(@NonNull final RequestAction action) {
			mWriteLock.lock();
			if (mIsActive) {
				mRequestActions.add(action);
			}
			mWriteLock.unlock();
		}

		void removeRequestAction(@NonNull final String actionId) {
			mWriteLock.lock();

			final Iterator<RequestAction> it = mRequestActions.iterator();
			while (it.hasNext()) {
				if (it.next().id.equals(actionId)) {
					it.remove();
				}
			}

			mWriteLock.unlock();
		}

		void addResponseAction(@NonNull final ResponseAction action) {
			mWriteLock.lock();
			if (mIsActive) {
				mResponseActions.add(action);
			}
			mWriteLock.unlock();
		}

		void removeResponseAction(@NonNull final String actionId) {
			mWriteLock.lock();

			final Iterator<ResponseAction> it = mResponseActions.iterator();
			while (it.hasNext()) {
				if (it.next().id.equals(actionId)) {
					it.remove();
				}
			}

			mWriteLock.unlock();
		}

		void setActionActive(@NonNull final String actionId, final boolean isActive) {
			mWriteLock.lock();

			for (final ResponseAction responseAction : mResponseActions) {
				if (responseAction.id.equals(actionId)) {
					responseAction.active = isActive;
				}
			}
			for (final RequestAction requestAction : mRequestActions) {
				if (requestAction.id.equals(actionId)) {
					requestAction.active = isActive;
				}
			}

			mWriteLock.unlock();
		}

		@Nullable
		CompletableFuture<DebugResponse> handleRequest(@NonNull final NiddlerRequest request, @NonNull final NiddlerDebuggerImpl debugger) throws IOException {
			try {
				mReadLock.lock();

				if (mIsActive && !mActionsMuted) {
					for (final RequestAction requestAction : mRequestActions) {
						final CompletableFuture<DebugResponse> response = requestAction.handleRequest(request, debugger);
						if (response != null) {
							return response;
						}
					}
				}

				return null;
			} finally {
				mReadLock.unlock();
			}
		}

		@Nullable
		CompletableFuture<DebugResponse> handleResponse(@NonNull final NiddlerRequest request,
				@NonNull final NiddlerResponse response,
				final NiddlerDebuggerImpl niddlerDebugger) throws IOException {
			try {
				mReadLock.lock();

				if (mIsActive && !mActionsMuted) {
					for (final ResponseAction responseAction : mResponseActions) {
						final CompletableFuture<DebugResponse> serverResponse = responseAction.handleResponse(request, response, niddlerDebugger);
						if (serverResponse != null) {
							return serverResponse;
						}
					}
				}

				return null;
			} finally {
				mReadLock.unlock();
			}
		}

		@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
		static CompletableFuture<DebugResponse> sendHandleResponse(@NonNull final NiddlerRequest request,
				@Nullable final NiddlerResponse response,
				@NonNull final ServerConnection connection,
				@NonNull final Map<String, CompletableFuture<DebugResponse>> waitingResponses) {
			final CompletableFuture<DebugResponse> future = new CompletableFuture<>();

			synchronized (waitingResponses) {
				waitingResponses.put(request.getMessageId(), future);
			}
			try {
				connection.send(makeDebugRequestMessage(request, response));
			} catch (final JSONException e) {
				future.offer(null);
				if (Log.isLoggable(TAG, Log.WARN)) {
					Log.w(TAG, "Failed to offer:", e);
				}
			}
			return future;
		}

	}

	static abstract class DebugAction {
		@NonNull
		final String id;

		transient boolean active = true;

		DebugAction(@NonNull final String id) {
			this.id = id;
		}

		@NonNull
		static String extractId(final JSONObject object) throws JSONException {
			return object.getString("id");
		}

		@Nullable
		static String extractMatchingRegex(final JSONObject object) {
			return object.optString("regex");
		}

		@NonNull
		static <T> T notNull(final T data) throws JSONException {
			if (data == null) {
				throw new JSONException("Item was expected to be non-null");
			}
			return data;
		}

	}

	static abstract class RequestAction extends DebugAction {

		RequestAction(@NonNull final String id) {
			super(id);
		}

		@Nullable
		abstract CompletableFuture<DebugResponse> handleRequest(@NonNull final NiddlerRequest request, @NonNull final NiddlerDebuggerImpl debugger) throws IOException;

	}

	static abstract class ResponseAction extends DebugAction {

		ResponseAction(@NonNull final String id) {
			super(id);
		}

		@Nullable
		abstract CompletableFuture<DebugResponse> handleResponse(@NonNull final NiddlerRequest request,
				@NonNull final NiddlerResponse response,
				@NonNull final NiddlerDebuggerImpl debugger) throws IOException;

	}

	static final class DefaultResponseAction extends RequestAction {

		@NonNull
		private final Pattern mRegex;
		@NonNull
		private final DebugResponse mDebugResponse;

		DefaultResponseAction(final JSONObject object) throws JSONException {
			super(extractId(object));

			mRegex = Pattern.compile(notNull(extractMatchingRegex(object)));
			mDebugResponse = parseResponse(object);
		}

		@Nullable
		@Override
		CompletableFuture<DebugResponse> handleRequest(@NonNull final NiddlerRequest request, @NonNull final NiddlerDebuggerImpl debugger) {
			if (active && mRegex.matcher(request.getUrl()).matches()) {
				return new CompletableFuture<>(mDebugResponse);
			}
			return null;
		}
	}

	static final class DebugRequestAction extends RequestAction {
		@NonNull
		private final Pattern mRegex;

		DebugRequestAction(final JSONObject object) throws JSONException {
			super(extractId(object));

			mRegex = Pattern.compile(notNull(extractMatchingRegex(object)));
		}

		@Nullable
		@Override
		CompletableFuture<DebugResponse> handleRequest(@NonNull final NiddlerRequest request, @NonNull final NiddlerDebuggerImpl debugger) {
			if (!active || !mRegex.matcher(request.getUrl()).matches()) {
				return null;
			}
			return debugger.sendHandleRequest(request, null);
		}
	}

	static final class DebugRequestResponseAction extends ResponseAction {
		@NonNull
		private final Pattern mRegex;

		DebugRequestResponseAction(final JSONObject object) throws JSONException {
			super(extractId(object));

			mRegex = Pattern.compile(notNull(extractMatchingRegex(object)));
		}

		@Nullable
		@Override
		CompletableFuture<DebugResponse> handleResponse(@NonNull final NiddlerRequest request,
				@NonNull final NiddlerResponse response,
				@NonNull final NiddlerDebuggerImpl debugger) {
			if (!active || !mRegex.matcher(request.getUrl()).matches()) {
				return null;
			}
			return debugger.sendHandleRequest(request, response);
		}
	}

	@NonNull
	static DebugResponse parseResponse(@NonNull final JSONObject config) throws JSONException {
		return new DebugResponse(config.getInt("code"),
				config.getString("message"),
				parseHeaders(config.optJSONObject("headers")),
				config.optString("encodedBody"),
				config.optString("bodyMimeType"));
	}

	@Nullable
	private static Map<String, String> parseHeaders(@Nullable final JSONObject headersObject) throws JSONException {
		if (headersObject == null) {
			return null;
		}

		final Map<String, String> headers = new HashMap<>();

		final Iterator<String> keys = headersObject.keys();
		while (keys.hasNext()) {
			final String key = keys.next();
			final String value = headersObject.getString(key);
			headers.put(key, value);
		}

		return headers;
	}

	@NonNull
	static String makeDebugRequestMessage(@NonNull final NiddlerRequest request, @Nullable final NiddlerResponse response) throws JSONException {
		final JSONObject object = new JSONObject();
		object.put("type", "debugRequest");
		object.put("requestId", request.getMessageId());
		if (response != null) {
			object.put("response", MessageBuilder.buildMessage(response));
		}
		return object.toString();
	}

	static class CompletableFuture<T> implements Future<T> {

		private final BlockingQueue<T> reply = new ArrayBlockingQueue<>(1);
		private volatile boolean done = false;

		CompletableFuture() {
		}

		CompletableFuture(T value) {
			try {
				reply.put(value);
			} catch (final InterruptedException ignored) {
			}
		}

		void offer(final T value) {
			try {
				reply.put(value);
			} catch (final InterruptedException ignored) {
				if (Log.isLoggable(TAG, Log.WARN)) {
					Log.w(TAG, "Failed to offer:", ignored);
				}
			}
		}

		@Override
		public boolean cancel(final boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return done;
		}

		@Override
		public T get() throws InterruptedException {
			return reply.take();
		}

		@Override
		public T get(final long timeout, @NonNull final TimeUnit unit) throws InterruptedException, TimeoutException {
			final T replyOrNull = reply.poll(timeout, unit);
			if (replyOrNull == null) {
				throw new TimeoutException();
			}
			return replyOrNull;
		}
	}

}
