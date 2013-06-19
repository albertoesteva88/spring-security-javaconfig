/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.config.annotation.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.access.expression.ExpressionBasedFilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.util.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Adds URL based authorization based upon SpEL expressions to an application. At least one
 * {@link org.springframework.web.bind.annotation.RequestMapping} needs to be mapped to {@link ConfigAttribute}'s for
 * this {@link SecurityContextConfigurer} to have meaning.
 * <h2>Security Filters</h2>
 *
 * The following Filters are populated
 *
 * <ul>
 *     <li>{@link org.springframework.security.web.access.intercept.FilterSecurityInterceptor}</li>
 * </ul>
 *
 * <h2>Shared Objects Created</h2>
 *
 * The following shared objects are populated to allow other {@link org.springframework.security.config.annotation.SecurityConfigurer}'s to customize:
 * <ul>
 *     <li>{@link org.springframework.security.web.access.intercept.FilterSecurityInterceptor}</li>
 * </ul>
 *
 * <h2>Shared Objects Used</h2>
 *
 * The following shared objects are used:
 *
 * <ul>
 *     <li>{@link org.springframework.security.config.annotation.web.HttpConfiguration#authenticationManager()}</li>
 * </ul>
 *
 * @author Rob Winch
 * @since 3.2
 * @see {@link org.springframework.security.config.annotation.web.HttpConfiguration#authorizeUrls()}
 */
public final class ExpressionUrlAuthorizations<H extends HttpBuilder<H>> extends BaseInterceptUrlConfigurer<ExpressionUrlAuthorizations<H>.AuthorizedUrl,H> {
    static final String permitAll = "permitAll";
    private static final String denyAll = "denyAll";
    private static final String anonymous = "anonymous";
    private static final String authenticated = "authenticated";
    private static final String fullyAuthenticated = "fullyAuthenticated";
    private static final String rememberMe = "rememberMe";

    private SecurityExpressionHandler<FilterInvocation> expressionHandler = new DefaultWebSecurityExpressionHandler();

    /**
     * Creates a new instance
     * @see HttpConfiguration#authorizeUrls()
     */
    ExpressionUrlAuthorizations() {
    }

    /**
     * Allows customization of the {@link SecurityExpressionHandler} to be used. The default is {@link DefaultWebSecurityExpressionHandler}
     *
     * @param expressionHandler the {@link SecurityExpressionHandler} to be used
     * @return the {@link ExpressionUrlAuthorizations} for further customization.
     */
    public ExpressionUrlAuthorizations<H> expressionHandler(SecurityExpressionHandler<FilterInvocation> expressionHandler) {
        this.expressionHandler = expressionHandler;
        return this;
    }

    @Override
    final AuthorizedUrl chainRequestMatchers(List<RequestMatcher> requestMatchers) {
        return new AuthorizedUrl(requestMatchers);
    }

    @Override
    final List<AccessDecisionVoter> decisionVoters() {
        List<AccessDecisionVoter> decisionVoters = new ArrayList<AccessDecisionVoter>();
        WebExpressionVoter expressionVoter = new WebExpressionVoter();
        expressionVoter.setExpressionHandler(expressionHandler);
        decisionVoters.add(expressionVoter);
        return decisionVoters;
    }

    @Override
    final ExpressionBasedFilterInvocationSecurityMetadataSource createMetadataSource() {
        LinkedHashMap<RequestMatcher, Collection<ConfigAttribute>> requestMap = createRequestMap();
        return requestMap.isEmpty() ? null : new ExpressionBasedFilterInvocationSecurityMetadataSource(requestMap, expressionHandler);
    }

    /**
     * Allows registering multiple {@link RequestMatcher} instances to a collection of {@link ConfigAttribute} instances
     *
     * @param requestMatchers the {@link RequestMatcher} instances to register to the {@link ConfigAttribute} instances
     * @param configAttributes the {@link ConfigAttribute} to be mapped by the {@link RequestMatcher} instances
     * @return the {@link ExpressionUrlAuthorizations} for further customization.
     */
    private ExpressionUrlAuthorizations<H> interceptUrl(Iterable<? extends RequestMatcher> requestMatchers, Collection<ConfigAttribute> configAttributes) {
        for(RequestMatcher requestMatcher : requestMatchers) {
            addMapping(new UrlMapping(requestMatcher, configAttributes));
        }
        return this;
    }

    private static String hasRole(String role) {
        Assert.notNull(role, "role cannot be null");
        if (role.startsWith("ROLE_")) {
            throw new IllegalArgumentException("role should not start with 'ROLE_' since it is automatically inserted. Got '" + role + "'");
        }
        return "hasRole('ROLE_" + role + "')";
    }

    private static String hasAuthority(String authority) {
        return "hasAuthority('" + authority + "')";
    }

    private static String hasAnyAuthority(String... authorities) {
        String anyAuthorities = StringUtils.arrayToDelimitedString(authorities, "','");
        return "hasAnyAuthority('" + anyAuthorities + "')";
    }

    private static String hasIpAddress(String ipAddressExpression) {
        return "hasIpAddress('" + ipAddressExpression + "')";
    }

    public final class AuthorizedUrl {
        private List<RequestMatcher> requestMatchers;

        /**
         * Creates a new instance
         *
         * @param requestMatchers the {@link RequestMatcher} instances to map
         */
        private AuthorizedUrl(List<RequestMatcher> requestMatchers) {
            this.requestMatchers = requestMatchers;
        }

        /**
         * Shortcut for specifying URLs require a particular role. If you do not want to have "ROLE_" automatically
         * inserted see {@link #hasAuthority(String)}.
         *
         * @param role the role to require (i.e. USER, ADMIN, etc). Note, it should not start with "ROLE_" as
         *             this is automatically inserted.
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> hasRole(String role) {
            return access(ExpressionUrlAuthorizations.hasRole(role));
        }

        /**
         * Specify that URLs require a particular authority.
         *
         * @param authority the authority to require (i.e. ROLE_USER, ROLE_ADMIN, etc).
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> hasAuthority(String authority) {
            return access(ExpressionUrlAuthorizations.hasAuthority(authority));
        }

        /**
         * Specify that URLs requires any of a number authorities.
         *
         * @param authorities the requests require at least one of the authorities (i.e. "ROLE_USER","ROLE_ADMIN" would
         *                    mean either "ROLE_USER" or "ROLE_ADMIN" is required).
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> hasAnyAuthority(String... authorities) {
            return access(ExpressionUrlAuthorizations.hasAnyAuthority(authorities));
        }

        /**
         * Specify that URLs requires a specific IP Address or
         * <a href="http://forum.springsource.org/showthread.php?102783-How-to-use-hasIpAddress&p=343971#post343971">subnet</a>.
         *
         * @param ipaddressExpression the ipaddress (i.e. 192.168.1.79) or local subnet (i.e. 192.168.0/24)
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> hasIpAddress(String ipaddressExpression) {
            return access(ExpressionUrlAuthorizations.hasIpAddress(ipaddressExpression));
        }

        /**
         * Specify that URLs are allowed by anyone.
         *
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> permitAll() {
            return access(permitAll);
        }

        /**
         * Specify that URLs are allowed by anonymous users.
         *
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> anonymous() {
            return access(anonymous);
        }

        /**
         * Specify that URLs are allowed by users that have been remembered.
         *
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         * @see {@link RememberMeConfigurer}
         */
        public ExpressionUrlAuthorizations<H> rememberMe() {
            return access(rememberMe);
        }

        /**
         * Specify that URLs are not allowed by anyone.
         *
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> denyAll() {
            return access(denyAll);
        }

        /**
         * Specify that URLs are allowed by any authenticated user.
         *
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> authenticated() {
            return access(authenticated);
        }

        /**
         * Specify that URLs are allowed by users who have authenticated and were not "remembered".
         *
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         * @see {@link RememberMeConfigurer}
         */
        public ExpressionUrlAuthorizations<H> fullyAuthenticated() {
            return access(fullyAuthenticated);
        }

        /**
         * Allows specifying that URLs are secured by an arbitrary expression
         *
         * @param attribute the expression to secure the URLs (i.e. "hasRole('ROLE_USER') and hasRole('ROLE_SUPER')")
         * @return the {@link ExpressionUrlAuthorizations} for further customization
         */
        public ExpressionUrlAuthorizations<H> access(String attribute) {
            interceptUrl(requestMatchers, SecurityConfig.createList(attribute));
            return ExpressionUrlAuthorizations.this;
        }
    }
}