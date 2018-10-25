package com.masmovil.apigee;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.slf4j.Slf4j;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ValidatorVerticle extends AbstractVerticle {


    private WebClient client;

    private Map<String,Schema> cacheSchema =new HashMap<>();

    @Override
    public void start(Future<Void> fut) {
        log.info("ahi va el validator");

        // Create a router object.
        Router router = Router.router(vertx);

        // Bind "/" to our hello message - so we are still compatible.
        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response
                    .putHeader("content-type", "text/html")
                    .end("<h1>Hello from validator</h1>");
        });

        router.route("/v1/validator*").handler(BodyHandler.create());
        router.post("/v1/validator/:id").handler(this::validate);
        router.get("/v1/validator/cache").handler(this::getCache);
        router.delete("/v1/validator/cache/:id").handler(this::deleteCache);


        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(// Retrieve the port from the configuration,
                        // default to 8080.
                        config().getInteger("http.port", 8080),
                        result -> {
                            if (result.succeeded()) {
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                            }
                        });
        // Create the web client and enable SSL/TLS with a trust store
        client = WebClient.create(vertx,
                new WebClientOptions()
                        .setSsl(true)
/*
                        .setTrustStoreOptions(new JksOptions()
                                .setPath("client-truststore.jks")
                                .setPassword("wibble")
                        )
*/
        );
    }


    private void getCache(RoutingContext rc) {
        rc.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encode(cacheSchema));
    }

    private void deleteCache(RoutingContext rc) {
        final String id = rc.request().getParam("id");
        if (id == null) {
            rc.response().setStatusCode(400).end();
        } else {
            log.info("borrando de la cache el schema {}",id);
            cacheSchema.remove(id);
        }
        rc.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encode(cacheSchema));


    }

    private void validate(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            log.info("id de schema {}",id);
            Schema schema = cacheSchema.get(id);
            if (schema!=null) {
                log.info("schema cacheado");
                validateGeneric(routingContext, routingContext.getBodyAsString(), schema);
            }else {
                // Send a GET request
                client
                        .get(443, "raw.githubusercontent.com", "/ivan-garcia-santamaria/schema-validator/master/schema_" + id + ".json")
                        .send(ar -> {
                            if (ar.succeeded()) {
                                // Obtain response
                                HttpResponse<Buffer> response = ar.result();

                                log.info("response.statusCode() {}", response.statusCode());
                                log.info("response.headers().get(\"content-type\") {}", response.headers().get("content-type"));
                                String schemaRemote=response.bodyAsString();
                                log.info("schema: {}", schemaRemote);
                                try {
                                    JSONObject schemaObject = new JSONObject(schemaRemote);
                                    Schema schemaNew = SchemaLoader.load(schemaObject);
                                    cacheSchema.put(id, schemaNew);

                                    validateGeneric(routingContext, routingContext.getBodyAsString(), schemaNew);

                                }catch (Exception e) {
                                    log.error("Error obteniendo el schema",e);
                                    routingContext.response().setStatusCode(500).end();
                                }
                            } else {
                                log.info("Something went wrong {}", ar.cause().getMessage());
                            }
                        });
            }
        }
    }

    private void validateGeneric(RoutingContext routingContext, String payload, Schema schema) {

        try {
            final JSONObject input = new JSONObject(payload);
            schema.validate(input); // throws a ValidationException if this object is invalid
            routingContext.response().setStatusCode(200).end();
        } catch (ValidationException e) {
            log.error("ValidationException: {}", e.getAllMessages());
            routingContext.response()
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .setStatusCode(400)
                    .end(Json.encode(e.getAllMessages()));
        }

    }

}


