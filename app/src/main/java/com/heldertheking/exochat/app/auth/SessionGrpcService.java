package com.heldertheking.exochat.app.auth;

import org.springframework.stereotype.Service;

import com.google.protobuf.Empty;
import com.heldertheking.exochat.identity.v1alpha1.DeleteSessionRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetSessionRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListSessionsRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListSessionsResponse;
import com.heldertheking.exochat.identity.v1alpha1.Session;
import com.heldertheking.exochat.identity.v1alpha1.SessionServiceGrpc;
import com.heldertheking.quela.FieldParseException;
import com.heldertheking.quela.FieldResolutionException;
import com.heldertheking.quela.orderby.OrderByParseException;
import com.heldertheking.quela.pagination.InvalidPageTokenException;
import com.heldertheking.quela.search.QueryParseException;
import com.heldertheking.quela.search.UnsupportedOperatorException;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/// Lists and revokes the sessions of the caller. A user only ever sees and revokes its own sessions.
@Service
class SessionGrpcService extends SessionServiceGrpc.SessionServiceImplBase {
    private final SessionRepository sessions;

    SessionGrpcService(SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> responseObserver) {
        var caller = Caller.current();
        var parent = NameParsing.user("parent", request.getParent());
        caller.requireUser(parent);
        try {
            var page = sessions.list(
                    caller.userId(),
                    request.getFilter(),
                    request.getOrderBy(),
                    request.getPageSize(),
                    request.getPageToken());
            var response = ListSessionsResponse.newBuilder().setNextPageToken(page.getNextPageToken());
            page.forEach(row ->
                    response.addSessions(AuthMapper.session(row, row.getId().equals(caller.sessionId()))));
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (QueryParseException
                | OrderByParseException
                | FieldResolutionException
                | FieldParseException
                | UnsupportedOperatorException
                | InvalidPageTokenException e) {
            throw Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
        }
    }

    @Override
    public void getSession(GetSessionRequest request, StreamObserver<Session> responseObserver) {
        var caller = Caller.current();
        var name = NameParsing.session("name", request.getName());
        caller.requireUser(name.getParent());
        var row = sessions.find(caller.userId(), name.getPersistenceId())
                .orElseThrow(() ->
                        Status.NOT_FOUND.withDescription("session not found").asRuntimeException());
        responseObserver.onNext(AuthMapper.session(row, row.getId().equals(caller.sessionId())));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteSession(DeleteSessionRequest request, StreamObserver<Empty> responseObserver) {
        var caller = Caller.current();
        var name = NameParsing.session("name", request.getName());
        caller.requireUser(name.getParent());
        if (!sessions.delete(caller.userId(), name.getPersistenceId())) {
            throw Status.NOT_FOUND.withDescription("session not found").asRuntimeException();
        }
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
