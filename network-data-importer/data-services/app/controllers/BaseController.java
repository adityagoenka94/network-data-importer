package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.commons.exception.ProjectCommonException;
import org.commons.logger.LoggerEnum;
import org.commons.logger.ProjectLogger;
import org.commons.request.Request;
import org.commons.response.Response;
import org.commons.responsecode.ResponseCode;
import org.commons.util.Constants;
import org.dataexporter.actors.RequestRouter;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Results;
import play.mvc.With;
import scala.compat.java8.FutureConverters;
import util.ResponseHeaders;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@With(ResponseHeaders.class)
public class BaseController extends Controller {

private static ActorSystem system;
private  static Timeout timeout;
private static ActorRef actorRef;
//    private HttpExecutionContext httpExecutionContext;


static {
    // Creating Actor for the Data exporter
    ProjectLogger.log("Creating Actor System in BaseController", LoggerEnum.INFO.name());
    Config customConf = ConfigFactory.load();
    system = ActorSystem.create(Constants.ACTOR_SYSTEM, customConf);
    timeout = Timeout.apply(2, TimeUnit.MINUTES);
    actorRef = system.actorOf(RequestRouter.props(), RequestRouter.class.getSimpleName());

}

public BaseController() {

}




    protected CompletionStage<Object> handleCustomRequest(Request request,HttpExecutionContext httpExecutionContext,String className) {
    // To handle the custom request generated after reading the file from the request by using the Actor Model System of Data-Exporter

//        for(int i=0 ; i<3 ; i++)
//        {
//            actorRef.tell(request,ActorRef.noSender());
//        }
        request.setActorClassName(className);
        return FutureConverters.toJava(
                Patterns.ask(actorRef, request, timeout))
                .thenApplyAsync(response -> {
                    if(response instanceof ProjectCommonException) {
                        ProjectCommonException exc = (ProjectCommonException) response;
                        ProjectLogger.log("Actor returned an error : ",exc, LoggerEnum.ERROR.name());
                        exc.setRequestPath(request.getRequestPath());
                        return Results.status(exc.getResponseCode(), Json.toJson(exc.toMap()));
                    }
                    else if(response instanceof Response) {
                        Map<String,Object> responseMap = ((Response)response).getResponse();
                        ProjectLogger.log("Actor Success Response : "+responseMap, LoggerEnum.INFO.name());
                        return responseMap;
//                        return ok(Json.toJson(responseMap));
                    }
                    else {
                        ProjectLogger.log("Unknown response from the Actor "+response, LoggerEnum.WARN.name());
                        ProjectCommonException exc = new ProjectCommonException(ResponseCode.internalServerError);
                        return Results.status(exc.getResponseCode(),Json.toJson(exc.toMap()));
                    }
                },httpExecutionContext.current());
    }

}
