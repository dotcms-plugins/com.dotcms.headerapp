package com.dotcms.headerapp;

import com.dotcms.filters.interceptor.Result;
import com.dotcms.filters.interceptor.WebInterceptor;
import com.dotcms.headerapp.api.HeaderAppAPI;
import com.dotcms.security.apps.AppsAPI;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.util.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * This web interceptor adds the access control allow origin in addition to overrides the request and response
 * @author jsanca
 */
public class HeaderWebInterceptor implements WebInterceptor {

    private static final String ALL_REQUEST = "/*";
    private final HeaderAppAPI  headerAppAPI;

    public HeaderWebInterceptor() {

        this(new HeaderAppAPI());
    }

    public HeaderWebInterceptor(final HeaderAppAPI headerAppAPI) {

        this.headerAppAPI = headerAppAPI;
    }

    @Override
    public String[] getFilters() {
        return new String[] { ALL_REQUEST };
    }

    @Override
    public Result intercept(final HttpServletRequest request,
                            final HttpServletResponse response) throws IOException {

        final Optional<Map<String, String>> headersOpt = this.getHeaders(request);
        if (headersOpt.isPresent() && !headersOpt.get().isEmpty()) {

            headersOpt.get().forEach(response::setHeader);
        }

        return Result.NEXT;
    }

    private Optional<Map<String, String>> getHeaders(final HttpServletRequest request) {

        final String url  = request.getRequestURI();
        final Host   host = WebAPILocator.getHostWebAPI().getCurrentHostNoThrow(request);
        return null != url ? this.headerAppAPI.getHeadersFor(url, host): Optional.empty();
    }

    public HeaderAppAPI getHeaderAppAPI() {
        return headerAppAPI;
    }
}
