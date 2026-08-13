package org.acme.edgy.runtime.scatter;

import java.util.Set;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

import io.netty.handler.codec.DecoderResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

/**
 * Minimal {@link HttpServerRequest} wrapper for scatter legs.
 * Delegates read-only metadata to the original request.
 * Body streaming is a no-op (the body is set on the {@code ProxyRequest} by
 * {@link org.acme.edgy.runtime.interceptors.scatter.MethodBodyInterceptor}).
 * {@link #response()} returns a no-op stub since the response is captured by
 * {@link ScatterLegContext}, never sent to the client.
 */
class ScatterLegRequest extends HttpServerRequestInternal {

    private final HttpServerRequest delegate;
    private final HttpServerRequestInternal delegateInternal;
    private final HttpServerResponse noOpResponse = new NoOpServerResponse();

    ScatterLegRequest(HttpServerRequest original) {
        this.delegate = original;
        this.delegateInternal = (HttpServerRequestInternal) original;
    }

    // --- HttpServerRequestInternal abstract methods ---

    @Override
    public boolean isUseSemicolonAsQueryParamDelimiter() {
        return delegateInternal.isUseSemicolonAsQueryParamDelimiter();
    }

    @Override
    public Context context() {
        return delegateInternal.context();
    }

    @Override
    public Object metric() {
        return delegateInternal.metric();
    }

    // --- Read-only metadata delegated to original ---

    @Override
    public HttpVersion version() {
        return delegate.version();
    }

    @Override
    public HttpMethod method() {
        return delegate.method();
    }

    @Override
    public String uri() {
        return delegate.uri();
    }

    @Override
    public String path() {
        return delegate.path();
    }

    @Override
    public String query() {
        return delegate.query();
    }

    @Override
    public String scheme() {
        return delegate.scheme();
    }

    @Override
    public String host() {
        return delegate.host();
    }

    @Override
    public HostAndPort authority() {
        return delegate.authority();
    }

    @Override
    public HostAndPort authority(boolean real) {
        return delegate.authority(real);
    }

    @Override
    public long bytesRead() {
        return delegate.bytesRead();
    }

    @Override
    public MultiMap headers() {
        return delegate.headers();
    }

    @Override
    public MultiMap params(boolean semicolonIsNormalChar) {
        return delegate.params(semicolonIsNormalChar);
    }

    @Override
    public String absoluteURI() {
        return delegate.absoluteURI();
    }

    @Override
    public SocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    @Override
    public SocketAddress localAddress() {
        return delegate.localAddress();
    }

    @Override
    public HttpConnection connection() {
        return delegate.connection();
    }

    @Override
    public boolean isSSL() {
        return delegate.isSSL();
    }

    @Override
    public DecoderResult decoderResult() {
        return delegate.decoderResult();
    }

    @Override
    public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
        return delegate.peerCertificateChain();
    }

    @Override
    public Cookie getCookie(String name) {
        return delegate.getCookie(name);
    }

    @Override
    public Cookie getCookie(String name, String domain, String path) {
        return delegate.getCookie(name, domain, path);
    }

    @Override
    public Set<Cookie> cookies(String name) {
        return delegate.cookies(name);
    }

    @Override
    public Set<Cookie> cookies() {
        return delegate.cookies();
    }

    // --- Response returns a no-op stub ---

    @Override
    public HttpServerResponse response() {
        return noOpResponse;
    }

    // --- Body streaming: no-op (body is set by MethodBodyInterceptor) ---

    @Override
    public HttpServerRequest handler(Handler<Buffer> handler) {
        return this;
    }

    @Override
    public HttpServerRequest pause() {
        return this;
    }

    @Override
    public HttpServerRequest resume() {
        return this;
    }

    @Override
    public HttpServerRequest fetch(long amount) {
        return this;
    }

    @Override
    public HttpServerRequest endHandler(Handler<Void> endHandler) {
        return this;
    }

    @Override
    public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
        return this;
    }

    @Override
    public Future<Buffer> body() {
        return Future.succeededFuture(Buffer.buffer());
    }

    @Override
    public Future<Void> end() {
        return Future.succeededFuture();
    }

    @Override
    public boolean isEnded() {
        return true;
    }

    // --- Unused features: no-op ---

    @Override
    public HttpServerRequest setExpectMultipart(boolean expect) {
        return this;
    }

    @Override
    public boolean isExpectMultipart() {
        return false;
    }

    @Override
    public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> uploadHandler) {
        return this;
    }

    @Override
    public MultiMap formAttributes() {
        return MultiMap.caseInsensitiveMultiMap();
    }

    @Override
    public String getFormAttribute(String attributeName) {
        return null;
    }

    @Override
    public Future<NetSocket> toNetSocket() {
        return Future.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public Future<ServerWebSocket> toWebSocket() {
        return Future.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
        return this;
    }

    @Override
    public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
        return this;
    }

    @Override
    public HttpServerRequest setParamsCharset(String charset) {
        return this;
    }

    @Override
    public String getParamsCharset() {
        return "UTF-8";
    }

    // --- No-op HttpServerResponse stub ---

    private static class NoOpServerResponse implements HttpServerResponse {

        private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        private final MultiMap trailers = MultiMap.caseInsensitiveMultiMap();

        @Override
        public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public Future<Void> write(Buffer data) {
            return Future.succeededFuture();
        }

        @Override
        public void write(Buffer data, Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public Future<Void> write(String chunk, String enc) {
            return Future.succeededFuture();
        }

        @Override
        public void write(String chunk, String enc, Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public Future<Void> write(String chunk) {
            return Future.succeededFuture();
        }

        @Override
        public void write(String chunk, Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public Future<Void> end() {
            return Future.succeededFuture();
        }

        @Override
        public void end(Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public Future<Void> end(String chunk) {
            return Future.succeededFuture();
        }

        @Override
        public void end(String chunk, Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public Future<Void> end(String chunk, String enc) {
            return Future.succeededFuture();
        }

        @Override
        public void end(String chunk, String enc, Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public Future<Void> end(Buffer chunk) {
            return Future.succeededFuture();
        }

        @Override
        public void end(Buffer chunk, Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public boolean writeQueueFull() {
            return false;
        }

        @Override
        public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public HttpServerResponse drainHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public int getStatusCode() {
            return 200;
        }

        @Override
        public HttpServerResponse setStatusCode(int statusCode) {
            return this;
        }

        @Override
        public String getStatusMessage() {
            return "OK";
        }

        @Override
        public HttpServerResponse setStatusMessage(String statusMessage) {
            return this;
        }

        @Override
        public HttpServerResponse setChunked(boolean chunked) {
            return this;
        }

        @Override
        public boolean isChunked() {
            return false;
        }

        @Override
        public MultiMap headers() {
            return headers;
        }

        @Override
        public HttpServerResponse putHeader(String name, String value) {
            return this;
        }

        @Override
        public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
            return this;
        }

        @Override
        public HttpServerResponse putHeader(String name, Iterable<String> values) {
            return this;
        }

        @Override
        public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
            return this;
        }

        @Override
        public MultiMap trailers() {
            return trailers;
        }

        @Override
        public HttpServerResponse putTrailer(String name, String value) {
            return this;
        }

        @Override
        public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
            return this;
        }

        @Override
        public HttpServerResponse putTrailer(String name, Iterable<String> values) {
            return this;
        }

        @Override
        public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> values) {
            return this;
        }

        @Override
        public HttpServerResponse closeHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public HttpServerResponse endHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public Future<Void> writeHead() {
            return Future.succeededFuture();
        }

        @Override
        public HttpServerResponse writeContinue() {
            return this;
        }

        @Override
        public Future<Void> writeEarlyHints(MultiMap headers) {
            return Future.succeededFuture();
        }

        @Override
        public void writeEarlyHints(MultiMap headers, Handler handler) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
        }

        @Override
        public Future<Void> sendFile(String filename, long offset, long length) {
            return Future.succeededFuture();
        }

        @Override
        public HttpServerResponse sendFile(String filename, long offset, long length, Handler handler) {
            return this;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean ended() {
            return false;
        }

        @Override
        public boolean closed() {
            return false;
        }

        @Override
        public boolean headWritten() {
            return false;
        }

        @Override
        public HttpServerResponse headersEndHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public HttpServerResponse bodyEndHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public long bytesWritten() {
            return 0;
        }

        @Override
        public int streamId() {
            return -1;
        }

        @Override
        public Future<HttpServerResponse> push(HttpMethod method, String host, String path, MultiMap headers) {
            return Future.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public Future<HttpServerResponse> push(HttpMethod method, HostAndPort authority, String path,
                MultiMap headers) {
            return Future.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public boolean reset(long code) {
            return false;
        }

        @Override
        public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
            return this;
        }

        @Override
        public HttpServerResponse addCookie(Cookie cookie) {
            return this;
        }

        @Override
        public Cookie removeCookie(String name, boolean invalidate) {
            return null;
        }

        @Override
        public Set<Cookie> removeCookies(String name, boolean invalidate) {
            return Set.of();
        }

        @Override
        public Cookie removeCookie(String name, String domain, String path, boolean invalidate) {
            return null;
        }
    }
}
