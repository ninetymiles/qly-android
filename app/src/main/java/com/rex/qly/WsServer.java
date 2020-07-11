package com.rex.qly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoWSD;

public class WsServer extends NanoWSD {

    private final Logger mLogger = LoggerFactory.getLogger(WsServer.class);

    private final List<WsStreamSocket> mSocks = new ArrayList<>();
    private final Callback mCallback;

    public interface Callback {
        void onStart(WsStreamSocket ws);
        void onStop(WsStreamSocket ws);
        void onMessage(WsStreamSocket ws, String message);
    }

    public WsServer(int port, Callback callback) {
        super(port);
        mCallback = callback;
    }

    public void sendMessage(String message) {
        //mLogger.trace("");
        synchronized (mSocks) {
            for (WsStreamSocket ws : mSocks) {
                try {
                    //mLogger.trace("send {} message {}<{}>", ws, message.length(), message);
                    ws.send(message);
                } catch (IOException ex) {
                    mLogger.warn("failed to send message - {}", ex.toString());
                }
            }
        }
    }

    public void sendFrame(byte[] frame) {
        //mLogger.trace("");
        synchronized (mSocks) {
            for (WsStreamSocket ws : mSocks) {
                try {
                    //mLogger.trace("send {} frame {}\n{}", ws, frame.length, Common.byteArrayToHexString(frame));
                    ws.send(frame);
                } catch (IOException ex) {
                    mLogger.warn("failed to send frame - {}", ex.toString());
                }
            }
        }
    }

    public void quitSession() {
        mLogger.trace("");
        synchronized (mSocks) {
            for (WsStreamSocket ws : mSocks) {
                try {
                    ws.close(WebSocketFrame.CloseCode.NormalClosure, "User requested quit", false);
                } catch (IOException ex) {
                    mLogger.warn("failed to quit session - {}", ex.toString());
                }
            }
        }
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        WsStreamSocket ws = new WsStreamSocket(this, handshake);
        mLogger.info("ws:{}", ws);
        synchronized (mSocks) {
            mSocks.add(ws);
        }
        if (mCallback != null) {
            mCallback.onStart(ws);
        }
        return ws;
    }

    private void closeWebSocket(WsStreamSocket ws) {
        mLogger.info("");
        boolean existed = false;
        synchronized (mSocks) {
            existed = mSocks.remove(ws);
        }
        if (mCallback != null && existed) {
            mCallback.onStop(ws);
        }
    }

    public class WsStreamSocket extends WebSocket {

        private final WsServer mServer;

        public WsStreamSocket(WsServer server, IHTTPSession handshakeRequest) {
            super(handshakeRequest);
            //mLogger.trace("server:{} request:{}", server, handshakeRequest);
            mServer = server;
        }

        @Override
        protected void onOpen() {
            mLogger.debug("opened");
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            mLogger.debug("closed code:{} reason:{} initiatedByRemote:{}", code, reason, initiatedByRemote);
            closeWebSocket(this);
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            //mLogger.trace("ws:{} message:{}", this, message.getTextPayload());
            if (mCallback != null) {
                mCallback.onMessage(this, message.getTextPayload());
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            //mLogger.trace("pong:{}", pong);
        }

        @Override
        protected void onException(IOException exception) {
            mLogger.warn("- {}", exception.getMessage());
        }
    }
}
