package com.dotcms.headerapp;

import com.dotcms.filters.interceptor.FilterWebInterceptorProvider;
import com.dotcms.filters.interceptor.WebInterceptorDelegate;
import com.dotcms.headerapp.api.HeaderAppAPI;
import com.dotcms.system.event.local.business.LocalSystemEventsAPI;
import com.dotcms.security.apps.AppSecretSavedEvent;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.filters.AutoLoginFilter;
import com.dotmarketing.filters.InterceptorFilter;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import org.h2.util.IOUtils;
import org.osgi.framework.BundleContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * This Activator adds the header configuration to the app portlet
 * In addition to subscribes the HeaderFilter
 *
 * When stop removes both things from the framework
 * @author jsanca
 */
public class Activator extends GenericBundleActivator {

    private final File installedAppYaml = new File(ConfigUtils.getAbsoluteAssetsRootPath() + File.separator + "server"
            + File.separator + "apps" + File.separator + HeaderAppAPI.APP_YAML_NAME);

    private String interceptorName;
    private final LocalSystemEventsAPI localSystemEventsAPI = APILocator.getLocalSystemEventsAPI();

    @SuppressWarnings ("unchecked")
    public void start (final BundleContext context) throws Exception {

        //Initializing services...
        //initializeServices(context);

        // copy the yaml
        copyAppYml();

        // flush the app cache
        CacheLocator.getAppsCache().clearCache();

        final HeaderWebInterceptor headerWebInterceptor = new HeaderWebInterceptor();
        // add an event that subscribes to changes in App configuration
        subscribeToAppSaveEvent(headerWebInterceptor);

        // add the filter
        final FilterWebInterceptorProvider filterWebInterceptorProvider =
                FilterWebInterceptorProvider.getInstance(Config.CONTEXT);

        final WebInterceptorDelegate delegate =
                    filterWebInterceptorProvider.getDelegate(InterceptorFilter.class);

        this.interceptorName = headerWebInterceptor.getName();
        delegate.addFirst(headerWebInterceptor);
    }


    public void stop (final BundleContext context) throws Exception {

        unsubscribeToAppSaveEvent();
        deleteYml();

        //Unregister the servlet
        final FilterWebInterceptorProvider filterWebInterceptorProvider =
                FilterWebInterceptorProvider.getInstance(Config.CONTEXT);

        final WebInterceptorDelegate delegate =
                filterWebInterceptorProvider.getDelegate(AutoLoginFilter.class);

        delegate.remove(this.interceptorName, true);
    }

    //////

    /**
     * copies the App yaml to the apps directory and refreshes the apps
     *
     * @throws IOException
     */
    private void copyAppYml() throws IOException {

        Logger.info(this.getClass().getName(), "copying YAML File:" + installedAppYaml);
        try (final InputStream in = this.getClass().getResourceAsStream("/" + HeaderAppAPI.APP_YAML_NAME)) {
            IOUtils.copy(in, Files.newOutputStream(installedAppYaml.toPath()));
        }
    }

    /**
     * Deletes the App yaml to the apps directory and refreshes the apps
     *
     * @throws IOException
     */
    private void deleteYml() throws IOException {

        Logger.info(this.getClass().getName(), "deleting the YAML File:" + installedAppYaml);

        if (installedAppYaml.delete()) {
            Logger.info(this.getClass().getName(), "deleted the YAML File:" + installedAppYaml);
        } else {
            Logger.info(this.getClass().getName(), "Couldn't delete the YAML File:" + installedAppYaml);
        }
        CacheLocator.getAppsCache().clearCache();
    }


    /**
     *
     */
    private void subscribeToAppSaveEvent(final HeaderWebInterceptor headerWebInterceptor) {

        Logger.info(this.getClass().getName(), "Subscribing to Header App Save Event");
        localSystemEventsAPI.subscribe(AppSecretSavedEvent.class, headerWebInterceptor.getHeaderAppAPI());
    }


    private void unsubscribeToAppSaveEvent() {
        Logger.info(this.getClass().getName(), "Unsubscribing to Header App Save Event");
        localSystemEventsAPI.unsubscribe(HeaderAppAPI.class);
    }

}
