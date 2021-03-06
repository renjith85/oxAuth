/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.auth;

import org.apache.commons.lang.StringUtils;
import org.jboss.seam.Component;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.web.RequestParameter;
import org.jboss.seam.contexts.Context;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.core.Events;
import org.jboss.seam.faces.FacesManager;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage.Severity;
import org.jboss.seam.log.Log;
import org.jboss.seam.security.Identity;
import org.jboss.seam.security.SimplePrincipal;
import org.xdi.model.AuthenticationScriptUsageType;
import org.xdi.model.custom.script.conf.CustomScriptConfiguration;
import org.xdi.oxauth.model.common.Prompt;
import org.xdi.oxauth.model.common.SessionId;
import org.xdi.oxauth.model.common.User;
import org.xdi.oxauth.model.config.ConfigurationFactory;
import org.xdi.oxauth.model.config.Constants;
import org.xdi.oxauth.model.jwt.JwtClaimName;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.model.session.OAuthCredentials;
import org.xdi.oxauth.model.session.SessionClient;
import org.xdi.oxauth.service.AuthenticationService;
import org.xdi.oxauth.service.ClientService;
import org.xdi.oxauth.service.SessionIdService;
import org.xdi.oxauth.service.UserService;
import org.xdi.oxauth.service.external.ExternalAuthenticationService;
import org.xdi.util.StringHelper;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Authenticator component
 *
 * @author Javier Rojas Blum
 * @author Yuriy Movchan
 * @version 0.9 January 29, 2015
 */
@Name("authenticator")
@Scope(ScopeType.EVENT)
// Do not change scope, we try to keep server without http sessions
public class Authenticator implements Serializable {

    private static final long serialVersionUID = 669395320060928092L;

    @Logger
    private Log log;

    @In
    private Identity identity;

    @In
    private OAuthCredentials credentials;

    @In
    private UserService userService;

    @In
    private ClientService clientService;

    @In
    private SessionIdService sessionIdService;

    @In
    private AuthenticationService authenticationService;

    @In
    private ExternalAuthenticationService externalAuthenticationService;

    @In
    private FacesMessages facesMessages;

    @RequestParameter("auth_step")
    private Integer authStep;

    @RequestParameter("auth_level")
    private String authLevel;

    @RequestParameter("auth_mode")
    private String authMode;

    @RequestParameter(JwtClaimName.AUTHENTICATION_METHOD_REFERENCES)
    private String authAcr;

    /**
     * Tries to authenticate an user, returns <code>true</code> if the
     * authentication succeed
     *
     * @return Returns <code>true</code> if the authentication succeed
     */
    public boolean authenticate() {
        if (!authenticateImpl(Contexts.getEventContext(), true)) {
            return authenticationFailed();
        } else {
            return true;
        }
    }

    public String authenticateWithOutcome() {
        boolean result = authenticateImpl(Contexts.getEventContext(), true);
        if (result) {
            return Constants.RESULT_SUCCESS;
        } else {
            return Constants.RESULT_FAILURE;
        }

    }

    public boolean authenticateWebService() {
        return authenticateImpl(getWebServiceContext(), false);
    }

    public Context getWebServiceContext() {
        //    	org.jboss.seam.resteasy.Application application = (org.jboss.seam.resteasy.Application) Component.getInstance(org.jboss.seam.resteasy.Application.class);
        //    	if ((application == null) || application.isDestroySessionAfterRequest()) {
        return Contexts.getEventContext();
        //    	} else {
        // ATTENTION : WE CAN'T USE SESSION CONTEXT because in cluster we are not going to have session replication.
        //            return Contexts.getSessionContext();
        //    	}
    }

    public boolean authenticateImpl(Context context, boolean interactive) {
        Map<String, String> restoredRequestParametersFromSession = authenticationService.restoreRequestParametersFromSession();
        initCustomAuthenticatorVariables(restoredRequestParametersFromSession);

        setAuthModeFromAcr();

        if (interactive && (this.authStep == null)) {
            return false;
        }

        try {
            if (StringHelper.isNotEmpty(credentials.getUsername()) && StringHelper.isNotEmpty(credentials.getPassword())
                    && credentials.getUsername().startsWith("@!")) {

                boolean isServiceUsesExternalAuthenticator = !interactive && externalAuthenticationService.isEnabled(AuthenticationScriptUsageType.SERVICE);
                if (isServiceUsesExternalAuthenticator) {
                    CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
                            .determineCustomScriptConfiguration(AuthenticationScriptUsageType.SERVICE, 1, this.authLevel, this.authMode);

                    if (customScriptConfiguration == null) {
                        log.error("Failed to get CustomScriptConfiguration. auth_step: {0}, auth_mode: {1}, auth_level: {2}",
                                this.authStep, this.authMode, authLevel);
                    } else {
                        this.authMode = customScriptConfiguration.getCustomScript().getName();

                        boolean result = externalAuthenticationService.executeExternalAuthenticate(
                                customScriptConfiguration, null, 1);
                        log.info("Authentication result for {0}. auth_step: {1}, result: {2}", credentials.getUsername(), this.authStep, result);

                        if (result) {
                            configureSessionClient(context);

                            log.info("Authentication success for Client: {0}", credentials.getUsername());
                            return true;
                        }
                    }
                }

                boolean loggedIn = clientService.authenticate(credentials.getUsername(), credentials.getPassword());
                if (loggedIn) {
                    configureSessionClient(context);

                    log.info("Authentication success for Client: {0}", credentials.getUsername());
                    return true;
                }
            } else {
                if (interactive) {
                    if (externalAuthenticationService.isEnabled(AuthenticationScriptUsageType.INTERACTIVE)) {
                        ExternalContext extCtx = FacesContext.getCurrentInstance().getExternalContext();
                        CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
                                .determineCustomScriptConfiguration(AuthenticationScriptUsageType.INTERACTIVE, 1, this.authLevel, this.authMode);

                        if (customScriptConfiguration == null) {
                            log.error("Failed to get CustomScriptConfiguration. auth_step: {0}, auth_mode: {1}, auth_level: {2}",
                                    this.authStep, this.authMode, this.authLevel);
                            return false;
                        }

                        this.authMode = customScriptConfiguration.getName();

                        boolean result = externalAuthenticationService.executeExternalAuthenticate(
                                customScriptConfiguration, extCtx.getRequestParameterValuesMap(), this.authStep);
                        log.info("Authentication result for {0}. auth_step: {1}, result: {2}", credentials.getUsername(), this.authStep, result);
                        if (!result) {
                            return false;
                        }

                        int countAuthenticationSteps = externalAuthenticationService
                                .executeExternalGetCountAuthenticationSteps(customScriptConfiguration);
                        if (this.authStep < countAuthenticationSteps) {
                            final int nextStep = this.authStep + 1;
                            String redirectTo = externalAuthenticationService.executeExternalGetPageForStep(
                                    customScriptConfiguration, nextStep);
                            if (StringHelper.isEmpty(redirectTo)) {
                                return false;
                            }

                            Contexts.getEventContext().set("auth_step", Integer.toString(nextStep));
                            Contexts.getEventContext().set("auth_mode", this.authMode);

                            List<String> extraParameters = externalAuthenticationService.executeExternalGetExtraParametersForStep(customScriptConfiguration, nextStep);

                            Map<String, String> parametersMap;
                            if (restoredRequestParametersFromSession == null) {
                                parametersMap = authenticationService.getParametersMap(extraParameters);
                            } else {
                                parametersMap = authenticationService.getParametersMap(extraParameters, restoredRequestParametersFromSession);
                            }

                            log.trace("Redirect to page: {0}", redirectTo);
                            FacesManager.instance().redirect(redirectTo, (Map) parametersMap, false);
                            return false;
                        }

                        if (this.authStep == countAuthenticationSteps) {
                            authenticationService.configureEventUser(interactive);

                            Principal principal = new SimplePrincipal(credentials.getUsername());
                            identity.acceptExternallyAuthenticatedPrincipal(principal);
                            identity.quietLogin();

                            // Redirect back to original page
                            if (Events.exists()) {
                                // Parameter authMode is an workaround for basic authentication when there is only one step
                                log.info("Sending event to trigger user redirection: {0}", credentials.getUsername());
                                Events.instance().raiseEvent(Constants.EVENT_OXAUTH_CUSTOM_LOGIN_SUCCESSFUL, this.authMode, restoredRequestParametersFromSession);
                            }

                            log.info("Authentication success for User: {0}", credentials.getUsername());
                            return true;
                        }
                    } else {
                        if (StringHelper.isNotEmpty(credentials.getUsername())) {
                            boolean authenticated = authenticationService.authenticate(credentials.getUsername(), credentials.getPassword());
                            if (authenticated) {
                                authenticationService.configureEventUser(interactive);

                                // Redirect back to original page
                                if (Events.exists()) {
                                    // Parameter authMode is an workaround for basic authentication when there is only one step
                                    log.info("Sending event to trigger user redirection: {0}", credentials.getUsername());
                                    Events.instance().raiseEvent(Constants.EVENT_OXAUTH_CUSTOM_LOGIN_SUCCESSFUL, this.authMode, null);
                                }

                                log.info("Authentication success for User: {0}", credentials.getUsername());
                                return true;
                            }
                        }
                    }
                } else {
                    if (externalAuthenticationService.isEnabled(AuthenticationScriptUsageType.SERVICE)) {
                        CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
                                .determineCustomScriptConfiguration(AuthenticationScriptUsageType.SERVICE, 1, this.authLevel, this.authMode);

                        if (customScriptConfiguration == null) {
                            log.error("Failed to get CustomScriptConfiguration. auth_step: {0}, auth_mode: {1}, auth_level: {2}",
                                    this.authStep, this.authMode, authLevel);
                        } else {
                            this.authMode = customScriptConfiguration.getName();

                            boolean result = externalAuthenticationService.executeExternalAuthenticate(
                                    customScriptConfiguration, null, 1);
                            log.info("Authentication result for {0}. auth_step: {1}, result: {2}", credentials.getUsername(), this.authStep, result);

                            if (result) {
                                authenticateExternallyWebService(credentials.getUsername());
                                authenticationService.configureEventUser(interactive);

                                log.info("Authentication success for User: {0}", credentials.getUsername());
                                return true;
                            }
                        }
                    }

                    if (StringHelper.isNotEmpty(credentials.getUsername())) {
                        boolean authenticated = authenticationService.authenticate(credentials.getUsername(), credentials.getPassword());
                        if (authenticated) {
                            authenticateExternallyWebService(credentials.getUsername());
                            authenticationService.configureEventUser(interactive);

                            log.info("Authentication success for User: {0}", credentials.getUsername());
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        log.info("Authentication failed for {0}", credentials.getUsername());
        return false;
    }

    private void initCustomAuthenticatorVariables(Map<String, String> restoredRequestParametersFromSession) {
        Map<String, String> requestParameters = restoredRequestParametersFromSession;
        if (restoredRequestParametersFromSession == null) {
            return;
        }

        this.authStep = StringHelper.toInteger(requestParameters.get("auth_step"), null);
        this.authLevel = requestParameters.get("auth_level");
        this.authMode = requestParameters.get("auth_mode");
        this.authAcr = requestParameters.get(JwtClaimName.AUTHENTICATION_METHOD_REFERENCES);

    }

    public String prepareAuthenticationForStep() {
        setAuthModeFromAcr();

        if (this.authMode == null) {
            return Constants.RESULT_SUCCESS;
        }

        if ((this.authStep == null) || (this.authStep < 1)) {
            return Constants.RESULT_NO_PERMISSIONS;
        }

        CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
                .determineCustomScriptConfiguration(AuthenticationScriptUsageType.INTERACTIVE, this.authStep, this.authLevel, this.authMode);
        String currentAuthMode = customScriptConfiguration.getName();
        if (customScriptConfiguration == null) {
            log.error("Failed to get CustomScriptConfiguration. auth_step: '{0}', auth_mode: '{1}'", this.authStep, this.authMode);
            return Constants.RESULT_FAILURE;
        }


        customScriptConfiguration = externalAuthenticationService
                .determineExternalAuthenticatorForWorkflow(
                        AuthenticationScriptUsageType.INTERACTIVE,
                        customScriptConfiguration);
        if (customScriptConfiguration == null) {
            return Constants.RESULT_FAILURE;
        } else {
            String determinedAuthMode = customScriptConfiguration
                    .getName();
            if (!StringHelper.equalsIgnoreCase(currentAuthMode,
                    determinedAuthMode)) {
                // Redirect user to alternative login workflow
                String redirectTo = externalAuthenticationService.executeExternalGetPageForStep(
                        customScriptConfiguration, this.authStep);

                if (StringHelper.isEmpty(redirectTo)) {
                    redirectTo = "/login.xhtml";
                }

                log.debug(
                        "Redirect to page: {0}. Force to use auth_mode: '{1}'",
                        redirectTo, determinedAuthMode);

                Map<String, String> parametersMap = authenticationService
                        .getParametersMap(null);
                parametersMap.put("auth_mode", determinedAuthMode);
                FacesManager.instance().redirect(redirectTo,
                        (Map) parametersMap, false);

                return Constants.RESULT_SUCCESS;
            }
        }


        ExternalContext extCtx = FacesContext.getCurrentInstance().getExternalContext();
        Boolean result = externalAuthenticationService.executeExternalPrepareForStep(customScriptConfiguration,
                extCtx.getRequestParameterValuesMap(), authStep);
        if (result == null) {
            return Constants.RESULT_FAILURE;
        }

        if (result) {
            return Constants.RESULT_SUCCESS;
        } else {
            return Constants.RESULT_FAILURE;
        }
    }

    public void authenticateExternallyWebService(String userName) {
        org.jboss.seam.resteasy.Application application = (org.jboss.seam.resteasy.Application) Component
                .getInstance(org.jboss.seam.resteasy.Application.class);
        if ((application != null) && !application.isDestroySessionAfterRequest()) {
            Principal principal = new SimplePrincipal(userName);
            identity.acceptExternallyAuthenticatedPrincipal(principal);
            identity.quietLogin();
        }
    }

    public void configureSessionClient(Context context) {
        identity.addRole("client");

        Client client = clientService.getClient(credentials.getUsername());
        SessionClient sessionClient = new SessionClient();
        sessionClient.setClient(client);

        context.set("sessionClient", sessionClient);

        clientService.updatAccessTime(client, true);
    }

    public int getCurrentAuthenticationStep() {
        return authStep;
    }

    public void setCurrentAuthenticationStep(int currentAuthenticationStep) {
        this.authStep = currentAuthenticationStep;
    }

    public boolean authenticationFailed() {
        facesMessages.addFromResourceBundle(Severity.ERROR, "login.errorMessage");
        return false;
    }

    public boolean authenticateBySessionId(String p_sessionId) {
        if (StringUtils.isNotBlank(p_sessionId) && ConfigurationFactory.getConfiguration().getSessionIdEnabled()) {
            try {
                SessionId sessionId = sessionIdService.getSessionId(p_sessionId);
                log.trace("authenticateBySessionId, sessionId = {0}, session = {1}", p_sessionId, sessionId);
                if (sessionId != null) {
                    final User user = getUserOrRemoveSession(sessionId);
                    if (user != null) {
                        authenticateExternallyWebService(user.getUserId());
                        authenticationService.configureEventUser(sessionId, new ArrayList<Prompt>(Arrays.asList(Prompt.NONE)));
                        return true;
                    }
                }
            } catch (Exception e) {
                log.trace(e.getMessage(), e);
            }
        }
        return false;
    }

    private User getUserOrRemoveSession(SessionId p_sessionId) {
        if (p_sessionId != null) {
            try {
                if (StringUtils.isNotBlank(p_sessionId.getUserDn())) {
                    final User user = userService.getUserByDn(p_sessionId.getUserDn());
                    if (user != null) {
                        return user;
                    } else { // if there is no user than session is invalid
                        sessionIdService.remove(p_sessionId);
                    }
                } else { // if there is no user than session is invalid
                    sessionIdService.remove(p_sessionId);
                }
            } catch (Exception e) {
                log.trace(e.getMessage(), e);
            }
        }
        return null;
    }

    private void setAuthModeFromAcr() {
        if (StringHelper.isNotEmpty(this.authAcr)) {
            this.authMode = this.authAcr;
        }
    }

}