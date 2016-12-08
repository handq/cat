package com.handq.track.dubbo;

import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.utils.TimeUtils;
import com.alibaba.dubbo.remoting.TimeoutException;
import com.alibaba.dubbo.rpc.*;
import com.dianping.cat.Cat;
import com.dianping.cat.message.Event;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.internal.DefaultEvent;
import com.site.lookup.util.StringUtils;
import org.codehaus.plexus.logging.Logger;
import org.springframework.util.CollectionUtils;
import org.unidal.lookup.logger.LoggerFactory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>com.handq.track.dubbo<br/>
 * 创建时间：2016/12/8 12:21<br/>
 * </p>
 *
 * @author handaquan
 * @version V1.0
 */

@Activate(
        group = {"provider", "consumer"}
)
public class CatDubboFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(CatDubboFilter.class);
    private ThreadLocal<Cat.Context> dubbo_context = new ThreadLocal();
    private static final long serialVersionUID = 3602770346726780014L;

    public CatDubboFilter() {
    }

    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Cat.Context context = this.getContext();
        this.initContext(context, invocation);
        Transaction t = null;
        DefaultEvent event = null;
        DefaultEvent eventApp = null;
        Map map = null;
        Result result = null;

        Object isConsumerSide;
        try {
            label75: {
                label74: {
                    RpcContext e = RpcContext.getContext();
                    boolean isConsumerSide1 = e.isConsumerSide();
                    if(StringUtils.isEmpty(invoker.getUrl().getParameter("side"))) {
                        if(isConsumerSide1) {
                            break label74;
                        }
                    } else if(invoker.getUrl().getParameter("side").equalsIgnoreCase("consumer")) {
                        break label74;
                    }

                    map = this.invokerBefore(invoker, invocation, t, event, eventApp);
                    t = (Transaction)map.get("t");
                    Cat.logRemoteCallServer(context);
                    break label75;
                }

                map = this.invokerBefore(invoker, invocation, t, event, eventApp);
                t = (Transaction)map.get("t");
                Cat.logRemoteCallClient(context);
            }

            event = (DefaultEvent)map.get("event");
            eventApp = (DefaultEvent)map.get("eventApp");
            RpcInvocation rpcIvocation = (RpcInvocation)invocation;
            this.setAttachment(context, rpcIvocation);
            result = invoker.invoke(invocation);
            if(result.getException() != null) {
                this.catchException(result.getException(), t, invocation);
            } else {
                this.invokerSuccessAfter(t, event, eventApp);
            }

            Result var12 = result;
            return var12;
        } catch (RuntimeException var16) {
            this.catchException(var16, t, invocation);
            if(result == null) {
                throw var16;
            }

            isConsumerSide = result;
        } finally {
            this.invokerFinally(t);
        }

        return (Result)isConsumerSide;
    }

    private void catchException(Throwable e, Transaction t, Invocation invocation) {
        Event event = null;
        String eventMark = invocation.getInvoker().getInterface().getSimpleName() + "." + invocation.getMethodName();
        if(RpcException.class == e.getClass()) {
            Throwable caseBy = e.getCause();
            if(caseBy != null && caseBy.getClass() == TimeoutException.class) {
                event = Cat.newEvent("DUBBO_TIMEOUT_EXCEPT", eventMark);
            } else {
                event = Cat.newEvent("DUBBO_REMOTING_EXCEPT", eventMark);
            }
        } else {
            event = Cat.newEvent("DUBBO_BIZ_EXCEPT", eventMark);
        }

        event.setStatus(e);
        t.addChild(event);
        t.setStatus(e.getClass().getSimpleName());
    }

    private void setAttachment(Cat.Context context, RpcInvocation invocation) {
        invocation.setAttachment("_catRootMessageId", context.getProperty("_catRootMessageId") != null?context.getProperty("_catRootMessageId"):null);
        invocation.setAttachment("_catParentMessageId", context.getProperty("_catParentMessageId") != null?context.getProperty("_catParentMessageId"):null);
        invocation.setAttachment("_catChildMessageId", context.getProperty("_catChildMessageId") != null?context.getProperty("_catChildMessageId"):null);
    }

    private void invokerSuccessAfter(Transaction t, DefaultEvent event, DefaultEvent eventApp) {
        event.setStatus("0");
        eventApp.setStatus("0");
        t.setStatus("0");
    }

    private void invokerFinally(Transaction t) {
        if(t == null) {
            logger.error("GCat Transaction instance is null,please check Gcat client config ...");
        } else {
            t.complete();
            this.reset();
        }
    }

    private Map<String, Object> invokerBefore(Invoker<?> invoker, Invocation invocation, Transaction t, DefaultEvent event, DefaultEvent eventApp) {
        HashMap map;
        label29: {
            RpcContext rpcContext;
            label30: {
                map = new HashMap();
                rpcContext = RpcContext.getContext();
                boolean isConsumerSide = rpcContext.isConsumerSide();
                if(StringUtils.isEmpty(invoker.getUrl().getParameter("side"))) {
                    if(!isConsumerSide) {
                        break label30;
                    }
                } else if(!invoker.getUrl().getParameter("side").equalsIgnoreCase("consumer")) {
                    break label30;
                }

                t = Cat.newTransaction("PigeonCall", invocation.getInvoker().getInterface().getSimpleName() + "." + invocation.getMethodName());
                event = new DefaultEvent("PigeonCall.server", rpcContext.getRemoteAddressString());
                event.setTimestamp(TimeUtils.timestamp() + 300000L);
                t.addChild(event);
                eventApp = new DefaultEvent("PigeonCall.app", StringUtils.isEmpty(invoker.getUrl().getParameter("providerside"))?"ProviderProjectNotYetJoinCat":invoker.getUrl().getParameter("providerside"));
                eventApp.setTimestamp(TimeUtils.timestamp() + 300000L + 100L);
                t.addChild(eventApp);
                break label29;
            }

            t = Cat.newTransaction("PigeonService", invocation.getInvoker().getInterface().getSimpleName() + "." + invocation.getMethodName());
            event = new DefaultEvent("PigeonService.client", rpcContext.getRemoteAddressString());
            event.setTimestamp(TimeUtils.timestamp() + 300000L);
            t.addChild(event);
            eventApp = new DefaultEvent("PigeonService.app", StringUtils.isEmpty(invocation.getAttachment("consumerside"))?"ConsumerProjectNotYetJoinCat":invocation.getAttachment("consumerside"));
            eventApp.setTimestamp(TimeUtils.timestamp() + 300000L + 100L);
            t.addChild(eventApp);
        }

        map.put("t", t);
        map.put("event", event);
        map.put("eventApp", eventApp);
        return map;
    }

    private Cat.Context getContext() {
        final HashMap attachments = new HashMap();
        Cat.Context ctx = (Cat.Context)this.dubbo_context.get();
        if(ctx != null) {
            return ctx;
        } else {
            if(logger.isDebugEnabled()) {
                logger.debug("CatDubbo filter is instancing Cat.Context ...");
            }

            ctx = new Cat.Context() {
                public void addProperty(String key, String value) {
                    attachments.put(key, value);
                }

                public String getProperty(String key) {
                    return (String)attachments.get(key);
                }
            };
            this.dubbo_context.set(ctx);
            return ctx;
        }
    }

    private void initContext(Cat.Context context, Invocation invocation) {
        Map attachmentsMap = invocation.getAttachments();
        if(!CollectionUtils.isEmpty(attachmentsMap)) {
            Iterator i$ = attachmentsMap.entrySet().iterator();

            while(true) {
                Map.Entry entry;
                do {
                    if(!i$.hasNext()) {
                        return;
                    }

                    entry = (Map.Entry)i$.next();
                } while(!((String)entry.getKey()).equals("_catRootMessageId") && !((String)entry.getKey()).equals("_catParentMessageId") && !((String)entry.getKey()).equals("_catChildMessageId"));

                context.addProperty((String)entry.getKey(), (String)entry.getValue());
            }
        }
    }

    private void reset() {
        Cat.Context ctx = (Cat.Context)this.dubbo_context.get();
        if(ctx != null) {
            ctx.addProperty("_catRootMessageId", "");
            ctx.addProperty("_catParentMessageId", "");
            ctx.addProperty("_catChildMessageId", "");
            this.dubbo_context.remove();
        }

    }
}
