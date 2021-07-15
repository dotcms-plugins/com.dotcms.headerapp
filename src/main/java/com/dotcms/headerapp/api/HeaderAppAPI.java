package com.dotcms.headerapp.api;

import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.AppsAPI;
import com.dotcms.security.apps.Secret;
import com.dotcms.system.event.local.model.EventSubscriber;
import com.dotcms.util.CollectionsUtils;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.RegEX;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.liferay.util.StringPool;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;

import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import com.dotcms.security.apps.AppSecretSavedEvent;
import com.dotcms.system.event.local.model.KeyFilterable;

/**
 * Encapsulates the logic to retrieve the header for an URL
 * @author jsanca
 */
public class HeaderAppAPI implements EventSubscriber<AppSecretSavedEvent>, KeyFilterable {

    private final static String APP_PROP_NAME = "name";
    public final static String APP_KEY        = "headerapp";
    public final static String APP_YAML_NAME  = APP_KEY + ".yml";

    private static final Collection<Tuple2<Collection<String>, Map<String, String>>> HEADER_CONFIG_404 =
            Collections.emptyList();// Tuple.of(Collections.emptyList(), Collections.emptyMap());

    // host id ->
    //      Collection of configuration
    //          --> for each element: ... Collection of patterns -> list of headers
    private final Map<String, Collection<Tuple2<Collection<String>, Map<String, String>>>>  headerPerHostMap =
            new ConcurrentHashMap<>();

    protected final AppsAPI appsAPI;
    protected final HeaderAppSecretsParser headerAppSecretsParser;

    public HeaderAppAPI() {

        this(APILocator.getAppsAPI(), new DefaultHeaderAppSecretsParser());
    }

    public HeaderAppAPI(final AppsAPI appsAPI,
                        final HeaderAppSecretsParser headerAppSecretsParser) {

        this.appsAPI = appsAPI;
        this.headerAppSecretsParser = headerAppSecretsParser;
    }

    /**
     * Retrieves the header for the url and host (if any)
     * @param url    String url to match
     * @param host   Host   current host
     * @return Map could be null or empty if not has headers to add for this url and host.
     */
    public Optional<Map<String, String>> getHeadersFor(final String url, final Host host) {

        final Optional<Collection<Tuple2<Collection<String>, Map<String, String>>>> siteHeaderConfig =
                this.findHeaderConfigPerSite (host);

        return this.anyMatch(url, siteHeaderConfig);
    }

    private Optional<Map<String, String>> anyMatch(final String url,
                                         final Optional<Collection<Tuple2<Collection<String>, Map<String, String>>>> siteHeaderConfigListOpt) {

        if (siteHeaderConfigListOpt.isPresent() && null != siteHeaderConfigListOpt.get()) {

            for (final Tuple2<Collection<String>, Map<String, String>> siteHeaderConfig : siteHeaderConfigListOpt.get()) {

                if (null != siteHeaderConfig) {

                    final Collection<String> matchPatterns = siteHeaderConfig._1();
                    if (null != matchPatterns && matchPatterns.stream().anyMatch(pattern -> this.match(url, pattern))) {

                        return Optional.ofNullable(siteHeaderConfig._2());
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean match (final String uri, final String pattern) {

        final String uftUri = Try.of(()-> URLDecoder.decode(uri, "UTF-8")).getOrElse(uri);
        return RegEX.containsCaseInsensitive(uftUri, pattern.trim());
    } // match.

    private Optional<Collection<Tuple2<Collection<String>, Map<String, String>>>> findHeaderConfigPerSite(final Host site) {

        if (!this.headerPerHostMap.containsKey(site.getIdentifier())) {

            this.loadConfigurationFromApps (site);
        }

        return Optional.ofNullable(this.headerPerHostMap.get(site.getIdentifier()));
    }

    private void loadConfigurationFromApps(final Host site) {

        final Optional<AppSecrets> appSecrets = Try.of(
                () -> APILocator.getAppsAPI().getSecrets(APP_KEY, true, site, APILocator.systemUser()))
                .getOrElse(Optional.empty());

        if (appSecrets.isPresent()) {

            this.parseAppConfiguration(site.getIdentifier(), appSecrets.get());
        } else {

            // if not found config for this site.
            this.headerPerHostMap.put(site.getIdentifier(), HEADER_CONFIG_404);
        }
    }

    private void parseAppConfiguration(final String siteId, final AppSecrets appSecrets) {

        final Map<String, Secret> secrets = appSecrets.getSecrets();
        final ImmutableList.Builder<Tuple2<Collection<String>, Map<String, String>>> siteHeaderListBuilder =
                new ImmutableList.Builder<>();
        for (Map.Entry<String, Secret> secretEntry : secrets.entrySet()) {

            if (!APP_PROP_NAME.equalsIgnoreCase(secretEntry.getKey())) { // not need to parse the name

                siteHeaderListBuilder.add(this.headerAppSecretsParser.parse(
                        secretEntry.getKey(), secretEntry.getValue().getString()));
            }
        }

        this.headerPerHostMap.put(siteId, siteHeaderListBuilder.build());
    }



    @Override
    public void notify(final AppSecretSavedEvent appSecretSavedEvent) {

        Logger.info(this.getClass().getName(), "Cleaning local cache for : " + appSecretSavedEvent.getKey());
        this.headerPerHostMap.clear();
        Logger.info(this.getClass().getName(), "Cleaned local cache for : " + appSecretSavedEvent.getKey());
    }

    @Override
    public Comparable getKey() {
        return APP_KEY;
    }

    /**
     * The default format is name as list of pattern split by commas such as
     *
     * /*.jpg,/*.png,/*.webp
     *
     * the value will be a header split by semicolon too, such as
     *
     * access-control-allow-credentials:true; access-control-allow-headers:*; access-control-allow-methods: GET,PUT,POST,DELETE,HEAD,OPTIONS,PATCH
     *
     */
    private static class DefaultHeaderAppSecretsParser implements HeaderAppSecretsParser {

        @Override
        public Tuple2<Collection<String>, Map<String, String>> parse(final String headersPatterns,
                                                                     final String headersValues) {

            final String [] patterns = headersPatterns.split(StringPool.COMMA);
            final String [] headerValuesArray = headersValues.split(StringPool.SEMICOLON);
            final ImmutableMap.Builder<String, String> headersMapBuilder =
                    new ImmutableMap.Builder<>();

            for (final String headerValue : headerValuesArray) {

                final String [] headerKeyValueArray = headerValue.trim().split(StringPool.COLON);
                if (headerKeyValueArray.length >= 2) {
                    headersMapBuilder.put(headerKeyValueArray[0].trim(), headerKeyValueArray[1].trim());
                }
            }

            return Tuple.of(Stream.of(patterns).map(String::trim).collect(CollectionsUtils.toImmutableList()),
                    headersMapBuilder.build());
        }
    }
}
