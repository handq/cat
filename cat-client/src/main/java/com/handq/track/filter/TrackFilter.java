package com.handq.track.filter;

import com.dianping.cat.Cat;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.internal.DefaultTransaction;
import com.handq.track.utils.PatternMatcher;
import com.handq.track.utils.ServletPathMatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * <p>com.handq.track.filter<br/>
 * 创建时间：2016/12/8 12:22<br/>
 * </p>
 *
 * @author handaquan
 * @version V1.0
 */
public class TrackFilter implements Filter {
    public static final String PARAM_NAME_EXCLUSIONS = "exclusions";
    FilterConfig config;
    protected PatternMatcher pathMatcher = new ServletPathMatcher();
    protected String contextPath;
    private Set<String> excludesPattern;

    public TrackFilter() {
    }

    public void destroy() {
        this.config = null;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        String requestURI = this.getRequestURI(httpRequest);
        if(this.isExclusion(requestURI)) {
            chain.doFilter(request, response);
        } else {
            Transaction t = null;

            try {
                boolean e = !Cat.getManager().hasContext();
                if(e) {
                    t = Cat.newTransaction("URL", this.getRequestURI(httpRequest));
                    this.logRequestClientInfo(httpRequest, "URL");
                    this.logRequestPayload(httpRequest, "URL");
                } else {
                    t = Cat.newTransaction("URL.Forward", this.getRequestURI(httpRequest));
                    this.logRequestPayload(httpRequest, "URL.Forward");
                }

                this.customizeStatus(t, httpRequest);
                chain.doFilter(request, response);
            } catch (ServletException var14) {
                Cat.logError(var14);
                t.setStatus(var14);
                throw var14;
            } catch (IOException var15) {
                Cat.logError(var15);
                t.setStatus(var15);
                throw var15;
            } catch (RuntimeException var16) {
                Cat.logError(var16);
                t.setStatus(var16);
                throw var16;
            } catch (Error var17) {
                Cat.logError(var17);
                t.setStatus(var17);
                throw var17;
            } finally {
                if(t != null) {
                    this.customizeUri(t, httpRequest);
                    t.complete();
                }

            }

        }
    }

    public void init(FilterConfig config) throws ServletException {
        this.config = config;
        String exclusions = config.getInitParameter("exclusions");
        if(exclusions != null && exclusions.trim().length() != 0) {
            this.excludesPattern = new HashSet(Arrays.asList(exclusions.split("\\s*,\\s*")));
        }

    }

    public boolean isExclusion(String requestURI) {
        if(this.excludesPattern == null) {
            return false;
        } else {
            if(this.contextPath != null && requestURI.startsWith(this.contextPath)) {
                requestURI = requestURI.substring(this.contextPath.length());
                if(!requestURI.startsWith("/")) {
                    requestURI = "/" + requestURI;
                }
            }

            Iterator i$ = this.excludesPattern.iterator();

            String pattern;
            do {
                if(!i$.hasNext()) {
                    return false;
                }

                pattern = (String)i$.next();
            } while(!this.pathMatcher.matches(pattern, requestURI));

            return true;
        }
    }

    public String getRequestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }

    private void customizeStatus(Transaction t, HttpServletRequest req) {
        Object catStatus = req.getAttribute("cat-state");
        if(catStatus != null) {
            t.setStatus(catStatus.toString());
        } else {
            t.setStatus("0");
        }

    }

    private void customizeUri(Transaction t, HttpServletRequest req) {
        Object catPageUri = req.getAttribute("cat-page-uri");
        if(t instanceof DefaultTransaction && catPageUri instanceof String) {
            ((DefaultTransaction)t).setName(catPageUri.toString());
        }

    }

    protected void logRequestClientInfo(HttpServletRequest req, String type) {
        StringBuilder sb = new StringBuilder(1024);
        String ip = "";
        String ipForwarded = req.getHeader("x-forwarded-for");
        if(ipForwarded == null) {
            ip = req.getRemoteAddr();
        } else {
            ip = ipForwarded;
        }

        sb.append("IPS=").append(ip);
        sb.append("&VirtualIP=").append(req.getRemoteAddr());
        sb.append("&Server=").append(req.getServerName());
        sb.append("&Referer=").append(req.getHeader("referer"));
        sb.append("&Agent=").append(req.getHeader("user-agent"));
        Cat.logEvent(type, type + ".Server", "0", sb.toString());
    }

    protected void logRequestPayload(HttpServletRequest req, String type) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(req.getScheme().toUpperCase()).append('/');
        sb.append(req.getMethod()).append(' ').append(req.getRequestURI());
        String qs = req.getQueryString();
        if(qs != null) {
            sb.append('?').append(qs);
        }

        Cat.logEvent(type, type + ".Method", "0", sb.toString());
    }
}

