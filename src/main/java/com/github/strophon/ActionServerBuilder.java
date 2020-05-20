package com.github.strophon;

import com.github.strophon.init.Instantiator;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class ActionServerBuilder {
    private Vertx vertx;
    private Instantiator instantiator;

    private int serverInstances;
    private int actionVerticleInstances;
    private int eventVerticleInstances;

    private DeploymentOptions serverDeploymentOptions = new DeploymentOptions();
    private DeploymentOptions actionVerticleDeploymentOptions = new DeploymentOptions();
    private DeploymentOptions eventVerticleDeploymentOptions = new DeploymentOptions();

    public ActionServerBuilder(Vertx vertx, String instantiatorClass) {
        this(vertx, getInstantiator(instantiatorClass));
    }

    public ActionServerBuilder(Vertx vertx, String instantiatorClass, int numEventLoops) {
        this(vertx, getInstantiator(instantiatorClass), numEventLoops);
    }

    public ActionServerBuilder(Vertx vertx, Instantiator instantiator) {
        this(vertx, instantiator, VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
    }

    public ActionServerBuilder(Vertx vertx, Instantiator instantiator, int numEventLoops) {
        this.vertx = vertx;
        this.instantiator = instantiator;
        serverInstances = numEventLoops / 2;
        actionVerticleInstances = numEventLoops / 2;
        eventVerticleInstances = numEventLoops / 2;
    }

    private static Instantiator getInstantiator(String instantiatorClass) {
        try {
            return (Instantiator) Class.forName(instantiatorClass).newInstance();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public final ActionServerBuilder withServerInstances(int serverInstances) {
        this.serverInstances = serverInstances;
        return this;
    }

    public final ActionServerBuilder withActionVerticleInstances(int actionVerticleInstances) {
        this.actionVerticleInstances = actionVerticleInstances;
        return this;
    }

    public final ActionServerBuilder withEventVerticleInstances(int eventVerticleInstances) {
        this.eventVerticleInstances = eventVerticleInstances;
        return this;
    }

    public final ActionServerBuilder withServerOptions(DeploymentOptions options) {
        return withServerOptions(options, true);
    }

    public final ActionServerBuilder withActionVerticleOptions(DeploymentOptions options) {
        return withActionVerticleOptions(options, true);
    }

    public final ActionServerBuilder withEventVerticleOptions(DeploymentOptions options) {
        return withEventVerticleOptions(options, true);
    }

    public final ActionServerBuilder withServerOptions(DeploymentOptions options,
                                                       boolean useOptionsInstanceNumber) {
        this.serverDeploymentOptions = options;
        if(useOptionsInstanceNumber) {
            serverInstances = options.getInstances();
        }
        return this;
    }

    public final ActionServerBuilder withActionVerticleOptions(DeploymentOptions options,
                                                               boolean useOptionsInstanceNumber) {
        this.actionVerticleDeploymentOptions = options;
        if(useOptionsInstanceNumber) {
            actionVerticleInstances = options.getInstances();
        }
        return this;
    }

    public final ActionServerBuilder withEventVerticleOptions(DeploymentOptions options,
                                                              boolean useOptionsInstanceNumber) {
        this.eventVerticleDeploymentOptions = options;
        if(useOptionsInstanceNumber) {
            eventVerticleInstances = options.getInstances();
        }
        return this;
    }

    public final void build() {
        vertx.deployVerticle(instantiator.getServerVerticleSupplier(),
                serverDeploymentOptions.setInstances(serverInstances));

        vertx.deployVerticle(instantiator.getActionVerticleSupplier(),
                actionVerticleDeploymentOptions.setInstances(actionVerticleInstances));

        vertx.deployVerticle(instantiator.getEventVerticleSupplier(),
                eventVerticleDeploymentOptions.setInstances(eventVerticleInstances));
    }
}
