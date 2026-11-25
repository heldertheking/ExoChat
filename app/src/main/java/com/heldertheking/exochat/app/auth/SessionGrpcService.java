package com.heldertheking.exochat.app.auth;

import org.springframework.stereotype.Service;

import com.google.protobuf.Empty;
import com.heldertheking.exochat.app.grpc.NameParsing;
import com.heldertheking.exochat.app.security.PermissionEvaluator;
import com.heldertheking.exochat.identity.v1alpha1.DeleteSessionRequest;
import com.heldertheking.exochat.identity.v1alpha1.GetSessionRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListSessionsRequest;
import com.heldertheking.exochat.identity.v1alpha1.ListSessionsResponse;
import com.heldertheking.exochat.identity.v1alpha1.Session;
import com.heldertheking.exochat.identity.v1alpha1.SessionServiceGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/// Lists, reads and revokes sessions. Who may do that is decided by the [SessionPermissionChecker].
@Service
class SessionGrpcService extends SessionServiceGrpc.SessionServiceImplBase {
    private final SessionRepository sessions;
    private final SessionPermissionChecker permissionChecker;
    private final PermissionEvaluator permissionEvaluator;

    SessionGrpcService(
            SessionRepository sessions,
            SessionPermissionChecker permissionChecker,
            PermissionEvaluator permissionEvaluator) {
        this.sessions = sessions;
        this.permissionChecker = permissionChecker;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Override
    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> responseObserver) {
        var parent = NameParsing.user("parent", request.getParent());
        permissionChecker.verifyListPermission(parent);
        var currentSession = permissionEvaluator.currentSessionId();
        var page = sessions.list(
                parent.getPersistenceId(),
                request.getFilter(),
                request.getOrderBy(),
                request.getPageSize(),
                request.getPageToken());
        var response = ListSessionsResponse.newBuilder().setNextPageToken(page.getNextPageToken());
        page.forEach(
                row -> response.addSessions(AuthMapper.session(row, row.getId().equals(currentSession))));
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getSession(GetSessionRequest request, StreamObserver<Session> responseObserver) {
        var name = NameParsing.session("name", request.getName());
        permissionChecker.verifyReadPermission(name);
        var row = sessions.find(name.getParent().getPersistenceId(), name.getPersistenceId())
                .orElseThrow(() ->
                        Status.NOT_FOUND.withDescription("session not found").asRuntimeException());
        responseObserver.onNext(AuthMapper.session(row, row.getId().equals(permissionEvaluator.currentSessionId())));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteSession(DeleteSessionRequest request, StreamObserver<Empty> responseObserver) {
        var name = NameParsing.session("name", request.getName());
        permissionChecker.verifyDeletePermission(name);
        if (!sessions.delete(name.getParent().getPersistenceId(), name.getPersistenceId())) {
            throw Status.NOT_FOUND.withDescription("session not found").asRuntimeException();
        }
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
