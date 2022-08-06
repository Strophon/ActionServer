package com.github.strophon.web;

import co.paralleluniverse.fibers.Suspendable;
import com.github.strophon.util.Randomizer;
import com.google.gson.Gson;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.sync.SyncVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import com.github.strophon.cache.CacheAccessObject;
import com.github.strophon.data.DataIO;
import com.github.strophon.data.transfer.User;
import com.github.strophon.email.EmailSender;
import com.github.strophon.init.Instantiator;
import com.github.strophon.util.SyncUtil;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import static com.github.strophon.util.Misc.secureEqualsIgnoreCase;

public abstract class ServerVerticle extends SyncVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ServerVerticle.class);
    protected static final Gson GSON = new Gson();

    protected Instantiator instantiator;
    protected CacheAccessObject cache;
    protected EmailSender sender;

    public ServerVerticle(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    @Suspendable
    public void start() {
        sender = instantiator.createEmailSender();

        cache = instantiator.getCache();

        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);

        router.options().handler(context -> context.fail(501));

        router.route().handler(CookieHandler.create());

        SessionStore store = ClusteredSessionStore.create(vertx);

        SessionHandler sessionHandler = SessionHandler.create(store)
                                                      .setCookieHttpOnlyFlag(true)
                                                      .setCookieSecureFlag(true);

        router.route().handler(sessionHandler);

        AuthProvider authProvider = getAuthProvider();

        router.route().handler(UserSessionHandler.create(authProvider));

        setUpLoginGateway(router);

        setUpRegistrationGateway(router);

        setUpEmailConfirmationGateway(router);

        setUpRecoveryInitiationGateway(router);

        setUpRecoveryCompletionGateway(router);

        setUpAdditionalGateways(router);

        AuthHandler authHandler = getAuthHandler(authProvider);

        PostLoginHandler postLoginHandler = instantiator.createPostLoginHandler(vertx);

        BridgeOptions bridgeOptions = getDefaultBridgeOptions();
        setAdditionalBridgeOptions(bridgeOptions);

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        sockJSHandler.bridge(bridgeOptions);

        String eventBusAddress = getEventBusAddress();
        eventBusAddress += (eventBusAddress.endsWith("/") ? "" : "/") + "*";

        router.route(eventBusAddress)
              .handler(authHandler)
              .handler(Sync.fiberHandler(postLoginHandler))
              .handler(sockJSHandler);

        server.requestHandler(router::accept).listen(8080);

        vertx.eventBus().consumer("pause", this::pause);

        vertx.eventBus().consumer("resume", this::resume);

        startEventBusServices();
    }

    /**
     * Instantiates an <code>AuthProvider</code> for the server to use.
     * @return instantiated <code>AuthProvider</code>
     */
    protected abstract AuthProvider getAuthProvider();

    /**
     * Specifies the relative location of the event bus for this <code>ServerVerticle</code>. For
     * example, <code>"/eventbus"</code>.
     * @return the relative location of the event bus
     */
    protected String getEventBusAddress() {
        return "/eventbus/";
    }

    /**
     * Instantiates an <code>AuthHandler</code> for the server to use.
     * @return instantiated <code>AuthHandler</code>
     */
    protected abstract AuthHandler getAuthHandler(AuthProvider authProvider);

    private void pause(Message<String> msg) {
        cache.pause(res -> {
            if(res.succeeded()) {
                msg.reply("Successfully paused system.");
            } else {
                msg.reply("Failed to pause system.");
            }
        });
    }

    private void resume(Message<String> msg) {
        cache.resume(res -> {
            if(res.succeeded()) {
                msg.reply("Successfully resumed system.");
            } else {
                msg.reply("Failed to resume system.");
            }
        });
    }

    /**
     * Sets up the login gateway URL that clients will use to log into the server.
     * @param router the <code>Router</code> object to use to set up the gateway
     */
    protected abstract void setUpLoginGateway(Router router);

    /**
     * Sets up the registration gateway URL that clients will use to register a new user.
     * @param router the <code>Router</code> object to use to set up the gateway
     */
    protected void setUpRegistrationGateway(Router router) {
        String address =
                "/register/:name/:email/" + (useSecretToken() ? ":secretToken/" : "") + ":authBlob";
        router.route(HttpMethod.PUT, address).handler(Sync.fiberHandler(this::registerUser));
    }

    /**
     * Specifies whether or not to require a secret token value for authentication
     * @return <code>true</code> if secret token required for authentication, <code>false</code> if
     * not
     */
    protected abstract boolean useSecretToken();

    /**
     * Sets up the confirmation gateway URL that users will use to confirm their email addresses.
     * @param router the <code>Router</code> object to use to set up the gateway
     */
    protected void setUpEmailConfirmationGateway(Router router) {
        router.route(HttpMethod.GET, "/confirmEmail/:userId/:emailToken")
              .handler(Sync.fiberHandler(this::confirmEmail));
    }

    /**
     * Sets up the recovery initiation gateway URL that clients will use to recover access to
     * accounts whose credentials have been lost or forgotten.
     * @param router the <code>Router</code> object to use to set up the gateway
     */
    protected void setUpRecoveryInitiationGateway(Router router) {
        router.route(HttpMethod.PUT, "/recover/:email")
              .handler(Sync.fiberHandler(this::recover));
    }

    /**
     * Sets up the recovery completion gateway URL that clients will use to complete
     * account recovery after receiving a recovery email.
     * @param router the <code>Router</code> object to use to set up the gateway
     */
    protected void setUpRecoveryCompletionGateway(Router router) {
        String address =
                "/completeRecovery/:userId/:recoveryToken/"
                        + (useSecretToken() ? ":secretToken/" : "") + ":authBlob";

        router.route(HttpMethod.PUT, address).handler(Sync.fiberHandler(this::completeRecovery));
    }

    /**
     * Sets up any further gateway URL(s) that implementers may want to make available.
     * @param router the <code>Router</code> object to use to set up the gateway(s)
     */
    protected abstract void setUpAdditionalGateways(Router router);

    private BridgeOptions getDefaultBridgeOptions() {
        BridgeOptions options = new BridgeOptions();

        options.addInboundPermitted(new PermittedOptions()
                .setAddress("login.confirm")
                .setRequiredAuthority("USER"));
        options.addInboundPermitted(new PermittedOptions()
                .setAddress("action")
                .setRequiredAuthority("USER"));

        options.addInboundPermitted(new PermittedOptions()
                .setAddress("pause")
                .setRequiredAuthority("ADMIN"));
        options.addInboundPermitted(new PermittedOptions()
                .setAddress("resume")
                .setRequiredAuthority("ADMIN"));

        int tokenLength = Randomizer.getFreshToken(instantiator.getTokenSize()).length();

        options.addOutboundPermitted(new PermittedOptions()
                .setAddressRegex("client\\.[A-Z2-7=]{" + tokenLength + "}\\.events"));
        options.addOutboundPermitted(new PermittedOptions()
                .setAddressRegex("client\\.[A-Z2-7=]{" + tokenLength + "}\\.data"));
        options.addOutboundPermitted(new PermittedOptions()
                .setAddressRegex("client\\.[A-Z2-7=]{" + tokenLength + "}\\.statistics"));

        return options;
    }

    /**
     * Allows implementers to set up any additional bridge options they wish to include, to allow
     * their services to function correctly.
     * @param options the existing <code>BridgeOptions</code> object to which new permitted
     *                addresses should be added
     */
    protected abstract void setAdditionalBridgeOptions(BridgeOptions options);

    /**
     * Allows implementers to set up any additional services on the event-bus that they wish to
     * make available to clients.
     */
    protected abstract void startEventBusServices();

    /**
     * Provides the client's IP address. Default configuration is for a server behind a
     * load-balancer that sets client's IP as the <code>X-Forwarded-For</code> header on each
     * request, and returns loopback address if header not present.
     * @param context the Vert.x <code>RoutingContext</code> from which to retrieve the IP address
     * @return the client's IP address in <code>String</code> form
     */
    protected static String getClientIp(RoutingContext context) {
        String clientIp = context.request().getHeader("X-Forwarded-For");
        return clientIp == null ? "127.0.0.1" : clientIp;
    }

    /**
     * Checks the cache to see if we have already received an excessive number of failed requests
     * from this IP address and should ignore further requests.
     * @param clientIpAddress client's IP address, in <code>String</code> form
     * @return <code>true</code> if the IP is banned, <code>false</code> if not
     */
    @Suspendable
    protected boolean checkForIpBan(String clientIpAddress) {
        return SyncUtil.await(handler -> cache.checkForIpBan(clientIpAddress, handler));
    }

    /**
     * Method to log requests made by banned IPs and return an HTTP error 400.
     * @param context the <code>RoutingContext</code> for the request
     * @param requestType the type of request made
     * @param clientIpAddress the banned client IP address, in <code>String</code> form
     */
    protected void handleIpBan(RoutingContext context, String requestType, String clientIpAddress) {
        logger.info("Failed " + requestType + ": banned IP " + clientIpAddress + "; " +
                "threshold: " + cache.getIpErrorThreshold());

        context.fail(400);
    }

    /**
     * Method to retrieve the user ID parameter from an incoming request.
     * @param context the <code>RoutingContext</code> for the request
     * @return the <code>Integer</code> user ID value
     */
    protected Integer getUserIdFromIncomingRequest(RoutingContext context) {
        Integer userId;
        try {
            userId = Integer.parseInt(context.request().getParam("userId"));
        } catch(NumberFormatException e) {
            userId = null;
        }

        return userId;
    }

    /**
     * Method to log requests made with un-parseable user IDs and return an HTTP error 400.
     * @param context the <code>RoutingContext</code> for the request
     * @param requestType the type of request made
     * @param clientIpAddress client's IP address, in <code>String</code> form
     */
    protected void handleUnparseableUserId(RoutingContext context,
                                         String requestType,
                                         String clientIpAddress) {
        logger.info("Failed " + requestType + ": userId \""
                + context.request().getParam("userId")
                + "\" unparseable; IP " + clientIpAddress);

        cache.logIpForPotentialBan(clientIpAddress, res -> { });

        context.fail(400);
    }

    @Suspendable
    private void registerUser(RoutingContext context) {
        try {
            String clientIpAddress = getClientIp(context);
            if(SyncUtil.await(handler -> cache.checkForIpBan(clientIpAddress, handler))) {
                handleIpBan(context, "registration attempt", clientIpAddress);
                return;
            }

            String name = context.request().getParam("name");

            if(name.length() > getMaximumNameLength()) {
                logger.info("Failed registration attempt: name \"" + name + "\" too long; IP "
                        + clientIpAddress);

                cache.logIpForPotentialBan(clientIpAddress, res -> { });

                context.response()
                       .setStatusCode(400).end(
                               "Maximum name length is " + getMaximumNameLength() + " characters.");
                return;
            } else if(name.length() < getMinimumNameLength()) {
                logger.info(
                        "Failed registration attempt: name \"" + name + "\" too short; IP "
                                + clientIpAddress);

                cache.logIpForPotentialBan(clientIpAddress, res -> { });

                context.response()
                       .setStatusCode(400).end(
                               "Minimum name length is " + getMinimumNameLength() + " characters.");
                return;
            }

            if(nameMustStartWithLetter() && !Character.isLetter(name.charAt(0))) {
                logger.info("Failed registration attempt: name \"" + name + "\" "
                        + "starts with non-alphabetic character; IP " + clientIpAddress);

                cache.logIpForPotentialBan(clientIpAddress, res -> { });

                context.response().setStatusCode(400).end("Name must start with a letter.");
                return;
            }

            if(nameCanOnlyContainAlphanumeric()) {
                for(char c : name.toCharArray()) {
                    if(!Character.isLetterOrDigit(c)) {
                        logger.info("Failed registration attempt: name \"" + name + "\" "
                                + "contains non-alphanumeric characters; IP " + clientIpAddress);

                        cache.logIpForPotentialBan(clientIpAddress, res -> { });

                        context.response().setStatusCode(400).end("Name may only contain "
                                + "alphanumeric characters.");
                        return;
                    }
                }
            }

            String strEmail = context.request().getParam("email");
            UserAuthInfo authInfo = retrieveUserAuthInfo(context);

            if(authInfo.parsedAndConvertedAuthBlob == null) { // verification failed
                String defaultErrorMessage = "auth blob invalid/unparseable; IP "
                        + clientIpAddress + "; auth blob: " + authInfo.rawAuthBlob;
                logger.info("Failed registration attempt: " +
                        (authInfo.errorMessage != null ?
                                authInfo.errorMessage : defaultErrorMessage));
                cache.logIpForPotentialBan(clientIpAddress, res -> { });

                context.fail(400);
                return;
            }

            InternetAddress email;

            try {
                email = new InternetAddress(strEmail, true);
            } catch(AddressException e) {
                logger.info("Failed registration attempt: invalid email; IP "
                        + clientIpAddress + "; provided email: " + strEmail);

                cache.logIpForPotentialBan(clientIpAddress, res -> { });

                context.response().setStatusCode(400).end("Invalid email address provided.");
                return;
            }

            try(DataIO dio = instantiator.getDataIoWithCache()) {
                boolean nameIsTaken = isNameTaken(dio, name);
                boolean emailIsTaken = !nameIsTaken && isEmailTaken(dio, email.toString());

                if(nameIsTaken || emailIsTaken) {
                    logger.info("Failed registration attempt: " + (nameIsTaken ? "name " : "email ")
                            + name + " taken; IP " + clientIpAddress);

                    cache.logIpForPotentialBan(clientIpAddress, res -> { });

                    context.response()
                           .setStatusCode(409)
                           .end("The " + (nameIsTaken ? "name " + name : "email "
                                   + strEmail) + " is already in use, sorry!");
                    return;
                }

                String emailToken = Randomizer.getFreshToken(instantiator.getTokenSize());

                User user = createUser(name, email, emailToken);
                updateUserAuthInfo(user, authInfo);

                cache.addEmailToken(user.getId(), emailToken, res -> { });

                SyncUtil.awaitBlockingUnordered(vertx, future -> {
                    try {
                        dio.addUser(user);
                        dio.commitSql();
                        future.complete();
                    } catch(Exception e) {
                        future.fail(e);
                    }
                });

                boolean emailSent = SyncUtil.awaitBlockingUnordered(vertx, future -> {
                    boolean sent = sendEmail(user.getId(), email, name, emailToken);
                    if(!sent) {
                        dio.deleteUser(user);
                        dio.commitSql();
                    }
                    future.complete(sent);
                });

                if(!emailSent) {
                    logger.info("Failed registration attempt: email send failed; IP " +
                            clientIpAddress + "; email address: " + strEmail);
                    context.fail(500);
                    return;
                }

                logger.info("New user registered: " + GSON.toJson(user)
                        + "; IP " + clientIpAddress);

                context.response().headers()
                       .add("userId", String.valueOf(user.getId()));

                context.response()
                       .setStatusCode(201)
                       .end("Registration successful! "
                               + "Check your email to confirm your account and get started!");
            }
        } catch(Exception e) {
            logger.error("Error during registerUser: ", e);

            context.fail(500);
        }
    }

    /**
     * Specifies the maximum number of characters allowed in a user's name.
     * @return maximum name length
     */
    protected abstract int getMaximumNameLength();

    /**
     * Specifies the minimum number of characters allowed in a user's name.
     * @return minimum name length
     */
    protected abstract int getMinimumNameLength();

    /**
     * Specifies whether or not the first character of the user's name must be a letter.
     * @return whether name must start with a letter
     */
    protected abstract boolean nameMustStartWithLetter();

    /**
     * Specifies whether or not the all characters in the name must be alphanumeric.
     * @return whether name must only contain alphanumeric characters
     */
    protected abstract boolean nameCanOnlyContainAlphanumeric();

    /**
     * Method to retrieve user authentication information from a user request's
     * <code>RoutingContext</code>.
     * @param context the <code>RoutingContext</code> from which to retrieve the authentication
     *                information
     * @return a populated <code>UserAuthInfo</code> object
     */
    protected UserAuthInfo retrieveUserAuthInfo(RoutingContext context) {
        UserAuthInfo authInfo = new UserAuthInfo();

        if(useSecretToken()) {
            authInfo.secretToken = context.request().getParam("secretToken");
        }

        authInfo.rawAuthBlob = context.request().getParam("authBlob");

        verifyAndConvertAuthBlob(authInfo);

        return authInfo;
    }

    /**
     * Allows for parsing and verification (for ECDSA authentication) or hashing (for password
     * authentication) of the string value provided by the client for authentication. Should return
     * <code>null</code> if parsing/verification/hashing fails.
     * @param authInfo a <code>UserAuthInfo</code> containing the provided authentication string
     *                 value, to carry back to the caller either the successfully converted
     *                 authentication blob or, optionally, an error message
     * Returns the byte array comprising the authentication blob (e.g. password hash, public key)
     * to be stored in the database, or <code>null</code> if parsing or verification fail
     */
    protected abstract void verifyAndConvertAuthBlob(UserAuthInfo authInfo);

    /**
     * Method for implementers to create a <code>User</code> object (used during registration).
     * @param name user's name
     * @param email user's email address
     * @param emailToken user's email-confirmation token (sent to user via email to confirm
     *                   address ownership)
     * @return the <code>User</code> object representing the user being registered
     */
    protected abstract User createUser(String name, InternetAddress email, String emailToken);

    /**
     * Method to update a <code>User</code> with the authentication information contained in the
     * provided <code>UserAuthInfo</code>
     *
     * @param user     the <code>User</code> to update
     * @param authInfo the authentication information, in a <code>UserAuthInfo</code> object
     */
    protected void updateUserAuthInfo(User user, UserAuthInfo authInfo) {
        user.setAuthBlob(authInfo.parsedAndConvertedAuthBlob);
        if(useSecretToken()) {
            user.setSecretToken(authInfo.secretToken);
        }
    }

    @Suspendable
    private boolean isNameTaken(DataIO dio, String name) {
        return SyncUtil.awaitBlockingUnordered(vertx, future -> {
            try {
                future.complete(dio.isNameUsed(name));
            } catch(Exception e) {
                future.fail(e);
            }
        });
    }

    @Suspendable
    private boolean isEmailTaken(DataIO dio, String email) {
        return SyncUtil.awaitBlockingUnordered(vertx, future -> {
            try {
                future.complete(dio.isEmailUsed(email));
            } catch(Exception e) {
                future.fail(e);
            }
        });
    }

    private boolean sendEmail(int userId, InternetAddress email,
                              String playerName, String emailToken) {
        return sender.sendRegistrationEmail(userId, email, playerName, emailToken);
    }

    @Suspendable
    private void confirmEmail(RoutingContext context) {
        try {
            String clientIpAddress = getClientIp(context);
            if(checkForIpBan(clientIpAddress)) {
                handleIpBan(context, "email confirmation", clientIpAddress);
                return;
            }

            Integer userId = getUserIdFromIncomingRequest(context);
            if(userId == null) {
                handleUnparseableUserId(context, "email confirmation", clientIpAddress);
                return;
            }

            String emailToken = context.request().getParam("emailToken");
            String retrievedToken = SyncUtil.await(handler -> cache.getEmailToken(userId, handler));

            boolean correct = retrievedToken != null
                    && secureEqualsIgnoreCase(retrievedToken, emailToken);

            if(retrievedToken != null && !correct) {
                logger.info("Failed confirmation attempt: token incorrect (submitted: " + emailToken
                        + "; actual: " + retrievedToken + "); IP " + clientIpAddress);
                cache.logIpForPotentialBan(clientIpAddress, res -> { });
                context.fail(401);
                return;
            }

            try(DataIO dio = instantiator.getDataIoWithCache()) {
                User user = SyncUtil.awaitBlocking(vertx, future ->
                        future.complete(dio.getUser(userId, true)));

                boolean success = correct
                        || (user != null && !user.isEmailConfirmed()
                        && secureEqualsIgnoreCase(user.getEmailToken(), emailToken));

                if(success == false) {
                    logger.info("Failed confirmation attempt: "
                            + (user == null ?
                            "user missing"
                            :
                            (user.isEmailConfirmed() ?
                                    "user confirmed already"
                                    :
                                    "token incorrect & missing from cache"
                                            + " (submitted: " + emailToken + "; actual: "
                                            + user.getEmailToken() + ")"))
                            + "; IP " + clientIpAddress);

                    cache.logIpForPotentialBan(clientIpAddress, res -> { });

                    context.fail(401);

                    return;
                }

                if(retrievedToken != null) {
                    cache.removeEmailToken(userId, res -> { });
                }

                dio.setUserEmailConfirmed(user.getId());

                String response = handleConfirmedEmailAndGiveResponse(dio, user);

                dio.commitSql();

                context.response()
                       .putHeader("content-type", "text/html; charset=UTF-8")
                       .setStatusCode(201)
                       .end(response);
            }
        } catch(Exception e) {
            logger.error("Error during confirmEmail: ", e);

            context.fail(500);
        }
    }

    /**
     * Allows implementers to take any appropriate actions upon successful confirmation of user's
     * email address (in particular, updating or inserting rows to any appropriate database tables).
     * All blocking calls must use <code>SyncUtil.awaitBlocking()</code>.
     * IMPORTANT NOTE: It is *imperative* that this method be annotated <code>@Suspendable</code>
     * if any <code>SyncUtil</code> calls are made here!
     * @param dio the <code>DataIO</code> object which will be used to make any database changes
     * @param user the <code>User</code> object representing the user whose email was confirmed
     * @return an HTML response to let the user know their email confirmation succeeded
     */
    @Suspendable
    protected abstract String handleConfirmedEmailAndGiveResponse(DataIO dio, User user);

    @Suspendable
    private void recover(RoutingContext context) {
        String clientIpAddress = getClientIp(context);

        boolean ipBanned = SyncUtil.await(handler -> cache.checkForIpBan(clientIpAddress, handler));
        if(ipBanned) {
            handleIpBan(context, "recovery", clientIpAddress);
            return;
        }

        String strEmail = context.request().getParam("email");
        InternetAddress email;
        try {
            email = new InternetAddress(strEmail, true);
        } catch(AddressException e) {
            logger.info("Failed recovery attempt: invalid email; IP "
                    + clientIpAddress + "; provided email: " + strEmail);

            cache.logIpForPotentialBan(clientIpAddress, res -> { });

            context.response().setStatusCode(400).end("Invalid email address provided.");
            return;
        }

        try(DataIO dio = instantiator.getDataIoWithCache()) {
            User user = SyncUtil.awaitBlockingUnordered(vertx,
                    future -> future.complete(dio.getUserByEmail(email.toString())));

            HttpServerResponse response = context.response();

            if(user != null) {
                String recoveryToken = Randomizer.getFreshToken(getRecoveryTokenSize());
                user.setRecoveryToken(recoveryToken);

                boolean emailSent = SyncUtil.awaitBlockingUnordered(vertx, future -> {
                    boolean sent = sender.sendRecoveryEmail(user.getId(), email,
                            user.getName(), recoveryToken);
                    future.complete(sent);
                });

                if(!emailSent) {
                    logger.info("Failed recovery attempt: email send failed; IP " +
                            clientIpAddress + "; email address: " + strEmail);
                    context.fail(500);
                    return;
                }

                updateUser(dio, user);
            } else {
                cache.logIpForPotentialBan(clientIpAddress, res -> { });
            }

            response.end("If that email address was found in our database, a recovery email has "
                    + "been sent to you.");
        }
    }

    @Suspendable
    private void completeRecovery(RoutingContext context) {
        String clientIpAddress = getClientIp(context);
        if(checkForIpBan(clientIpAddress)) {
            handleIpBan(context, "recovery completion", clientIpAddress);
            return;
        }

        Integer userId = getUserIdFromIncomingRequest(context);
        if(userId == null) {
            handleUnparseableUserId(context, "recovery completion", clientIpAddress);
            return;
        }

        String recoveryToken = context.request().getParam("recoveryToken");
        UserAuthInfo authInfo = retrieveUserAuthInfo(context);

        if(authInfo.parsedAndConvertedAuthBlob == null) { // verification failed
            String defaultErrorMessage = "auth blob invalid/unparseable; IP "
                    + clientIpAddress + "; auth blob: " + authInfo.rawAuthBlob;
            logger.info("Failed recovery attempt: " +
                    (authInfo.errorMessage != null ?
                            authInfo.errorMessage : defaultErrorMessage));
            cache.logIpForPotentialBan(clientIpAddress, res -> { });

            context.fail(400);
            return;
        }

        boolean recoverySucceeded = false;

        try(DataIO dio = instantiator.getDataIoWithCache()) {
            User user = SyncUtil.awaitBlockingUnordered(vertx,
                    future -> future.complete(dio.getUser(userId, false)));

            if(user.getRecoveryToken() != null
                    && user.getRecoveryToken().equalsIgnoreCase(recoveryToken)) {
                recoverySucceeded = true;
                updateUserAuthInfo(user, authInfo);
            }

            user.setRecoveryToken(null);

            updateUser(dio, user);
        }

        if(recoverySucceeded) {
            logger.info("Processed successful recovery completion for user " + userId);

            context.response().end("Recovery succeeded!");
        } else {
            logger.info("Failed recovery attempt: user had no token in DB, or token incorrect.");
            cache.logIpForPotentialBan(clientIpAddress, res -> { });

            context.response().setStatusCode(400).end("Recovery failed.");
        }
    }

    /**
     * Specifies the number of bytes to use for the recovery token sent to users (default value
     * is 5, for an 8-character, 40-bit base32 string). Note that an incorrect recovery token
     * causes the token to be deleted and requires the user to re-initiate recovery, so less
     * entropy is needed for security.
     * @return number of bytes in recovery token
     */
    protected int getRecoveryTokenSize() {
        return 5;
    }

    @Suspendable
    private void updateUser(DataIO dio, User user) {
        SyncUtil.awaitBlockingUnordered(vertx, future -> {
            try {
                dio.updateUser(user);
                dio.commitSql();
                future.complete();
            } catch(Exception e) {
                future.fail(e);
            }
        });
    }

    protected static class UserAuthInfo {
        public String secretToken;
        public String rawAuthBlob;
        public byte[] parsedAndConvertedAuthBlob;

        public String errorMessage;
    }
}
