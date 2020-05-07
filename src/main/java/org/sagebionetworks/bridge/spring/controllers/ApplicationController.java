package org.sagebionetworks.bridge.spring.controllers;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.AndroidAppSiteAssociation;
import org.sagebionetworks.bridge.models.AppleAppSiteAssociation;
import org.sagebionetworks.bridge.models.apps.AndroidAppLink;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.AppleAppLink;
import org.sagebionetworks.bridge.services.UrlShortenerService;

/**
 * A controller for the few non-REST endpoints in our application.
 */
@CrossOrigin
@Controller
public class ApplicationController extends BaseController {
    static final String ROBOTS_TXT_CONTENT = "# robotstxt.org\n\nUser-agent: *\n";

    @SuppressWarnings("serial")
    private static final class AndroidAppLinkList extends ArrayList<AndroidAppSiteAssociation> {};

    static final String PASSWORD_DESCRIPTION = "passwordDescription";
    static final String APP_NAME = "appName";
    static final String SUPPORT_EMAIL = "supportEmail";
    static final String APP_ID = "appId";
    
    private ViewCache viewCache;
    
    private UrlShortenerService urlShortenerService;

    @Resource(name = "appLinkViewCache")
    final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }
    
    @Autowired
    final void setUrlShortenerService(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }
    
    @GetMapping(path="/robots.txt", produces = "text/plain")
    public ResponseEntity<String> getRobots(Model model) {
        return ResponseEntity.ok(ROBOTS_TXT_CONTENT);
    }
    
    @GetMapping({"/", "/index.html"})
    public String loadApp(Model model) {
        return "index";
    }
    
    @GetMapping({"/mobile/verifyStudyEmail.html", "/mobile/verifyAppEmail.html", "/vse", "/vae"})
    public String verifyAppEmail(Model model, 
            @RequestParam(name="appId", required=false) String appId,
            @RequestParam(name="study", required=false) String studyId) {
        App app = appService.getApp(appId != null ? appId : studyId);
        model.addAttribute(APP_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        return "verifyAppEmail";
    }
    
    @GetMapping({"/mobile/verifyEmail.html", "/ve"})
    public String verifyEmail(Model model, 
            @RequestParam(name="appId", required=false) String appId,
            @RequestParam(name="study", required=false) String studyId) {
        App app = appService.getApp(appId != null ? appId : studyId);
        model.addAttribute(APP_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        model.addAttribute(SUPPORT_EMAIL, app.getSupportEmail());
        model.addAttribute(APP_ID, app.getIdentifier());
        return "verifyEmail";
    }
    
    @GetMapping({"/mobile/resetPassword.html", "/rp"})
    public String resetPassword(Model model, 
            @RequestParam(name="appId", required=false) String appId,
            @RequestParam(name="study", required=false) String studyId) {
        App app = appService.getApp(appId != null ? appId : studyId);
        String passwordDescription = BridgeUtils.passwordPolicyDescription(app.getPasswordPolicy());
        model.addAttribute(APP_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        model.addAttribute(SUPPORT_EMAIL, app.getSupportEmail());
        model.addAttribute(APP_ID, app.getIdentifier());
        model.addAttribute(PASSWORD_DESCRIPTION, passwordDescription);
        return "resetPassword";
    }
    
    /* Full URL to phone will include email and token, but these are not required for the error page. */
    @GetMapping({"/mobile/{appId}/startSession.html", "/s/{appId}"})
    public String startSessionWithPath(Model model, @PathVariable String appId) {
        App app = appService.getApp(appId);
        model.addAttribute(APP_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        model.addAttribute(APP_ID, app.getIdentifier());
        return "startSession";
    }
    
    /* Full URL to phone will include email and token, but these are not required for the error page. */
    @GetMapping("/mobile/startSession.html")
    public String startSessionWithQueryParam(Model model,
            @RequestParam(name = "appId", required = false) String appId,
            @RequestParam(name = "study", required = false) String studyId) {
        App app = appService.getApp(appId != null ? appId : studyId);
        model.addAttribute(APP_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        model.addAttribute(APP_ID, app.getIdentifier());
        return "startSession";
    }
    
    // The JSON produces attribute is required here, because we're returning a string and @Controller + @ResponseBody 
    // determine it's of type text/plain
    @GetMapping(value="/.well-known/assetlinks.json", produces={MediaType.APPLICATION_JSON_UTF8_VALUE})
    @ResponseBody
    public String androidAppLinks() {
        CacheKey cacheKey = viewCache.getCacheKey(AndroidAppLinkList.class);
        String json = viewCache.getView(cacheKey, () -> {
            AndroidAppLinkList links = new AndroidAppLinkList();
            List<App> apps = appService.getApps();
            for(App app : apps) {
                for (AndroidAppLink link : app.getAndroidAppLinks()) {
                    links.add(new AndroidAppSiteAssociation(link));
                }
            }
            return links;
        });
        return json;
    }
    
    // The JSON produces attribute is required here, because we're returning a string and @Controller + @ResponseBody
    // determine it's of type text/plain
    @GetMapping(value="/.well-known/apple-app-site-association", produces={MediaType.APPLICATION_JSON_UTF8_VALUE})
    @ResponseBody
    public String appleAppLinks() {
        CacheKey cacheKey = viewCache.getCacheKey(AppleAppSiteAssociation.class);
        String json = viewCache.getView(cacheKey, () -> {
            List<AppleAppLink> links = Lists.newArrayList();
            List<App> apps = appService.getApps();
            for(App app : apps) {
                links.addAll(app.getAppleAppLinks());
            }
            return new AppleAppSiteAssociation(links);
        });
        return json;
    }
    
    @GetMapping("/r/{token}")
    public ResponseEntity<String> redirectToURL(@PathVariable String token) {
        if (StringUtils.isBlank(token)) {
            throw new BadRequestException("URL is malformed.");
        }
        String url = urlShortenerService.retrieveUrl(token);
        if (url != null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
        }
        return ResponseEntity.notFound().build();
    }
}
