package com.github.strophon.web.ecdsa;

import co.paralleluniverse.fibers.Suspendable;
import com.github.strophon.data.DataIO;
import com.github.strophon.data.transfer.User;
import com.github.strophon.init.Instantiator;
import com.github.strophon.util.Misc;
import com.github.strophon.util.Randomizer;
import com.github.strophon.util.SyncUtil;
import com.github.strophon.web.ServerVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.ecdsa.EcdsaAuthHandler;
import io.vertx.ext.auth.ecdsa.EcdsaAuthProvider;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import org.bitcoinj.core.ECKey;

import java.util.Base64;

public abstract class EcdsaAuthServerVerticle extends ServerVerticle {
    private static final Logger logger = LoggerFactory.getLogger(EcdsaAuthServerVerticle.class);

    public EcdsaAuthServerVerticle(Instantiator instantiator) {
        super(instantiator);
    }

    @Override
    protected AuthProvider getAuthProvider() {
        return new EcdsaAuthProvider(vertx, cache, instantiator);
    }

    @Override
    protected AuthHandler getAuthHandler(AuthProvider authProvider) {
        return new EcdsaAuthHandler(authProvider);
    }

    protected boolean useSecretToken() {
        return true;
    }

    @Override
    protected void setUpLoginGateway(Router router) {
        router.route(HttpMethod.GET, "/challenge/:userId/:secretToken")
              .handler(Sync.fiberHandler(this::getChallenge));
    }

    @Suspendable
    private void getChallenge(RoutingContext context) {
        try {
            String clientIpAddress = getClientIp(context);
            if(checkForIpBan(clientIpAddress)) {
                handleIpBan(context, "challenge attempt", clientIpAddress);
                return;
            }

            Integer userId = getUserIdFromIncomingRequest(context);
            if(userId == null) {
                handleUnparseableUserId(context, "challenge attempt", clientIpAddress);
                return;
            }

            String secretToken = context.request().getParam("secretToken");
            if(secretToken == null) {
                logger.info("Failed challenge attempt: no secret token provided. User: " +
                        userId + "; IP: " + clientIpAddress);

                cache.logIpForPotentialBan(clientIpAddress, res -> { });

                context.fail(401);
                return;
            }

            boolean tokenMatches;
            try(DataIO dio = instantiator.getDataIoWithCache()) {
                tokenMatches = SyncUtil.awaitBlockingUnordered(vertx, future -> {
                    User user = dio.getUser(userId, false);
                    if(user == null) {
                        future.complete(false);
                        return;
                    }

                    String userSecretToken = user.getSecretToken();

                    future.complete(Misc.secureEqualsIgnoreCase(userSecretToken, secretToken));
                });
            }

            if(tokenMatches) {
                HttpServerResponse response = context.response();
                String challenge = Randomizer.getFreshToken(instantiator.getTokenSize());


                SyncUtil.<String>await(handler -> cache.setChallenge(userId, challenge, handler));

                logger.info("Challenge issued to user " + userId + ": " + challenge
                        + "; IP: " + clientIpAddress);

                response.end(challenge);
            } else {
                logger.info("Failed challenge attempt: user " + userId + "; IP " + clientIpAddress);

                cache.logIpForPotentialBan(clientIpAddress, res -> { });

                context.fail(401);
            }
        } catch(Exception e) {
            logger.error("Error during getChallenge: ", e);

            context.fail(500);
        }
    }

    @Override
    protected void verifyAndConvertAuthBlob(UserAuthInfo authInfo) {
        byte[] pubkey;
        try {
            pubkey = Base64.getUrlDecoder().decode(authInfo.rawAuthBlob);
            ECKey.fromPublicOnly(pubkey); // ensure public key is valid
            authInfo.parsedAndConvertedAuthBlob = pubkey;
        } catch(Exception e) {
            authInfo.parsedAndConvertedAuthBlob = null;
        }
    }
}
