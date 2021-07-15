package com.dotcms.headerapp.api;

import com.dotcms.security.apps.AppSecrets;
import io.vavr.Tuple2;

import java.util.Collection;
import java.util.Map;

/**
 * In charge of parsing the headers into a
 * @author jsanca
 */
public interface HeaderAppSecretsParser {

    Tuple2<Collection<String>, Map<String, String>> parse (final String headersPatterns, final String headersValues);
}
