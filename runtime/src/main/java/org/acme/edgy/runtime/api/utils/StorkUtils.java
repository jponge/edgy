package org.acme.edgy.runtime.api.utils;

import io.smallrye.stork.Stork;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.httpproxy.ProxyContext;

public interface StorkUtils {

    static Future<HttpClientRequest> storkFuture(String serviceName, ProxyContext proxyContext) {
        return Future.fromCompletionStage(
                getInstance().getService(serviceName).selectInstance()
                        .convert().toCompletionStage())
                .compose(serviceInstance -> proxyContext.client().request(new RequestOptions()
                        .setHost(serviceInstance.getHost())
                        .setPort(serviceInstance.getPort())));
    }

    private static Stork getInstance() {
        Stork stork = Stork.getInstance();
        if (stork == null) {
            throw new IllegalStateException(
                    "Trying to use a stork but the quarkus-smallrye-stork extension is missing, please add the extension.");
        }
        return stork;
    }
}
