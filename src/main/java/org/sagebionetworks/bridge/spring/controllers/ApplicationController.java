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
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.UrlShortenerService;

/**
 * A controller for the few non-REST endpoints in our application.
 */
@CrossOrigin
@Controller("applicationController")
public class ApplicationController extends BaseController {
    @SuppressWarnings("serial")
    private static final class AndroidAppLinkList extends ArrayList<AndroidAppSiteAssociation> {};

    static final String PASSWORD_DESCRIPTION = "passwordDescription";
    static final String STUDY_NAME = "studyName";
    static final String SUPPORT_EMAIL = "supportEmail";
    static final String STUDY_ID = "studyId";
    
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
    
    @GetMapping({"/", "/index.html"})
    public String loadApp(Model model) throws Exception {
        return "index";
    }
    
    @GetMapping({"/mobile/verifyStudyEmail.html", "/vse"})
    public String verifyStudyEmail(Model model, @RequestParam(name="study", defaultValue="api") String studyId) {
        Study study = studyService.getStudy(studyId);
        model.addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        return "verifyStudyEmail";
    }
    
    @GetMapping({"/mobile/verifyEmail.html", "/ve"})
    public String verifyEmail(Model model, @RequestParam(name="study", defaultValue="api") String studyId) {
        Study study = studyService.getStudy(studyId);
        model.addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        model.addAttribute(SUPPORT_EMAIL, study.getSupportEmail());
        model.addAttribute(STUDY_ID, study.getIdentifier());
        return "verifyEmail";
    }
    
    @GetMapping({"/mobile/resetPassword.html", "/rp"})
    public String resetPassword(Model model, @RequestParam(name="study", defaultValue="api") String studyId) {
        Study study = studyService.getStudy(studyId);
        String passwordDescription = BridgeUtils.passwordPolicyDescription(study.getPasswordPolicy());
        model.addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        model.addAttribute(SUPPORT_EMAIL, study.getSupportEmail());
        model.addAttribute(STUDY_ID, study.getIdentifier());
        model.addAttribute(PASSWORD_DESCRIPTION, passwordDescription);
        return "resetPassword";
    }
    
    @GetMapping({"/mobile/{studyId}/startSession.html", "/s/{studyId}"})
    public String startSessionWithPath(Model model, @PathVariable String studyId, @RequestParam String email,
            @RequestParam String token) {
        Study study = studyService.getStudy(studyId);
        model.addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        model.addAttribute(STUDY_ID, study.getIdentifier());
        return "startSession";
    }
    
    @GetMapping("/mobile/startSession.html")
    public String startSessionWithQueryParam(Model model, @RequestParam("study") String studyId,
            @RequestParam String email, @RequestParam String token) {
        Study study = studyService.getStudy(studyId);
        model.addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        model.addAttribute(STUDY_ID, study.getIdentifier());
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
            List<Study> studies = studyService.getStudies();
            for(Study study : studies) {
                for (AndroidAppLink link : study.getAndroidAppLinks()) {
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
            List<Study> studies = studyService.getStudies();
            for(Study study : studies) {
                links.addAll(study.getAppleAppLinks());
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
