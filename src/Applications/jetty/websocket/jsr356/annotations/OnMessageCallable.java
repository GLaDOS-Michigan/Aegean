//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package Applications.jetty.websocket.jsr356.annotations;

import Applications.jetty.websocket.common.events.annotated.InvalidSignatureException;
import Applications.jetty.websocket.common.util.ReflectUtils;
import Applications.jetty.websocket.jsr356.EncoderFactory;
import Applications.jetty.websocket.jsr356.InitException;
import Applications.jetty.websocket.jsr356.JsrSession;
import Applications.jetty.websocket.jsr356.annotations.Param.Role;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import java.lang.reflect.Method;

public class OnMessageCallable extends JsrCallable {
    protected final Class<?> returnType;
    protected Encoder returnEncoder;
    protected Class<? extends Decoder> decoderClass;
    protected Decoder decoder;
    protected int idxPartialMessageFlag = -1;
    protected int idxMessageObject = -1;
    protected boolean messageRoleAssigned = false;

    public OnMessageCallable(Class<?> pojo, Method method) {
        super(pojo, method);
        this.returnType = method.getReturnType();
    }

    public OnMessageCallable(OnMessageCallable copy) {
        super(copy);
        this.returnType = copy.returnType;
        this.decoderClass = copy.decoderClass;
        this.decoder = copy.decoder;
        this.idxPartialMessageFlag = copy.idxPartialMessageFlag;
        this.idxMessageObject = copy.idxMessageObject;
    }

    protected void assertDecoderRequired() {
        if (getDecoder() == null) {
            StringBuilder err = new StringBuilder();
            err.append("Unable to find a valid ");
            err.append(Decoder.class.getName());
            err.append(" for parameter #");
            Param param = params[idxMessageObject];
            err.append(param.index);
            err.append(" [").append(param.type).append("] in method: ");
            err.append(ReflectUtils.toString(pojo, method));
            throw new InvalidSignatureException(err.toString());
        }
    }

    private int findMessageObjectIndex() {
        int index = -1;

        for (Param.Role role : Param.Role.getMessageRoles()) {
            index = findIndexForRole(role);
            if (index >= 0) {
                return index;
            }
        }

        return -1;
    }

    public Decoder getDecoder() {
        return decoder;
    }

    public Class<? extends Decoder> getDecoderClass() {
        return decoderClass;
    }

    public Param getMessageObjectParam() {
        if (idxMessageObject < 0) {
            idxMessageObject = findMessageObjectIndex();

            if (idxMessageObject < 0) {
                StringBuilder err = new StringBuilder();
                err.append("A message type must be specified [TEXT, BINARY, DECODER, or PONG] : ");
                err.append(ReflectUtils.toString(pojo, method));
                throw new InvalidSignatureException(err.toString());
            }
        }

        return super.params[idxMessageObject];
    }

    public Encoder getReturnEncoder() {
        return returnEncoder;
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    @Override
    public void init(JsrSession session) {
        super.init(session);
        idxPartialMessageFlag = findIndexForRole(Role.MESSAGE_PARTIAL_FLAG);

        EncoderFactory.Wrapper encoderWrapper = session.getEncoderFactory().getWrapperFor(returnType);
        if (encoderWrapper != null) {
            this.returnEncoder = encoderWrapper.getEncoder();
        }

        if (decoderClass != null) {
            try {
                this.decoder = decoderClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new InitException("Unable to create decoder: " + decoderClass.getName(), e);
            }
        }
    }

    public boolean isMessageRoleAssigned() {
        return messageRoleAssigned;
    }

    public boolean isPartialMessageSupported() {
        return (idxPartialMessageFlag >= 0);
    }

    @Override
    public void setDecoderClass(Class<? extends Decoder> decoderClass) {
        this.decoderClass = decoderClass;
        messageRoleAssigned = true;
    }

    public void setPartialMessageFlag(Param param) {
        idxPartialMessageFlag = param.index;
    }
}
