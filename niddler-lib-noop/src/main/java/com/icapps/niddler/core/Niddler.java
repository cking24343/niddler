package com.icapps.niddler.core;


import android.app.Application;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Maarten Van Giel
 */

@SuppressWarnings({"UnusedParameters", "unused"})
public final class Niddler implements Closeable {

	private Niddler(final int port, final long cacheSize, final NiddlerServerInfo niddlerServerInfo) {
		// Dummy implementation
	}

	public void logRequest(final NiddlerRequest request) {
		// Do nothing
	}

	public void logResponse(final NiddlerResponse response) {
		// Do nothing
	}

	private String generateMessage(final String messageId, final String requestId, final long timestamp, final String messageContents) {
		return null;
	}

	private String generateControlMessageContents(final int controlCode, final String data) {
		return null;
	}

	public void start() {
		// Do nothing
	}

	public void attachToApplication(final Application application) {
		// Do nothing
	}

	@Override
	public void close() throws IOException {
		// Do nothing
	}

	public boolean enabled() {
		return false;
	}

	public boolean isStarted() {
		return false;
	}

	public boolean isClosed() {
		return false;
	}

	private void sendWithCache(final String message) {
		// Do nothing
	}

	private String transformBody(final NiddlerMessageBase base) {
		return null;
	}

	private StringBuilder transformHeaders(final StringBuilder builder, final NiddlerMessageBase base) {
		return null;
	}

	public final static class NiddlerServerInfo {

		public NiddlerServerInfo(String mName, String mDescription) {
			// Do nothing
		}

		public String toJsonString() {
			return null;
		}
	}

	public final static class Builder {

		/**
		 * Sets the port on which Niddler will listen for incoming connections
		 *
		 * @param port The port to be used
		 * @return Builder
		 */
		public Builder setPort(final int port) {
			return this;
		}

		/**
		 * Sets the cache size to be used for caching requests and responses while there is no client connected
		 *
		 * @param cacheSize The cache size to be used, in bytes
		 * @return Builder
		 */
		public Builder setCacheSize(final long cacheSize) {
			return this;
		}

		/**
		 * Sets additional information about this Niddler server which will be shown on the client side
		 *
		 * @param niddlerServerInfo The additional information about this Niddler server
		 * @return Builder
		 */
		public Builder setNiddlerInformation(final NiddlerServerInfo niddlerServerInfo) {
			return this;
		}

		/**
		 * Sets the NiddlerServerInformation to the application's package name and device info
		 *
		 * @param application the current application
		 * @return Builder
		 */
		public Builder forApplication(final Application application) {
			return this;
		}

		/**
		 * Builds a Niddler instance with the configured parameters
		 *
		 * @return a Niddler instance
		 */
		public Niddler build() {
			return new Niddler(0, 0, null);
		}

	}

}