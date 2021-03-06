/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.coheigea.cxf.samlsso.idp;

import java.security.Principal;

import javax.security.auth.callback.CallbackHandler;
import javax.ws.rs.core.Response;

import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.security.SecurityContext;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSUsernameTokenPrincipal;
import org.apache.ws.security.handler.RequestData;
import org.apache.ws.security.message.token.UsernameToken;
import org.apache.ws.security.validate.Credential;
import org.apache.ws.security.validate.UsernameTokenValidator;
import org.w3c.dom.Document;

/**
 * A simple filter to validate a Basic Auth username/password via a CallbackHandler
 */
public class BasicAuthFilter implements RequestHandler {

    private CallbackHandler callbackHandler;
    
    @Override
    public Response handleRequest(Message message, ClassResourceInfo arg1) {
        AuthorizationPolicy policy = message.get(AuthorizationPolicy.class);
        if (policy == null || policy.getUserName() == null || policy.getPassword() == null) {
            return Response.status(401).header("WWW-Authenticate", "Basic realm=\"IdP\"").build();
        }

        try {
            UsernameToken token = convertPolicyToToken(policy);
            Credential credential = new Credential();
            credential.setUsernametoken(token);
            
            RequestData data = new RequestData();
            data.setMsgContext(message);
            data.setCallbackHandler(callbackHandler);
            UsernameTokenValidator validator = new UsernameTokenValidator();
            credential = validator.validate(credential, data);
            
            // Create a Principal/SecurityContext
            Principal p = null;
            if (credential != null && credential.getPrincipal() != null) {
                p = credential.getPrincipal();
            } else {
                p = new WSUsernameTokenPrincipal(policy.getUserName(), false);
                ((WSUsernameTokenPrincipal)p).setPassword(policy.getPassword());
            }
            message.put(SecurityContext.class, createSecurityContext(p));
            return null;
        } catch (Exception ex) {
            throw ExceptionUtils.toInternalServerErrorException(ex, null);
        }
    }

    protected UsernameToken convertPolicyToToken(AuthorizationPolicy policy) 
        throws Exception {

        Document doc = DOMUtils.createDocument();
        UsernameToken token = new UsernameToken(false, doc, 
                                                WSConstants.PASSWORD_TEXT);
        token.setName(policy.getUserName());
        token.setPassword(policy.getPassword());
        return token;
    }
    
    protected SecurityContext createSecurityContext(final Principal p) {
        return new SecurityContext() {

            public Principal getUserPrincipal() {
                return p;
            }

            public boolean isUserInRole(String arg0) {
                return false;
            }
        };
    }

    public CallbackHandler getCallbackHandler() {
        return callbackHandler;
    }

    public void setCallbackHandler(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

}
