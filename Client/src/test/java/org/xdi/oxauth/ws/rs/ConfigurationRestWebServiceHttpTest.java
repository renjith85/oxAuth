/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.ws.rs;

import org.testng.annotations.Test;
import org.xdi.oxauth.BaseTest;
import org.xdi.oxauth.client.OpenIdConfigurationClient;
import org.xdi.oxauth.client.OpenIdConfigurationResponse;

import static org.testng.Assert.*;

/**
 * Functional tests for OpenId Configuration Web Services (HTTP)
 *
 * @author Javier Rojas Blum
 * @version 0.9 January 22, 2015
 */
public class ConfigurationRestWebServiceHttpTest extends BaseTest {

    @Test
    public void requestOpenIdConfiguration() throws Exception {
        showTitle("requestOpenIdConfiguration");

        OpenIdConfigurationClient client = new OpenIdConfigurationClient(configurationEndpoint);
        OpenIdConfigurationResponse response = client.execOpenIdConfiguration();

        showClient(client);
        assertEquals(response.getStatus(), 200, "Unexpected response code");
        assertNotNull(response.getIssuer(), "The issuer is null");
        assertNotNull(response.getAuthorizationEndpoint(), "The authorizationEndpoint is null");
        assertNotNull(response.getTokenEndpoint(), "The tokenEndpoint is null");
        assertNotNull(response.getUserInfoEndpoint(), "The userInfoEndPoint is null");
        assertNotNull(response.getClientInfoEndpoint(), "The clientInfoEndPoint is null");
        assertNotNull(response.getCheckSessionIFrame(), "The checkSessionIFrame is null");
        assertNotNull(response.getEndSessionEndpoint(), "The endSessionEndpoint is null");
        assertNotNull(response.getJwksUri(), "The jwksUri is null");
        assertNotNull(response.getRegistrationEndpoint(), "The registrationEndpoint is null");
        assertNotNull(response.getValidateTokenEndpoint(), "The validateTokenEndpoint is null");
        assertNotNull(response.getFederationMetadataEndpoint(), "The federationMetadataEndpoint is null");
        assertNotNull(response.getFederationEndpoint(), "The federationEndpoint is null");
        assertNotNull(response.getIntrospectionEndpoint(), "The introspectionEndpoint is null");
        assertNotNull(response.getIdGenerationEndpoint(), "The idGenerationEndpoint is null");

        assertTrue(response.getScopesSupported().size() > 0, "The scopesSupported is empty");
        assertTrue(response.getScopeToClaimsMapping().size() > 0, "The scope to claims mapping is empty");
        assertTrue(response.getResponseTypesSupported().size() > 0, "The responseTypesSupported is empty");
        assertTrue(response.getGrantTypesSupported().size() > 0, "The grantTypesSupported is empty");
        assertTrue(response.getAcrValuesSupported().size() > 0, "The acrValuesSupported is empty");
        assertTrue(response.getSubjectTypesSupported().size() > 0, "The subjectTypesSupported is empty");
        assertTrue(response.getUserInfoSigningAlgValuesSupported().size() > 0, "The userInfoSigningAlgValuesSupported is empty");
        assertTrue(response.getUserInfoEncryptionAlgValuesSupported().size() > 0, "The userInfoEncryptionAlgValuesSupported is empty");
        assertTrue(response.getUserInfoEncryptionEncValuesSupported().size() > 0, "The userInfoEncryptionEncValuesSupported is empty");
        assertTrue(response.getIdTokenSigningAlgValuesSupported().size() > 0, "The idTokenSigningAlgValuesSupported is empty");
        assertTrue(response.getIdTokenEncryptionAlgValuesSupported().size() > 0, "The idTokenEncryptionAlgValuesSupported is empty");
        assertTrue(response.getIdTokenEncryptionEncValuesSupported().size() > 0, "The idTokenEncryptionEncValuesSupported is empty");
        assertTrue(response.getRequestObjectSigningAlgValuesSupported().size() > 0, "The requestObjectSigningAlgValuesSupported is empty");
        assertTrue(response.getRequestObjectEncryptionAlgValuesSupported().size() > 0, "The requestObjectEncryptionAlgValuesSupported is empty");
        assertTrue(response.getRequestObjectEncryptionEncValuesSupported().size() > 0, "The requestObjectEncryptionEncValuesSupported is empty");
        assertTrue(response.getTokenEndpointAuthMethodsSupported().size() > 0, "The tokenEndpointAuthMethodsSupported is empty");
        assertTrue(response.getTokenEndpointAuthSigningAlgValuesSupported().size() > 0, "The tokenEndpointAuthSigningAlgValuesSupported is empty");

        assertTrue(response.getDisplayValuesSupported().size() > 0, "The displayValuesSupported is empty");
        assertTrue(response.getClaimTypesSupported().size() > 0, "The claimTypesSupported is empty");
        assertTrue(response.getClaimsSupported().size() > 0, "The claimsSupported is empty");
        assertNotNull(response.getServiceDocumentation(), "The serviceDocumentation is null");
        assertTrue(response.getClaimsLocalesSupported().size() > 0, "The claimsLocalesSupported is empty");
        assertTrue(response.getUiLocalesSupported().size() > 0, "The uiLocalesSupported is empty");
        assertTrue(response.getClaimsParameterSupported(), "The claimsParameterSupported is false");
        assertTrue(response.getRequestParameterSupported(), "The requestParameterSupported is false");
        assertTrue(response.getRequestUriParameterSupported(), "The requestUriParameterSupported is false");
        assertFalse(response.getRequireRequestUriRegistration(), "The requireRequestUriRegistration is true");
        assertNotNull(response.getOpPolicyUri(), "The opPolicyUri is null");
        assertNotNull(response.getOpTosUri(), "The opTosUri is null");
    }
}