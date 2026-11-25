package com.heldertheking.exochat.app.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/// Lets services report problems by throwing a [StatusRuntimeException]: grpc-java would turn every exception that
/// leaves a handler into UNKNOWN. A thrown [StatusRuntimeException] is sent as it is, any other exception becomes
/// INTERNAL without its details (they are logged).
@Component
@GlobalServerInterceptor
class StatusExceptionInterceptor implements ServerInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(StatusExceptionInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return new SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (StatusRuntimeException e) {
                    var trailers = e.getTrailers();
                    call.close(e.getStatus(), trailers == null ? new Metadata() : trailers);
                } catch (RuntimeException e) {
                    LOG.error(
                            "unhandled exception in {}",
                            call.getMethodDescriptor().getFullMethodName(),
                            e);
                    call.close(Status.INTERNAL.withDescription("internal error"), new Metadata());
                }
            }
        };
    }
}
